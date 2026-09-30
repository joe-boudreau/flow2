package com.flow2.comments

import com.flow2.repository.posts.PostRepositoryInterface
import io.ktor.events.Events
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

class CommentService(
    private val repository: CommentRepository,
    private val posts: PostRepositoryInterface,
    private val mailer: CommentMailer,
    private val config: CommentConfig,
    private val baseUrl: String,
    private val clock: Clock = Clock.systemUTC(),
    appEventsMonitor: Events,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        runBlocking { repository.initialize() }

        val mailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mailScope.launch {
            while (isActive) {
                try {
                    runMailCycle()
                }
                catch (e: CancellationException) {
                    throw e
                }
                catch (e: Exception) {
                    log.error("Comment mail worker failed: ${e.javaClass.simpleName}")
                }

                delay(60_000.milliseconds)
            }
        }
        appEventsMonitor.subscribe(ApplicationStopped) { mailScope.cancel() }
    }

    suspend fun unsubscribe(token: String): Boolean = repository.unsubscribe(token)

    suspend fun setCommentingEnabled(enabled: Boolean) = repository.setCommentingEnabled(enabled)

    suspend fun getComment(id: String): PublicComment? = repository.getComment(id)

    suspend fun delete(id: String): Boolean = repository.delete(id)

    suspend fun deletePost(postId: String) = repository.deletePost(postId)

    suspend fun list(postId: String, offset: Int): CommentPage {
        if (posts.getPost(postId) == null) {
            throw CommentProblem(404, "Post not found.")
        }

        return page(postId, offset)
    }

    suspend fun listAdmin(postId: String?, offset: Int): CommentPage = page(postId, offset, admin = true)

    private suspend fun page(postId: String?, offset: Int, admin: Boolean = false): CommentPage {
        val (rows, count) = repository.page(postId, offset, admin)
        val nextOffset = (offset + rows.size).takeIf { it < count }

        return CommentPage(
            comments = rows,
            total = count,
            enabled = repository.isCommentingEnabled(),
            next = nextOffset,
        )
    }

    suspend fun status(): CommentStatus = CommentStatus(
        enabled = repository.isCommentingEnabled(),
        used = repository.dailyCommentLimitRemaining(clock.instant()),
        limit = 100,
        resetAt = nextDay(clock.instant()),
        failedEmails = repository.failedEmailCount(),
    )

    suspend fun submit(postId: String, raw: CommentInput, ip: String, owner: Boolean = false): PublicComment {
        val input = validateComment(if (owner) raw.copy(name = config.ownerName) else raw)
        val post = posts.getPost(postId) ?: throw CommentProblem(404, "Post not found.")

        if (!repository.isCommentingEnabled()) {
            throw CommentProblem(403, "Comments are currently closed.")
        }

        val parent = input.replyToId?.let {
            repository.getComment(it) ?: throw CommentProblem(400, "Reply target not found.")
        }
        if (parent != null && (parent.postId != post.id || parent.deleted)) {
            throw CommentProblem(400, "That comment cannot receive replies.")
        }

        val now = clock.instant()
        repository.incrementCommentLimit(ip, now)

        val subscription = parent?.let { repository.getSubscription(it.id) }
        val notifyParent = subscription != null && !subscription.email.equals(input.email, ignoreCase = true)

        return repository.create(
            postId = post.id,
            name = input.name,
            body = input.body,
            email = input.email,
            replyToId = input.replyToId,
            threadId = parent?.threadId,
            owner = owner,
            notificationPending = notifyParent,
            now = now,
        )
    }

    suspend fun runMailCycle() {
        repository.queuePendingEmails(clock.instant())

        repeat(20) {
            val job = repository.claimEmail(clock.instant()) ?: return

            try {
                deliver(job)
                repository.markEmailSent(job.id, clock.instant())
                log.info("Comment email delivered for job {}", job.id)
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                // Do not log SMTP exceptions: server responses can contain recipient addresses.
                log.warn("Comment email delivery failed for job {}; type={}", job.id, e.javaClass.simpleName)
                repository.markEmailFailed(
                    id = job.id,
                    exhausted = job.attempts >= 3,
                    now = clock.instant(),
                )
            }
        }
    }

    private suspend fun deliver(job: CommentEmailJob) {
        if (job is CommentEmailJob.DailyLimit) {
            val reset = LocalDate.parse(job.day).plusDays(1)
            val message = """
                Your blog reached its global limit of 100 comments for ${job.day} (UTC).
                Posting resumes at $reset 00:00 UTC.
                Manage comments: $baseUrl/admin#comments-admin
            """.trimIndent()

            mailer.send(config.alertTo, "Blog daily comment limit reached", CommentEmail(message))
            return
        }

        if (job !is CommentEmailJob.Reply) return

        val comment = repository.getComment(job.commentId) ?: return
        if (comment.deleted) return

        val subscription = repository.getSubscription(job.targetId) ?: return
        val post = posts.getPost(comment.postId) ?: return
        val slug = URLEncoder.encode(post.slug, Charsets.UTF_8).replace("+", "%20")
        val replyUrl = "$baseUrl/post/$slug#comment-${comment.id}"
        val unsubscribeUrl = "$baseUrl/comments/unsubscribe/${subscription.unsubscribeToken}"
        val message = CommentEmails.reply(
            name = comment.name,
            postTitle = post.title,
            body = comment.body,
            replyUrl = replyUrl,
            unsubscribeUrl = unsubscribeUrl,
        )

        mailer.send(subscription.email, "New reply on flow2", message)
        log.info("Comment email delivered for email {}", subscription.email)
    }
}
