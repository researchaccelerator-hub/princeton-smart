package com.screenlake.data.model

import android.graphics.drawable.Drawable
import androidx.annotation.Keep

/**
 * Data class representing a restricted app.
 *
 * @property packageName The package name of the app.
 * @property name The name of the app.
 * @property isUserRestricted Whether the app is user restricted.
 * @property authOverride Whether the app has authentication override.
 * @property isAuthRestricted Whether the app is authentication restricted.
 * @property iconDrawable The drawable icon of the app.
 * @property isHeader Whether the app is a header.
 * @property id The unique identifier of the app.
 */
@Keep
data class RestrictedApp(
    var packageName: String?,
    var name: String?,
    var isUserRestricted: Boolean,
    var authOverride: Boolean = false,
    var isAuthRestricted: Boolean = false,
    var iconDrawable: Drawable? = null,
    var isHeader: Boolean? = false,
    var id: Int? = null
) {
    constructor() : this(
        "", "", false, false, false, null
    )
}