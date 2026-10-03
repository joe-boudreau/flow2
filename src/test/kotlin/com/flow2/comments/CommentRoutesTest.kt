package com.flow2.comments

import com.flow2.auth.configureAdminAuth
import com.flow2.model.Category
import com.flow2.repository.posts.MongoPostRepository
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.server.thymeleaf.Thymeleaf
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class CommentRoutesTest {
    @Test fun `HTTP endpoints enforce validation authentication origin kill switch and private responses`() {
        val uri = System.getenv("COMMENT_TEST_MONGO_URI")
        assumeTrue(!uri.isNullOrBlank(), "Set COMMENT_TEST_MONGO_URI")
        val mongo = MongoClient.create(uri!!)
        val db = mongo.getDatabase("comment_routes_" + UUID.randomUUID().toString().replace("-", ""))
        try {
            val repo = CommentRepository(db, CommentAvatar("test-avatar-key"))
            val posts = MongoPostRepository(db)
            val post = runBlocking {
                repo.initialize(); repo.setCommentingEnabled(true)
                posts.createPost("Test", null, "", emptyList(), Category.PERSONAL, null)
            }
            testApplication {
                environment { this.config = MapApplicationConfig("app.adminAuth.sessionCookie" to "test-session", "app.adminAuth.username" to "admin", "app.adminAuth.password" to "test", "app.adminAuth.digestSalt" to "test") }
                application {
                    install(ContentNegotiation) { json() }
                    install(Thymeleaf) {
                        setTemplateResolver(ClassLoaderTemplateResolver().apply {
                            prefix = "templates/"
                            suffix = ".html"
                            characterEncoding = "utf-8"
                        })
                    }
                    configureAdminAuth()
                    configureCommentRoutes()
                }
                val endpoint = "/api/posts/${post.id}/comments"
                val response = client.post(endpoint) {
                    header("X-Comment-Request", "1"); header(HttpHeaders.Origin, "http://localhost")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Reader","body":"<img src=x onerror=alert(1)>","email":"private@example.com"}""")
                }
                assertEquals(HttpStatusCode.Created, response.status)
                assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
                assertFalse(response.bodyAsText().contains("private@example.com"))
                val id = Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
                val listing = client.get(endpoint)
                assertFalse(listing.bodyAsText().contains("private@example.com"))
                assertFalse(listing.bodyAsText().contains("unsubscribeToken"))
                assertEquals(HttpStatusCode.Forbidden, client.post(endpoint) {
                    contentType(ContentType.Application.Json); setBody("""{"name":"A","body":"B"}""")
                }.status)
                assertEquals(HttpStatusCode.Forbidden, client.post(endpoint) {
                    header("X-Comment-Request", "1"); header(HttpHeaders.Origin, "https://evil.example")
                    contentType(ContentType.Application.Json); setBody("""{"name":"A","body":"B"}""")
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.post(endpoint) {
                    header("X-Comment-Request", "1"); contentType(ContentType.Application.Json); setBody("{bad")
                }.status)
                assertEquals(HttpStatusCode.PayloadTooLarge, client.post(endpoint) {
                    header("X-Comment-Request", "1"); contentType(ContentType.Application.Json); setBody("x".repeat(33000))
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.get("$endpoint?offset=-1").status)
                val unauthorized = client.put("/admin/comments/settings") {
                    header("X-Comment-Request", "1"); contentType(ContentType.Application.Json); setBody("""{"enabled":false}""")
                }
                assertFalse(unauthorized.status.isSuccess())
                val change = client.put("/admin/comments/settings") {
                    basicAuth("admin", "test"); header("X-Comment-Request", "1")
                    contentType(ContentType.Application.Json); setBody("""{"enabled":false}""")
                }
                assertEquals(HttpStatusCode.OK, change.status)
                assertEquals(100, Json.parseToJsonElement(change.bodyAsText()).jsonObject["limit"]!!.jsonPrimitive.int)
                assertEquals(HttpStatusCode.Forbidden, client.post(endpoint) {
                    header("X-Comment-Request", "1"); contentType(ContentType.Application.Json); setBody("""{"name":"A","body":"B"}""")
                }.status)
                assertEquals(HttpStatusCode.OK, client.get(endpoint).status)
                val token = runBlocking { repo.getSubscription(id)!!.unsubscribeToken }
                val confirmation = client.get("/comments/unsubscribe/$token")
                assertEquals(HttpStatusCode.OK, confirmation.status)
                assertTrue(confirmation.contentType()!!.match(ContentType.Text.Html))
                assertTrue(confirmation.bodyAsText().contains("<form method=\"post\">"))
                assertTrue(confirmation.bodyAsText().contains("Stop reply notifications"))
                assertNotNull(runBlocking { repo.getSubscription(id) })
                client.post("/comments/unsubscribe/$token")
                assertNull(runBlocking { repo.getSubscription(id) })
            }
        } finally { runBlocking { db.drop() }; mongo.close() }
    }
}
