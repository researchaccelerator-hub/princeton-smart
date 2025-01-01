package com.screenlake.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.screenlake.data.enums.BehavioralEvents

/**
 * Entity class representing an accessibility event.
 *
 * @property user The user associated with the event.
 * @property eventGroupId The group ID of the event.
 * @property sessionId The session ID of the event.
 * @property accessibilitySessionId The accessibility session ID of the event.
 * @property appIntervalId The app interval ID of the event.
 * @property eventType The type of the event.
 * @property eventTime The time of the event.
 * @property packageName The package name associated with the event.
 * @property className The class name associated with the event.
 * @property text The text associated with the event.
 * @property contentDescription The content description of the event.
 * @property password Indicates if the event involves a password.
 * @property isFocused Indicates if the event is focused.
 * @property scrollDeltaX The scroll delta X value of the event.
 * @property scrollDeltaY The scroll delta Y value of the event.
 * @property r1 Reserved field 1.
 * @property r2 Reserved field 2.
 * @property r3 Reserved field 3.
 * @property r4 Reserved field 4.
 * @property id The unique identifier of the event.
 * @property behavorType The behavioral type of the event.
 */
@Entity(tableName = "accessibility_event")
data class AccessibilityEventEntity(
    var user: String? = null,
    var eventGroupId: String? = null,
    var sessionId: String? = null,
    var accessibilitySessionId: String? = null,
    var appIntervalId: String? = null,
    var eventType: String? = "ROOT",
    var eventTime: Long? = null,
    @SerializedName("PackageName")
    var packageName: String? = null,
    @SerializedName("ClassName")
    var className: String? = null,
    @SerializedName("Text")
    var text: String? = null,
    @SerializedName("ContentDescription")
    var contentDescription: String? = null,
    @SerializedName("Password")
    var password: Boolean? = null,
    @SerializedName("IsFocused")
    var isFocused: Boolean? = null,
    @SerializedName("ScrollDeltaX")
    var scrollDeltaX: Int = -1,
    @SerializedName("ScrollDeltaY")
    var scrollDeltaY: Int = -1,
    var r1: Int? = -1,
    var r2: Int? = -1,
    var r3: Int? = -1,
    var r4: Int? = -1,
) {

    @PrimaryKey(autoGenerate = true)
    var id: Int? = null

    @Transient
    var behavorType: BehavioralEvents? = BehavioralEvents.NONE
}