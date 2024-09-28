package com.flow2.repository

import com.flow2.plugin.MEDIA_RESOURCE_PATH
import java.io.File

const val INTERNAL_MEDIA_DIR = "src/main/resources/media"

class MediaRepository : MediaRepositoryInterface {

    // TODO replace with config values
    private val publicMediaDir = "$MEDIA_RESOURCE_PATH/"

    private val bannerFileName = "banner"
    private val defaultBannerFilePath = "/assets/images/banner_image_ex.jpg"

    override fun savePostMedia(postId: String, filename: String, fileContent: ByteArray) {
        File(getInternalPostMediaDir(postId)).mkdirs()
        File(getInternalFilePathForPostMedia(postId, filename)).writeBytes(fileContent)
    }

    override fun savePostBanner(postId: String, fileContent: ByteArray) {
        savePostMedia(postId, bannerFileName, fileContent)
    }

    override fun getPublicPostBannerResourcePath(postId: String): String {
        File(getInternalFilePathForPostMedia(postId, bannerFileName)).let {
            return if (it.exists()) {
                getPublicFilePathForPostMedia(postId, bannerFileName)
            } else {
                defaultBannerFilePath
            }
        }
    }

    override fun getPublicPostMediaDir(postId: String) = "$publicMediaDir/$postId"

    private fun getInternalFilePathForPostMedia(postId: String, filename: String) =
        "${getInternalPostMediaDir(postId)}/$filename"

    private fun getInternalPostMediaDir(postId: String) = "$INTERNAL_MEDIA_DIR/$postId"

    private fun getPublicFilePathForPostMedia(postId: String, filename: String) =
        "${getPublicPostMediaDir(postId)}/$filename"
}