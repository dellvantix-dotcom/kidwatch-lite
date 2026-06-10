package com.dellvantix.kidwatch.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.dellvantix.kidwatch.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent,
    private val firebaseRepo: FirebaseRepository
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val metrics: DisplayMetrics by lazy {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
    }

    suspend fun captureAndUpload() = withContext(Dispatchers.IO) {
        val bitmap = captureScreen() ?: return@withContext
        val bytes = bitmapToJpeg(bitmap, quality = 60)  // 60% quality keeps size small
        bitmap.recycle()
        firebaseRepo.uploadScreenshot(bytes)
        releaseResources()
    }

    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "KidWatchCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        val handler = Handler(Looper.getMainLooper())

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()

                    cont.resume(croppedBitmap)
                }
            } catch (e: Exception) {
                cont.resume(null)
            } finally {
                image?.close()
            }
        }, handler)

        cont.invokeOnCancellation { releaseResources() }
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun releaseResources() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
