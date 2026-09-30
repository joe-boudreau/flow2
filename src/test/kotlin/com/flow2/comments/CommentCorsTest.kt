package com.flow2.comments

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import com.flow2.routing.configureCors
import com.flow2.auth.ADMIN_API_CONFIG
import com.flow2.auth.ADMIN_SESSION_CONFIG
import io.ktor.server.auth.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentCorsTest {
    @Test
    fun `comment CORS overrides any host while other routes remain unrestricted`() = testApplication {
        environment {
            config = MapApplicationConfig("app.schemeDomainPort" to "https://flowtwo.io")
        }

        var writes = 0
        application {
            install(Authentication) {
                basic(ADMIN_API_CONFIG) {
                    validate { UserIdPrincipal(it.name) }
                }
                basic(ADMIN_SESSION_CONFIG) {
                    validate { UserIdPrincipal(it.name) }
                }
            }

            fun Route.commentHandlers() {
                get {
                    call.respond(HttpStatusCode.OK)
                }
                post {
                    try {
                        requireCommentHeader(call)
                        writes++
                        call.respond(HttpStatusCode.Created)
                    }
                    catch (e: CommentProblem) {
                        call.respond(HttpStatusCode.fromValue(e.status), e.message)
                    }
                }
                put("/settings") {
                    requireCommentHeader(call)
                    writes++
                    call.respond(HttpStatusCode.OK)
                }
            }

            routing {
                route("/api/posts/{postId}/comments") {
                    commentHandlers()
                }
                route("/admin/comments") {
                    authenticate(ADMIN_SESSION_CONFIG, ADMIN_API_CONFIG) {
                        commentHandlers()
                    }
                }

                post("/admin/publish") {
                    call.respond(HttpStatusCode.OK)
                }
                post("/comments/unsubscribe/token") {
                    call.respond(HttpStatusCode.OK)
                }
            }
            configureCors()
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/comments").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/admin/comments/settings") {
            header("X-Comment-Request", "1")
        }.status)

        for (path in listOf("/api/posts/post/comments", "/admin/comments")) {
            assertEquals(HttpStatusCode.OK, client.get(path) { basicAuth("admin", "test") }.status)

            for (origin in listOf("https://evil.example", "http://flowtwo.io", "https://flowtwo.io:444")) {
                assertEquals(HttpStatusCode.Forbidden, client.post(path) {
                    basicAuth("admin", "test")
                    header(HttpHeaders.Origin, origin)
                    header("X-Comment-Request", "1")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }.status, "$path from $origin")
            }
            assertEquals(0, writes)

            assertEquals(HttpStatusCode.Created, client.post(path) {
                basicAuth("admin", "test")
                header(HttpHeaders.Origin, "https://flowtwo.io")
                header("X-Comment-Request", "1")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.status)

            assertEquals(HttpStatusCode.Created, client.post(path) {
                basicAuth("admin", "test")
                header("X-Comment-Request", "1")
            }.status)

            val missingHeader = client.post(path) {
                basicAuth("admin", "test")
                header(HttpHeaders.Origin, "https://flowtwo.io")
            }
            assertEquals(HttpStatusCode.Forbidden, missingHeader.status)
            assertEquals("Please submit comments from this website.", missingHeader.bodyAsText())
            assertEquals(2, writes)
            writes = 0
        }

        for (origin in listOf("https://flowtwo.io", "https://evil.example")) {
            val response = client.options("/admin/comments/settings") {
                header(HttpHeaders.Origin, origin)
                header(HttpHeaders.AccessControlRequestMethod, "PUT")
                header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-comment-request")
            }
            val expected = if (origin == "https://flowtwo.io") HttpStatusCode.OK else HttpStatusCode.Forbidden
            assertEquals(expected, response.status)
        }

        assertEquals(HttpStatusCode.OK, client.put("/admin/comments/settings") {
            basicAuth("admin", "test")
            header(HttpHeaders.Origin, "https://flowtwo.io")
            header("X-Comment-Request", "1")
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/admin/comments/settings") {
            basicAuth("admin", "test")
            header(HttpHeaders.Origin, "https://evil.example")
            header("X-Comment-Request", "1")
        }.status)
        assertEquals(1, writes)

        assertEquals(HttpStatusCode.OK, client.post("/admin/publish") {
            header(HttpHeaders.Origin, "app://obsidian.md")
        }.status)
        assertEquals(HttpStatusCode.OK, client.post("/comments/unsubscribe/token").status)
    }
}
