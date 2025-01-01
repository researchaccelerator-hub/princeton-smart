package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "upload_history_table")
data class UploadHistoryEntity(
    var email: String? = null,
    @ColumnInfo(name = "total_uploaded")
    var totalUploaded: Int = 0
) {

    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}

