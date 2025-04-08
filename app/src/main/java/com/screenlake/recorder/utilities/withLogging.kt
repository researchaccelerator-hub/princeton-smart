package com.screenlake.recorder.utilities

import timber.log.Timber
import java.io.File

/**
 * Performs an operation on a file and logs any failures using Timber.
 * 
 * @param tag The tag to use for logging
 * @param operation A description of the operation being performed
 * @param action The action to perform on the file
 * @return The result of the action, or null if an exception occurred
 */
inline fun <T> File.withLogging(tag: String, operation: String, action: (File) -> T): T? {
    return try {
        action(this)
    } catch (e: Exception) {
        e.record()
        Timber.tag(tag).e(e, "Failed to $operation file: ${this.absolutePath}")
        null
    }
}

// Usage examples:
// Read a file with logging
fun readFileContent(file: File): String? {
    return file.withLogging("FileOps", "read") { it.readText() }
}

// Write to a file with logging
fun writeToFile(file: File, content: String): Boolean {
    return file.withLogging("FileOps", "write to") { 
        it.writeText(content)
        true
    } ?: false
}

// Delete a file with logging
fun deleteFileWithLogging(file: File): Boolean {
    return file.withLogging("FileOps", "delete") {
        it.delete()
    } ?: false
}