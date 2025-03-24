package com.screenlake.recorder.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import com.screenlake.R
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.ocr.Assets
import com.screenlake.recorder.ocr.Recognize
import com.screenlake.recorder.utilities.record
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class PeriodicOcrService : Service() {
    private val alarmManager by lazy { getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private lateinit var pendingIntent: PendingIntent

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var recognize: Recognize

    private val ACTION_PROCESS_OCR = "ACTION_PROCESS_OCR"

    companion object {
        private const val TAG = "OcrWorker"
        private const val BATCH_SIZE = 10  // Number of images to process per batch
        private const val OCR_IMAGE_THRESHOLD = 10  // Minimum number of images to trigger OCR
        private const val CHANNEL_ID = "ocr_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVAL_MILLIS = 60 * 1000L // 15 minutes

        // Helper method to start the service
        fun startService(context: Context) {
            val startIntent = Intent(context, PeriodicOcrService::class.java)
            startIntent.action = "ACTION_PROCESS_OCR"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, startIntent)
            } else {
                context.startService(startIntent)
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        startForeground()

        // Create a PendingIntent that will restart your service
        val intent = Intent(this, PeriodicOcrService::class.java).apply {
            action = ACTION_PROCESS_OCR
        }
        pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        CoroutineScope(Dispatchers.IO).launch {
            beginOcr()
        }
    }

//    /**
//     * Initializes and begins the OCR process if there are enough images to process.
//     */
//    private fun beginOcr() = CoroutineScope(Dispatchers.IO).launch {
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROCESS_OCR) {
            beginOcr()
        }

        // Schedule next run
        scheduleNextRun()

        return START_STICKY
    }

    private fun scheduleNextRun() {
        // Schedule next run in 15 minutes (or your desired interval)
        val triggerTime = SystemClock.elapsedRealtime() + INTERVAL_MILLIS

        // Use setInexactRepeating for better battery performance
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerTime,
            INTERVAL_MILLIS,
            pendingIntent
        )
    }
    
    private fun startForeground() {
        // Create notification channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OCR Processing",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR Processing")
            .setContentText("Processing screenshots...")
            .setSmallIcon(R.drawable.circle_icon)
            .build()
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Initializes the Tesseract OCR engine if not already initialized.
     */
    private fun initializeTesseract() {
        val dataPath = Assets.getTessDataPath(applicationContext)
        val language = Assets.language

        recognize.initTesseract(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)
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
                WorkerProgressManager.updateProgress("Processed $processedCount images of ${images.size}.")
            }
        }

        val endTime = System.currentTimeMillis()
        Timber.d("OCR completed for $processedCount images in ${endTime - startTime} ms.")
    }

    /**
     * Processes a single screenshot image for OCR and marks it as complete in the database.
     */
    private suspend fun processScreenshot(screenshot: ScreenshotEntity) {
        try {
            Timber.tag(TAG).d("Processing image: ${screenshot.filePath}")
            if (recognize.processImage(screenshot)) {
                generalOperationsRepository.setScreenToOcrComplete(screenshot)
            } else {
                logOcrFailure(screenshot.filePath, "")
            }
        } catch (ex: Exception) {
            ex.record()
            Timber.tag(TAG).w("Error processing image: ${screenshot.filePath}, ${ex.message}")
            logOcrFailure(screenshot.filePath, "Error processing image: ${screenshot.filePath}, ${ex.stackTrace}")
        }
    }

    /**
     * Logs an OCR failure for a given image.
     */
    private suspend fun logOcrFailure(filePath: String?, msg: String) {
        Timber.tag(TAG).w("Could not process OCR for file: $filePath")
        generalOperationsRepository.saveLog("OCRProcess", "Could not process OCR for filePath $filePath -> $msg")
    }

    /**
     * Handles any exceptions that occur during the OCR process.
     */
    private fun handleException(ex: Exception) {
        ex.record()
        recognize?.stop()
        Timber.tag(TAG).e(ex, "OCR Worker failed.")
    }

    /**
     * Handles exceptions that occur during the OCR process specifically.
     */
    private fun handleOcrException(ex: Exception) {
        ex.record()
        recognize.stop()
        Timber.tag(TAG).e(ex, "OCR exception occurred.")
    }

    private fun beginOcr() = CoroutineScope(Dispatchers.IO).launch {
        val screenshots = generalOperationsRepository.getScreenshotsToOcr(1000)
        Timber.tag(TAG).d("Starting OCR: Processing ${screenshots.size} images.")

        // Only proceed if there are enough images to process
        if (screenshots.size >= OCR_IMAGE_THRESHOLD) {
            try {
                initializeTesseract()  // Initialize Tesseract for OCR
                delay(3000)

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


    override fun onBind(intent: Intent): IBinder? = null
}