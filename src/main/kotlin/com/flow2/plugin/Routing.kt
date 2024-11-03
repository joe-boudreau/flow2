package com.flow2.plugin

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.INTERNAL_MEDIA_DIR
import com.flow2.service.PostService
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.request.web.GetPostRequest
import com.flow2.request.CreatePostRequest
import com.flow2.request.GetPostsByCategory
import com.flow2.request.GetPostsByTag
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.post as postRoute
import io.ktor.server.thymeleaf.*
import io.ktor.util.pipeline.*
import org.koin.ktor.ext.inject
import java.io.File

const val ASSETS_RESOURCE_PATH = "/assets"
const val MEDIA_RESOURCE_PATH = "/media"

fun Application.configureRouting() {

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
            val multiparts = call.receiveMultipart().readAllParts()

            val mdFilePart = multiparts.first { it.name == "mdFile" } as PartData.FileItem

            val mdContent = mdFilePart.streamProvider().bufferedReader().use { it.readText() }

            val title = (multiparts.first { it.name == "title" } as PartData.FormItem).value
            val categoryStr = (multiparts.first { it.name == "category" } as PartData.FormItem).value
            val category = Category.valueOf(categoryStr)
            val tags = (multiparts.first { it.name == "tags" } as PartData.FormItem).value.split(",")

            postService.createPost(title, mdContent, tags, category)
            call.respond(HttpStatusCode.Created)
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
                val streamProvider = part.streamProvider()
                val bytes = streamProvider.readBytes()
                streamProvider.close()
                mediaRepository.savePostMedia(postId, part.originalFileName!!, bytes)
            }

            call.respond(HttpStatusCode.Created)
        }

        get("/") {
            val allPosts = postService.getAllPosts()

            call.respond(ThymeleafContent("index", mapOf(
                "posts" to allPosts,
                "postUrls" to getPostUrlMap(allPosts)
            )))
        }

        get<GetPostRequest> { req ->
            val post = postService.getPostBySlug(req.slug)

            if (post == null) {
                call.respond404()
                return@get
            }

            val bannerFilePath = mediaRepository.getPublicPostBannerResourcePath(post.id)
            call.respond(ThymeleafContent("post", mapOf(
                "post" to post,
                "bannerFilePath" to bannerFilePath,
            )))
        }

        get<GetPostsByCategory> { req ->
            val category = Category.getByUrlName(req.category)

            if (category == null) {
                call.respond404()
                return@get
            }

            val posts = postService.getPostsByCategory(category)

            if (posts.isEmpty()) {
                call.respond404()
                return@get
            }

            call.respond(ThymeleafContent("category", mapOf(
                "posts" to posts,
                "postUrls" to getPostUrlMap(posts),
                "category" to category,
            )))
        }

        get<GetPostsByTag> { req ->
            val posts = postService.getPostsByTag(req.tag)

            if (posts.isEmpty()) {
                call.respond404()
                return@get
            }

            call.respond(ThymeleafContent("tag", mapOf(
                "posts" to posts,
                "postUrls" to getPostUrlMap(posts),
                "tag" to req.tag,
            )))
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

        if (developmentMode) {
            staticFiles(ASSETS_RESOURCE_PATH, File("src/main/resources/assets"))
        } else {
            staticResources(ASSETS_RESOURCE_PATH, "assets")
        }

        staticFiles(MEDIA_RESOURCE_PATH, File(INTERNAL_MEDIA_DIR))
    }
}

private suspend fun ApplicationCall.respond404() {
    this.respond(HttpStatusCode.NotFound, ThymeleafContent("404", emptyMap()))
}

private fun PipelineContext<Unit, ApplicationCall>.getPostUrlMap(posts: List<Post>) = posts.associateBy(
        { it.id },
        { application.href<GetPostRequest>(GetPostRequest(it.slug)) }
)