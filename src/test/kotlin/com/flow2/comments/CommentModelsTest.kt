package com.flow2.comments

import java.time.Instant
import kotlin.test.*

class CommentModelsTest {
    @Test fun `validation trims optional fields and preserves plain text`() {
        val input = validateComment(CommentInput(" A ", " <script>x</script>\nhello ", " "))
        assertEquals("A", input.name)
        assertEquals("<script>x</script>\nhello", input.body)
        assertNull(input.email)
        assertFailsWith<CommentProblem> { validateComment(CommentInput(" ", "hello")) }
        assertFailsWith<CommentProblem> { validateComment(CommentInput("a", "a".repeat(5001))) }
        assertFailsWith<CommentProblem> { validateComment(CommentInput("a", "hello", "x@y.com\r\nBcc:evil@x.com")) }
        assertFailsWith<CommentProblem> { validateComment(CommentInput("bad\nname", "hello")) }
        assertEquals("a@b.com", validateComment(CommentInput("a", "hello", " a@b.com ")).email)
    }
    @Test fun `quotas use UTC boundaries`() {
        val now = Instant.parse("2026-09-22T23:59:59Z")
        assertEquals("2026-09-22", dayKey(now))
        assertEquals(Instant.parse("2026-09-23T00:00:00Z").toEpochMilli(), nextDay(now))
    }
    @Test fun `IP normalization handles IPv6 aliases without resolving hostnames`() {
        assertEquals("192.0.2.1", normalizeVisitorIp("::ffff:192.0.2.1"))
        assertEquals(normalizeVisitorIp("2001:db8::1"), normalizeVisitorIp("2001:0db8:0:0:0:0:0:1"))
        assertEquals("192.0.2.1", normalizeVisitorIp("192.000.002.001"))
        assertEquals("unknown", normalizeVisitorIp("attacker.example"))
        assertEquals("unknown", normalizeVisitorIp("999.0.0.1"))
    }

}
