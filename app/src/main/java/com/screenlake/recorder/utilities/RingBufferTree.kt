package com.screenlake.recorder.utilities

import com.screenlake.recorder.constants.ResearchConfig
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber tree that keeps the most recent log lines in memory for on-demand export
 * (see Settings -> Send Logs). Bounded by ResearchConfig.SEND_LOGS_MAX_LINES. When
 * full, the oldest low-priority (below WARN) entry is dropped first, so a burst of
 * routine DEBUG/INFO activity cannot evict the WARN/ERROR lines that are usually
 * what a researcher actually needs, only falling back to dropping the oldest entry
 * outright once every remaining entry is already WARN or above. Only planted when
 * ResearchConfig.SEND_LOGS_ENABLED is true.
 */
object RingBufferTree : Timber.Tree() {

    private class Entry(val text: String, val highPriority: Boolean)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val entries = ArrayDeque<Entry>()
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
        val highPriority = priority >= android.util.Log.WARN

        synchronized(lock) {
            // dateFormat.format() must run inside the lock: SimpleDateFormat mutates a
            // shared internal Calendar and is not thread-safe, and this tree is fed from
            // multiple concurrent background threads/services in production.
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp $priorityLabel/${tag ?: "Screenlake"}: $message"
            entries.addLast(Entry(line, highPriority))
            if (t != null) entries.addLast(Entry(t.stackTraceToString(), highPriority))
            evictIfNeeded()
        }
    }

    // Caller must hold `lock`. Prefers dropping the oldest low-priority (below WARN)
    // entry so routine DEBUG/INFO volume can't push out WARN/ERROR history; only
    // falls back to dropping the oldest entry outright if every remaining entry is
    // high-priority.
    private fun evictIfNeeded() {
        while (entries.size > ResearchConfig.SEND_LOGS_MAX_LINES) {
            val lowPriorityIndex = entries.indexOfFirst { !it.highPriority }
            if (lowPriorityIndex >= 0) {
                entries.removeAt(lowPriorityIndex)
            } else {
                entries.removeFirst()
            }
        }
    }

    fun dumpAsText(): String = synchronized(lock) { entries.joinToString("\n") { it.text } }

    internal fun clear() = synchronized(lock) { entries.clear() }
}
