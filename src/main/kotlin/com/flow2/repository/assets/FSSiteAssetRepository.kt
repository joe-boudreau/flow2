package com.flow2.repository.assets

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import java.io.File

const val PUBLIC_URL_PATH_PREFIX = "/assets"
const val FILESYSTEM_DIRECTORY_PATH = "src/main/resources/assets"

class FSSiteAssetRepository: SiteAssetRepositoryInterface {
    override fun getAsset(assetPath: String) = File("$FILESYSTEM_DIRECTORY_PATH/$assetPath").readBytes()

    override fun getPublicSiteAssetUrl(assetPath: String) = "$PUBLIC_URL_PATH_PREFIX/$assetPath"

    override fun configureRouting(application: Application) {
        application.routing {
            if (application.developmentMode) {
                staticFiles(PUBLIC_URL_PATH_PREFIX, File(FILESYSTEM_DIRECTORY_PATH))
            } else {
                staticResources(PUBLIC_URL_PATH_PREFIX, "assets")
            }
        }
    }
}