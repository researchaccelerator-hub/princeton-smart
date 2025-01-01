package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.screenlake.recorder.utilities.TimeUtility

@Keep
@Entity(tableName = "log_event_table")
class LogEventEntity(
    @Expose val event:String,
    @Expose var msg:String,
    @Expose val user:String,
    @Expose var timestamp: String? = TimeUtility.getCurrentTimeForSQL(),
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}