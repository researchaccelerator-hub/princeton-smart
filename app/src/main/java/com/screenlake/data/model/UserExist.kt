package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose

/**
 * Data class representing the user existence status.
 *
 * @property message Indicates whether the user exists.
 */
@Keep
data class UserExist(
    @Expose val message: Boolean
)