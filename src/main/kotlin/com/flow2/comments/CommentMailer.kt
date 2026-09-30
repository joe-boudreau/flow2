package com.flow2.comments

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

fun interface CommentMailer {
    suspend fun send(to: String, subject: String, body: String)
}

class SmtpCommentMailer(private val config: CommentConfig) : CommentMailer {
    override suspend fun send(to: String, subject: String, body: String) = withContext(Dispatchers.IO) {
        val properties = Properties().apply {
            setProperty("mail.smtp.host", config.smtpHost)
            setProperty("mail.smtp.port", config.smtpPort.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.starttls.enable", "true")
            setProperty("mail.smtp.starttls.required", "true")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
        }
        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(config.smtpUser, config.smtpToken)
        })
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.sender, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(to, true))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        Transport.send(message)
    }
}
