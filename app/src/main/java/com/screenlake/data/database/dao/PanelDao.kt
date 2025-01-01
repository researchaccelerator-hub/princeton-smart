package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.PanelInviteEntity

@Dao
interface PanelDao {

    /**
     * Inserts or updates a PanelInvite.
     *
     * @param panel The PanelInvite to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanelObj(panel: PanelInviteEntity)

    /**
     * Checks if a user exists in the panel_table.
     *
     * @return True if a user exists, false otherwise.
     */
    @Query("SELECT EXISTS (SELECT 1 FROM panel_table)")
    suspend fun userExists(): Boolean

    /**
     * Retrieves a single PanelInvite from the panel_table.
     *
     * @return The PanelInvite object.
     */
    @Query("SELECT * FROM panel_table LIMIT 1")
    suspend fun getPanel(): PanelInviteEntity

    /**
     * Retrieves all PanelInvites from the panel_table.
     *
     * @return A list of PanelInvite objects.
     */
    @Query("SELECT * FROM panel_table")
    suspend fun getPanels(): List<PanelInviteEntity>

    /**
     * Deletes all PanelInvites from the panel_table.
     */
    @Query("DELETE FROM panel_table")
    suspend fun deletePanels()
}