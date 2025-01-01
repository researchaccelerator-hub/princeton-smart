package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "scroll_event_segment_table")
data class ScrollEventSegmentEntity(
    var timestamp: Long,
    var scrollCount: Int,
    val sessionId: String? = null,
    var accessibilitySessionId: String? = null,
    var intervalId: String,
    var apk: String,
    var scrollDeltaXTotal: Int? = -1,
    var scrollDeltaYTotal: Int? = -1,
) {
    var scrollTotal: Int? = scrollDeltaXTotal?.let { xTotal ->
        scrollDeltaYTotal?.let { yTotal ->
            xTotal + yTotal
        }
    }
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}
