package com.flow2.request

import com.flow2.model.Category
import kotlinx.serialization.Serializable

@Serializable
data class CreatePostRequest(
    val title: String,
    val mdContent: String,
    val tags: List<String>,
    val category: Category
)