package com.flow2.plugin

import com.flow2.request.CreatePostRequest
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.koin.ktor.ext.inject

const val ASSETS_RESOURCE_PATH = "/assets"

fun Application.configureRouting() {

    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()

    routing {
        get("/admin") {
            val allPosts = postService.getAllPosts()
            call.respond(ThymeleafContent("admin", mapOf("posts" to allPosts)))
        }

        post("/admin/post/banner") {
            val multiparts = call.receiveMultipart().readAllParts()

            val bannerPart = multiparts.first { it is PartData.FileItem } as PartData.FileItem
            val postIdPart = multiparts.first { it is PartData.FormItem } as PartData.FormItem
            val postId = postIdPart.value

            val bannerContent = bannerPart.streamProvider().readBytes()
            mediaRepository.savePostMedia(postId, "banner", bannerContent)
            call.respond(HttpStatusCode.Created)
        }

        post("/admin/post/media") {
            val multipart = call.receiveMultipart()
            val postId = call.parameters["postId"] ?: ""

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val mediaContent = part.streamProvider().readBytes()
                    mediaRepository.savePostMedia(postId, part.originalFileName!!, mediaContent)
                }
            }

            call.respond(HttpStatusCode.Created)
        }

        get("/post/{slug}") {
            val slug = call.parameters["slug"] ?: ""
            val post = postService.getPostBySlug(slug)

            if (post != null) {
                val bannerFilePath = mediaRepository.getPublicPostBannerResourcePath(post.id)
                call.respond(ThymeleafContent("index", mapOf(
                    "post" to post,
                    "bannerFilePath" to bannerFilePath,
                )))
            } else {
                call.respond(HttpStatusCode.NotFound, ThymeleafContent("404", emptyMap()))
            }
        }

        post("/post") {
            val request = call.receive<CreatePostRequest>()

            val post = postService.createPost(
                request.title,
                request.mdContent,
                request.tags,
                request.category
            )
            call.respond(HttpStatusCode.Created, "Post created with id: ${post.id}")
        }

        staticResources(ASSETS_RESOURCE_PATH, "assets")
    }
}