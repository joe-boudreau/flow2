package com.flow2.service

import com.flow2.model.Category
import com.flow2.model.Post
import com.flow2.repository.MediaRepositoryInterface
import com.flow2.repository.PostRepositoryInterface
import org.bson.types.ObjectId

class PostService(
    private val postRepository: PostRepositoryInterface,
    private val mdService: MarkdownService,
    private val mediaRepository: MediaRepositoryInterface
) {
    suspend fun getPostBySlug(slug: String): Post? = postRepository.getPostBySlug(slug)

    suspend fun getAllPosts(includeContent: Boolean = false): List<Post> = postRepository.getAllPosts(includeContent)

    suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category
    ): Post {
        val postId = ObjectId().toString()
        val imgSrcPrefix = mediaRepository.getPublicPostMediaDir(postId)
        val htmlContent = mdService.parseHtmlContent(mdContent, imgSrcPrefix)
        return postRepository.createPost(postId, title, mdContent, htmlContent, tags, category)
    }
}