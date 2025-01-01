package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose

/**
 * Data class representing the mobile status.
 *
 * @property id The unique identifier of the mobile status.
 * @property createdAt The creation timestamp of the mobile status.
 * @property updatedAt The last updated timestamp of the mobile status.
 * @property allTimeUploads The total number of uploads made by the mobile.
 * @property mobileStatus The current status of the mobile.
 * @property mobileStatusConfirmed Whether the mobile status is confirmed.
 * @property notUploaded The number of items not uploaded.
 * @property tenant The tenant associated with the mobile status.
 * @property uploadsThisWeek The number of uploads made by the mobile this week.
 */
@Keep
data class MobileStatus(
    @Expose val id: String,
    @Expose val createdAt: String,
    @Expose val updatedAt: String,
    @Expose val allTimeUploads: Int,
    @Expose val mobileStatus: String,
    @Expose val mobileStatusConfirmed: Boolean,
    @Expose val notUploaded: Int,
    @Expose val tenant: String,
    @Expose val uploadsThisWeek: Int
)