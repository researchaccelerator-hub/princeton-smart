package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.TopicSeenIntervalEntity

@Dao
interface TopicSeenDao {
    /**
     * Update TopicSeenInterval.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(topicSeenInterval: TopicSeenIntervalEntity)

    /**
     * Update TopicSeenInterval.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(topicSeenIntervals: List<TopicSeenIntervalEntity>)

    /**
     * Delete all TopicIntervals.
     */
    @Query("DELETE FROM word_seen_table")
    suspend fun nukeTable()
}