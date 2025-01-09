package com.flow2.request.web

import io.ktor.resources.*

@Resource("/tag/{tag}")
data class GetPostsByTag(val tag: String)
