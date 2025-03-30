package com.flow2.request.web

import com.flow2.model.Category
import com.flow2.model.Post
import io.ktor.server.application.Application
import io.ktor.server.resources.href

class RequestUrlBuilder(private val app: Application, private val schemeDomainPort: String) {
    fun getPostUrl(post: Post) = app.href<GetPostRequest>(GetPostRequest(post.slug))
    fun getCategoryUrl(category: Category) = app.href<GetPostsByCategory>(GetPostsByCategory(category.urlName))
    fun getTagUrl(tag: String) = app.href<GetPostsByTag>(GetPostsByTag(tag))

    fun getPostAbsoluteUrl(post: Post) = "$schemeDomainPort${getPostUrl(post)}"
    fun getCategoryAbsoluteUrl(category: Category) = "$schemeDomainPort${getCategoryUrl(category)}"
    fun getTagAbsoluteUrl(tag: String) = "$schemeDomainPort${getTagUrl(tag)}"

    fun getRootUrl() = schemeDomainPort

    fun getAbsoluteUrl(relativeUrl: String) : String {
        val relativeUrlWithoutLeadingSlash = relativeUrl.removePrefix("/")
        return "$schemeDomainPort/$relativeUrlWithoutLeadingSlash"
    }
}