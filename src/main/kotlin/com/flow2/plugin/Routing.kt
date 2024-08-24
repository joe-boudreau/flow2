package com.flow2.plugin

import com.flow2.request.CreatePostRequest
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {

    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()

    routing {
        get("/admin") {
            val allPosts = postService.getAllPosts()
            call.respond(ThymeleafContent("admin", mapOf("posts" to allPosts)))
        }

        get("/post/{slug}") {
            val slug = call.parameters["slug"] ?: ""
            val post = postService.getPostBySlug(slug)

            if (post != null) {
                val bannerFilePath = mediaRepository.getPostBannerFilePath(post.id)
                call.respond(ThymeleafContent("index", mapOf(
                    "post" to post,
                    "bannerFilePath" to bannerFilePath,
                )))
            } else {
                call.respond(ThymeleafContent("404", emptyMap()))
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

        staticResources("/assets", "assets")
    }
}