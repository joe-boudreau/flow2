package com.flow2.comments

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.ByteArrayOutputStream
import java.util.Properties
import kotlin.test.*

class CommentEmailTest {
    @Test
    fun `reply template escapes user content and preserves line breaks and links`() {
        val email = CommentEmails.reply(
            name = "<b>Reader</b>",
            postTitle = "Books & <script>alert(1)</script>",
            body = "Hello <img src=x>\nYour cousin 😍",
            replyUrl = "https://flowtwo.io/post/book#comment-123",
            unsubscribeUrl = "https://flowtwo.io/comments/unsubscribe/token",
        )

        val html = assertNotNull(email.html)
        assertTrue(html.contains("&lt;b&gt;Reader&lt;/b&gt;"))
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<img src=x>"))
        assertTrue(html.contains("Hello &lt;img src=x&gt;</span><br"))
        assertTrue(html.contains("Your cousin 😍"))
        assertTrue(html.contains("href=\"https://flowtwo.io/post/book#comment-123\""))
        assertTrue(html.contains("href=\"https://flowtwo.io/comments/unsubscribe/token\""))
        assertTrue(email.text.contains("Hello <img src=x>\nYour cousin 😍"))
    }

    @Test
    fun `SMTP message contains plain text followed by HTML with UTF-8 content`() {
        val email = CommentEmails.reply("Reader", "Book", "Hi 😍", "https://flowtwo.io/post/book", "https://flowtwo.io/comments/unsubscribe/token")
        val session = Session.getInstance(Properties())
        val message = MimeMessage(session).apply {
            setContent(email.toMultipart())
            saveChanges()
        }
        val bytes = ByteArrayOutputStream().apply { message.writeTo(this) }.toByteArray()
        val parsed = MimeMessage(session, bytes.inputStream())
        assertTrue(parsed.isMimeType("multipart/alternative"))
        val parts = parsed.content as MimeMultipart
        assertEquals(2, parts.count)
        assertTrue(parts.getBodyPart(0).isMimeType("text/plain"))
        assertTrue(parts.getBodyPart(1).isMimeType("text/html"))
        assertEquals(email.text, (parts.getBodyPart(0).content as String).replace("\r\n", "\n"))
        assertEquals(email.html, (parts.getBodyPart(1).content as String).replace("\r\n", "\n"))
    }
}
