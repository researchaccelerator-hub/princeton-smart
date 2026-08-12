package com.screenlake.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing an app install/uninstall/replace event.
 *
 * @property user The user (email hash) associated with the event.
 * @property packageName The package name that was installed, uninstalled, or replaced.
 * @property appName The human-readable app label, best-effort (may be null for PACKAGE_REMOVED,
 * since the package may already be fully uninstalled by the time it's looked up).
 * @property eventType One of PackageEventType's names: INSTALLED, UNINSTALLED, REPLACED.
 * @property eventTime Epoch milliseconds when the event was received.
 * @property isReplacing True when an UNINSTALLED event is immediately followed by a reinstall
 * (from the broadcast's EXTRA_REPLACING), distinguishing it from a true uninstall.
 * @property id The unique identifier of the event.
 */
@Entity(tableName = "package_event")
data class PackageEventEntity(
    var user: String? = null,
    var packageName: String? = null,
    var appName: String? = null,
    var eventType: String? = null,
    var eventTime: Long? = null,
    var isReplacing: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}
