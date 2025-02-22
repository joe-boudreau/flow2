package com.flow2.routing

import com.flow2.auth.ADMIN_LOGIN_CONFIG
import com.flow2.auth.ADMIN_SESSION_CONFIG
import com.flow2.auth.AdminUser
import com.flow2.model.Category
import com.flow2.service.PostService
import com.flow2.repository.media.MediaRepositoryInterface
import com.flow2.request.CreatePostRequest
import com.flow2.service.RssService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import io.ktor.server.thymeleaf.*
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Application.configureAdminRoutes() {
    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()
    val rssService by inject<RssService>()

    val adminCookieName = environment.config.property("app.adminAuth.sessionCookie").getString()

    routing {
        get("/login") {
            call.respond(ThymeleafContent("login", mapOf()))
        }

        authenticate(ADMIN_LOGIN_CONFIG) {
            post("/login") {
                val username = call.principal<UserIdPrincipal>()?.name

                if (username != null) {
                    call.sessions.set(adminCookieName, AdminUser(username))
                    call.respondRedirect("/admin")
                } else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        authenticate(ADMIN_SESSION_CONFIG) {
            route("/admin") {
                get {
                    val allPosts = postService.getAllPosts()
                    val model: MutableMap<String, Any> = mutableMapOf(
                        "posts" to allPosts,
                        "categories" to Category.entries,
                    )

                    call.request.queryParameters["postForUpdate"]?.let { postId ->
                        postService.getPost(postId)?.let { post ->
                            model["postForUpdate"] = post
                        }
                    }

                    call.respond(ThymeleafContent("admin", model))
                }

                post("/post") {
                    var title: String? = null
                    var categoryStr: String? = null
                    var tags: String?= null
                    var mdFile: String? = null
                    var publishedAt: String? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part.name) {
                            "title" -> title = (part as PartData.FormItem).value
                            "category" -> categoryStr = (part as PartData.FormItem).value
                            "tags" -> tags = (part as PartData.FormItem).value
                            "publishedAt" -> publishedAt = (part as PartData.FormItem).value
                            "mdFile" -> mdFile = (part as PartData.FileItem).provider().readRemaining().readText()
                        }
                    }

                    if (title == null || categoryStr == null || tags == null || mdFile == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val tagsList = tags.split(",")
                    val category = Category.valueOf(categoryStr)
                    var publishedAtTimestamp = publishedAt?.let {
                        if (publishedAt.isEmpty()) {
                            null
                        } else
                        LocalDateTime
                            .parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME)
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                    }

                    postService.createPost(title, mdFile.toString(), tagsList, category, publishedAtTimestamp)
                    call.respond(HttpStatusCode.Created)
                }

                put("/post/{id}") {
                    val parameters = call.receiveParameters()
                    val id = call.pathParameters["id"]
                    val title = parameters["title"]
                    val categoryStr = parameters["category"]
                    val tags = parameters["tags"]
                    val mdContent = parameters["mdContent"]

                    if (id == null || title == null || categoryStr == null || tags == null || mdContent == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }

                    val tagsList = tags.split(",")
                    val category = Category.valueOf(categoryStr)

                    postService.updatePost(id, title, mdContent, tagsList, category)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/post/{id}") {
                    val id = call.pathParameters["id"]
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }

                    val success = postService.deletePost(id)
                    call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }

                post("/post/banner") {
                    var postId: String? = null
                    var bannerFile: ByteArray? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part) {
                            is PartData.FileItem -> {
                                bannerFile = part.provider().readRemaining().readByteArray()
                            }
                            is PartData.FormItem -> {
                                postId = part.value
                            }
                            else -> {
                                //noop
                            }
                        }
                    }

                    if (postId == null || bannerFile == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    mediaRepository.savePostMedia(postId, "banner", bannerFile)
                    call.respond(HttpStatusCode.Created)
                }

                post("/post/media") {
                    val files = mutableMapOf<String, ByteArray>()
                    var postId: String? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part) {
                            is PartData.FileItem -> {
                                files[part.originalFileName!!] = part.provider().readRemaining().readByteArray()
                            }
                            is PartData.FormItem -> {
                                postId = part.value
                            }
                            else -> {
                                //noop
                            }
                        }
                    }

                    if (postId == null || files.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    files.forEach { filename, data ->
                        mediaRepository.savePostMedia(postId, filename, data)
                    }

                    call.respond(HttpStatusCode.Created)
                }

                post("/api/post") {
                    val request = call.receive<CreatePostRequest>()

                    val post = postService.createPost(
                        request.title,
                        request.mdContent,
                        request.tags,
                        request.category
                    )
                    call.respond(HttpStatusCode.Created, "Post created with id: ${post.id}")
                }
            }
        }
    }
}