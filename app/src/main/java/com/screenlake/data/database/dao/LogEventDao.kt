package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.recorder.services.util.ScreenshotData

@Dao
interface LogEventDao {

    /**
     * Inserts or updates a LogEvent.
     *
     * @param log The LogEvent to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(log: LogEventEntity)

    /**
     * Inserts or updates a LogEvent synchronously.
     *
     * @param log The LogEvent to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSync(log: LogEventEntity)

    /**
     * Retrieves a list of LogEvents ordered by timestamp in descending order, limited by the specified number and offset.
     *
     * @param limit The maximum number of LogEvents to retrieve.
     * @param offset The offset from which to start retrieving LogEvents.
     * @return A list of LogEvents.
     */
    @Query("SELECT * FROM log_event_table ORDER BY timestamp DESC LIMIT :limit OFFSET :offset ")
    suspend fun getLogsFrom(limit: Int, offset: Int): List<LogEventEntity>

    /**
     * Retrieves the count of LogEvents.
     *
     * @return The count of LogEvents.
     */
    @Query("SELECT Count(*) FROM log_event_table")
    suspend fun logCount(): Int

    /**
     * Deletes LogEvents with the specified IDs.
     *
     * @param idList The list of IDs of the LogEvents to be deleted.
     */
    @Query("delete from log_event_table where id in (:idList)")
    fun deleteLogEvents(idList: List<Int>)

    /**
     * Deletes all LogEvents.
     */
    @Query("DELETE FROM log_event_table")
    suspend fun deleteLogs()

    /**
     * Saves a LogEvent after cleaning up the message using OCR.
     *
     * @param log The LogEvent to be saved.
     */
    suspend fun saveException(log: LogEventEntity){
        log.msg = ScreenshotData.ocrCleanUp(log.msg)
        save(log)
    }

    /**
     * Saves a LogEvent synchronously after cleaning up the message using OCR.
     *
     * @param log The LogEvent to be saved.
     */
    fun saveExceptionSync(log: LogEventEntity){
        log.msg = ScreenshotData.ocrCleanUp(log.msg)
        saveSync(log)
    }
}