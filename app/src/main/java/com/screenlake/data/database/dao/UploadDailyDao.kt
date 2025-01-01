package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.UploadDailyEntity

@Dao
interface UploadDailyDao {
    /**
     * Update UploadDaily.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(uploadDaily: UploadDailyEntity)

    /**
     * Get UploadDaily object
     */
    @Query("SELECT * FROM UPLOAD_DAILY_TABLE WHERE id = :id")
    suspend fun get(id: String) : UploadDailyEntity

    /**
     * Get UploadDaily object
     */
    @Query("SELECT * FROM UPLOAD_DAILY_TABLE  ORDER BY timestamp DESC LIMIT 7")
    suspend fun getMostRecent() : List<UploadDailyEntity>

    /**
     * Delete all.
     */
    @Query("DELETE FROM settings_table")
    suspend fun nukeTable()
}