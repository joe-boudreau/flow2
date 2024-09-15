package com.flow2.plugin

import com.flow2.request.api.CreatePostRequest
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.request.web.GetPostRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post as postRoute
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

        postRoute("/admin/post/banner") {
            val multiparts = call.receiveMultipart().readAllParts()

            val bannerPart = multiparts.first { it is PartData.FileItem } as PartData.FileItem
            val postIdPart = multiparts.first { it is PartData.FormItem } as PartData.FormItem
            val postId = postIdPart.value

            val bannerContent = bannerPart.streamProvider().readBytes()
            mediaRepository.savePostMedia(postId, "banner", bannerContent)
            call.respond(HttpStatusCode.Created)
        }

        postRoute("/admin/post/media") {
            val multiparts = call.receiveMultipart().readAllParts()

            val mediaParts = multiparts.filterIsInstance<PartData.FileItem>()
            val postIdPart = multiparts.first { it is PartData.FormItem } as PartData.FormItem
            val postId = postIdPart.value

            mediaParts.forEach { part ->
                val mediaContent = part.streamProvider().readBytes()
                mediaRepository.savePostMedia(postId, part.originalFileName!!, mediaContent)
            }

            call.respond(HttpStatusCode.Created)
        }

        get("/") {
            val allPosts = postService.getAllPosts()
            val postUrls = allPosts.associateBy(
                { it.id },
                { application.href(GetPostRequest(it.slug)) }
            )

            call.respond(ThymeleafContent("home", mapOf(
                "posts" to allPosts,
                "postUrls" to postUrls
            )))
        }

        get<GetPostRequest> { req ->
            val post = postService.getPostBySlug(req.slug)

            if (post != null) {
                val bannerFilePath = mediaRepository.getPublicPostBannerResourcePath(post.id)
                call.respond(ThymeleafContent("post", mapOf(
                    "post" to post,
                    "bannerFilePath" to bannerFilePath,
                )))
            } else {
                call.respond(HttpStatusCode.NotFound, ThymeleafContent("404", emptyMap()))
            }
        }

        post<CreatePostRequest> { req ->
            val post = postService.createPost(
                req.title,
                req.mdContent,
                req.tags,
                req.category
            )
            call.respond(HttpStatusCode.Created, "Post created with id: ${post.id}")
        }

        staticResources(ASSETS_RESOURCE_PATH, "assets")
    }
}