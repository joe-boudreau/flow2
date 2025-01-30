package com.flow2.routing

import com.flow2.model.Category
import com.flow2.service.PostService
import com.flow2.repository.media.MediaRepositoryInterface
import com.flow2.repository.assets.SiteAssetRepositoryInterface
import com.flow2.request.web.GetPostRequest
import com.flow2.request.web.GetPostsByCategory
import com.flow2.request.web.GetPostsByTag
import com.flow2.service.MarkdownService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
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
            val postsByCategory = allPosts.groupBy { it.category }
            val displayCategoryOrder = listOf(
                Category.TECH,
                Category.BOOK_REVIEW,
                Category.PERSONAL,
            )
            val postsWithCategory = displayCategoryOrder.map { category ->
                category to (postsByCategory[category] ?: emptyList()).take(3)
            }

            call.respond(ThymeleafContent("index", mapOf(
                "postsWithCategory" to postsWithCategory,
            )))
        }

        get("/archive") {
            val allPosts = postService.getAllPosts()

            val postsByYear = allPosts.groupBy { it.getPublishDate().year }
            val years = postsByYear.keys.sortedDescending()
            val postsWithYear = years.map { year ->
                year to postsByYear[year]!!
            }

            call.respond(ThymeleafContent("archive", mapOf(
                "postsWithYear" to postsWithYear,
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

            val model = mutableMapOf(
                "post" to post,
                "postContentHtml" to postContentHtml,
                "bannerFilePath" to bannerFilePath,
            )

            val (prev, next) = postService.getPreviousAndNext(post)
            if (prev != null) {
                model["previousPost"] = prev
            }
            if (next != null) {
                model["nextPost"] = next
            }

            call.respond(ThymeleafContent("post", model))
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
                "tag" to req.tag,
            )))
        }

        get("/synd/rss") {
            call.respondText(postService.getRssFeed(), ContentType.Application.Rss)
        }

        get("/search") {
            val searchQuery = call.request.queryParameters["q"]
            if (searchQuery == null) {
                call.respond404()
                return@get
            }

            val posts = postService.searchPosts(searchQuery)
            call.respond(ThymeleafContent("search-result", mapOf(
                "posts" to posts,
                "searchQuery" to searchQuery,
            )))
        }
    }
}

private suspend fun ApplicationCall.respond404() {
    this.respond(HttpStatusCode.NotFound, ThymeleafContent("404", emptyMap()))
}