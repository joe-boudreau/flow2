package com.flow2.service

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.repository.PostRepositoryInterface
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId

class PostService(
    private val postRepository: PostRepositoryInterface,
    private val mdService: MarkdownService,
    private val mediaRepository: MediaRepositoryInterface
) {
    /**
     * In-memory cache of all posts without content included. Sorted newest to oldest
     */
    private val allPosts = mutableListOf<Post>()

    init {
        runBlocking() {
            allPosts.addAll(getAllPosts())
        }
    }
    suspend fun getPostBySlug(slug: String): Post? = postRepository.getPostBySlug(slug)

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post> = postRepository.getAllPosts(includeContent)

    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ): Post {
        val postId = ObjectId().toString()
        val postMediaDir = mediaRepository.getPublicPostMediaDir(postId)
        val htmlContent = mdService.parseHtmlContent(mdContent, postMediaDir)
        return postRepository.createPost(postId, title, mdContent, htmlContent, tags, category)
    }

    suspend fun getPostsByCategory(category: Category): List<Post> {
        return postRepository.getPostsByCategory(category)
    }

    suspend fun getPostsByTag(tag: String): List<Post> {
        return postRepository.getPostsByTag(tag)
    }

    fun getPreviousAndNext(post: Post): Pair<Post?, Post?> {
        val postIndex = allPosts.indexOfFirst { it.id == post.id }
        if (postIndex == -1) return null to null

        val previousPost = if (postIndex > 0) allPosts[postIndex - 1] else null
        val nextPost = if (postIndex < allPosts.lastIndex) allPosts[postIndex + 1] else null
        return previousPost to nextPost
    }
}