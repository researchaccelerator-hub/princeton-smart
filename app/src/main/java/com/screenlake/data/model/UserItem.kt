package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.screenlake.data.database.entity.UserEntity

/**
 * Data class representing a list of user items.
 *
 * @property items The list of user items.
 */
@Keep
data class UserItem(
    @Expose val items: List<UserEntity>
)