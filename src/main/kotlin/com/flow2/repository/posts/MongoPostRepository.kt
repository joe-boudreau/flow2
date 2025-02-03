package com.flow2.repository.posts

import com.flow2.model.Category
import com.flow2.model.Post
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.conversions.Bson

internal val log = KtorSimpleLogger(MongoPostRepository::class.simpleName!!)

class MongoPostRepository(
    db: MongoDatabase
): PostRepositoryInterface {

    private val postCollection = db.getCollection<Post>("posts")

    init {
        runBlocking {
            postCollection.createIndex(
                Indexes.descending(Post::slug.name), IndexOptions().unique(true)
            )
            postCollection.createIndex(
                Indexes.compoundIndex(
                    Indexes.text(Post::title.name),
                    Indexes.text(Post::mdContent.name),
                ),
                IndexOptions().name("textSearchIndex").weights(Document()
                    .append(Post::title.name, 5)
                    .append(Post::mdContent.name, 1)
                )
            )
            log.info("Post Collection initialized with indexes")
        }
    }

    override suspend fun getPostBySlug(slug: String): Post? = postCollection
        .find(eq(Post::slug.name, slug))
        .firstOrNull()

    override suspend fun getPost(id: String): Post? = postCollection
            .find(eq(id))
            .firstOrNull()

    override suspend fun getAllPosts(includeContent: Boolean) =
        findPosts(includeContent)

    override suspend fun getPostsByCategory(category: Category, includeContent: Boolean) =
        findPosts(includeContent, eq(Post::category.name, category))

    override suspend fun getPostsByTag(tag: String, includeContent: Boolean) =
        findPosts(includeContent, eq(Post::tags.name, tag))

    /**
     * Default sorted newest to oldest
     */
    private suspend fun findPosts(
        includeContent: Boolean,
        filter: Bson = Filters.empty(),
        sort: Bson = Sorts.descending(Post::publishedAt.name)
    ): List<Post> {
        var findFlow = postCollection
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
        publishedAt: Long?,
    ): Post {
        val publishedTimestamp = publishedAt ?: System.currentTimeMillis()
        val post = Post(
            title = title,
            mdContent = mdContent,
            tags = tags,
            category = category,
            publishedAt = publishedTimestamp,
            updatedAt = publishedTimestamp,
        )

        val result = postCollection.insertOne(post)
        return postCollection.find(eq(result.insertedId)).first()
    }

    override suspend fun updatePost(
        id: String,
        title: String,
        mdContent: String,
        tags: List<String>,
        category: Category,
    ): Post {
        val currentTime = System.currentTimeMillis()
        val updates = Updates.combine(
            Updates.set(Post::title.name, title),
            Updates.set(Post::mdContent.name, mdContent),
            Updates.set(Post::tags.name, tags),
            Updates.set(Post::category.name, category),
            Updates.set(Post::updatedAt.name, currentTime),
        )
        postCollection.updateOne(eq(id), updates)
        return postCollection.find(eq(id)).first()
    }

    override suspend fun deletePost(id: String): Boolean {
        val result = postCollection.deleteOne(eq(id))
        return result.deletedCount == 1L
    }

    override suspend fun searchPosts(query: String): List<Post> {
        return postCollection.find(Filters.text(query)).sort(Sorts.metaTextScore("score")).toList()
    }
}

