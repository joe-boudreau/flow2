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

    private val bannerFileNamePrefix = "banner"

    override fun configureRouting(app: Application) {
        app.routing {
            staticFiles(PUBLIC_URL_PATH_PREFIX, File(mediaDirectoryPath))
        }
    }

    override fun savePostMedia(postId: String, filename: String, fileContent: ByteArray) {
        File(getInternalPostMediaDir(postId)).mkdirs()
        File(getInternalFilePathForPostMedia(postId, filename)).writeBytes(fileContent)
    }

    override fun savePostBanner(postId: String, fileName: String, fileContent: ByteArray) {
        // rename it to banner. whatever the file extension is
        val bannerFileName = "$bannerFileNamePrefix.${fileName.split('.').last()}"
        savePostMedia(postId, bannerFileName, fileContent)
    }

    override fun getPublicPostBannerUrl(postId: String): String? {
        val postMediaDir = File(getInternalPostMediaDir(postId))
        val bannerFullFileName = postMediaDir.listFiles()
            ?.firstOrNull { it.name.startsWith(bannerFileNamePrefix) }
            ?.name
        return if (bannerFullFileName != null) {
            getPublicFilePathForPostMedia(postId, bannerFullFileName)
        } else {
            null
        }
    }

    override fun getPublicPostMediaUrl(postId: String) = "$PUBLIC_URL_PATH_PREFIX/$postId"

    private fun getInternalFilePathForPostMedia(postId: String, filename: String) =
        "${getInternalPostMediaDir(postId)}/$filename"

    private fun getInternalPostMediaDir(postId: String) = "$mediaDirectoryPath/$postId"

    private fun getPublicFilePathForPostMedia(postId: String, filename: String) =
        "${getPublicPostMediaUrl(postId)}/$filename"
}