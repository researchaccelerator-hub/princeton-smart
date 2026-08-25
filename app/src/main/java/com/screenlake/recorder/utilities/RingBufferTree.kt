package com.screenlake.recorder.utilities

import com.screenlake.recorder.constants.ResearchConfig
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber tree that keeps the most recent log lines in memory for on-demand export
 * (see Settings -> Send Logs). Bounded by ResearchConfig.SEND_LOGS_MAX_LINES; oldest
 * lines are dropped once the cap is reached. Only planted when
 * ResearchConfig.SEND_LOGS_ENABLED is true.
 */
object RingBufferTree : Timber.Tree() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val lines = ArrayDeque<String>()
    private val lock = Any()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val priorityLabel = when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            else -> "?"
        }
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp $priorityLabel/${tag ?: "Screenlake"}: $message"

        synchronized(lock) {
            lines.addLast(line)
            if (t != null) lines.addLast(t.stackTraceToString())
            while (lines.size > ResearchConfig.SEND_LOGS_MAX_LINES) {
                lines.removeFirst()
            }
        }
    }

    fun dumpAsText(): String = synchronized(lock) { lines.joinToString("\n") }

    internal fun clear() = synchronized(lock) { lines.clear() }
}
