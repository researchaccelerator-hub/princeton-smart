package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "user_table")
data class UserEntity(
    var createdAt: String? = null,
    var email: String? = null,
    @ColumnInfo(name = "email_hash")
    var emailHash: String? = null,
    @ColumnInfo(name = "tenant_id")
    var tenantId: String? = "ut_austin_tenant_1",
    @ColumnInfo(name = "tenant_name")
    var tenantName: String? = "ut_austin_tenant",
    var updatedAt: String? = null,
    var username: String? = null,
    @ColumnInfo(name = "created_timestamp")
    var createdTimestamp: String? = null,
    @ColumnInfo(name = "panel_id")
    var panelId: String? = "ut_austin_1",
    @ColumnInfo(name = "panel_name")
    var panelName: String? = "ut_austin_panel",
    var _lastChangedAt: String? = null,
    var _version: String? = null,
    var __typename: String? = null,
    var sdk: String? = null,
    var device: String? = null,
    var model: String? = null,
    var product: String? = null,
    @ColumnInfo(name = "upload_images")
    var uploadImages: Boolean = true,
    @ColumnInfo(name = "is_emulator")
    var isEmulator: Boolean? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null

    companion object {
        const val TENANT_ID = "ut_austin_tenant_1"
        const val PANEL_ID = "ut_austin_1"
    }
}

