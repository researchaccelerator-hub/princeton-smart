package com.screenlake.recorder.viewmodels

import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Progress log for upload, zip, and OCR work, shown in the stats screen. Keeps only the most
 * recent [MAX_MESSAGES] messages, one per line, each stamped with the time it was logged.
 */
class WorkerProgressManager {
    companion object {
        val progressUpdates = MutableLiveData<String>("")
        private const val MAX_MESSAGES = 250
        private val timestampFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val messages = ArrayDeque<String>()

        fun updateProgress(message: String) {
            synchronized(messages) {
                val timestamp = timestampFormatter.format(Date())
                messages.addLast("[$timestamp] $message")
                while (messages.size > MAX_MESSAGES) {
                    messages.removeFirst()
                }
                progressUpdates.postValue(messages.joinToString("\n"))
            }
        }

        fun clear() {
            synchronized(messages) {
                messages.clear()
                progressUpdates.postValue("")
            }
        }
    }
}
