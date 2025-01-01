package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class representing a screenshot.
 *
 * @property user The user associated with the screenshot.
 * @property imageUrl The URL of the image.
 * @property epochTimeStamp The epoch timestamp of the screenshot.
 * @property appSegmentId The app segment ID associated with the screenshot.
 * @property timestamp The timestamp of the screenshot.
 * @property type The type of the screenshot.
 * @property localTimeStamp The local timestamp of the screenshot.
 * @property currentAppInUse The current app in use when the screenshot was taken.
 * @property isAppRestricted Whether the app is restricted.
 * @property sessionId The session ID associated with the screenshot.
 * @property currentAppRealNameInUse The real name of the current app in use.
 * @property text The text extracted from the screenshot.
 * @property isOcrComplete Whether OCR is complete for the screenshot.
 * @property filePath The file path of the screenshot.
 * @property fileName The file name of the screenshot.
 * @property zipFileId The zip file ID associated with the screenshot.
 * @property sessionDepth The session depth of the screenshot.
 * @property id The primary key ID (auto-generated).
 */
@Keep
@Entity(tableName = "screenshot_table")
data class ScreenshotEntity(
    var user: String? = null,
    var imageUrl: String? = null,
    var epochTimeStamp: Long? = null,
    var appSegmentId: String? = null,
    var timestamp: String? = null,
    var type: String? = null,
    var localTimeStamp: String? = null,
    var currentAppInUse: String? = null,
    var isAppRestricted: Boolean? = null,
    var sessionId: String? = null,
    var currentAppRealNameInUse: String? = null,
    var text: String? = null,
    var isOcrComplete: Boolean = false,
    var filePath: String? = null,
    var fileName: String? = null,
    var zipFileId: String? = null,
    var sessionDepth: Long? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}