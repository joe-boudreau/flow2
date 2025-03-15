package com.flow2.repository.media

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import java.io.File

const val PUBLIC_URL_PATH_PREFIX = "/media"

internal val log = KtorSimpleLogger("FSMediaRepository")

class FSMediaRepository(
    private val mediaDirectoryPath: String,
) : MediaRepositoryInterface {

    private val publicMediaDir = "$PUBLIC_URL_PATH_PREFIX/"

    private val bannerFileName = "banner"

    override fun configureRouting(app: Application) {
        app.routing {
            staticFiles(PUBLIC_URL_PATH_PREFIX, File(mediaDirectoryPath))
        }
    }

    override fun savePostMedia(postId: String, filename: String, fileContent: ByteArray) {
        File(getInternalPostMediaDir(postId)).mkdirs()
        File(getInternalFilePathForPostMedia(postId, filename)).writeBytes(fileContent)
    }

    override fun savePostBanner(postId: String, fileContent: ByteArray) {
        savePostMedia(postId, bannerFileName, fileContent)
    }

    override fun getPublicPostBannerUrl(postId: String): String? {
        File(getInternalFilePathForPostMedia(postId, bannerFileName)).let {
            return if (it.exists()) {
                getPublicFilePathForPostMedia(postId, bannerFileName)
            } else {
                null
            }
        }
    }

    override fun getPublicPostMediaUrl(postId: String) = "$publicMediaDir/$postId"

    private fun getInternalFilePathForPostMedia(postId: String, filename: String) =
        "${getInternalPostMediaDir(postId)}/$filename"

    private fun getInternalPostMediaDir(postId: String) = "$mediaDirectoryPath/$postId"

    private fun getPublicFilePathForPostMedia(postId: String, filename: String) =
        "${getPublicPostMediaUrl(postId)}/$filename"
}