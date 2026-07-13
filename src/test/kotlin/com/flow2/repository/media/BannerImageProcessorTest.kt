package com.flow2.repository.media

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BannerImageProcessorTest {

    private fun solidImageBytes(width: Int, height: Int): ByteArray {
        val image = ImmutableImage.filled(width, height, Color(120, 80, 200))
        return image.bytes(PngWriter.NoCompression)
    }

    @Test
    fun `downscales images wider than the cap and outputs webp`() {
        val input = solidImageBytes(4000, 3000)

        val output = BannerImageProcessor.process(input)

        val result = ImmutableImage.loader().fromBytes(output)
        assertEquals(BANNER_MAX_WIDTH, result.width)
        assertEquals(1875, result.height) // aspect ratio preserved (4000x3000 -> 2500x1875)
        assertTrue(output.size < input.size, "processed banner should be smaller than the source")
    }

    @Test
    fun `does not upscale images narrower than the cap`() {
        val input = solidImageBytes(800, 400)

        val output = BannerImageProcessor.process(input)

        val result = ImmutableImage.loader().fromBytes(output)
        assertEquals(800, result.width)
        assertEquals(400, result.height)
    }
}
