package com.flow2.repository

import com.flow2.model.Category
import com.flow2.model.Post
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class MongoPostRepository(
    private val db: MongoDatabase
): PostRepositoryInterface
{

    override suspend fun getPostBySlug(slug: String): Post? = db
            .getCollection<Post>("posts")
            .find(eq("slug", slug))
            .firstOrNull()


    override suspend fun createPost(
        title: String,
        mdContent: String,
        htmlContent: String,
        bannerImage: String,
        tags: List<String>,
        category: Category,
    ): Post {
        val currentTime = System.currentTimeMillis()
        val post = Post(
            title = title,
            mdContent = mdContent,
            htmlContent = htmlContent,
            bannerImage = bannerImage,
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