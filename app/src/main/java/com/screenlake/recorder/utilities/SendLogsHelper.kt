package com.screenlake.recorder.utilities

import android.content.Context
import android.os.Build
import com.screenlake.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SendLogsHelper {

    private const val FILE_NAME = "send_logs.txt"

    /**
     * Writes recent log output plus basic device/app metadata to a single text file
     * under a dedicated filesDir/send_logs/ subdirectory, overwriting any previous
     * export. Returns the file for the caller to share via FileProvider.
     */
    fun buildLogFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
        val metadata = buildString {
            appendLine("Princeton SMART log export")
            appendLine("Generated: $timestamp")
            appendLine("App version: ${BuildConfig.VERSION_NAME}")
            appendLine("OS version: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("----------------------------------------")
        }

        val dir = File(context.filesDir, "send_logs")
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("Could not create directory: ${dir.absolutePath}")
        }
        val file = File(dir, FILE_NAME)
        file.writeText(metadata + RingBufferTree.dumpAsText())
        return file
    }
}
