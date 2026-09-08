package org.mistykmedia.insertabot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [scaledDimensions] is the half of the downscale that can be reasoned about
 * without a real Bitmap, and the half that had the bug: a zero edge reaches
 * `createScaledBitmap` as an IllegalArgumentException, which
 * [uriToJpegBase64] swallows into a null — an attachment that silently never
 * appears rather than an error the reader can act on.
 */
class ImageScalingTest {

    private val max = 1024

    @Test
    fun `image already within bounds is returned unchanged`() {
        assertEquals(800 to 600, scaledDimensions(800, 600, max))
        assertEquals(1024 to 1024, scaledDimensions(1024, 1024, max))
    }

    @Test
    fun `landscape image scales its long edge to the maximum`() {
        assertEquals(1024 to 768, scaledDimensions(2048, 1536, max))
    }

    @Test
    fun `portrait image scales its long edge to the maximum`() {
        assertEquals(768 to 1024, scaledDimensions(1536, 2048, max))
    }

    @Test
    fun `extreme panorama keeps a short edge of at least one pixel`() {
        // 1024 / (10000 / 3.0) truncates to 0 without the floor.
        val (width, height) = scaledDimensions(10000, 3, max)
        assertEquals(1024, width)
        assertEquals(1, height)
    }

    @Test
    fun `extreme vertical strip keeps a short edge of at least one pixel`() {
        val (width, height) = scaledDimensions(3, 10000, max)
        assertEquals(1, width)
        assertEquals(1024, height)
    }

    @Test
    fun `a degenerate zero-height image does not divide by zero into a bad size`() {
        val (width, height) = scaledDimensions(4000, 0, max)
        assertTrue("width must be positive, was $width", width > 0)
        assertTrue("height must be positive, was $height", height > 0)
    }

    @Test
    fun `no aspect ratio ever produces a non-positive dimension`() {
        val sizes = listOf(1, 2, 3, 7, 100, 1023, 1024, 1025, 4000, 10000, 40000)
        for (w in sizes) for (h in sizes) {
            val (width, height) = scaledDimensions(w, h, max)
            assertTrue("${w}x$h scaled to ${width}x$height", width >= 1 && height >= 1)
            assertTrue("${w}x$h exceeded max: ${width}x$height", width <= max || height <= max)
        }
    }
}
