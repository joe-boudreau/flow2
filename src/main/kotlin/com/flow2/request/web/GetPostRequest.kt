package com.flow2.request.web

import io.ktor.resources.*

@Resource("/post/{slug}")
data class GetPostRequest(val slug: String)