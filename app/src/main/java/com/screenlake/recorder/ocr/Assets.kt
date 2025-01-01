package com.screenlake.recorder.ocr

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Utility object for handling assets related to Tesseract OCR.
 */
object Assets {
    private const val TESSDATA_DIR = "tessdata"
    private const val ENG_TRAINEDDATA = "eng.traineddata"
    private const val HI_JPG = "hi.jpg"
    private const val LANGUAGE = "eng"

    /**
     * Returns the path to the directory containing the Tesseract data files.
     *
     * @param context The application context.
     * @return The absolute path to the Tesseract data directory.
     */
    fun getTessDataPath(context: Context): String {
        return context.filesDir.absolutePath
    }

    /**
     * The language to be used by Tesseract.
     */
    val language: String
        get() = LANGUAGE

    /**
     * Extracts the necessary assets for Tesseract OCR from the app's assets folder to the app's files directory.
     *
     * @param contextWeak A weak reference to the application context.
     */
    fun extractAssets(contextWeak: WeakReference<Context>) {
        val context = contextWeak.get()
        val am = context?.assets

        if (am != null) {
            val tessDir = File(getTessDataPath(context), TESSDATA_DIR)
            if (!tessDir.exists()) {
                tessDir.mkdir()
            }
            val engFile = File(tessDir, ENG_TRAINEDDATA)
            if (!engFile.exists()) {
                copyFile(am, ENG_TRAINEDDATA, engFile)
            }

            val image = File(tessDir, HI_JPG)
            if (!image.exists()) {
                copyFile(am, HI_JPG, image)
            }
        }
    }

    /**
     * Copies a file from the assets folder to the specified output file.
     *
     * @param am The AssetManager to access the assets.
     * @param assetName The name of the asset file to copy.
     * @param outFile The output file to which the asset will be copied.
     */
    private fun copyFile(am: AssetManager, assetName: String, outFile: File) {
        try {
            am.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}