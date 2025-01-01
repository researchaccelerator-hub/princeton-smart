package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose

/**
 * Data class representing a list of mobile status items.
 *
 * @property items The list of mobile status items.
 */
@Keep
data class MobileStatusItem(
    @Expose val items: List<MobileStatus>
)