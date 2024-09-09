package com.flow2.repository

interface MediaRepositoryInterface {
    fun savePostMedia(postId: String, filename: String, fileContent: ByteArray)

    fun savePostBanner(postId: String, fileContent: ByteArray)

    fun getPublicPostBannerResourcePath(postId: String): String

    fun getPublicPostMediaDir(postId: String): String
}