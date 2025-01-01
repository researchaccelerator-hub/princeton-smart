package com.screenlake.data.model

import com.screenlake.data.database.entity.AppSegmentEntity
import com.screenlake.data.database.entity.ScreenshotEntity

/**
 * Data class representing the result of an app segment analysis.
 *
 * @property appSegments An array of app segment data.
 * @property screenshots A list of screenshots associated with the app segments.
 */
data class AppSegmentResult(
    var appSegments: Array<AppSegmentEntity>,
    var screenshots: List<ScreenshotEntity>
) {
    /**
     * Checks if this object is equal to another object.
     *
     * @param other The other object to compare.
     * @return True if the objects are equal, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppSegmentResult

        if (!appSegments.contentEquals(other.appSegments)) return false
        if (screenshots != other.screenshots) return false

        return true
    }

    /**
     * Computes the hash code for this object.
     *
     * @return The hash code.
     */
    override fun hashCode(): Int {
        var result = appSegments.contentHashCode()
        result = 31 * result + screenshots.hashCode()
        return result
    }
}