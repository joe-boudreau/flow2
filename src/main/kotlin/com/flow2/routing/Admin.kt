package com.flow2.routing

import com.flow2.model.Category
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.request.CreatePostRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.post as postRoute
import io.ktor.server.thymeleaf.*
import io.ktor.utils.io.toByteArray
import org.koin.ktor.ext.inject

fun Application.configureAdminRoutes() {

    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()

    routing {
        get("/admin") {
            val allPosts = postService.getAllPosts()
            call.respond(ThymeleafContent("admin", mapOf(
                "posts" to allPosts,
                "categories" to Category.entries
            )))
        }

        postRoute("/admin/post") {
            var title: String? = null
            var categoryStr: String? = null
            var tags: String?= null
            var mdFile: String? = null

            call.receiveMultipart().forEachPart { part ->
                when(part.name) {
                    "title" -> title = (part as PartData.FormItem).value
                    "category" -> categoryStr = (part as PartData.FormItem).value
                    "tags" -> tags = (part as PartData.FormItem).value
                    "mdFile" -> mdFile = (part as PartData.FileItem).provider().toString()
                }
            }

            if (title == null || categoryStr == null || tags == null || mdFile == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@postRoute
            }

            val tagsList = tags.split(",")
            val category = Category.valueOf(categoryStr)

            postService.createPost(title, mdFile, tagsList, category)
            call.respond(HttpStatusCode.Created)
        }

        postRoute("/admin/post/banner") {
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
                return@postRoute
            }
            mediaRepository.savePostMedia(postId, "banner", bannerContent)
            call.respond(HttpStatusCode.Created)
        }

        postRoute("/admin/post/media") {
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
                return@postRoute
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