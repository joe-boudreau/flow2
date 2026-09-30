package com.flow2.comments

import com.mongodb.MongoWriteException
import com.mongodb.client.model.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates.*
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import java.util.UUID
import org.bson.Document
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

private const val DAILY_GLOBAL_COMMENT_LIMIT = 100
private const val DAILY_IP_COMMENT_LIMIT = 5

class CommentRepository(db: MongoDatabase) {

    private val comments = db.getCollection<Document>("comments")
    private val settings = db.getCollection<Document>("comment_settings")
    private val limits = db.getCollection<Document>("comment_limits")
    private val jobs = db.getCollection<Document>("comment_email_jobs")

    suspend fun initialize() {
        comments.createIndex(Indexes.compoundIndex(Indexes.ascending("postId", "createdAt", "_id")))
        comments.createIndex(Indexes.ascending("chirpyId"), IndexOptions().unique(true).partialFilterExpression(exists("chirpyId")))
        comments.createIndex(Indexes.ascending("unsubscribeToken"), IndexOptions().unique(true).partialFilterExpression(exists("unsubscribeToken")))
        comments.createIndex(Indexes.ascending("notificationPending"))
        limits.createIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        jobs.createIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        jobs.createIndex(Indexes.compoundIndex(Indexes.ascending("status", "nextAttempt")))
        settings.updateOne(eq("_id", "global"), setOnInsert("enabled", false), UpdateOptions().upsert(true))
    }

    suspend fun isCommentingEnabled() = settings.find(eq("_id", "global")).firstOrNull()?.getBoolean("enabled", false) ?: false
    suspend fun setCommentingEnabled(value: Boolean) { settings.updateOne(eq("_id", "global"), set("enabled", value), UpdateOptions().upsert(true)) }

    suspend fun getComment(id: String): PublicComment? {
        val comment = comments.find(eq("_id", id)).firstOrNull() ?: return null
        val parent = comment.getString("replyToId")?.let { comments.find(eq("_id", it)).firstOrNull() }
        return comment.toPublicComment(parent?.let { if (it.getBoolean("deleted", false)) "Comment deleted" else it.getString("name") })
    }

    suspend fun getSubscription(id: String): CommentSubscription? {
        val comment = comments.find(and(eq("_id", id), ne("deleted", true))).firstOrNull() ?: return null
        return CommentSubscription(comment.getString("email") ?: return null, comment.getString("unsubscribeToken") ?: return null)
    }

    suspend fun create(
        postId: String,
        name: String,
        body: String,
        email: String?,
        replyToId: String?,
        threadId: String?,
        owner: Boolean,
        notificationPending: Boolean,
        now: Instant,
    ): PublicComment {
        val id = ObjectId().toHexString()
        val comment = Document("_id", id)
            .append("postId", postId)
            .append("name", name)
            .append("body", body)
            .append("createdAt", now.toEpochMilli())
            .append("updatedAt", now.toEpochMilli())
            .append("threadId", threadId ?: id)
            .append("replyToId", replyToId)
            .append("owner", owner)
            .append("deleted", false)

        if (email != null) {
            comment.append("email", email).append("unsubscribeToken", UUID.randomUUID().toString())
        }
        // Persist notification intent with the comment so it survives a restart.
        if (notificationPending) comment.append("notificationPending", true)

        comments.insertOne(comment)
        val parent = replyToId?.let { getComment(it) }
        return comment.toPublicComment(parent?.let { if (it.deleted) "Comment deleted" else it.name })
    }

    suspend fun dailyCommentLimitRemaining(now: Instant) = limits
                                                            .find(eq("_id", dayKey(now)))
                                                            .firstOrNull()?.getInteger("count", 0)
                                                            ?: 0

    // Both quotas live in one bounded document: one conditional write reserves both.
    suspend fun incrementCommentLimit(ip: String, now: Instant): Int {
        val day = dayKey(now)
        try {
            limits.insertOne(
                Document("_id", day)
                    .append("count", 0)
                    .append("ips", emptyList<String>())
                    .append("expiresAt", Date(nextDay(now) + TimeUnit.DAYS.toMillis(7)))
            )
        } catch (e: MongoWriteException) { if (e.error.code != 11000) throw e }

        // Store plain IPs as values, not field names (IPv4 addresses contain dots).
        // The global cap bounds this list to DAILY_GLOBAL_COMMENT_LIMIT entries, one per reservation.
        val matchingIps = Document("\$filter", Document("input", "\$ips").append("as", "ip")
            .append("cond", Document("\$eq", listOf("\$\$ip", Document("\$literal", ip)))))

        val result = limits.findOneAndUpdate(
            and(
                eq("_id", day),
                lt("count", DAILY_GLOBAL_COMMENT_LIMIT),
                expr(Document("\$lt", listOf(Document("\$size", matchingIps), DAILY_IP_COMMENT_LIMIT)))
            ),
            combine(
                inc("count", 1),
                push("ips", ip))
            ,
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )

        if (result == null) {
            val message = if (dailyCommentLimitRemaining(now) >= DAILY_GLOBAL_COMMENT_LIMIT) {
                "The daily comment limit has been reached. Please try again after the reset."
            }
            else {
                "You have reached your daily limit of comments."
            }
            throw CommentProblem(status = 429, message = message, resetAt = nextDay(now))
        }

        return result.getInteger("count")
    }

    suspend fun page(postId: String?, offset: Int, admin: Boolean = false): Pair<List<PublicComment>, Long> {
        val filter = postId?.let { eq("postId", it) } ?: Document()
        val total = comments.countDocuments(filter)
        val rows = comments.find(filter)
                           .sort(if (admin) Indexes.descending("createdAt", "_id") else Indexes.ascending("createdAt", "_id"))
                           .skip(offset)
                           .limit(if (admin) 20 else 50)
                           .toList()

        val names = rows.mapNotNull { it.getString("replyToId") }
                        .distinct()
                        .let { ids ->
                                comments.find(`in`("_id", ids))
                                        .toList()
                                        .associate { it.getString("_id") to if (it.getBoolean("deleted", false)) "Comment deleted" else it.getString("name") }
                        }

        return rows.map { it.toPublicComment(names[it.getString("replyToId")]) } to total
    }

    suspend fun delete(id: String): Boolean {
        val result = comments.updateOne(
            eq("_id", id),
            combine(
                set("deleted", true),
                set("name", ""),
                set("body", ""),
                unset("email"),
                unset("unsubscribeToken"),
                unset("notificationPending")
            )
        )

        jobs.deleteMany(or(eq("commentId", id), eq("targetId", id)))
        return result.matchedCount > 0
    }

    suspend fun deletePost(postId: String) {
        comments.deleteMany(eq("postId", postId))
        jobs.deleteMany(eq("postId", postId))
    }

    suspend fun unsubscribe(token: String): Boolean {
        val result = comments.updateOne(
            eq("unsubscribeToken", token),
            combine(unset("email"), unset("unsubscribeToken"))
        )
        return result.matchedCount > 0
    }

    suspend fun failedEmailCount(): Long = jobs.countDocuments(eq("status", "failed"))

    suspend fun queuePendingEmails(now: Instant) {
        // check for reply notifications
        val pendingReplyNotifications = comments.find(eq("notificationPending", true)).toList()
        for (comment in pendingReplyNotifications) {
            val replyNotificationJob = Document("kind", "reply")
                                        .append("commentId", comment.getString("_id"))
                                        .append("targetId", comment.getString("replyToId"))
                                        .append("postId", comment.getString("postId"))
            queue(
                "reply:${comment.getString("_id")}",
                replyNotificationJob,
                now
            )
            comments.updateOne(
                eq("_id",comment.getString("_id")),
                unset("notificationPending")
            )
        }

        // check for daily limit notification to admin
        val dailyLimitRecords = limits.find(and(gte("count", DAILY_GLOBAL_COMMENT_LIMIT), ne("alertQueued", true))).toList()
        for (day in dailyLimitRecords) {
            queue(
                "limit:${day.getString("_id")}",
                Document("kind", "limit").append("day", day.getString("_id")),
                now
            )
            limits.updateOne(
                eq("_id", day.getString("_id")),
                set("alertQueued", true)
            )
        }
    }

    suspend fun claimEmail(now: Instant): CommentEmailJob? {
        val job = jobs.findOneAndUpdate(
            and(
                `in`("status", "pending", "sending"),
                lte("nextAttempt", Date.from(now))
            ),
            combine(
                set("status", "sending"),
                set("nextAttempt", Date.from(now.plusSeconds(120))),
                inc("attempts", 1)
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        ) ?: return null

        return when (job.getString("kind")) {
            "limit" -> CommentEmailJob.DailyLimit(
                job.getString("_id"),
                job.getInteger("attempts"),
                job.getString("day")
            )
            "reply" -> CommentEmailJob.Reply(
            job.getString("_id"),
                job.getInteger("attempts"),
                job.getString("commentId"),
                job.getString("targetId")
            )
            else -> error("Unknown comment email job kind")
        }
    }

    suspend fun markEmailSent(id: String, now: Instant) {
        jobs.updateOne(
            eq("_id", id),
            combine(
                set("status", "sent"),
                set("expiresAt", Date.from(now.plusSeconds(TimeUnit.DAYS.toSeconds(7))))
            )
        )
    }

    suspend fun markEmailFailed(id: String, exhausted: Boolean, now: Instant) {
        jobs.updateOne(
            eq("_id", id),
            combine(
                set("status", if (exhausted) "failed" else "pending"),
                set("nextAttempt", Date.from(now.plusSeconds(300))),
                set("expiresAt", Date.from(now.plusSeconds(TimeUnit.DAYS.toSeconds(30))))
            )
        )
    }

    private suspend fun queue(id: String, fields: Document, now: Instant) {
        fields.append("status", "pending").append("attempts", 0).append("nextAttempt", Date.from(now))
        jobs.updateOne(
            eq("_id", id),
            Document("\$setOnInsert", fields),
            UpdateOptions().upsert(true)
        )
    }
}

private fun Document.toPublicComment(replyName: String? = null) =
    PublicComment(
        getString("_id"),
        getString("postId"),
        if (getBoolean("deleted", false)) "" else getString("name"),
        if (getBoolean("deleted", false)) "" else getString("body"),
        getLong("createdAt"),
        getString("threadId"),
        getString("replyToId"),
        replyName,
        getBoolean("deleted", false),
        getBoolean("owner", false),
)
