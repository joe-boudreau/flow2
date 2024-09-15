package com.flow2.request.api

import com.flow2.model.Category
import io.ktor.resources.*

@Resource("/post")
data class CreatePostRequest(
    val title: String,
    val mdContent: String,
    val tags: List<String>,
    val category: Category
)