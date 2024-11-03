package com.flow2.repository

import com.flow2.model.Category
import com.flow2.model.Post
import org.bson.types.ObjectId

interface PostRepositoryInterface {
    suspend fun getPostBySlug(slug: String): Post?

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post>

    suspend fun createPost(
        id: String = ObjectId().toString(),
        title: String,
        mdContent: String,
        htmlContent: String,
        tags: List<String>,
        category: Category,
    ): Post

    suspend fun getPostsByCategory(category: Category, includeContent: Boolean = false): List<Post>

    suspend fun getPostsByTag(tag: String, includeContent: Boolean = false): List<Post>
}