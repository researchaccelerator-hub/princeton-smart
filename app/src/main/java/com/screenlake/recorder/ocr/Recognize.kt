package com.screenlake.recorder.ocr

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import com.googlecode.tesseract.android.TessBaseAPI
import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.recorder.services.util.ScreenshotData
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Recognize @Inject constructor(
    private val userDao: UserDao
) {

    private val tessApi: TessBaseAPI
    val processing = MutableLiveData(false)
    private val progress = MutableLiveData<String>()
    var isInitialized = false
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
            this.isInitialized = tessApi.init(dataPath, language, engineMode)
            tessApi.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/' '")
            tessApi.setVariable("user_defined_dpi", "300")
        } catch (e: IllegalArgumentException) {
            this.isInitialized = false
            Timber.tag(TAG).d("Cannot initialize Tesseract: $e")
        }
    }

    /**
     * Processes an image using Tesseract OCR.
     *
     * @param screenshot The screenshot to be processed.
     * @return True if the image was processed successfully, false otherwise.
     */
    suspend fun processImage(screenshot: ScreenshotEntity): Boolean {
        val file = screenshot.filePath?.let { File(it) }

        if (file != null && file.exists()) {
            tessApi.setImage(file)
            val startTime = SystemClock.uptimeMillis()

            tessApi.getHOCRText(0)
            val regexCleanup1 = tessApi.utF8Text?.lowercase(Locale.ROOT) ?: ""
            val regexCleanup2 = ScreenshotData.ocrCleanUp(regexCleanup1)

            screenshot.text = regexCleanup2
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

            return true
        } else {
            return false
        }
    }

    /**
     * Stops the Tesseract OCR engine and releases resources.
     */
    fun stop() {
        tessApi.recycle()
        stopped = true
    }

    companion object {
        const val TAG = "Recognize"
    }
}