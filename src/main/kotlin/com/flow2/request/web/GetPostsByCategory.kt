package com.flow2.request.web

import io.ktor.resources.*

@Resource("/category/{category}")
data class GetPostsByCategory(val category: String)
