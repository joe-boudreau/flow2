package com.flow2.repository.posts

import com.flow2.model.Category
import com.flow2.model.Post

interface PostRepositoryInterface {
    suspend fun getPostBySlug(slug: String): Post?

    suspend fun getPost(id: String): Post?

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post>

    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category,
        publishedAt: Long? = null,
    ): Post

    suspend fun updatePost(
        id: String,
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category,
    ): Post

    suspend fun deletePost(id: String): Boolean

    suspend fun getPostsByCategory(category: Category, includeContent: Boolean = false): List<Post>

    suspend fun getPostsByTag(tag: String, includeContent: Boolean = false): List<Post>

    suspend fun searchPosts(query: String): List<Post>
}