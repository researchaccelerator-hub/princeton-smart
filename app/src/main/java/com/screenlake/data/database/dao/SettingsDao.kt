package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    /**
     * Update frames per second
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    /**
     * Update frames per second
     */
    @Query("UPDATE settings_table SET fps = :fps WHERE id =:id")
    suspend fun updateFPS(fps: String?, id: Int)

    /**
     * Update payment handle
     */
    @Query("UPDATE settings_table SET paymentsHandle = :payment_handle WHERE id =:id")
    suspend fun updatePaymentHandle(payment_handle: String?, id: Int)

    /**
     * Update payment handle
     */
    @Query("UPDATE settings_table SET paymentHandleType = :payment_handle_type WHERE id =:id")
    suspend fun updatePaymentHandleType(payment_handle_type: String?, id: Int)

    /**
     * Update power usage
     */
    @Query("UPDATE settings_table SET limitPowerUsage = :limit_power_usage WHERE id =:id")
    suspend fun updatePowerUsage(limit_power_usage: Boolean?, id: Int)

    /**
     * Update data usage
     */
    @Query("UPDATE settings_table SET limitDataUsage = :limit_data_usage WHERE id =:id")
    suspend fun updateDataUsage(limit_data_usage: Boolean?, id: Int)

    /**
     * Get settings object
     */
    @Query("SELECT * FROM settings_table Limit 1")
    fun getSettings(): Flow<SettingsEntity>

    /**
     * Deleting all settings
     */
    @Query("DELETE FROM settings_table")
    suspend fun nukeTable()
}