package com.flow2.comments

import com.flow2.model.Category
import com.flow2.repository.posts.MongoPostRepository
import com.mongodb.client.model.Filters.*
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import org.bson.Document
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class CommentIntegrationTest {
    private class TestClock(var current: Instant = Instant.parse("2026-09-22T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
    private fun databaseTest(block: suspend (CommentRepository, MongoPostRepository, TestClock, CommentConfig, MongoDatabase) -> Unit) = runBlocking {
        val uri = System.getenv("COMMENT_TEST_MONGO_URI")
        assumeTrue(!uri.isNullOrBlank(), "Set COMMENT_TEST_MONGO_URI to run real MongoDB integration tests")
        val client = MongoClient.create(uri!!)
        val db = client.getDatabase("comment_test_" + UUID.randomUUID().toString().replace("-", ""))
        try {
            val repo = CommentRepository(db, CommentAvatar("test-avatar-key"))
            repo.initialize()
            block(repo, MongoPostRepository(db), TestClock(), CommentConfig(ownerName = "flow2", smtpHost = "smtp.protonmail.ch", smtpPort = 587, smtpUser = "test", smtpToken = "test", sender = "blog@example.com", alertTo = "owner@example.com"), db)
        } finally { db.drop(); client.close() }
    }
    @Test fun `concurrent IP and global reservations obey hard limits and reset at midnight`() = databaseTest { repo, _, clock, _, db ->
        val accepted = coroutineScope { (1..20).map { async { try { repo.incrementCommentLimit("192.0.2.1", clock.instant()); true } catch (_: CommentProblem) { false } } }.awaitAll().count { it } }
        assertEquals(5, accepted)
        assertEquals(List(5) { "192.0.2.1" }, db.getCollection<Document>("comment_limits").find(eq("_id", dayKey(clock.instant()))).first().getList("ips", String::class.java))
        val other = coroutineScope { (1..150).map { async { try { repo.incrementCommentLimit("2001:db8::${it.toString(16)}", clock.instant()); true } catch (_: CommentProblem) { false } } }.awaitAll().count { it } }
        assertEquals(95, other)
        assertEquals(100, repo.dailyCommentLimitRemaining(clock.instant()))
        val ips = db.getCollection<Document>("comment_limits").find(eq("_id", dayKey(clock.instant()))).first().getList("ips", String::class.java)
        assertEquals(100, ips.size)
        assertEquals(95, ips.count { it.startsWith("2001:db8::") })
        clock.current = Instant.parse("2026-09-23T00:00:00Z")
        assertEquals(1, repo.incrementCommentLimit("192.0.2.1", clock.instant()))
    }
    @Test fun `posting kill switch cross-post validation and stable thread relationships`() = databaseTest { repo, posts, clock, config, db ->
        val service = CommentService(repo, posts, CommentMailer { _, _, _ -> }, config, "https://flowtwo.io", clock)
        val post = posts.createPost("Post", null, "", emptyList(), Category.PERSONAL, null)
        val other = posts.createPost("Other", null, "", emptyList(), Category.PERSONAL, null)
        assertFailsWith<CommentProblem> { service.submit(post.id, CommentInput("A", "Hello"), "1") }
        repo.setCommentingEnabled(true)
        val root = service.submit(post.id, CommentInput("A", "Hello", "a@example.com"), "1")
        val reply = service.submit(post.id, CommentInput("B", "Reply", replyToId = root.id), "2")
        val nested = service.submit(post.id, CommentInput("C", "Nested", replyToId = reply.id), "3")
        assertEquals(root, service.getComment(root.id))
        val encoded = Json.encodeToString(service.getComment(root.id))
        assertFalse(encoded.contains("a@example.com"))
        assertFalse(encoded.contains("unsubscribeToken"))
        assertEquals(root.id, nested.threadId)
        assertEquals(reply.id, nested.replyToId)
        assertFailsWith<CommentProblem> { service.submit(other.id, CommentInput("A", "Bad", replyToId = root.id), "4") }
        assertEquals(3, repo.dailyCommentLimitRemaining(clock.instant()))
        posts.updatePost(post.id, "Renamed", null, "", emptyList(), Category.PERSONAL)
        assertEquals(3L, service.list(post.id, 0).total)
        repo.setCommentingEnabled(false)
        assertFailsWith<CommentProblem> { service.submit(post.id, CommentInput("A", "Late"), "4") }
        assertEquals(3, service.list(post.id, 0).comments.size)
        repo.delete(root.id)
        assertNull(repo.getSubscription(root.id))
        assertEquals("", service.getComment(root.id)!!.name)
        assertEquals("", service.getComment(root.id)!!.body)
        assertTrue(service.list(post.id, 0).comments.first().deleted)
        assertEquals(3, repo.dailyCommentLimitRemaining(clock.instant()))
        repo.deletePost(post.id)
        assertEquals(0L, db.getCollection<Document>("comments").countDocuments())
    }
    @Test fun `notifications target direct parent respect unsubscribe and retry independently of posting`() = databaseTest { repo, posts, clock, config, db ->
        val sent = mutableListOf<Pair<String, String>>()
        var failing = false
        val mail = CommentMailer { to, _, body -> if (failing) error("SMTP unavailable") else sent.add(to to body.text) }
        val service = CommentService(repo, posts, mail, config, "https://flowtwo.io", clock)
        val post = posts.createPost("Post", null, "", emptyList(), Category.PERSONAL, null)
        repo.setCommentingEnabled(true)
        val root = service.submit(post.id, CommentInput("A", "Hello", "a@example.com"), "1")
        val reply = service.submit(post.id, CommentInput("B", "Reply", "b@example.com", root.id), "2")
        service.runMailCycle(); service.runMailCycle()
        assertEquals(listOf("a@example.com"), sent.map { it.first })
        assertTrue(sent.single().second.contains("#comment-${reply.id}"))
        assertFalse(sent.single().second.contains("b@example.com"))
        failing = true
        service.submit(post.id, CommentInput("C", "Nested", replyToId = reply.id), "3")
        service.runMailCycle()
        assertEquals(3L, db.getCollection<Document>("comments").countDocuments())
        assertEquals(1, sent.size)
        failing = false; clock.current = clock.current.plusSeconds(301)
        service.runMailCycle()
        assertEquals(listOf("a@example.com", "b@example.com"), sent.map { it.first })
        val token = repo.getSubscription(root.id)!!.unsubscribeToken
        service.submit(post.id, CommentInput("D", "Another", replyToId = root.id), "4")
        assertTrue(repo.unsubscribe(token))
        service.runMailCycle()
        assertEquals(2, sent.size)
        assertFalse(repo.unsubscribe(token))
    }
    @Test fun `global alert is queued once and recovers after restart`() = databaseTest { repo, posts, clock, config, db ->
        val sent = mutableListOf<String>()
        val mail = CommentMailer { _, _, text -> sent.add(text.text) }
        repeat(100) { repo.incrementCommentLimit("2001:db8::${it.toString(16)}", clock.instant()) }
        CommentService(repo, posts, mail, config, "https://flowtwo.io", clock).runMailCycle()
        CommentService(repo, posts, mail, config, "https://flowtwo.io", clock).runMailCycle()
        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("2026-09-23 00:00 UTC"))
        assertEquals(1L, db.getCollection<Document>("comment_email_jobs").countDocuments())
    }
    @Test fun `owner identity cannot be supplied by visitors and deletions cancel pending mail`() = databaseTest { repo, posts, clock, config, db ->
        val sent = mutableListOf<String>()
        val service = CommentService(repo, posts, CommentMailer { to, _, _ -> sent.add(to) }, config, "https://flowtwo.io", clock)
        val post = posts.createPost("Post", null, "", emptyList(), Category.PERSONAL, null)
        repo.setCommentingEnabled(true)
        val root = service.submit(post.id, CommentInput("A", "Hello", "a@example.com"), "1")
        assertFalse(root.owner)
        val reply = service.submit(post.id, CommentInput("Spoof", "Reply", replyToId = root.id), "2", owner = true)
        assertEquals("flow2", reply.name); assertTrue(reply.owner)
        repo.delete(reply.id)
        service.runMailCycle()
        assertTrue(sent.isEmpty())
    }
    @Test fun `queued mail survives restart and repeated SMTP failure is bounded`() = databaseTest { repo, posts, clock, config, db ->
        var attempts = 0
        val mailer = CommentMailer { _, _, _ -> attempts++; error("offline") }
        val original = CommentService(repo, posts, mailer, config, "https://flowtwo.io", clock)
        val post = posts.createPost("Mail", null, "", emptyList(), Category.PERSONAL, null)
        repo.setCommentingEnabled(true)
        val root = original.submit(post.id, CommentInput("A", "Hello", "a@example.com"), "1")
        original.submit(post.id, CommentInput("B", "Reply", replyToId = root.id), "2")
        repo.queuePendingEmails(clock.instant())
        assertEquals(0, attempts)
        assertEquals(1L, db.getCollection<Document>("comment_email_jobs").countDocuments(eq("status", "pending")))
        val enabled = CommentService(repo, posts, mailer, config, "https://flowtwo.io", clock)
        repeat(4) { enabled.runMailCycle(); clock.current = clock.current.plusSeconds(301) }
        assertEquals(3, attempts)
        assertEquals(1L, enabled.status().failedEmails)
        assertEquals(2L, db.getCollection<Document>("comments").countDocuments())
        repo.initialize()
        assertTrue(repo.isCommentingEnabled())
        assertEquals(2, repo.dailyCommentLimitRemaining(clock.instant()))
    }

    @Test fun `pagination is stable and admin pages remain small`() = databaseTest { repo, posts, clock, config, db ->
        val post = posts.createPost("Pages", null, "", emptyList(), Category.PERSONAL, null)
        db.getCollection<Document>("comments").insertMany((0 until 120).map {
            val id = it.toString().padStart(3, '0')
            Document("_id", id).append("postId", post.id).append("name", "Reader").append("body", "Hello")
                .append("createdAt", it.toLong()).append("threadId", id)
        })
        val service = CommentService(repo, posts, CommentMailer { _, _, _ -> }, config, "https://flowtwo.io", clock)
        val first = service.list(post.id, 0)
        val second = service.list(post.id, first.next!!)
        val last = service.list(post.id, second.next!!)
        assertEquals(120L, first.total)
        assertEquals(50, first.comments.size)
        assertEquals("000", first.comments.first().id)
        assertEquals("050", second.comments.first().id)
        assertEquals(20, last.comments.size)
        assertNull(last.next)
        assertEquals(20, repo.page(null, 0, true).first.size)
        assertEquals("119", repo.page(null, 0, true).first.first().id)
    }

}
