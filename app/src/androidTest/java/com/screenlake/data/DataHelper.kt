package com.screenlake.data

import android.R
import android.content.Context
import com.screenlake.data.database.entity.AppSegmentEntity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


object DataHelper {
//    fun main() {
//        // Sample NRC VAD dictionary stored as a string
//        val nrcVadDictionaryString = Data.lexicon.trimIndent()
//
//        val nrcVadMap = parseNrcVadDictionary(nrcVadDictionaryString)
//
//        val words = listOf("happy", "sad", "excited", "angry", "peaceful")
//
//        for (word in words) {
//            val vadValues = nrcVadMap[word]
//            if (vadValues != null) {
//                val valence = vadValues[0]
//                val sentiment = if (valence > 0.0) "positive" else if (valence < 0.0) "negative" else "neutral"
//                println("Word: $word, Valence: $valence, Sentiment: $sentiment")
//            } else {
//                println("Word: $word not found in dictionary")
//            }
//        }
//    }

    fun parseNrcVadDictionary(dictionaryString: String): Map<String, DoubleArray> {
        val nrcVadMap = mutableMapOf<String, DoubleArray>()
        val lines = dictionaryString.lines()
        for (line in lines) {
            val parts = line.split(" ")
            if (parts.size >= 4) {
                val word = parts[0]
                val valence = parts[1].toDouble()
                val arousal = parts[2].toDouble()
                val dominance = parts[3].toDouble()

                val vadValues = doubleArrayOf(valence, arousal, dominance)
                nrcVadMap[word] = vadValues
            }
        }
        return nrcVadMap
    }

    fun getAppSegmentData(context: Context) : List<AppSegmentEntity> {
        return parseCSVFile(context,"app_segment_data.csv")
    }
    fun parseCSVFile(context: Context, fileName: String): List<AppSegmentEntity> {
        val appSegmentDataList = mutableListOf<AppSegmentEntity>()

        try {
            // Open the CSV file from the assets folder
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Read the header line to skip it
            val headerLine = reader.readLine()

            // Process the remaining lines
            reader.forEachLine { line ->
                val columns = line.split(",").map { it.trim() }
                if (columns.size == 12) { // Ensure 12 columns exist
                    // Create and populate an AppSegmentData object here
                    val appSegmentData = mapCSVRowToAppSegmentData(columns)
                    appSegmentDataList.add(appSegmentData)
                }
            }

            // Close the input stream
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return appSegmentDataList
    }

    fun mapCSVRowToAppSegmentData(columns: List<String>): AppSegmentEntity {
        return AppSegmentEntity(
            appTitle = columns[0],
            appSegmentStart = columns[1].toLong(),
            appSegmentEnd = columns[2].toLong(),
            appSegmentDuration = columns[3].toLong(),
            sessionId = columns[4],
            appPrev1 = columns[5],
            appSegmentId = columns[6],
            appPrev2 = columns[7],
            appPrev3 = columns[8],
            appPrev4 = columns[9],
            appNext1 = columns[10],
            userId = columns[11]
        )
    }

    /**
     * Copies an asset file to the internal storage directory.
     *
     * @param context The context used to access the assets and internal storage.
     * @param assetFileName The name of the asset file to be copied.
     * @return The parent directory of the copied file.
     */
    fun copyAssetToInternalStorage(context: Context, assetFileName: String): String {
        val file = File(context.filesDir, assetFileName)

        if (file.exists()) {
            return file.parent // Full file path in internal storage
        }

        // Copy the asset file to internal storage
        context.assets.open(assetFileName).use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.parent // Full file path in internal storage
    }
}