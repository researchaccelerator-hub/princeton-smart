package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing a restricted app.
 *
 * @property appName The name of the app.
 * @property packageName The package name of the app.
 * @property isUserRestricted Indicates if the app is user-restricted.
 * @property timestamp The timestamp when the app was restricted.
 * @property authOverride Indicates if the app has an authentication override.
 * @property isAuthRestricted Indicates if the app is authentication-restricted.
 * @property id The unique identifier of the restricted app.
 */
@Keep
@Entity(tableName = "restricted_app_table")
data class RestrictedAppPersistentEntity(
    var appName: String,
    var packageName: String,
    var isUserRestricted: Boolean,
    var timestamp: String? = null,
    var authOverride: Boolean = false,
    var isAuthRestricted: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}