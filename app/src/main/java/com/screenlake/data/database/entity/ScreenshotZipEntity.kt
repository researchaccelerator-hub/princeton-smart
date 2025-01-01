package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "screenshot_zip_table")
data class ScreenshotZipEntity(
    var user: String? = null,
    var timestamp: String? = null,
    var localTimeStamp: String? = null,
    var file: String? = null,
    var toDelete: Boolean? = null,
    @ColumnInfo(name = "panel_id")
    var panelId: String? = null,
    @ColumnInfo(name = "panel_name")
    var panelName: String? = null,
    @ColumnInfo(name = "tenant_id")
    var tenantId: String? = null
) {

    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}