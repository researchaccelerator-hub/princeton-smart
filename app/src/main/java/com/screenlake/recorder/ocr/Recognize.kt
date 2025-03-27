package com.screenlake.recorder.ocr

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import com.googlecode.tesseract.android.TessBaseAPI
import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.record
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.Path

@Singleton
class Recognize @Inject constructor(
    private val userDao: UserDao,
    private val generalOperationsRepository: GeneralOperationsRepository
) {

    private var tessApi: TessBaseAPI
    val processing = MutableLiveData(false)
    private val progress = MutableLiveData<String>()
    var isInitialized = true
        private set
    var stopped = false

    init {
        this.tessApi = TessBaseAPI { progressValues ->
            progress.postValue("Progress: ${progressValues.percent} %")
        }
    }

    /**
     * Initializes the Tesseract OCR engine.
     *
     * @param dataPath The path to the Tesseract data files.
     * @param language The language to be used by Tesseract.
     * @param engineMode The engine mode to be used by Tesseract.
     */
    fun initTesseract(dataPath: String, language: String, engineMode: Int) {
        Timber.tag(TAG).d("Initializing Tesseract with: dataPath = [$dataPath], language = [$language], engineMode = [$engineMode]")
        try {
            // Initialize TessBaseAPI
            this.tessApi = TessBaseAPI()

            this.isInitialized = tessApi.init(dataPath, language, engineMode)
            tessApi.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/' '")
            tessApi.setVariable("user_defined_dpi", "300")
            stopped = false
        } catch (e: IllegalArgumentException) {
            this.isInitialized = false

            CoroutineScope(Dispatchers.IO).launch {
                generalOperationsRepository.saveLog("INIT_TESSERACT", "OCR failed with message -> ${e.message} stacktrace -> ${e.stackTraceToString()}.")
            }

            Timber.tag(TAG).d("Cannot initialize Tesseract: $e")
        }
    }

    suspend fun processImage(screenshot: ScreenshotEntity): Boolean {
        // First check if we're already in a bad state
        if (!isInitialized || stopped) {
            Timber.tag(TAG).w("Tesseract not initialized or stopped - skipping OCR")
            return false
        }

        try {
            val file = screenshot.filePath?.let { File(it) }

            if (file == null || !file.exists()) {
                Timber.tag(TAG).w("Image file doesn't exist: ${screenshot.filePath}")
                return false
            }

            // Set a flag to track if we're inside Tesseract operations
            var insideTesseract = false

            // Run the OCR in a controlled context
            return try {
                withTimeoutOrNull(30000) { // 30 second timeout
                    try {
                        insideTesseract = true
                        val bitmap = BitmapFactory.decodeFile(screenshot.filePath)
                        tessApi.setImage(bitmap)
                        val startTime = SystemClock.uptimeMillis()

                        // This is where most crashes happen
                       tessApi.getHOCRText(0)
                        val regexCleanup1 = tessApi.utF8Text
                        insideTesseract = false

                        if (regexCleanup1 != null) {
                            val regexCleanup2 = ScreenshotData.ocrCleanUp(regexCleanup1)?.lowercase(Locale.ROOT) ?: ""
                            screenshot.text = regexCleanup2
                        }

                        screenshot.isOcrComplete = true

                        if (!userDao.getUser().uploadImages) {
                            screenshot.filePath?.let { File(it).delete() }
                        }

                        if (stopped) {
                            progress.postValue("Stopped.")
                        } else {
                            val duration = SystemClock.uptimeMillis() - startTime
                            val totalTime = String.format(Locale.ENGLISH, "Completed in %.3fs.", duration / 1000f)
                            Timber.tag("OCRTime").d(totalTime.toString())
                        }

                        true // OCR succeeded
                    } catch (e: Exception) {
                        // Handle exceptions that occur inside Tesseract operations
                        Timber.tag(TAG).e(e, "Tesseract operation failed")

                        screenshot.filePath?.let { File(it).delete() }
                        screenshot.id?.let { generalOperationsRepository.deleteScreenshots(listOf(it)) }

                        // If we get here and insideTesseract is still true, we might need to recover
                        if (insideTesseract) {
                            try {
                                // Try to reset Tesseract state
                                tessApi.clear()
                            } catch (clearEx: Exception) {
                                Timber.tag(TAG).e(clearEx, "Failed to reinitialize Tesseract")
                            }
                        }

                        screenshot.isOcrComplete = true
                        screenshot.text = "" // Set empty text since OCR failed

                        e.record()
                        generalOperationsRepository.saveLog(
                            "OCR_FAILED",
                            "OCR failed with message -> ${e.message} stacktrace -> ${e.stackTraceToString()}."
                        )

                        false // OCR failed
                    }
                } ?: run {
                    // Handle timeout
                    Timber.tag(TAG).w("OCR processing timed out for ${screenshot.filePath}")
                    screenshot.isOcrComplete = true
                    screenshot.text = "" // Set empty text since OCR timed out

                    generalOperationsRepository.saveLog(
                        "OCR_TIMEOUT",
                        "OCR processing timed out for ${screenshot.filePath}"
                    )

                    false // OCR timed out
                }
            } catch (e: Exception) {
                // Catch any exceptions that might have escaped
                Timber.tag(TAG).e(e, "Unexpected error in processImage")
                e.record()

                screenshot.isOcrComplete = true
                screenshot.text = ""

                generalOperationsRepository.saveLog(
                    "OCR_UNEXPECTED_ERROR",
                    "Unexpected error in processImage: ${e.message} stacktrace -> ${e.stackTraceToString()}."
                )

                false
            }
        } catch (e: OutOfMemoryError) {
            // Handle OOM specifically
            Timber.tag(TAG).e(e, "Out of memory during OCR")
            System.gc() // Request garbage collection

            screenshot.isOcrComplete = true
            screenshot.text = ""

            generalOperationsRepository.saveLog(
                "OCR_OUT_OF_MEMORY",
                "Out of memory during OCR: ${e.message}"
            )

            false
        }

        return TODO("Provide the return value")
    }

    /**
     * Stops the Tesseract OCR engine and releases resources.
     */
    fun stop() {
        tessApi.recycle()
        stopped = true
        isInitialized = false
    }

    companion object {
        const val TAG = "Recognize"
    }
}