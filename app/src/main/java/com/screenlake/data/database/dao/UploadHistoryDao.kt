package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.UploadHistoryEntity

@Dao
interface UploadHistoryDao {
    /**
     * Update UploadHistory.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(uploadHistory: UploadHistoryEntity)

    @Query("UPDATE upload_history_table SET total_uploaded = total_uploaded + :total_uploaded WHERE id = :id")
    fun updateQuantity(id:Int, total_uploaded: Int)

    /**
     * Get UploadHistory object
     */
    @Query("SELECT * FROM upload_history_table Limit 1")
    suspend fun get() : UploadHistoryEntity

    /**
     * Delete all.
     */
    @Query("DELETE FROM settings_table")
    suspend fun nukeTable()
}