package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "upload_daily_table")
data class UploadDailyEntity(
    @PrimaryKey()
    var id: String,
    @ColumnInfo(name = "today_uploads")
    var todayUploads: Int = 0,
    var timestamp: String? = null
)

