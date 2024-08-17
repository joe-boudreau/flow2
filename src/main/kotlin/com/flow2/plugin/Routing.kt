package com.flow2.plugin

import com.flow2.repository.PostRepositoryInterface
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {

    // inject HelloService
    val postRepository by inject<PostRepositoryInterface>()

    routing {
        get("/post/{slug}") {
            val slug = call.parameters["slug"] ?: ""
            val post = postRepository.getPostBySlug(slug)

            if (post != null) {
                call.respond(ThymeleafContent("index", mapOf("post" to post)))
            } else {
                call.respond(ThymeleafContent("404", emptyMap()))
            }
        }

        staticResources("/assets", "assets")
    }
}