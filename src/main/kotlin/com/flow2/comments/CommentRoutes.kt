package com.flow2.comments

import com.flow2.auth.ADMIN_API_CONFIG
import com.flow2.auth.ADMIN_SESSION_CONFIG
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.ThymeleafContent
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.time.Instant

fun Application.configureCommentRoutes() {

    val commentService by inject<CommentService>()

    routing {
        route("/api/posts/{postId}/comments") {
            get {
                commentResponse(call) {
                    val postId = call.parameters["postId"]!!
                    call.respond(commentService.list(postId, offset(call)))
                }
            }

            post {
                commentResponse(call) {
                    requireCommentHeader(call)
                    val comment = commentService.submit(
                        postId = call.parameters["postId"]!!,
                        raw = input(call),
                        ip = visitorIp(call),
                    )
                    call.respond(HttpStatusCode.Created, comment)
                }
            }
        }

        route("/comments/unsubscribe/{token}") {
            get {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("Referrer-Policy", "no-referrer")
                // GET is intentionally inert: mail scanners must not unsubscribe people.
                call.respond(ThymeleafContent("comment-unsubscribe", emptyMap()))
            }

            post {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("Referrer-Policy", "no-referrer")
                commentService.unsubscribe(call.parameters["token"]!!)
                call.respondText("Reply notifications are disabled for this comment.")
            }
        }

        route("/admin/comments") {
            authenticate(ADMIN_SESSION_CONFIG, ADMIN_API_CONFIG) {
                get("/status") {
                    commentResponse(call) {
                        call.respond(commentService.status())
                    }
                }

                put("/settings") {
                    commentResponse(call) {
                        requireCommentHeader(call)
                        val setting = Json.decodeFromString<CommentSetting>(body(call))
                        commentService.setCommentingEnabled(setting.enabled)
                        call.respond(commentService.status())
                    }
                }

                get {
                    commentResponse(call) {
                        val postId = call.request.queryParameters["postId"]?.takeIf { it.isNotBlank() }
                        call.respond(commentService.listAdmin(postId, offset(call)))
                    }
                }

                delete("/{id}") {
                    commentResponse(call) {
                        requireCommentHeader(call)
                        if (!commentService.delete(call.parameters["id"]!!)) {
                            throw CommentProblem(404, "Comment not found.")
                        }

                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                post("/{id}/reply") {
                    commentResponse(call) {
                        requireCommentHeader(call)
                        val parent = commentService.getComment(call.parameters["id"]!!)
                            ?: throw CommentProblem(404, "Comment not found.")
                        val comment = commentService.submit(
                            postId = parent.postId,
                            raw = input(call).copy(replyToId = parent.id),
                            ip = visitorIp(call),
                            owner = true,
                        )
                        call.respond(HttpStatusCode.Created, comment)
                    }
                }
            }
        }
    }
}

private fun offset(call: ApplicationCall): Int {
    val value = call.request.queryParameters["offset"] ?: return 0
    return value.toIntOrNull()?.takeIf { it >= 0 && it <= 1_000_000 }
        ?: throw CommentProblem(400, "Invalid page offset.")
}

private suspend fun body(call: ApplicationCall): String {
    if (!call.request.contentType().match(ContentType.Application.Json)) {
        throw CommentProblem(415, "Send JSON.")
    }

    val packet = call.receiveChannel().readRemaining(32_769)
    val bytes = packet.readByteArray()
    if (bytes.size > 32_768) {
        throw CommentProblem(413, "Comment request is too large.")
    }

    return bytes.decodeToString()
}

private suspend fun input(call: ApplicationCall) = Json.decodeFromString<CommentInput>(body(call))

internal fun requireCommentHeader(call: ApplicationCall) {
    if (call.request.header("X-Comment-Request") != "1") {
        throw CommentProblem(403, "Please submit comments from this website.")
    }
}

internal suspend fun commentResponse(call: ApplicationCall, action: suspend () -> Unit) {
    call.response.header(HttpHeaders.CacheControl, "no-store")

    try {
        action()
    }
    catch (e: CommentProblem) {
        e.resetAt?.let {
            val retryAfter = ((it - Instant.now().toEpochMilli()) / 1000).coerceAtLeast(1)
            call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
        }
        call.respond(HttpStatusCode.fromValue(e.status), CommentError(e.message, e.resetAt))
    }
    catch (_: SerializationException) {
        call.respond(HttpStatusCode.BadRequest, CommentError("Invalid comment request."))
    }
    catch (e: CancellationException) {
        throw e
    }
    catch (e: Exception) {
        call.application.log.error("Comment request failed: ${e.javaClass.simpleName}")
        call.respond(
            HttpStatusCode.InternalServerError,
            CommentError("Comments are temporarily unavailable. Please try again later."),
        )
    }
}
