package com.flow2.comments

import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

data class CommentEmail(val text: String, val html: String? = null)

internal object CommentEmails {
    private val templates = TemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/email/"
            suffix = ".html"
            characterEncoding = "UTF-8"
        })
    }

    fun reply(name: String, postTitle: String, body: String, replyUrl: String, unsubscribeUrl: String): CommentEmail {
        val excerpt = body.take(500)
        val text = "$name replied to your comment on $postTitle:\n\n" +
            "$excerpt\n\n" +
            "Read and reply: $replyUrl\n\n" +
            "Stop notifications for your comment: $unsubscribeUrl"

        val context = Context().apply {
            setVariables(mapOf(
                "name" to name,
                "postTitle" to postTitle,
                "replyLines" to excerpt.lines(),
                "replyUrl" to replyUrl,
                "unsubscribeUrl" to unsubscribeUrl,
            ))
        }

        return CommentEmail(text, templates.process("comment-reply", context))
    }
}

internal fun CommentEmail.toMultipart(): MimeMultipart {
    return MimeMultipart("alternative").apply {
        addBodyPart(MimeBodyPart().apply {
            setText(text, "UTF-8")
        })
        html?.let { content ->
            addBodyPart(MimeBodyPart().apply {
                setText(content, "UTF-8", "html")
            })
        }
    }
}
