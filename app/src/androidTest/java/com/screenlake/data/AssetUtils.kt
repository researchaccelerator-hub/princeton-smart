package com.screenlake.data

import android.content.Context
import java.io.File
import java.io.IOException

object AssetUtils {

    fun getFileFromAssets(context: Context, fileName: String): File? {
        try {
            // List all files in the assets folder
            val assetFiles = context.assets.list("")

            if (assetFiles != null) {
                val assetList = assetFiles.toList()

                // Check if the desired file exists in the assets
                if (assetList.contains(fileName)) {
                    // Create a File object for the asset
                    val file = File(context.cacheDir, fileName)

                    // Copy the asset to a file
                    copyAssetToFile(context, fileName, file)

                    // Now, 'file' represents the file you were looking for
                    return file
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // File not found or an error occurred
        return null
    }

    private fun copyAssetToFile(context: Context, assetFileName: String, targetFile: File) {
        try {
            context.assets.open(assetFileName).use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
