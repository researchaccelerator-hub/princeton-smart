package com.screenlake.recorder.screenshot

import com.screenlake.data.model.AppSegmentResult
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.AppSegmentEntity
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.recorder.utilities.TimeUtility.getFormattedDate
import com.screenlake.recorder.utilities.TimeUtility.getFormattedDay
import com.screenlake.recorder.utilities.TimeUtility.getFormattedHhMmSs
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object DataTransformation {
    private val screenshotHeaders = listOf(
        "id_user",
        "file",
        "zipFileId",
        "apk",
        "id_session",
        "id_segment",
        "app",
        "text",
        "weekday",
        "t_epoch_ts_ms",
        "t_natural_utc_ts",
        "t_natural_second_ts",
        "t_natural_day_ts"
    )
    fun createScreenshotCsv(screenshots: List<ScreenshotEntity>) : String {
        val screenshotCsv = csvOf(
            screenshotHeaders,
            screenshots
        ) {
            listOf(
                it.user.toString(),
                it.filePath.toString(),
                it.zipFileId.toString(),
                it.currentAppInUse.toString(),
                it.sessionId.toString(),
                it.appSegmentId.toString(),
                it.currentAppRealNameInUse.toString(),
                it.text.toString(),
                getFormattedDay(it.epochTimeStamp ?: 0L),
                it.epochTimeStamp.toString(),
                it.timestamp.toString(),
                getFormattedHhMmSs(it.epochTimeStamp ?: 0L),
                getFormattedDate(it.epochTimeStamp ?: 0L),
            )
        }
        return screenshotCsv
    }

    // \"id_user\", \"type\", \"t_natural_utc_ts\", \"apk\", \"id_session\", \"id_interval\", \"text\"
    fun createAccessibilityCSVs(accessibilityEvents: List<AccessibilityEventEntity>) : String {
        val screenshotCsv = csvOf(
            listOf(
                "id_user",
                "type",
                "t_unix_ts_ms",
                "apk",
                "id_session",
                "id_interval",
                "text"
            ),
            accessibilityEvents
        ) {
            listOf(
                it.user.toString(),
                it.eventType.toString(),
                it.eventTime.toString(),
                it.packageName.toString(),
                it.sessionId.toString(),
                it.appIntervalId.toString(),
                it.text.toString()
            )
        }
        return screenshotCsv
    }

    fun createSessionJson(session: List<SessionEntity>) : String {
        val sessionCsv = csvOf(
            listOf(
                "id_user",
                "session_start",
                "session_end",
                "id_session",
                "seconds_since_last_active_ms",
                "session_duration_ms",
                "session_count_per_day",
                "interval",
                "id_panel",
                "id_tenant",
                "t_natural_second_session_start",
                "t_natural_day_session_start",
                "t_natural_second_session_end",
                "t_natural_day_session_end"

            ),
            session
        ) {
            listOf(
                    it.user.toString(),
                    it.sessionStart.toString(),
                    it.sessionEnd.toString(),
                    it.sessionId.toString(),
                    it.secondsSinceLastActive.toString(),
                    it.sessionDuration.toString(),
                    it.sessionCountPerDay.toString(),
                    it.fps.toString(),
                    it.panelId.toString(),
                    it.tenantId.toString(),
                    getFormattedHhMmSs(it.sessionStartEpoch ?: 0L),
                    getFormattedDate(it.sessionStartEpoch ?: 0L),
                    getFormattedHhMmSs(it.sessionEndEpoch ?: 0L),
                    getFormattedDate(it.sessionEndEpoch ?: 0L)
            )
        }

        return sessionCsv
    }

    fun createAppSegmentCSV(session: List<AppSegmentEntity>) : String {
        val sessionCsv = csvOf(
            listOf(
                "apk",
                "t_unix_ts_segment_start",
                "duration_segment_ms",
                "id_session",
                "id_segment",
                "apk_prev_1",
                "apk_prev_2",
                "apk_prev_3",
                "apk_prev_4",
                "apk_next_1",
                "id_user"
            ),
            session
        ) {
            listOf(
                it.appTitle.toString(),
                it.appSegmentStart.toString(),
                it.appSegmentDuration.toString(),
                it.sessionId.toString(),
                it.appSegmentId.toString(),
                it.appPrev1.toString(),
                it.appPrev2.toString(),
                it.appPrev3.toString(),
                it.appPrev4.toString(),
                it.appNext1.toString(),
                it.userId.toString()
            )
        }

        return sessionCsv
    }

     fun getAndSaveLogs(logs: List<LogEventEntity>) : String{
        val csv = csvOf(
            listOf("event",
                "msg",
                "user",
                "timestamp"),
            logs
        ) {
            listOf(it.event,
                it.msg,
                it.user,
                it.timestamp.toString())
        }

        return csv
    }

    private fun <T> csvOf(
        headers: List<String>,
        data: List<T>,
        itemBuilder: (T) -> List<String>
    ) = buildString {
        append(headers.joinToString(",") { "\"$it\"" })
        append("\n")
        data.forEach { item ->
            append(itemBuilder(item).joinToString(",") { "\"$it\"" })
            append("\n")
        }
    }

//    data class GroupedData(val name: String, var duration: Long)
    fun groupAndSortData(dataList: List<ScreenshotEntity>): AppSegmentResult {
        val sortedData = dataList.sortedBy { it.epochTimeStamp }
        val groupedDataList = mutableListOf<AppSegmentEntity>()

        val resultScreenshots = mutableListOf<ScreenshotEntity>()
        var currentGroup: MutableList<ScreenshotEntity>? = null
        for (data in sortedData) {
            if (currentGroup == null || data.currentAppInUse != currentGroup.last().currentAppInUse) {
                // Start a new group
                 if (currentGroup != null && data.currentAppInUse != currentGroup?.last()?.currentAppInUse) {
                    val time1 = Instant.parse(currentGroup.first().timestamp)
                    val time2 = Instant.parse(currentGroup.last().timestamp)
                    val appSegmentId = UUID.randomUUID().toString()
                    val timeBetween = ChronoUnit.MILLIS.between(time1, time2)
                     val duration = if (timeBetween == 0L) 3000L else timeBetween + 3000L
                    groupedDataList.add(AppSegmentEntity(appTitle=currentGroup?.first()?.currentAppInUse,
                        appSegmentDuration = duration,
                        appSegmentStart = time1.toEpochMilli(),
                        appSegmentEnd = time2.toEpochMilli(),
                        appSegmentId = appSegmentId,
                        userId = currentGroup.first().user,
                        sessionId = currentGroup.first().sessionId.toString()))

                     currentGroup.forEach {
                         it.appSegmentId = appSegmentId
                         resultScreenshots.add(it)
                     }
                     currentGroup = mutableListOf(data)
                }else{
                     currentGroup = mutableListOf(data)
                }
            } else {
                currentGroup.add(data)
            }
        }

        if (currentGroup != null && currentGroup.size > 0){
            val time1 = Instant.parse(currentGroup.first().timestamp)
            val time2 = Instant.parse(currentGroup.last().timestamp)
            val timeBetween = ChronoUnit.MILLIS.between(time1, time2)

            val duration = if (timeBetween == 0L) 3L else timeBetween
            val appSegmentId = UUID.randomUUID().toString()

            groupedDataList.add(AppSegmentEntity(
                appTitle=currentGroup.first().currentAppInUse,
                appSegmentDuration = duration,
                appSegmentStart = time1.toEpochMilli(),
                appSegmentId = appSegmentId,
                appSegmentEnd = time2.toEpochMilli(),
                userId = currentGroup.first().user,
                sessionId = currentGroup.first().sessionId.toString()))

            currentGroup.forEach {
                it.appSegmentId = appSegmentId
                resultScreenshots.add(it)
            }
        }

        return AppSegmentResult(groupedDataList.toTypedArray(), resultScreenshots)
    }

    fun addPrevAppDataToSegments(appSegmentData: AppSegmentResult) : AppSegmentResult {
        for (i in appSegmentData.appSegments.size - 1 downTo 0) {
            val item = appSegmentData.appSegments[i]

            item.appNext1 = if((i + 1) <= (appSegmentData.appSegments.size - 1)) getItemFromArrayOrScreenLocked(appSegmentData.appSegments, i + 1) else "NA"
            item.appPrev1 = if((i - 1) >= 0) getItemFromArrayOrScreenLocked(appSegmentData.appSegments, i - 1) else "NA"
            item.appPrev2 = if((i - 2) >= 0) getItemFromArrayOrScreenLocked(appSegmentData.appSegments, i - 2) else "NA"
            item.appPrev3 = if((i - 3) >= 0) getItemFromArrayOrScreenLocked(appSegmentData.appSegments, i - 3) else "NA"
            item.appPrev4 = if((i - 4) >= 0) getItemFromArrayOrScreenLocked(appSegmentData.appSegments, i - 4) else "NA"

            // Do something with the item
            println("Item at index $i: $item")
        }

        return appSegmentData
    }

    fun getAppSegmentData(dataList: List<ScreenshotEntity>?) : AppSegmentResult? {
        val groupedDataList = dataList?.let { groupAndSortData(it) }
        return groupedDataList?.let { addPrevAppDataToSegments(it) }
    }

    fun getItemFromArrayOrScreenLocked(array: Array<AppSegmentEntity>, index: Int): String? {
        return if (index >= 0 && index < array.size && !array[index].appTitle.isNullOrEmpty()) {
            array[index].appTitle
        } else {
            "NA"
        }
    }

    fun groupAccessibilityEventsAndScreenshots(
        accessibilityEvents: List<AccessibilityEventEntity>,
        screenshots: List<ScreenshotEntity>
    ): List<AccessibilityEventEntity> {
        val groupedEvents = accessibilityEvents.groupBy { it.accessibilitySessionId }

        groupedEvents.forEach { (sessionId, events) ->
            val sortedEvents = events.sortedBy { it.eventTime }
            val startTime = sortedEvents.firstOrNull()?.eventTime
            val endTime = sortedEvents.lastOrNull()?.eventTime

            if (startTime != null && endTime != null){
                val screenshotsWithinInterval = screenshots.filter { screenshot ->
                    screenshot.epochTimeStamp in (startTime ?: 0L)..(endTime ?: Long.MAX_VALUE)
                }

                val firstScreenshot = screenshotsWithinInterval.firstOrNull()

                if (firstScreenshot != null){
                    val firstScreenshotSessionId = firstScreenshot?.sessionId

                    events.forEach { event ->
                        event.sessionId = firstScreenshotSessionId
                    }
                }
            }
        }

        return accessibilityEvents
    }

    data class Comparison(
        val screenshot: ScreenshotEntity,
        val accessibilityEvent: AccessibilityEventEntity
    )

    fun findMatchingScreenshotsAndEvents(
        time1: Long,
        screenshots: List<ScreenshotEntity>,
        accessibilityEvents: List<AccessibilityEventEntity>
    ) {
        // Step 5: Initialize lists for matched and unmatched comparisons
        val matchedComparisons = mutableListOf<Comparison>()
        val unmatchedScreenshots = mutableListOf<ScreenshotEntity>()
        val unmatchedAccessibilityEvents = mutableListOf<AccessibilityEventEntity>()

        // Step 6: Perform the matching
        for (screenshot in screenshots) {
            val closestEvent = findClosestEvent(screenshot, accessibilityEvents)
            if (closestEvent != null) {
                val eventTime = closestEvent.eventTime ?: 0
                if (Math.abs(eventTime - screenshot.epochTimeStamp!!) <= 1000) {
                    // Considered a match if the time difference is within 1 second (1000 milliseconds)
                    matchedComparisons.add(Comparison(screenshot, closestEvent))
                } else {
                    unmatchedScreenshots.add(screenshot)
                    unmatchedAccessibilityEvents.add(closestEvent)
                }
            } else {
                unmatchedScreenshots.add(screenshot)
            }
        }

        // Step 7: Log the results
        if (screenshots.size != accessibilityEvents.size) {
            // Log if the counts of screenshots and accessibility events do not match
            println("Mismatch between screenshot count (${screenshots.size}) and accessibility event count (${accessibilityEvents.size})")
        }

        if (unmatchedScreenshots.isNotEmpty()) {
            // Log unmatched screenshots
            println("Unmatched Screenshots:")
            unmatchedScreenshots.forEach { println(it) }
        }

        if (unmatchedAccessibilityEvents.isNotEmpty()) {
            // Log unmatched accessibility events
            println("Unmatched Accessibility Events:")
            unmatchedAccessibilityEvents.forEach { println(it) }
        }
    }

    // Helper function to find the closest AccessibilityEvent to a given Screenshot
    private fun findClosestEvent(screenshot: ScreenshotEntity, events: List<AccessibilityEventEntity>): AccessibilityEventEntity? {
        var closestEvent: AccessibilityEventEntity? = null
        var minTimeDifference = Long.MAX_VALUE

        for (event in events) {
            val eventTime = event.eventTime ?: 0
            val timeDifference = Math.abs(eventTime - screenshot.epochTimeStamp!!)
            if (timeDifference < minTimeDifference) {
                closestEvent = event
                minTimeDifference = timeDifference
            }
        }

        return closestEvent
    }

}
