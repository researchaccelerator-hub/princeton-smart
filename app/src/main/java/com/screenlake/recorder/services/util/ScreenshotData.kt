package com.screenlake.recorder.services.util

import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.model.AppInfo
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.services.ScreenshotService.Companion.appNameVsPackageName
import com.screenlake.recorder.services.ScreenshotService.Companion.lastCaptureTime
import com.screenlake.recorder.utilities.TimeUtility

object ScreenshotData {

    /**
     * Cleans up OCR text by removing unwanted characters and reducing multiple spaces.
     *
     * @param input The original OCR text.
     * @return A cleaned-up string with unwanted characters and excessive spaces removed.
     */
    fun ocrCleanUp(input: String): String {
        // Remove unwanted characters such as quotes, newlines, tabs, etc.
        val cleanedText = input
            .replace("""[\'\",\r\n;\t\\]""".toRegex(), " ")
            .replace("[\\[\\]]".toRegex(), " ") // Remove square brackets
            .replace("""\s{2,}""".toRegex(), " ") // Replace multiple spaces with a single space

        return cleanedText.trim() // Trim leading and trailing spaces for cleaner output
    }

    /**
     * Creates and returns a [ScreenshotEntity] object with the given data.
     *
     * @param filename The name of the screenshot file.
     * @param currentAppInUse The [AppInfo] object representing the current app in use during the screenshot.
     * @param sessionId The session ID for the screenshot session.
     * @param user The [UserEntity] object representing the user taking the screenshot.
     * @param imageUrl The optional URL of the image (null by default).
     * @param isAppRestricted Flag indicating whether the current app is restricted.
     * @return A [ScreenshotEntity] object populated with the provided information.
     */
    fun saveScreenshotData(
        filename: String,
        currentAppInUse: AppInfo,
        sessionId: String,
        user: UserEntity,
        imageUrl: String? = null,
        isAppRestricted: Boolean = false
    ): ScreenshotEntity {
        val timestamp = TimeUtility.getCurrentTimestamp().toInstant()

        lastCaptureTime = timestamp.toEpochMilli()

        // Extract the file ID (last part of the file path)
        val fileId = filename.substringAfterLast("/")

        return ScreenshotEntity().apply {
            this.user = user.emailHash
            this.epochTimeStamp = timestamp.toEpochMilli()
            this.timestamp = timestamp.toString()
            this.sessionId = sessionId
            this.isAppRestricted = ConstantSettings.RESTRICTED_APPS.contains(currentAppInUse.apk)
            this.type = "SCREENSHOT"
            this.currentAppInUse = currentAppInUse.apk
            this.currentAppRealNameInUse = currentAppInUse.name
            this.localTimeStamp = TimeUtility.getCurrentTimestampDefaultTimezoneString()
            this.filePath = filename
            this.fileName = fileId
            this.sessionDepth = ScreenshotService.lastUnlockTime?.let { timestamp.epochSecond.minus(it) }
        }
    }
}
