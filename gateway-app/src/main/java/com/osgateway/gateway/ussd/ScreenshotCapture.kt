package com.osgateway.gateway.ussd

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.Window
import androidx.core.view.drawToBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Screenshot helper interface.
 * Full MediaProjection requires user consent dialog; PixelCopy works for app windows.
 * For USSD system dialogs, capture is best-effort / may return null without MediaProjection.
 */
interface ScreenshotCapture {
    fun captureBase64(): String?
}

class PixelCopyScreenshotCapture(
    private val windowProvider: () -> Window?,
) : ScreenshotCapture {

    override fun captureBase64(): String? {
        val window = windowProvider() ?: return null
        val view = window.decorView
        return try {
            if (view.width <= 0 || view.height <= 0) return null
            val bitmap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
            encode(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun encode(bitmap: Bitmap, quality: Int = 70): String {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
    }
}

/**
 * Stub for MediaProjection-based full-screen capture.
 * Wire Activity result launcher + MediaProjectionManager in production builds.
 */
class MediaProjectionScreenshotStub : ScreenshotCapture {
    override fun captureBase64(): String? = null
}

object ViewScreenshotHelper {
    fun fromView(view: View?): String? {
        if (view == null || view.width == 0 || view.height == 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            PixelCopyScreenshotCapture.encode(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    fun fromActivitySync(activity: Activity?, timeoutMs: Long = 1500): String? {
        if (activity == null) return null
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                result.set(fromView(activity.window?.decorView))
            } finally {
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return result.get()
    }
}
