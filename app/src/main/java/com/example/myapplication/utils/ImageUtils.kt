package com.example.myapplication.utils

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy

object ImageUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return Bitmap.createBitmap(
            image.width, image.height, Bitmap.Config.ARGB_8888
        ).apply {
            copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        }
    }
}
