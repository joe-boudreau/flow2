package com.flow2.repository.assets

import io.ktor.server.application.Application

interface SiteAssetRepositoryInterface {

    fun getAsset(assetPath: String): ByteArray?

    fun getPublicSiteAssetUrl(assetPath: String): String

    fun configureRouting(application: Application)
}