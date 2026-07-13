package com.flow2.repository.media

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter

const val BANNER_MAX_WIDTH = 2500
const val BANNER_WEBP_QUALITY = 85

/**
 * Downscales oversized banner images and re-encodes them to WebP for consistent,
 * web-friendly file sizes. Loading via [ImmutableImage.loader] applies EXIF
 * orientation automatically, so photos from phones are not left sideways.
 */
object BannerImageProcessor {

    private val webpWriter = WebpWriter.DEFAULT.withQ(BANNER_WEBP_QUALITY).withM(6)

    /**
     * Returns WebP-encoded bytes for [fileContent], downscaled to at most
     * [BANNER_MAX_WIDTH] wide. Images narrower than the cap are never upscaled.
     */
    fun process(fileContent: ByteArray): ByteArray {
        val image = ImmutableImage.loader().fromBytes(fileContent)
        val resized = if (image.width > BANNER_MAX_WIDTH) {
            image.scaleToWidth(BANNER_MAX_WIDTH)
        } else {
            image
        }
        return resized.bytes(webpWriter)
    }
}
