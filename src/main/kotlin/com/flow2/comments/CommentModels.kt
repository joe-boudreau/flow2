package com.flow2.comments

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

@Serializable
data class CommentInput(
    val name: String = "",
    val body: String = "",
    val email: String? = null,
    val replyToId: String? = null
)

@Serializable
data class PublicComment(
    val id: String,
    val postId: String,
    val name: String,
    val body: String,
    val createdAt: Long,
    val threadId: String,
    val replyToId: String? = null,
    val replyingTo: String? = null,
    val deleted: Boolean = false,
    val owner: Boolean = false,
    val avatarSeed: String? = null,
)

@Serializable
data class CommentPage(
    val comments: List<PublicComment>,
    val total: Long,
    val enabled: Boolean,
    val next: Int? = null
)

@Serializable
data class CommentError(
    val error: String,
    val resetAt: Long? = null

)

@Serializable
data class CommentStatus(
    val enabled: Boolean,
    val used: Int,
    val limit: Int,
    val resetAt: Long,
    val failedEmails: Long,
)

@Serializable
data class CommentSetting(
    val enabled: Boolean
)

class CommentProblem(val status: Int, override val message: String, val resetAt: Long? = null) : RuntimeException(message)

data class CommentConfig(
    val ownerName: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUser: String,
    val smtpToken: String,
    val sender: String,
    val alertTo: String,
) {
    companion object {
        fun fromApplicationConfig(appConfig: ApplicationConfig): CommentConfig {
            return CommentConfig(
                ownerName = appConfig.property("app.comments.ownerName").getString(),
                smtpHost = appConfig.property("app.comments.smtpHost").getString(),
                smtpPort = appConfig.property("app.comments.smtpPort").getString().toInt(),
                smtpUser = appConfig.property("app.comments.smtpUser").getString(),
                smtpToken = appConfig.property("app.comments.smtpToken").getString(),
                sender = appConfig.property("app.comments.sender").getString(),
                alertTo = appConfig.property("app.comments.alertTo").getString(),
            )
        }
    }
}

fun validateComment(input: CommentInput): CommentInput {
    val value = input.copy(
        name = input.name.trim(),
        body = input.body.trim(),
        email = input.email?.trim()?.takeIf { it.isNotEmpty() },
    )

    // name length
    if (value.name.length !in 1..80 || value.name.any { it.isISOControl() }) {
        throw CommentProblem(400, "Please enter a name of 1–80 characters.")
    }

    // body length
    if (value.body.length !in 1..5000) {
        throw CommentProblem(400, "Please enter a comment of 1–5,000 characters.")
    }

    // valid email
    if (value.email != null) {
        val emailPattern = Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$")
        if (value.email.length > 254 || !emailPattern.matches(value.email)) {
            throw CommentProblem(400, "Please enter a valid email address or leave it blank.")
        }
    }

    return value
}

fun dayKey(now: Instant) = now.atOffset(ZoneOffset.UTC).toLocalDate().toString()

fun nextDay(now: Instant) = now.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

data class CommentSubscription(val email: String, val unsubscribeToken: String)

sealed interface CommentEmailJob {
    val id: String
    val attempts: Int

    data class Reply(
        override val id: String,
        override val attempts: Int,
        val commentId: String,
        val targetId: String,
    ) : CommentEmailJob

    data class DailyLimit(
        override val id: String,
        override val attempts: Int,
        val day: String,
    ) : CommentEmailJob
}
