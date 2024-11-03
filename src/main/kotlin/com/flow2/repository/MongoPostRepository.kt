package com.flow2.repository

import com.flow2.model.Category
import com.flow2.model.Post
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson

class MongoPostRepository(
    private val db: MongoDatabase
): PostRepositoryInterface {

    override suspend fun getPostBySlug(slug: String): Post? = db
        .getCollection<Post>("posts")
        .find(eq("slug", slug))
        .firstOrNull()

    override suspend fun getAllPosts(includeContent: Boolean): List<Post> {
        return getPostsWithFilter(Filters.empty(), includeContent)
    }


    override suspend fun createPost(
        id: String,
        title: String,
        mdContent: String,
        htmlContent: String,
        tags: List<String>,
        category: Category,
    ): Post {
        val currentTime = System.currentTimeMillis()
        val post = Post(
            id = id,
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

    override suspend fun getPostsByCategory(category: Category, includeContent: Boolean): List<Post> {
        return getPostsWithFilter(eq("category", category), includeContent)
    }

    override suspend fun getPostsByTag(tag: String, includeContent: Boolean): List<Post> {
        return getPostsWithFilter(eq("tags", tag), includeContent)
    }

    private suspend fun getPostsWithFilter(
        filter: Bson,
        includeContent: Boolean
    ): List<Post> {
        var findFlow = db.getCollection<Post>("posts")
            .find(filter)

        if (!includeContent) {
            findFlow = findFlow.projection(Projections.exclude(Post::mdContent.name, Post::htmlContent.name))
        }
        return findFlow.toList()
    }
}

