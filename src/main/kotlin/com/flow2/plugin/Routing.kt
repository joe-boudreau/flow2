package com.flow2.plugin

import com.flow2.request.CreatePostRequest
import com.flow2.service.PostService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {

    // inject HelloService
    val postService by inject<PostService>()

    routing {
        get("/post/{slug}") {
            val slug = call.parameters["slug"] ?: ""
            val post = postService.getPostBySlug(slug)

            if (post != null) {
                call.respond(ThymeleafContent("index", mapOf("post" to post)))
            } else {
                call.respond(ThymeleafContent("404", emptyMap()))
            }
        }

        post("/post") {
            val request = call.receive<CreatePostRequest>()

            val post = postService.createPost(
                request.title,
                request.mdContent,
                request.bannerImage,
                request.tags,
                request.category
            )
            call.respond(HttpStatusCode.Created, "Post created with id: ${post.id}")
        }

        staticResources("/assets", "assets")
    }
}