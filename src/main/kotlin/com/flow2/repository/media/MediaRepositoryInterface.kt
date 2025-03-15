package com.flow2.repository.media

import io.ktor.server.application.Application

interface MediaRepositoryInterface {
    fun savePostMedia(postId: String, filename: String, fileContent: ByteArray)

    fun savePostBanner(postId: String, fileContent: ByteArray)

    fun getPublicPostBannerUrl(postId: String): String?

    fun getPublicPostMediaUrl(postId: String): String

    fun configureRouting(application: Application)
}