package com.screenlake.recorder.utilities

import android.graphics.Bitmap
import java.nio.ByteBuffer

object ScreenshotBitmap {
    /**
     * Converts an ImageReader RGBA_8888 plane into a bitmap of exactly [width] x [height].
     *
     * Rows in the plane can be wider than the screen (`rowStride > pixelStride * width`).
     * Copying the buffer straight into a bitmap keeps that padding as an extra strip on the
     * right edge of every screenshot, so the padding is cropped off.
     */
    fun fromPlane(buffer: ByteBuffer, pixelStride: Int, rowStride: Int, width: Int, height: Int): Bitmap {
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) return padded

        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }
}
