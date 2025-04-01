// Currently disabled, OCR moved to ScreenshotService.

//package com.screenlake.recorder.services
//
//import android.app.Service
//import android.app.job.JobParameters
//import android.app.job.JobService
//import android.content.Context
//import android.content.Intent
//import android.os.IBinder
//import androidx.hilt.work.HiltWorker
//import androidx.work.CoroutineWorker
//import androidx.work.WorkerParameters
//import com.googlecode.tesseract.android.TessBaseAPI
//import com.screenlake.data.database.entity.ScreenshotEntity
//import com.screenlake.data.repository.GeneralOperationsRepository
//import com.screenlake.recorder.ocr.Assets
//import com.screenlake.recorder.ocr.Recognize
//import com.screenlake.recorder.utilities.record
//import com.screenlake.recorder.viewmodels.WorkerProgressManager
//import dagger.assisted.Assisted
//import dagger.assisted.AssistedInject
//import dagger.hilt.android.AndroidEntryPoint
//import jakarta.inject.Inject
//import kotlinx.coroutines.*
//import timber.log.Timber
//
//@AndroidEntryPoint
//class OcrWorker : JobService() {
//
//    @Inject
//    lateinit var generalOperationsRepository: GeneralOperationsRepository
//
//    @Inject
//    lateinit var recognize: Recognize
//
//    companion object {
//        private const val TAG = "OcrWorker"
//        private const val BATCH_SIZE = 10  // Number of images to process per batch
//        private const val OCR_IMAGE_THRESHOLD = 10  // Minimum number of images to trigger OCR
//    }
//
//    override fun onStartJob(params: JobParameters?): Boolean {
//        Timber.tag(TAG).d("OCR Worker has started.")
//        try {
//
////            // Run the OCR process in a background thread
////            beginOcr()
//
//            val test = getScreenshotsForOcr(10, 0)
//            Timber.tag(TAG).d("Test: ${test.size}")
//
////            recognize?.stop()
//            Timber.tag(TAG).d("OCR Worker has finished.")
//        } catch (ex: Exception) {
//            handleException(ex)
//            return false
//        }
//    }
//
//    override fun onStopJob(params: JobParameters?): Boolean {
//        TODO("Not yet implemented")
//    }
//
//    fun getScreenshotsForOcr(limit: Int = 10, offset: Int = 0): List<ScreenshotEntity> {
//        val uri = ScreenshotContentProvider.OCR_INCOMPLETE_URI
//            .buildUpon()
//            .appendQueryParameter("limit", limit.toString())
//            .appendQueryParameter("offset", offset.toString())
//            .build()
//
//        val screenshots = mutableListOf<ScreenshotEntity>()
//
//        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//            while (cursor.moveToNext()) {
//                // Create ScreenshotEntity from cursor
//                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
//                val text = cursor.getString(cursor.getColumnIndexOrThrow("text"))
//                val isOcrComplete = cursor.getInt(cursor.getColumnIndexOrThrow("isOcrComplete")) == 1
//                // Get other fields as needed
//
//                // Add to list
//                screenshots.add(ScreenshotEntity().apply {
//                    this.id = id
//                    this.text = text
//                    this.isOcrComplete = isOcrComplete
//                })
//            }
//        }
//
//        return screenshots
//    }
//
//    /**
//     * Initializes and begins the OCR process if there are enough images to process.
//     */
//    private suspend fun beginOcr() {
//        val screenshots = generalOperationsRepository.getScreenshotsToOcr(1000)
//        Timber.tag(TAG).d("Starting OCR: Processing ${screenshots.size} images.")
//
//        // Only proceed if there are enough images to process
//        if (screenshots.size >= OCR_IMAGE_THRESHOLD) {
//            try {
//                initializeTesseract()  // Initialize Tesseract for OCR
//                delay(3000)
//
//                val imagesToOcr = generalOperationsRepository.getScreenshotsToOcr(200)
//                recognizeImages(imagesToOcr)  // Process the images in batches
//                Timber.tag(TAG).d("Ending OCR.")
//            } catch (ex: Exception) {
//                handleOcrException(ex)
//            } finally {
//                System.gc()  // Trigger garbage collection to free memory
//            }
//        }
//    }
//
//    /**
//     * Initializes the Tesseract OCR engine if not already initialized.
//     */
//    private fun initializeTesseract() {
//        val dataPath = Assets.getTessDataPath(applicationContext)
//        val language = Assets.language
//
//        recognize.initTesseract(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)
//    }
//
//    /**
//     * Processes a list of screenshots using Tesseract OCR.
//     * Divides the list into batches for efficient processing.
//     */
//    private suspend fun recognizeImages(images: List<ScreenshotEntity>) {
//        if (recognize == null || !recognize!!.isInitialized) {
//            Timber.tag(TAG).d("Tesseract is not initialized.")
//            return
//        }
//
//        val startTime = System.currentTimeMillis()
//        var processedCount = 0
//
//        // Process images in batches to manage memory and performance
//        images.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
//            Timber.tag(TAG).d("Processing batch ${batchIndex + 1} with ${batch.size} images.")
//            batch.forEach { screenshot ->
//                processScreenshot(screenshot)
//                processedCount++
//                WorkerProgressManager.updateProgress("Processed $processedCount images of ${images.size}.")
//            }
//        }
//
//        val endTime = System.currentTimeMillis()
//        Timber.d("OCR completed for $processedCount images in ${endTime - startTime} ms.")
//    }
//
//    /**
//     * Processes a single screenshot image for OCR and marks it as complete in the database.
//     */
//    private suspend fun processScreenshot(screenshot: ScreenshotEntity) {
//        try {
//            Timber.tag(TAG).d("Processing image: ${screenshot.filePath}")
//            if (recognize.processImage(screenshot)) {
//                generalOperationsRepository.setScreenToOcrComplete(screenshot)
//            } else {
//                logOcrFailure(screenshot.filePath, "")
//            }
//        } catch (ex: Exception) {
//            ex.record()
//            Timber.tag(TAG).w("Error processing image: ${screenshot.filePath}, ${ex.message}")
//            logOcrFailure(screenshot.filePath, "Error processing image: ${screenshot.filePath}, ${ex.stackTrace}")
//        }
//    }
//
//    /**
//     * Logs an OCR failure for a given image.
//     */
//    private suspend fun logOcrFailure(filePath: String?, msg: String) {
//        Timber.tag(TAG).w("Could not process OCR for file: $filePath")
//        generalOperationsRepository.saveLog("OCRProcess", "Could not process OCR for filePath $filePath -> $msg")
//    }
//
//    /**
//     * Handles any exceptions that occur during the OCR process.
//     */
//    private fun handleException(ex: Exception) {
//        ex.record()
//        recognize?.stop()
//        Timber.tag(TAG).e(ex, "OCR Worker failed.")
//    }
//
//    /**
//     * Handles exceptions that occur during the OCR process specifically.
//     */
//    private fun handleOcrException(ex: Exception) {
//        ex.record()
//        recognize.stop()
//        Timber.tag(TAG).e(ex, "OCR exception occurred.")
//    }
//
//    override fun onBind(intent: Intent?): IBinder? {
//        TODO("Not yet implemented")
//    }
//}
