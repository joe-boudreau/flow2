package com.flow2.request

import io.ktor.resources.*

@Resource("/tag/{tag}")
data class GetPostsByTag(val tag: String)
