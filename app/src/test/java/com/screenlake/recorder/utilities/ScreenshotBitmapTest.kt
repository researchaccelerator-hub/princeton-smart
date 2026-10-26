package com.screenlake.recorder.utilities

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer

/**
 * ImageReader rows can be wider than the screen. The padding must not end up in the saved
 * screenshot as an extra strip on the right edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotBitmapTest {

    private val pixelStride = 4

    @Test
    fun `row padding is cropped so the bitmap matches the screen size`() {
        val width = 3
        val height = 2
        val paddingPixels = 2
        val rowStride = (width + paddingPixels) * pixelStride
        val buffer = planeBuffer(width, height, rowStride, content = Color.BLUE, padding = Color.RED)

        val bitmap = ScreenshotBitmap.fromPlane(buffer, pixelStride, rowStride, width, height)

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(Color.BLUE, bitmap.getPixel(x, y))
            }
        }
    }

    @Test
    fun `a plane without padding keeps its size`() {
        val width = 4
        val height = 3
        val rowStride = width * pixelStride
        val buffer = planeBuffer(width, height, rowStride, content = Color.GREEN, padding = Color.RED)

        val bitmap = ScreenshotBitmap.fromPlane(buffer, pixelStride, rowStride, width, height)

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
        assertEquals(Color.GREEN, bitmap.getPixel(width - 1, height - 1))
    }

    /** Builds an RGBA_8888 plane: [width] content pixels per row, then padding up to [rowStride]. */
    private fun planeBuffer(width: Int, height: Int, rowStride: Int, content: Int, padding: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until rowStride / pixelStride) {
                val color = if (x < width) content else padding
                buffer.put(Color.red(color).toByte())
                buffer.put(Color.green(color).toByte())
                buffer.put(Color.blue(color).toByte())
                buffer.put(Color.alpha(color).toByte())
            }
        }
        buffer.rewind()
        return buffer
    }
}
