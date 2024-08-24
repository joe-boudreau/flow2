package com.flow2.service

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.PostRepositoryInterface

class PostService(
    private val postRepository: PostRepositoryInterface,
    private val mdService: MarkdownService
) {
    suspend fun getPostBySlug(slug: String): Post? = postRepository.getPostBySlug(slug)

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post> = postRepository.getAllPosts(includeContent)

    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ): Post {
        val htmlContent = mdService.parseToHtml(mdContent)
        return postRepository.createPost(title, mdContent, htmlContent, tags, category)
    }
}