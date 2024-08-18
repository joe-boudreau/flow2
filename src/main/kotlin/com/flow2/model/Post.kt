package com.flow2.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class Post(
    @BsonId val id: String = ObjectId().toString(),
    val title: String,
    val mdContent: String,
    val htmlContent: String,
    val bannerImage: String,
    val tags: List<String>,
    val category: Category,
    val publishedAt: Long,
    val updatedAt: Long,
    val slug: String = title.replace(Regex("[^\\x00-\\x7F]"), "")
        .replace(" ", "-")
        .lowercase()
)