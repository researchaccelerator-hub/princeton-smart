package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "settings_table")
data class SettingsEntity(
    var fps: String? = null,
    var limitDataUsage: Boolean = false,
    var limitPowerUsage: Boolean = false,
    var paymentsHandle: String? = null,
    var paymentHandleType: String? = null
) {

    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}