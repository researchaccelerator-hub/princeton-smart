package com.screenlake.recorder.services

/**
 * User-visible screen-sharing lifecycle, kept separate from the study recording intent.
 * Every transition is persisted by [ScreenshotService] as a SCREEN_SHARING_STATE log event.
 */
enum class ProjectionInterruptionState {
    IDLE,
    STARTING,
    CAPTURING,
    USER_PAUSED,
    SCREEN_LOCKED_CAPTURE_SUSPENDED,
    SCREEN_LOCKED_CONSENT_REQUIRED,
    CONSENT_REQUIRED,
    STOPPED,
    ERROR
}
