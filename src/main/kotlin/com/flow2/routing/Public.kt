package com.flow2.routing

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.service.PostService
import com.flow2.repository.media.MediaRepositoryInterface
import com.flow2.repository.assets.SiteAssetRepositoryInterface
import com.flow2.request.web.GetPostRequest
import com.flow2.request.GetPostsByCategory
import com.flow2.request.GetPostsByTag
import com.flow2.service.MarkdownService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.*
import org.koin.ktor.ext.inject

fun Application.configurePublicRoutes() {

    val postService by inject<PostService>()
    val mediaRepository by inject<MediaRepositoryInterface>()
    val siteAssetRepository by inject<SiteAssetRepositoryInterface>()
    val markdownService by inject<MarkdownService>()

    mediaRepository.configureRouting(this)
    siteAssetRepository.configureRouting(this)

    routing {
        get("/") {
            val allPosts = postService.getAllPosts()

            call.respond(ThymeleafContent("index", mapOf(
                "posts" to allPosts,
                "postUrls" to getPostUrlMap(allPosts)
            )))
        }

        get("/about") {
            val aboutMarkdown = siteAssetRepository.getAsset("/markdown/about.md").toString(Charsets.UTF_8)
            val aboutContentHtml = markdownService.parseHtmlContent(aboutMarkdown)

            call.respond(ThymeleafContent("about", mapOf(
                "aboutContentHtml" to aboutContentHtml,
            )))
        }

        get<GetPostRequest> { req ->
            val post = postService.getPostBySlug(req.slug)

            if (post == null || post.mdContent == null) {
                call.respond404()
                return@get
            }

            val postContentHtml = markdownService.parseHtmlContent(post.mdContent, post.id)
            val bannerFilePath = mediaRepository.getPublicPostBannerUrl(post.id)
            val (prev, next) = postService.getPreviousAndNext(post)
            val prevUrl = prev?.let { getPostUrl(it) } ?: ""
            val nextUrl = next?.let { getPostUrl(it) } ?: ""

            call.respond(ThymeleafContent("post", mapOf(
                "post" to post,
                "postContentHtml" to postContentHtml,
                "bannerFilePath" to bannerFilePath,
                "previousPostUrl" to prevUrl,
                "nextPostUrl" to nextUrl,
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
    }
}

private suspend fun ApplicationCall.respond404() {
    this.respond(HttpStatusCode.NotFound, ThymeleafContent("404", emptyMap()))
}

private fun RoutingContext.getPostUrlMap(posts: List<Post>) =
    posts.associateBy(
        { it.id },
        { getPostUrl(it) }
    )

private fun RoutingContext.getPostUrl(post: Post) =
    call.application.href<GetPostRequest>(GetPostRequest(post.slug))