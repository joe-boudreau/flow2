package com.flow2.routing

import com.flow2.auth.AdminUser
import com.flow2.config.ADMIN_AUTH
import com.flow2.config.ADMIN_SESSION_COOKIE
import com.flow2.model.Category
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.request.CreatePostRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import io.ktor.server.thymeleaf.*
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import io.ktor.utils.io.toByteArray
import org.koin.ktor.ext.inject

fun Application.configureAdminRoutes() {

    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()

    routing {
        get("/login") {
            call.respond(ThymeleafContent("login", mapOf()))
        }

        authenticate(ADMIN_AUTH) {
            post("/login") {
                val username = call.principal<UserIdPrincipal>()?.name
                println("Username: $username")
                if (username != null) {
                    call.sessions.set(ADMIN_SESSION_COOKIE, AdminUser(username))
                    call.respondRedirect("/admin")
                } else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        authenticate("admin-session") {
            route("/admin") {
                get {
                    val allPosts = postService.getAllPosts()
                    call.respond(ThymeleafContent("admin", mapOf(
                        "posts" to allPosts,
                        "categories" to Category.entries
                    )))
                }

                post("/post") {
                    var title: String? = null
                    var categoryStr: String? = null
                    var tags: String?= null
                    var mdFile: String? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part.name) {
                            "title" -> title = (part as PartData.FormItem).value
                            "category" -> categoryStr = (part as PartData.FormItem).value
                            "tags" -> tags = (part as PartData.FormItem).value
                            "mdFile" -> mdFile = (part as PartData.FileItem).provider().readRemaining().readText()
                        }
                    }

                    if (title == null || categoryStr == null || tags == null || mdFile == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val tagsList = tags.split(",")
                    val category = Category.valueOf(categoryStr)

                    postService.createPost(title, mdFile.toString(), tagsList, category)
                    call.respond(HttpStatusCode.Created)
                }

                post("/post/banner") {
                    var postId: String? = null
                    var bannerContent: ByteArray? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part) {
                            is PartData.FileItem -> {
                                bannerContent = part.provider().toByteArray()
                            }
                            is PartData.FormItem -> {
                                postId = part.value
                            }
                            else -> {
                                //noop
                            }
                        }
                    }

                    if (postId == null || bannerContent == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    mediaRepository.savePostMedia(postId, "banner", bannerContent)
                    call.respond(HttpStatusCode.Created)
                }

                post("/post/media") {
                    val mediaParts = mutableListOf<PartData.FileItem>()
                    var postId: String? = null

                    call.receiveMultipart().forEachPart { part ->
                        when(part) {
                            is PartData.FileItem -> {
                                mediaParts.add(part)
                            }
                            is PartData.FormItem -> {
                                postId = part.value
                            }
                            else -> {
                                //noop
                            }
                        }
                    }

                    if (postId == null || mediaParts.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    mediaParts.forEach { part ->
                        val bytes = part.provider().toByteArray()
                        mediaRepository.savePostMedia(postId, part.originalFileName!!, bytes)
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