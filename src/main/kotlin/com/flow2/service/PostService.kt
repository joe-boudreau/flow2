package com.flow2.service

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.posts.PostRepositoryInterface
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking

internal val log = KtorSimpleLogger(PostService::class.simpleName!!)

class PostService(
    private val postRepository: PostRepositoryInterface,
    private val rssService: RssService,
    private val markdownService: MarkdownService,
) {

    /**
     * In-memory cache of all posts without content included. Sorted newest to oldest
     */
    private val allPosts = mutableListOf<Post>()

    private lateinit var rssFeed: String

    init {
        runBlocking {
            reloadPostCacheAndRss()
        }
    }

    private suspend fun reloadPostCacheAndRss() {
        allPosts.clear()
        allPosts.addAll(getAllPosts())
        rssFeed = rssService.createRSSFeed(getAllPosts(includeContent = true))
    }

    fun getRssFeed() = rssFeed

    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category,
        publishedAt: Long? = null,
    ): Post {
        val post = postRepository.createPost(title, mdContent, tags, category, publishedAt)
        reloadPostCacheAndRss()
        return post
    }

    suspend fun upsertPost(
        mdContentWithFrontMatter: String,
    ): Post {
        val frontMatter = markdownService.parseFrontMatter(mdContentWithFrontMatter)
        val id = frontMatter["id"]?.firstOrNull()
        val title = frontMatter["title"]?.firstOrNull() ?: "unknown-title-${System.currentTimeMillis()}"
        val publishedAt = frontMatter["publishedAt"]?.firstOrNull()?.toLongOrNull()
        val tags = frontMatter["tags"] ?: emptyList()
        val category = Category.valueOf(frontMatter["category"]?.firstOrNull() ?: "PERSONAL")

        val post = if (id == null) {
            val newPost = postRepository.createPost(title, mdContentWithFrontMatter, tags, category, publishedAt)
            val updatedContent = markdownService.addFrontMatter(newPost.mdContent?: "", mapOf(
                "id" to listOf(newPost.id),
                "publishedAt" to listOf(newPost.publishedAt.toString()),
            ))
            postRepository.updatePost(newPost.id, newPost.title, updatedContent, newPost.tags, newPost.category)
        } else {
            postRepository.updatePost(id, title, mdContentWithFrontMatter, tags, category)
        }
        reloadPostCacheAndRss()
        return post
    }

    fun generatePostContentWithFrontMatter(post: Post): String {
        val mdContent = post.mdContent ?: ""
        val existingFrontMatter = markdownService.parseFrontMatter(mdContent)

        return if (existingFrontMatter.isNotEmpty()) {
            mdContent
        } else {
            markdownService.addFrontMatter(
                mdContent, mapOf(
                "id" to listOf(post.id),
                "title" to listOf(post.title),
                "publishedAt" to listOf(post.publishedAt.toString()),
                "tags" to post.tags,
                "category" to listOf(post.category.name),
            ))
        }
    }

    suspend fun updatePost(
        id: String,
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ): Post {
        val post = postRepository.updatePost(id, title, mdContent, tags, category)
        reloadPostCacheAndRss()
        return post
    }

    suspend fun deletePost(id: String): Boolean {
        val success = postRepository.deletePost(id)
        if (success) {
            reloadPostCacheAndRss()
        }
        return success
    }

    suspend fun getPostBySlug(slug: String) = postRepository.getPostBySlug(slug)

    suspend fun getPost(id: String) = postRepository.getPost(id)

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post> = postRepository.getAllPosts(includeContent)

    suspend fun getPostsByCategory(category: Category) = postRepository.getPostsByCategory(category)

    suspend fun getPostsByTag(tag: String) = postRepository.getPostsByTag(tag)

    suspend fun searchPosts(query: String) = postRepository.searchPosts(query)

    fun getPreviousAndNext(post: Post): Pair<Post?, Post?> {
        val postIndex = allPosts.indexOfFirst { it.id == post.id }
        if (postIndex == -1) return null to null

        val previousPost = if (postIndex > 0) allPosts[postIndex - 1] else null
        val nextPost = if (postIndex < allPosts.lastIndex) allPosts[postIndex + 1] else null
        return previousPost to nextPost
    }
}