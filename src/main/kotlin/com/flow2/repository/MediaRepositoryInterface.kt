package com.flow2.repository

interface MediaRepositoryInterface {

    fun savePostMedia(postId: String, filename: String, fileContent: ByteArray)

    fun getPostBannerFilePath(postId: String): String
}