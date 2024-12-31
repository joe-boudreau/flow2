package com.flow2.service

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.posts.PostRepositoryInterface
import kotlinx.coroutines.runBlocking

class PostService(
    private val postRepository: PostRepositoryInterface,
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
    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ) = postRepository.createPost(title, mdContent, tags, category)

    suspend fun updatePost(
        id: String,
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ) = postRepository.updatePost(id, title, mdContent, tags, category)

    suspend fun deletePost(id: String) = postRepository.deletePost(id)

    suspend fun getPostBySlug(slug: String) = postRepository.getPostBySlug(slug)

    suspend fun getPost(id: String) = postRepository.getPost(id)

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post> = postRepository.getAllPosts(includeContent)

    suspend fun getPostsByCategory(category: Category) = postRepository.getPostsByCategory(category)

    suspend fun getPostsByTag(tag: String) = postRepository.getPostsByTag(tag)

    fun getPreviousAndNext(post: Post): Pair<Post?, Post?> {
        val postIndex = allPosts.indexOfFirst { it.id == post.id }
        if (postIndex == -1) return null to null

        val previousPost = if (postIndex > 0) allPosts[postIndex - 1] else null
        val nextPost = if (postIndex < allPosts.lastIndex) allPosts[postIndex + 1] else null
        return previousPost to nextPost
    }
}