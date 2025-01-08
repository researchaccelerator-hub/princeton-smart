package com.screenlake.recorder.utilities

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {


    fun copyAssetToLocalStorage(context: Context, assetFileName: String, outputFileName: String): File? {
        return try {
            // Open the asset file as an InputStream
            val inputStream = context.assets.open(assetFileName)

            // Get the local storage directory
            val outputDir = context.filesDir // Use the app's internal storage directory
            val outputFile = File(outputDir, outputFileName)

            // Create the output file if it doesn't exist
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }

            // Write the asset data to the output file
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)

            // Close streams
            inputStream.close()
            outputStream.close()

            // Return the saved file
            outputFile
        } catch (e: IOException) {
            e.printStackTrace()
            null // Return null in case of an error
        }
    }
}
