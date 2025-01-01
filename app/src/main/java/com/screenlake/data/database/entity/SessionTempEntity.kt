package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import com.screenlake.recorder.utilities.TimeUtility
import java.time.Instant

@Keep
@Entity(tableName = "session_temps")
class SessionTempEntity(
    var user: String? = null,
    var sessionStartEpoch: Long? = null,
    var sessionEndEpoch: Long? = null,
    var sessionStart: Instant? = TimeUtility.getCurrentTimestamp().toInstant(),
    var sessionEnd: Instant? = null,
    var sessionId: String? = null,
    var secondsSinceLastActive: Long? = null,
    var appsInSession: String? = null,
    var sessionDuration: Long? = null,
    var sessionCountPerDay: Int? = null,
    var fps: Double = 0.0,
    var panelId: String? = null,
    var tenantId: String? = null
) {
    fun toSession() : SessionEntity {
        return SessionEntity().also { sesh: SessionEntity ->
            sesh.sessionStartEpoch = sessionStart?.toEpochMilli()
            sesh.sessionEndEpoch = sessionEnd?.toEpochMilli()
            sesh.user = user
            sesh.sessionStart = sessionStart.toString()
            sesh.sessionEnd = sessionEnd.toString()
            sesh.sessionId = sessionId
            sesh.secondsSinceLastActive = secondsSinceLastActive
            sesh.sessionDuration = sessionDuration
            sesh.sessionCountPerDay = sessionCountPerDay
            sesh.fps = fps
            sesh.panelId = panelId
            sesh.tenantId = tenantId

        }
    }
}