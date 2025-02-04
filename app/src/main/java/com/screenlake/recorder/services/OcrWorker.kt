package com.screenlake.recorder.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.googlecode.tesseract.android.TessBaseAPI
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.ocr.Assets
import com.screenlake.recorder.ocr.Recognize
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import timber.log.Timber

@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val generalOperationsRepository: GeneralOperationsRepository,
    private val recognize: Recognize
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "OcrWorker"
        private const val BATCH_SIZE = 10  // Number of images to process per batch
        private const val OCR_IMAGE_THRESHOLD = 25  // Minimum number of images to trigger OCR
    }

    /**
     * The entry point for the Worker. Handles the OCR processing.
     */
    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("OCR Worker has started.")
        return try {
            generalOperationsRepository.saveLog("OCR_SERVICE_RUN", "")

            // Run the OCR process in a background thread
            withContext(Dispatchers.Default) {
                beginOcr()
            }

            recognize?.stop()
            Timber.tag(TAG).d("OCR Worker has finished.")
            Result.success()
        } catch (ex: Exception) {
            handleException(ex)
            Result.failure()
        }
    }

    /**
     * Initializes and begins the OCR process if there are enough images to process.
     */
    private suspend fun beginOcr() {
        val screenshots = generalOperationsRepository.getScreenshotsToOcr(1000)
        Timber.tag(TAG).d("Starting OCR: Processing ${screenshots.size} images.")

        // Only proceed if there are enough images to process
        if (screenshots.size >= OCR_IMAGE_THRESHOLD) {
            try {
                initializeTesseract()  // Initialize Tesseract for OCR
                val imagesToOcr = generalOperationsRepository.getScreenshotsToOcr(200)
                recognizeImages(imagesToOcr)  // Process the images in batches
                Timber.tag(TAG).d("Ending OCR.")
            } catch (ex: Exception) {
                handleOcrException(ex)
            } finally {
                System.gc()  // Trigger garbage collection to free memory
            }
        }
    }

    /**
     * Initializes the Tesseract OCR engine if not already initialized.
     */
    private fun initializeTesseract() {
        val dataPath = Assets.getTessDataPath(applicationContext)
        val language = Assets.language

        if (recognize == null || !recognize!!.isInitialized) {
            recognize.initTesseract(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)
        }
    }

    /**
     * Processes a list of screenshots using Tesseract OCR.
     * Divides the list into batches for efficient processing.
     */
    private suspend fun recognizeImages(images: List<ScreenshotEntity>) {
        if (recognize == null || !recognize!!.isInitialized) {
            Timber.tag(TAG).d("Tesseract is not initialized.")
            return
        }

        val startTime = System.currentTimeMillis()
        var processedCount = 0

        // Process images in batches to manage memory and performance
        images.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            Timber.tag(TAG).d("Processing batch ${batchIndex + 1} with ${batch.size} images.")
            batch.forEach { screenshot ->
                processScreenshot(screenshot)
                processedCount++
            }
        }

        val endTime = System.currentTimeMillis()
        Timber.d("OCR completed for $processedCount images in ${endTime - startTime} ms.")
        recognize?.stop()  // Stop Tesseract after processing
    }

    /**
     * Processes a single screenshot image for OCR and marks it as complete in the database.
     */
    private suspend fun processScreenshot(screenshot: ScreenshotEntity) {
        try {
            Timber.tag(TAG).d("Processing image: ${screenshot.filePath}")
            if (recognize!!.processImage(screenshot)) {
                generalOperationsRepository.setScreenToOcrComplete(screenshot)
            } else {
                logOcrFailure(screenshot.filePath)
            }
        } catch (ex: Exception) {
            Timber.tag(TAG).w("Error processing image: ${screenshot.filePath}, ${ex.message}")
            logOcrFailure(screenshot.filePath)
        }
    }

    /**
     * Logs an OCR failure for a given image.
     */
    private suspend fun logOcrFailure(filePath: String?) {
        Timber.tag(TAG).w("Could not process OCR for file: $filePath")
        generalOperationsRepository.saveLog("OCRProcess", "Could not process OCR for filePath $filePath")
    }

    /**
     * Handles any exceptions that occur during the OCR process.
     */
    private fun handleException(ex: Exception) {
        recognize?.stop()
        Timber.tag(TAG).e(ex, "OCR Worker failed.")
    }

    /**
     * Handles exceptions that occur during the OCR process specifically.
     */
    private fun handleOcrException(ex: Exception) {
        recognize.stop()
        Timber.tag(TAG).e(ex, "OCR exception occurred.")
    }
}
