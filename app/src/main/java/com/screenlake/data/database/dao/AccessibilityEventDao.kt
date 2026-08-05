package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.AccessibilityEventEntity

@Dao
interface AccessibilityEventDao {

    /**
     * Inserts or updates an AccessibilityEvent.
     *
     * @param accessibilityEvent The AccessibilityEvent to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(accessibilityEvent: AccessibilityEventEntity)

    /**
     * Inserts or updates a list of AccessibilityEvents.
     *
     * @param accessibilityEvent The list of AccessibilityEvents to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(accessibilityEvent: List<AccessibilityEventEntity>)

    /**
     * Retrieves all AccessibilityEvents ordered by eventTime in descending order, limited by the specified number.
     *
     * @param limit The maximum number of AccessibilityEvents to retrieve.
     * @return A list of AccessibilityEvents.
     */
    @Query("SELECT * FROM accessibility_event ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getAllAccessibilityEvents(limit: Int): List<AccessibilityEventEntity>

    /**
     * Deletes AccessibilityEvents with the specified IDs.
     *
     * @param idList The list of IDs of the AccessibilityEvents to be deleted.
     */
    @Query("delete from accessibility_event where id in (:idList)")
    suspend fun deleteAccessibilityEvents(idList: List<Int>)

    /**
     * Retrieves all AccessibilityEvents with the specified accessibilitySessionId.
     *
     * @param sessionId The accessibilitySessionId to filter the AccessibilityEvents.
     * @return A list of AccessibilityEvents.
     */
    @Query("SELECT * FROM accessibility_event where accessibilitySessionId = :sessionId")
    suspend fun getAllAccessibilityEventsBySessionId(sessionId: String): List<AccessibilityEventEntity>

    /**
     * Retrieves an AccessibilityEvent at the specified eventTime.
     *
     * @param time1 The eventTime to filter the AccessibilityEvent.
     * @return The AccessibilityEvent at the specified eventTime, or null if not found.
     */
    @Query("SELECT * FROM accessibility_event WHERE eventTime = :time1")
    fun getAccessibilityEventAtTime(time1: Long): AccessibilityEventEntity?

    /**
     * Retrieves all AccessibilityEvents with the specified sessionId.
     *
     * @param sessionId The sessionId to filter the AccessibilityEvents.
     * @return A list of AccessibilityEvents.
     */
    @Query("SELECT * FROM accessibility_event WHERE sessionId = :sessionId")
    fun getAccessibilityEventsBySessionId(sessionId: String): List<AccessibilityEventEntity>

    /**
     * Deletes all AccessibilityEvents.
     */
    @Query("DELETE FROM accessibility_event")
    suspend fun deleteAccessibilityEvents()

    /**
     * Deletes all AccessibilityEvents.
     */
    @Query("DELETE FROM accessibility_event")
    suspend fun nukeTable()

    /**
     * Retrieves the most recently recorded session-boundary event (SESSION_START or
     * SESSION_END), if any. Used to determine whether a session is currently active.
     * Note: TouchAccessibilityService also writes many other event types (APP_RESTRICTED,
     * SCREEN_TEXT, URL, IMAGE_METADATA) into this same table roughly every few seconds
     * during active use -- this query deliberately excludes those so it reflects only
     * session boundaries, not general activity.
     *
     * @return The most recent SESSION_START/SESSION_END event, or null if none exist.
     */
    @Query("SELECT * FROM accessibility_event WHERE eventType IN ('SESSION_START', 'SESSION_END') ORDER BY eventTime DESC LIMIT 1")
    suspend fun getMostRecentSessionBoundaryEvent(): AccessibilityEventEntity?
}
