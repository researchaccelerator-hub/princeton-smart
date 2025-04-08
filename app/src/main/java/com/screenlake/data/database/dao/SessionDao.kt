package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.SessionEntity

@Dao
interface SessionDao {
    /**
     * Get Session object
     */
    @Query("SELECT * FROM SESSION_TABLE WHERE id = :id")
    suspend fun get(id: String) : SessionEntity

    @Query("SELECT * FROM SESSION_TABLE WHERE sessionStartEpoch <= :time1 AND sessionEndEpoch >= :time1")
    fun getSessionAtTime(time1: Long): SessionEntity?

    /**
     * Get Session count from day
     */
    @Query("SELECT COUNT(*) FROM SESSION_TABLE WHERE sessionStart BETWEEN :dateStart and :dateEnd")
    suspend fun getCountByDay(dateStart: String, dateEnd: String) : Int

    @Query("delete from session_table where id in (:idList)")
    fun deleteSessions(idList: List<String>)

    @Query("delete from session_table where id in (:idList)")
    fun deleteSessionsId(idList: List<Int>)

    /**
     * Get Session object
     */
    @Query("SELECT * FROM SESSION_TABLE  ORDER BY sessionStart DESC LIMIT 200")
    suspend fun getMostRecent() : List<SessionEntity>

    /**
     * Get Session object
     */
    @Query("SELECT * FROM SESSION_TABLE  ORDER BY sessionStart DESC LIMIT 1")
    suspend fun getMostRecentSingle() : SessionEntity?

    /**
     * Get Session object
     */
    @Query("Select * from session_table where sessionId in (:sessionIds)")
    suspend fun getSessionByIds(sessionIds: List<String>) : List<SessionEntity>?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)

    /**
     * Delete all.
     */
    @Query("DELETE FROM SESSION_TABLE")
    suspend fun nukeTable()
}