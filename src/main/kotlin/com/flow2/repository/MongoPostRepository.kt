package com.flow2.repository

import com.flow2.model.Category
import com.flow2.model.Post
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

class MongoPostRepository(
    private val db: MongoDatabase
): PostRepositoryInterface
{

    override suspend fun getPostBySlug(slug: String): Post? = db
            .getCollection<Post>("posts")
            .find(eq("slug", slug))
            .firstOrNull()

    override suspend fun getAllPosts(includeContent: Boolean): List<Post> {
        val posts = db.getCollection<Post>("posts")
        var findFlow = posts.find()

        if (!includeContent) {
            findFlow = findFlow.projection(Projections.exclude(Post::mdContent.name, Post::htmlContent.name))
        }
        return findFlow.toList()
    }


    override suspend fun createPost(
        title: String,
        mdContent: String,
        htmlContent: String,
        tags: List<String>,
        category: Category,
    ): Post {
        val currentTime = System.currentTimeMillis()
        val post = Post(
            title = title,
            mdContent = mdContent,
            htmlContent = htmlContent,
            tags = tags,
            category = category,
            publishedAt = currentTime,
            updatedAt = currentTime,
        )

        val posts = this.db.getCollection<Post>("posts")
        val result = posts.insertOne(post)
        return posts.find(eq("_id", result.insertedId)).first()
    }


}