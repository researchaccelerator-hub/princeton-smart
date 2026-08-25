package com.screenlake.recorder.constants

/**
 * RESEARCHER CONFIGURATION
 *
 * This is the single file for adjusting data collection behavior before building
 * and distributing the app. Set the values below to match your study's requirements,
 * then rebuild and redistribute the app.
 *
 * Nothing in this file is visible to participants. All settings are compile-time
 * researcher choices.
 */
object ResearchConfig {

    // -----------------------------------------------------------------------------------------
    // SCREENSHOT CAPTURE FREQUENCY
    // -----------------------------------------------------------------------------------------

    /**
     * How often the app takes a screenshot.
     *
     * LOW    - 1 screenshot every 10 seconds. Least storage and battery impact.
     *          Best for long-duration studies where high temporal resolution is not required.
     *
     * MEDIUM - 1 screenshot every 5 seconds. Balanced fidelity and storage. (default)
     *          Suitable for most research use cases.
     *
     * HIGH   - 1 screenshot every 1 second. Highest temporal resolution.
     *          Use only for short-duration sessions or when fine-grained activity
     *          capture is essential, as storage and upload costs increase significantly.
     */
    val ACTIVE_PRESET: DataCollectionPreset = DataCollectionPreset.MEDIUM

    // -----------------------------------------------------------------------------------------
    // SCREENSHOT IMAGE QUALITY
    // -----------------------------------------------------------------------------------------

    /**
     * JPEG compression quality for captured screenshots (1-100).
     *
     * Lower values reduce file size but reduce image clarity, which may affect OCR accuracy.
     * Higher values improve clarity at the cost of more storage and upload bandwidth.
     *
     * Recommended range: 40-70.
     */
    const val SCREENSHOT_JPEG_QUALITY: Int = 50

    // -----------------------------------------------------------------------------------------
    // UPLOAD BEHAVIOR
    // -----------------------------------------------------------------------------------------

    /**
     * How often (in hours) the background upload and zip workers run.
     *
     * Increase this value to reduce background activity on participant devices.
     * Decrease it for more frequent data delivery to your S3 bucket.
     * Minimum enforced by Android WorkManager: 15 minutes (0.25). Values below that
     * are silently clamped to 15 minutes by the OS.
     */
    const val UPLOAD_WORKER_INTERVAL_HOURS: Long = 1L

    /**
     * When true, the app will only upload data when the device is connected to WiFi.
     * Recommended for studies where participants may have limited cellular data plans.
     */
    const val UPLOAD_OVER_WIFI_ONLY: Boolean = true

    /**
     * When true, the app will only upload data when the device is plugged in and charging.
     * Combine with UPLOAD_OVER_WIFI_ONLY to minimize impact on participants' devices.
     */
    const val UPLOAD_OVER_POWER_ONLY: Boolean = false

    // -----------------------------------------------------------------------------------------
    // STORAGE PRESSURE THRESHOLD
    // -----------------------------------------------------------------------------------------

    /**
     * When the device's storage is this percentage full, the app will attempt an upload
     * to free space, regardless of the normal upload schedule.
     *
     * Default: 95.0 (upload triggered when 95% of device storage is used).
     * Lower this value on studies targeting low-storage devices.
     */
    const val STORAGE_PRESSURE_THRESHOLD_PERCENT: Double = 95.0

    // -----------------------------------------------------------------------------------------
    // APP RECORDING CONTROLS
    // -----------------------------------------------------------------------------------------

    /**
     * A built-in system block list of sensitive app categories (banking, health, finance)
     * is always applied automatically — see ConstantSettings.RESTRICTED_APPS.
     *
     * Use the two lists below to make study-specific adjustments on top of that baseline.
     */

    /**
     * App package names to block IN ADDITION to the system block list.
     *
     * Use this to exclude apps that are specific to your study population or institution
     * but are not covered by the default block list.
     *
     * Example:
     *   val ADDITIONAL_BLOCKED_APPS: List<String> = listOf(
     *       "com.example.internalapp",
     *       "org.myuniversity.portal"
     *   )
     */
    val ADDITIONAL_BLOCKED_APPS: List<String> = emptyList()

    /**
     * App package names to ALLOW even if they appear in the system block list.
     *
     * Use this only when your study explicitly needs to capture an app that would
     * otherwise be blocked by the default list. Use with caution — the default
     * block list exists to protect participant privacy.
     *
     * Example:
     *   val ALLOWED_APPS_OVERRIDE: List<String> = listOf(
     *       "com.example.studyapp"
     *   )
     */
    val ALLOWED_APPS_OVERRIDE: List<String> = emptyList()

    // -----------------------------------------------------------------------------------------
    // PACKAGE INSTALL/UNINSTALL/REPLACE TRACKING
    // -----------------------------------------------------------------------------------------

    /**
     * Master switch for package install/uninstall/replace event tracking.
     *
     * When false (default), the feature is fully inactive: no events are recorded, and no
     * package-event data is queried or written anywhere in the pipeline. A deliberate
     * researcher action (setting this to true and rebuilding) is required to enable it.
     */
    val LOG_PACKAGE_EVENTS: Boolean = false

    /**
     * When false (default), package install/uninstall/replace events are logged for the
     * participant's entire enrollment window, regardless of screen/session state. When
     * true, only events that occur during an active accessibility session are logged.
     *
     * Whole-enrollment logging captures package activity even while the phone is locked or
     * idle, which is a broader footprint than screenshot/accessibility capture. Confirm
     * this default is covered by your study's consent language/IRB protocol before
     * building.
     */
    val LOG_PACKAGE_EVENTS_SESSION_ONLY: Boolean = false

    // -----------------------------------------------------------------------------------------
    // SEND LOGS (SETTINGS)
    // -----------------------------------------------------------------------------------------

    /**
     * Master switch for the "Send Logs" feature in Settings.
     *
     * When false (default), the feature is fully inactive: no "Send Logs" preference is
     * shown, and no log buffer is captured or held in memory. A deliberate researcher
     * action (setting this to true and rebuilding) is required to enable it.
     */
    val SEND_LOGS_ENABLED: Boolean = false

    /**
     * Maximum number of recent log lines kept in memory for export when SEND_LOGS_ENABLED
     * is true. Oldest lines are dropped once this cap is reached. Higher values capture
     * more history at the cost of memory while the app is running.
     */
    const val SEND_LOGS_MAX_LINES: Int = 1500

    /**
     * Optional email address pre-filled as the recipient when a participant/researcher
     * taps "Send Logs" and picks a mail app from the share sheet. Left blank by default
     * so the app ships with no destination tied to a monitored inbox or process -- set
     * this to your own study's address if you want it pre-filled.
     */
    val SEND_LOGS_DESTINATION_EMAIL: String = ""
}

/**
 * Data collection granularity presets.
 *
 * Each preset defines the screenshot capture interval and the corresponding
 * frames-per-second value recorded in session data.
 *
 * @param fps          Frames per second value stored in session CSV output.
 * @param intervalMs   Milliseconds between screenshot captures.
 * @param displayLabel Human-readable label for documentation and logs.
 */
enum class DataCollectionPreset(
    val fps: Double,
    val intervalMs: Long,
    val displayLabel: String
) {
    LOW(fps = 0.1, intervalMs = 10_000L, displayLabel = "Low (1 screenshot / 10 s)"),
    MEDIUM(fps = 0.2, intervalMs = 5_000L, displayLabel = "Medium (1 screenshot / 5 s)"),
    HIGH(fps = 1.0, intervalMs = 1_000L, displayLabel = "High (1 screenshot / 1 s)")
}
