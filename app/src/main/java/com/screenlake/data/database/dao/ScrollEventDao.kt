package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.model.GroupedScrollTotal
import com.screenlake.data.database.entity.ScrollEventSegmentEntity

@Dao
interface ScrollEventDao {
    /**
     * Update ScrollEventSegment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(scrollEventSegment: ScrollEventSegmentEntity)

    /**
     * Update ScrollEventSegment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(scrollEventSegments: List<ScrollEventSegmentEntity>)

    /**
     * Delete all ScrollEventSegments.
     */
    @Query("DELETE FROM scroll_event_segment_table")
    suspend fun nukeTable()

    /**
     * Get all events by time interval.
     */
    @Query("SELECT * FROM scroll_event_segment_table WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getScrollEventsWithinTimeInterval(startTime: Long, endTime: Long): List<ScrollEventSegmentEntity>

    /**
     * Get total of scroll grouped by apk.
     */
    @Query("SELECT apk, SUM(scrollTotal) AS total FROM scroll_event_segment_table WHERE timestamp >= :hoursBefore GROUP BY apk")
    suspend fun getGroupedScrollTotals(hoursBefore: Long): List<GroupedScrollTotal>
}