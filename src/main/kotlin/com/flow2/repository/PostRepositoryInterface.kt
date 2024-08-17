package com.flow2.repository

import com.flow2.model.Category
import com.flow2.model.Post

interface PostRepositoryInterface {
    suspend fun getPostBySlug(slug: String): Post?

    suspend fun createPost(
        title: String,
        mdContent: String,
        htmlContent: String,
        bannerImage: String,
        tags: List<String>,
        category: Category,
    ): Post
}