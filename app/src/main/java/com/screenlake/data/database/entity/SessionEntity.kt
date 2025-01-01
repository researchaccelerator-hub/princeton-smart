package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "session_table")
data class SessionEntity(
    var user: String? = null,
    var sessionStartEpoch: Long? = null,
    var sessionEndEpoch: Long? = null,
    var sessionStart: String? = null,
    var sessionEnd: String? = null,
    var sessionId: String? = null,
    var secondsSinceLastActive: Long? = null,
    var sessionDuration: Long? = null,
    var sessionCountPerDay: Int? = null,
    var fps: Double = 0.0,
    var panelId: String? = null,
    var tenantId: String? = null
) {

    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}