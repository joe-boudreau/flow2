package com.flow2.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
    fun getPublishDate() = LocalDateTime.ofInstant(Instant.ofEpochMilli(publishedAt), ZoneId.systemDefault());
    fun getUpdateDate() = LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedAt), ZoneId.systemDefault());
    fun getFormattedTags() = tags.map { it.trim() }.filter { it.isNotEmpty() }
}

fun slugify(title: String) = title
    .replace(Regex("[^\\x00-\\x7F]"), "")
    .replace(" ", "-")
    .lowercase()