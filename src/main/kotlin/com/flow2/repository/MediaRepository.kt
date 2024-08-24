package com.flow2.repository

import java.io.File

class MediaRepository: MediaRepositoryInterface {

    // TODO: replace with config value
    private val mediaDir = "/resources/assets/media"
    private val bannerFileName = "banner"
    private val defaultBannerFilePath = "/assets/images/banner_image_ex.jpg"

    override fun savePostMedia(postId: String, filename: String, fileContent: ByteArray) {
        File(getFilePathForPostMedia(postId, filename)).writeBytes(fileContent)
    }

    override fun getPostBannerFilePath(postId: String): String {
        File(getFilePathForPostMedia(postId, bannerFileName))
            .takeIf { it.exists() }
            ?.let { return it.path } ?: return defaultBannerFilePath
    }


    private fun getFilePathForPostMedia(postId: String, filename: String) = "$mediaDir/$postId/$filename"
}