package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose

/**
 * Data class representing a list of user existence items.
 *
 * @property items The list of user existence items.
 */
@Keep
data class UserExistItem(
    @Expose val items: List<UserExist>
)