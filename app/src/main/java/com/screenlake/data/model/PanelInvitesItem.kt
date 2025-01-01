package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.screenlake.data.database.entity.PanelInviteEntity

/**
 * Data class representing a list of panel invite items.
 *
 * @property items The list of panel invite items.
 */
@Keep
data class PanelInvitesItem(
    @Expose val items: List<PanelInviteEntity>
)