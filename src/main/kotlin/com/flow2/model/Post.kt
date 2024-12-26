package com.flow2.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.util.*

data class Post(
    @BsonId val id: String = ObjectId().toString(),
    val title: String,
    val mdContent: String?,
    val tags: List<String>,
    val category: Category,
    val publishedAt: Long,
    val updatedAt: Long,
    val slug: String = slugify(title)
) {
    fun getPublishDate() = Date(publishedAt)
    fun getUpdateDate() = Date(updatedAt)
}

private fun slugify(title: String) = title
    .replace(Regex("[^\\x00-\\x7F]"), "")
    .replace(" ", "-")
    .lowercase()