package com.flow2.repository.posts

import com.flow2.model.Category
import com.flow2.model.Post
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class MongoPostRepository(
    private val db: MongoDatabase
): PostRepositoryInterface {

    override suspend fun getPostBySlug(slug: String): Post? = db
        .getCollection<Post>("posts")
        .find(eq("slug", slug))
        .firstOrNull()

    override suspend fun getAllPosts(includeContent: Boolean) =
        findPosts(includeContent)

    override suspend fun getPostsByCategory(category: Category, includeContent: Boolean) =
        findPosts(includeContent, eq("category", category))

    override suspend fun getPostsByTag(tag: String, includeContent: Boolean) =
        findPosts(includeContent, eq("tags", tag))

    /**
     * Default sorted newest to oldest
     */
    private suspend fun findPosts(
        includeContent: Boolean,
        filter: Bson = Filters.empty(),
        sort: Bson = Sorts.descending("publishedAt")
    ): List<Post> {
        var findFlow = db.getCollection<Post>("posts")
            .find(filter)
            .sort(sort)


        if (!includeContent) {
            findFlow = findFlow.projection(Projections.exclude(Post::mdContent.name))
        }
        return findFlow.toList()
    }

    override suspend fun createPost(
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category,
    ): Post {
        val currentTime = System.currentTimeMillis()
        val post = Post(
            title = title,
            mdContent = mdContent,
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

