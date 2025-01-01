package com.screenlake.data.model

/**
 * Data class representing information about an application.
 *
 * @property apk The APK identifier of the application.
 * @property name The name of the application.
 */
data class AppInfo(
    var apk: String = "",
    var name: String = ""
)