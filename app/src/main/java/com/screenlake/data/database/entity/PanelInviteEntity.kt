package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Entity class representing a panel invite.
 *
 * @property id_dynanmo The DynamoDB ID of the panel invite.
 * @property email The email associated with the panel invite.
 * @property panel_id The panel ID.
 * @property tenant_id The tenant ID.
 * @property item_status The status of the item.
 * @property panel_name The name of the panel.
 * @property upload_images Indicates if images should be uploaded.
 * @property created_time The time the panel invite was created.
 * @property id The unique identifier of the panel invite.
 */
@Keep
@Entity(tableName = "panel_table")
class PanelInviteEntity(
    @SerializedName(value = "id")
    @Expose var id_dynanmo: String,
    @Expose var email: String,
    @Expose var panel_id: String,
    @Expose var tenant_id: String,
    @Expose var item_status: String,
    @Expose var panel_name: String? = null,
    @Expose var upload_images: Boolean = false,
    @Expose var created_time: String? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}