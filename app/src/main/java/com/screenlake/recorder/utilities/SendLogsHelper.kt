package com.screenlake.recorder.utilities

import android.content.Context
import android.os.Build
import com.screenlake.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SendLogsHelper {

    /**
     * Writes recent log output plus basic device/app metadata to a timestamped text
     * file (e.g. "SRK_20260827_143512_logs.txt") under a dedicated filesDir/send_logs/
     * subdirectory, so the filename itself tells the recipient which app it came from
     * and when the export was generated. Deletes any previous export(s) in that
     * directory first, so exports don't accumulate on disk across repeated taps.
     * Returns the file for the caller to share via FileProvider.
     */
    fun buildLogFile(context: Context): File {
        val now = Date()
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(now)
        val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val metadata = buildString {
            appendLine("Princeton SMART log export")
            appendLine("Generated: $isoTimestamp")
            appendLine("App version: ${BuildConfig.VERSION_NAME}")
            appendLine("OS version: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("----------------------------------------")
        }

        val dir = File(context.filesDir, "send_logs")
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("Could not create directory: ${dir.absolutePath}")
        }
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, "SRK_${fileTimestamp}_logs.txt")
        file.writeText(metadata + RingBufferTree.dumpAsText())
        return file
    }
}
