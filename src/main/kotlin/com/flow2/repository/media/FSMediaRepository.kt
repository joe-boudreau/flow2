package com.flow2.repository.media

import com.flow2.repository.assets.SiteAssetRepositoryInterface
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import io.ktor.util.cio.writeChannel
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.io.File

const val PUBLIC_URL_PATH_PREFIX = "/media"
const val FILESYSTEM_DIRECTORY_PATH = "src/main/resources/media"

internal val log = KtorSimpleLogger("FSMediaRepository")

class FSMediaRepository(
    private val siteAssetRepository: SiteAssetRepositoryInterface
) : MediaRepositoryInterface {

    private val publicMediaDir = "$PUBLIC_URL_PATH_PREFIX/"

    private val bannerFileName = "banner"
    private val defaultBannerAsset = "images/default_banner.jpg"

    override fun configureRouting(app: Application) {
        app.routing {
            staticFiles(PUBLIC_URL_PATH_PREFIX, File(FILESYSTEM_DIRECTORY_PATH))
        }
    }

    override fun savePostMedia(postId: String, filename: String, fileContent: ByteArray) {
        File(getInternalPostMediaDir(postId)).mkdirs()
        File(getInternalFilePathForPostMedia(postId, filename)).writeBytes(fileContent)
    }

    override fun savePostBanner(postId: String, fileContent: ByteArray) {
        savePostMedia(postId, bannerFileName, fileContent)
    }

    override fun getPublicPostBannerUrl(postId: String): String {
        File(getInternalFilePathForPostMedia(postId, bannerFileName)).let {
            return if (it.exists()) {
                getPublicFilePathForPostMedia(postId, bannerFileName)
            } else {
                siteAssetRepository.getPublicSiteAssetUrl(defaultBannerAsset)
            }
        }
    }

    override fun getPublicPostMediaUrl(postId: String) = "$publicMediaDir/$postId"

    private fun getInternalFilePathForPostMedia(postId: String, filename: String) =
        "${getInternalPostMediaDir(postId)}/$filename"

    private fun getInternalPostMediaDir(postId: String) = "$FILESYSTEM_DIRECTORY_PATH/$postId"

    private fun getPublicFilePathForPostMedia(postId: String, filename: String) =
        "${getPublicPostMediaUrl(postId)}/$filename"
}