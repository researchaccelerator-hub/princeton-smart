package com.screenlake.data.database

/**
 * Values of the `state` column in screenshot_data (schema version 4). The names match the
 * University of Minnesota builds so both studies' data can be analysed together.
 */
object ScreenshotState {
    /** A screenshot was captured. */
    const val NORMAL = "Normal"
    /** The foreground app is restricted, so no image was captured. */
    const val RESTRICTED = "Restricted"

    // Marker rows, written once when the state changes. They have no image.
    const val SCREEN_LOCKED = "ScreenLocked"
    const val SCREEN_UNLOCKED = "ScreenUnlocked"
    const val PAUSE_START = "pause start"
    const val PAUSE_END = "pause end"
    const val NO_SCREEN_SHARING = "NoScreenSharing"
}
