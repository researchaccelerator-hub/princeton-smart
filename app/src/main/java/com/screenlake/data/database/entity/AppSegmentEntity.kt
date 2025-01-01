package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class representing an app segment.
 *
 * @property appTitle The title of the app (or raw APK).
 * @property appSegmentStart The timestamp of the first screenshot of the app segment.
 * @property appSegmentEnd The timestamp of the last screenshot to be in the app segment.
 * @property appSegmentDuration The duration of the app segment (in milliseconds).
 * @property sessionId The session ID in which the app segment occurred.
 * @property appPrev1 The app on-screen in the immediately preceding app segment (or "screen unlock" if it's the session start).
 * @property appSegmentId The app segment ID.
 * @property appPrev2 The app on-screen in the 2-preceding app segment (or "screen unlock" if it's the session start).
 * @property appPrev3 The app on-screen in the 3-preceding app segment (or "screen unlock" if it's the session start).
 * @property appPrev4 The app on-screen in the 4-preceding app segment (or "screen unlock" if it's the session start).
 * @property appNext1 The app on-screen in the following app segment (or "screen lock" if it's the session end).
 * @property userId The user ID.
 * @property id The primary key ID (auto-generated).
 */
@Keep
@Entity(tableName = "app_segment_table")
data class AppSegmentEntity(
    val appTitle: String? = null,
    var appSegmentStart: Long? = null,
    var appSegmentEnd: Long? = null,
    var appSegmentDuration: Long? = null,
    var sessionId: String? = null,
    var appPrev1: String? = null,
    var appSegmentId: String? = null,
    var appPrev2: String? = null,
    var appPrev3: String? = null,
    var appPrev4: String? = null,
    var appNext1: String? = null,
    var userId: String? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}
