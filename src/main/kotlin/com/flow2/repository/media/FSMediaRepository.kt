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
        // Remove any existing banner.* so a stale extension (e.g. banner.jpg) doesn't
        // linger next to the new one and get picked up by getPublicPostBannerUrl.
        File(getInternalPostMediaDir(postId)).listFiles()
            ?.filter { it.name.startsWith(bannerFileNamePrefix) }
            ?.forEach { it.delete() }

        try {
            // Downscale + re-encode to WebP for a consistent, web-friendly file size.
            val webpBytes = BannerImageProcessor.process(fileContent)
            savePostMedia(postId, "$bannerFileNamePrefix.webp", webpBytes)
        } catch (e: Exception) {
            // Fall back to storing the original so an upload never hard-fails on an
            // image we can't decode/encode.
            log.warn("Failed to process banner for post $postId, storing original", e)
            val bannerFileName = "$bannerFileNamePrefix.${fileName.split('.').last()}"
            savePostMedia(postId, bannerFileName, fileContent)
        }
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