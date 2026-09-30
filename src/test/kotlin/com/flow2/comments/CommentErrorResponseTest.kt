package com.flow2.comments

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import com.flow2.routing.configureCors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentErrorResponseTest {
    @Test
    fun `localhost default origin reaches the handler and unexpected failures return JSON`() = testApplication {
        environment {
            config = MapApplicationConfig("app.schemeDomainPort" to "http://localhost")
        }

        application {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/posts/{postId}/comments") {
                    post {
                        commentResponse(call) {
                            requireCommentHeader(call)
                            error("Private database failure details")
                        }
                    }
                    get {
                        commentResponse(call) {
                            call.respond(CommentPage(emptyList(), 0, true))
                        }
                    }
                }
            }
            configureCors()
        }

        val response = client.post("/api/posts/test/comments") {
            header(HttpHeaders.Origin, "http://localhost")
            header("X-Comment-Request", "1")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertTrue(response.bodyAsText().contains("Comments are temporarily unavailable."))
        assertFalse(response.bodyAsText().contains("Private database"))

        assertEquals(HttpStatusCode.Forbidden, client.post("/api/posts/test/comments") {
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-Comment-Request", "1")
        }.status)
    }
}
