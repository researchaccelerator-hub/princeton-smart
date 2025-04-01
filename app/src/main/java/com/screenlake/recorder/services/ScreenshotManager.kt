//package com.screenlake.recorder.services
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.PixelFormat
//import android.hardware.display.VirtualDisplay
//import android.media.ImageReader
//import com.google.firebase.crashlytics.FirebaseCrashlytics
//import com.screenlake.recorder.constants.ConstantSettings
//import com.screenlake.data.model.AppInfo
//import com.screenlake.recorder.database.Screenshot
//import com.screenlake.recorder.database.User
//import com.screenlake.recorder.repositories.GenOp
//import com.screenlake.recorder.rest.RestAPI
//import com.screenlake.recorder.screenshot.ScreenCollectorSvc
//import com.screenlake.recorder.services.ScreenshotData.ScreenshotData
//import com.screenlake.recorder.utilities.TimeUtility
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import timber.log.Timber
//import java.io.File
//import java.io.FileOutputStream
//import java.lang.ref.WeakReference
//import java.nio.ByteBuffer
//import java.util.UUID
//
//class ScreenshotManager(
//    private val context: Context,
//    private val service: ScreenshotService,
//    private val genOp: GenOp,
//    private val notificationHelper: NotificationHelper
//) {
//
//    private var virtualDisplayAttempts = 0
//    private var outOfMemory = false
//    var virtualDisplay: VirtualDisplay? = null
//    private var notificationID = 1
//
//    suspend fun captureScreenshot(
//        user: User,
//        mImageReader: ImageReader,
//        currentAppInUse: AppInfo,
//        sessionId: String
//    ): Screenshot? {
//        return try {
//            val screenshotData = createScreenshotImage(user, mImageReader, currentAppInUse, sessionId) ?: return null
//
//            val screenshotFile = File(screenshotData.filePath)
//            if (!screenshotFile.exists()) {
//                Timber.d("File is empty, skipping...")
//                return null
//            }
//
//            // Further processing...
//            return screenshotData
//        } catch (exception: Exception) {
//            handleScreenshotError(exception)
//            return null
//        }
//    }
//
//    private suspend fun createScreenshotImage(
//        user: User,
//        mImageReader: ImageReader,
//        currentAppInUse: AppInfo,
//        sessionId: String
//    ): Screenshot? {
//        val metrics = context.resources.displayMetrics
//        val screenDensity = metrics.densityDpi
//        val width = metrics.widthPixels
//        val height = metrics.heightPixels
//
//        val filename = "${context.filesDir.path}/img_${UUID.randomUUID()}_${TimeUtility.getFormattedScreenCaptureTime()}.jpg"
//        val screenshotData = ScreenshotData.saveScreenshotData(filename, currentAppInUse, sessionId, user)
//
//        try {
//            val projection = ScreenshotService.projection
//            if (projection == null) {
//                Timber.e("Projection is null")
//                return null
//            }
//
//            var virtualDisplay = virtualDisplay
//            if (virtualDisplay == null) {
//                virtualDisplay = projection.createVirtualDisplay(
//                    filename,
//                    width, height, screenDensity,
//                    0,
//                    mImageReader.surface, null, null
//                )
//            }
//
//            if (virtualDisplay != null) {
//                setImageListener(filename, virtualDisplay, mImageReader)
//            }else{
//                Timber.e("Virtual display is null")
//                return null
//            }
//            return screenshotData
//        } catch (e: Exception) {
//            withContext(Dispatchers.IO) {
//                genOp.saveLog("Exception", ScreenshotData.ocrCleanUp(e.stackTraceToString()))
//            }
//            handleVirtualDisplayError(e)
//            return null
//        }
//    }
//
//    private fun setImageListener(path: String, virtualDisplay: VirtualDisplay, mImageReader: ImageReader) {
//        mImageReader.setOnImageAvailableListener({ image ->
//            val planes = image.acquireLatestImage()?.planes
//
//            if (planes != null) {
//                val buffer: ByteBuffer = planes[0].buffer
//                val pixelStride: Int = planes[0].pixelStride
//                val rowStride: Int = planes[0].rowStride
//                val rowPadding: Int = rowStride - pixelStride * context.resources.displayMetrics.widthPixels
//
//                var bitmap:Bitmap? = Bitmap.createBitmap(
//                    context.resources.displayMetrics.widthPixels + rowPadding / pixelStride,
//                    context.resources.displayMetrics.heightPixels,
//                    Bitmap.Config.ARGB_8888
//                )
//                bitmap?.copyPixelsFromBuffer(buffer)
//                buffer.clear()
//
//                FileOutputStream(path).use { fos ->
//                    bitmap?.compress(Bitmap.CompressFormat.JPEG, ConstantSettings.SCREENSHOT_IMAGE_QUALITY, fos)
//                }
//
//                bitmap?.recycle()
//                bitmap = null
//
//                service.IMAGES_PRODUCED++
//                Timber.tag("SCREENSHOT").d("**** ${TimeUtility.getCurrentTimestamp()} ****")
//                image.close()
//            }
//        }, null)
//    }
//
//    private suspend fun handleScreenshotError(exception: Exception) {
//        Timber.e(exception)
//        FirebaseCrashlytics.getInstance().recordException(exception)
//        genOp.saveLog(ConstantSettings.RECORDING_SERVICE, exception.message.toString())
//    }
//
//    private fun handleVirtualDisplayError(e: Exception) {
//        if (e is SecurityException) {
//            ScreenshotService.isProjectionValid.postValue(false)
//            notificationHelper.showNotification("Screenlake", "Please re-enable screen recording.", ScreenshotService.getRandomNumber(1, 100))
//            FirebaseCrashlytics.getInstance().log("Virtual display failed.")
//            FirebaseCrashlytics.getInstance().recordException(e)
//            Timber.e("Virtual display failed: $e")
//            // service.killService("Virtual display failed -> $e")
//        } else {
//            retryVirtualDisplayCreation(e)
//        }
//    }
//
//    private fun retryVirtualDisplayCreation(e: Exception) {
//        if (virtualDisplayAttempts < ScreenshotService.virtualDisplayLimit) {
//            virtualDisplayAttempts++
//            // Backoff retries based on the number of attempts
//            when (virtualDisplayAttempts) {
//                5 -> ScreenshotService.screenshotInterval.postValue(10000L)
//                10 -> ScreenshotService.screenshotInterval.postValue(30000L)
//                15 -> ScreenshotService.screenshotInterval.postValue(90000L)
//            }
//        } else {
//            notificationHelper.showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
//            FirebaseCrashlytics.getInstance().log("Virtual display failed.")
//            FirebaseCrashlytics.getInstance().recordException(e)
//            virtualDisplayAttempts = 0
//            // service.killService("Virtual display failed after retrying -> $e")
//        }
//    }
//}
