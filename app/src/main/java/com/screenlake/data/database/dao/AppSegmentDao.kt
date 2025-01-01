package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.AppSegmentEntity

@Dao
interface AppSegmentDao {
    /**
     * Update AppSegment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(appSegmentData: AppSegmentEntity)

    /**
     * Get AppSegment object
     */
    @Query("SELECT * FROM app_segment_table WHERE sessionId in (:sessionIds)")
    suspend fun getAppSegmentsBySessionId(sessionIds: List<String>) : List<AppSegmentEntity>

    @Query("delete from app_segment_table where id in (:appSegmentId)")
    fun deleteAppSegments(appSegmentId: List<String>)

    /**
     * Delete all AppSegments.
     */
    @Query("DELETE FROM app_segment_table")
    suspend fun nukeTable()
}