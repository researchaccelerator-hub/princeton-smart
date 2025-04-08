package com.screenlake.data.repository

import android.content.Context
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.dao.AppSegmentDao
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.database.dao.PanelDao
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.database.dao.ScreenshotZipDao
import com.screenlake.data.database.dao.ScrollEventDao
import com.screenlake.data.database.dao.SessionDao
import com.screenlake.data.database.dao.TopicSeenDao
import com.screenlake.data.database.dao.UploadDailyDao
import com.screenlake.data.database.dao.UploadHistoryDao
import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.database.entity.AppSegmentEntity
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.ScrollEventSegmentEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.data.database.entity.SessionTempEntity
import com.screenlake.data.database.entity.TopicSeenIntervalEntity
import com.screenlake.data.database.entity.UploadDailyEntity
import com.screenlake.data.database.entity.UploadHistoryEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_MAPPING
import com.screenlake.recorder.screenshot.DataTransformation
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.utilities.silence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralOperationsRepository @Inject constructor(
    private val context: Context,
    private val logEventDao: LogEventDao,
    private val accessibilityEventDao: AccessibilityEventDao,
    private val appSegmentDao: AppSegmentDao,
    private val panelDao: PanelDao,
    private val sessionDao: SessionDao,
    private val screenshotDao: ScreenshotDao,
    private val screenshotZipDao: ScreenshotZipDao,
    private val userDao: UserDao,
    private val uploadHistoryDao: UploadHistoryDao,
    private val uploadDailyDao: UploadDailyDao,
    private val restrictedAppDao: RestrictedAppDao,
) {

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    var currentSession = SessionTempEntity()
    private var lastActiveTime: Long? = null
    private val framesPerSecondConst: Double = ScreenshotService.framesPerSecondConst

    suspend fun clearPhone() {
        val path = context.filesDir?.path

        if (path != null) {
            File(path).walk().filter {
                it.name.endsWith("jpg")
                        || it.name.endsWith("zip")
                        || it.name.endsWith("csv")
                        || (it.name.endsWith("json") && it.name.contains("screenshot_data"))
            }.forEach {
                it.delete()
                Timber.tag("ClearPhone").d("Deleted file ${it.name} from phone.")
            }
        }

        context.getSharedPreferences(getString(R.string.payment_handle), 0)?.edit()?.clear()
            ?.apply()
        context.getSharedPreferences(getString(R.string.payment_handle_type), 0)?.edit()?.clear()
            ?.apply()
        context.getSharedPreferences(getString(R.string.limit_data_usage), 0)?.edit()?.clear()
            ?.apply()
        context.getSharedPreferences(getString(R.string.limit_power_usage), 0)?.edit()?.clear()
            ?.apply()

        // If a user registers and immediately logsout, this could still be in volatile memory.
        cloudAuthentication.clearUserAuth()

        deleteUser()

        deleteAllScreenshot()

        deleteAllScreenshotZip()

        deleteAllPanels()

        deleteAllSessions()

        deleteAllAppSegments()

        deleteAllAccessibilityEvents()

        ScreenshotService.postInitialValues()

        saveLog(ConstantSettings.LOGGED_OUT)
    }

    suspend fun saveAllSessionSegments() {
        val sessionIds = getAllSessionsWithoutAppSegments()

        for (sessionId in sessionIds) {
            if (sessionId.isNotEmpty()) {
                val screenshots = getScreenshotsBySessionId(sessionId)
                DataTransformation.getAppSegmentData(screenshots).takeIf {
                    it?.appSegments?.isNotEmpty() == true
                }.apply {
                    this?.let { saveAppSegments(it.appSegments) }
                    this?.let { saveScreenshots(it.screenshots) }
                }
            }
        }

        ScreenshotService.screenshotInterval.postValue(ConstantSettings.SCREENSHOT_MAPPING[ScreenshotService.framesPerSecond])
    }

    suspend fun buildCurrentSession(localFPS: Double) {
        val lastActiveTime1 = getLastTimeSessionActive()
        val time = TimeUtility.getCurrentTimestamp()
        currentSession.user = ScreenshotService.user.emailHash
        currentSession.sessionEnd = time.toInstant()
        currentSession.sessionCountPerDay = getScreenshotCount(TimeUtility.getCurrentTimestampDefaultTimezone()) + 1
        currentSession.secondsSinceLastActive =
            ((currentSession.sessionStart?.toEpochMilli() ?: 0L) - (lastActiveTime1
                ?: time.toInstant().toEpochMilli()))

        if((currentSession.secondsSinceLastActive ?: 0L) <= 0L) currentSession.secondsSinceLastActive = 0L

        currentSession.sessionId = ScreenshotService.sessionId
        currentSession.tenantId = UserEntity.TENANT_ID
        currentSession.panelId = UserEntity.PANEL_ID
        currentSession.fps = SCREENSHOT_MAPPING[ScreenshotService.Companion.framesPerSecond]!!.toDouble()
        if (currentSession.sessionId.isNullOrEmpty()) {
            currentSession.sessionId = ScreenshotService.sessionId
        }

        this.lastActiveTime = currentSession.sessionEnd?.toEpochMilli()

        currentSession.sessionDuration =
            ((currentSession.sessionEnd?.toEpochMilli()
                ?: 0L) - (currentSession.sessionStart?.toEpochMilli() ?: 0L))
    }

    suspend fun clearPhoneOnUpdate() {
        val path = context.filesDir?.path

        if (path != null) {
            File(path).walk().filter {
                it.name.endsWith("jpg")
                        || it.name.endsWith("zip")
                        || it.name.endsWith("csv")
                        || (it.name.endsWith("json") && it.name.contains("screenshot_data"))
            }.forEach {
                it.delete()
                Timber.tag("ClearPhone").d("Deleted file ${it.name} from phone.")
            }
        }

        deleteAllScreenshot()

        deleteAllScreenshotZip()

        deleteAllSessions()

        deleteAllAppSegments()
    }

    fun getString(resId: Int) = context.getString(resId)

    suspend fun setScreenToOcrComplete(screenshot: ScreenshotEntity) {
        screenshotDao.setOcrComplete(
            screenshot.id!!,
            true,
            screenshot.text ?: ""
        )
    }

    private suspend fun deleteUser() {
        userDao.deleteUser()
    }

    private suspend fun deleteAllScreenshot() {
        screenshotDao.nukeTable()
    }

    private suspend fun deleteAllScreenshotZip() {
        screenshotZipDao.nukeTable()
    }

    private suspend fun deleteAllSessions() {
        sessionDao.nukeTable()
    }

    private suspend fun deleteAllPanels() {
        panelDao.deletePanels()
    }

    private suspend fun deleteAllAppSegments() {
        appSegmentDao.nukeTable()
    }

    private suspend fun deleteAllAccessibilityEvents() {
        accessibilityEventDao.deleteAccessibilityEvents()
    }

    suspend fun saveLog(event: String, msg: String = "") = silence {
        logEventDao.saveException(
            LogEventEntity(event, msg, amplifyRepository.email)
        )
    }

    fun save(accessibilityEvent: AccessibilityEventEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            accessibilityEventDao.save(
                accessibilityEvent
            )
        }
    }

    fun save(accessibilityEvents: List<AccessibilityEventEntity>) {
        accessibilityEventDao.save(accessibilityEvents)
    }

    suspend fun deleteZip(id: Int) {
        screenshotZipDao.delete(id)
    }

    suspend fun getASEvents(): List<AccessibilityEventEntity> {
        return accessibilityEventDao.getAllAccessibilityEvents(500)
    }

    suspend fun getUser(): UserEntity {
        return userDao.getUser()
    }

    private suspend fun saveScreenshots(screenshots: List<ScreenshotEntity>) {
        screenshots.forEach {
            screenshotDao.insertScreenshot(it)
        }
    }

    fun getALlApps(): List<RestrictedAppPersistentEntity> {
        return restrictedAppDao.getAllRestrictedApps()
    }

    private suspend fun saveAppSegments(appSegments: Array<AppSegmentEntity>) {
       appSegments.forEach {
           appSegmentDao.save(it)
       }
    }

    suspend fun saveSession(sessionTemp: SessionTempEntity) {
        sessionDao.saveSession(sessionTemp.toSession())
    }

    private suspend fun getScreenshotCount(time: Date): Int {
        val dateStart = TimeUtility.getStartOfDay(time)
        val dateEnd = time.toInstant().toString()
        return sessionDao.getCountByDay(
            dateStart,
            dateEnd
        )
    }

    private suspend fun getLastTimeSessionActive(): Long? {
        return sessionDao.getMostRecentSingle()?.sessionEndEpoch
    }

    suspend fun deleteAccessibilityEvents(ids: List<Int>) {
        accessibilityEventDao.deleteAccessibilityEvents(ids)
    }

    suspend fun getSessions(): List<SessionEntity> {
        return sessionDao.getMostRecent()
    }

    suspend fun getSessionsById(sessionIds: List<String>): List<SessionEntity>? {
        return sessionDao.getSessionByIds(sessionIds)
    }

    suspend fun getAppSegmentsBySessionId(sessionIds: List<String>): List<AppSegmentEntity> {
        return appSegmentDao.getAppSegmentsBySessionId(sessionIds)
    }

    suspend fun insertScreenshot(screenshotData: ScreenshotEntity) {
        screenshotDao.insertScreenshot(screenshotData)
    }

    fun deleteScreenshots(screenshots: List<Int>) {
        screenshotDao.deleteScreenshots(screenshots)
    }

    fun deleteSessions(screenshots: List<String>) {
        sessionDao.deleteSessions(screenshots)
    }

    fun deleteSessionsId(screenshots: List<Int>) {
        sessionDao.deleteSessionsId(screenshots)
    }

    fun deleteAppSegments(appSegmentIds: List<String>) {
        appSegmentDao.deleteAppSegments(appSegmentIds)
    }

    fun deleteLogEvents(logEvents: List<Int>) {
        logEventDao.deleteLogEvents(logEvents)
    }

    fun insertScreenshotZip(screenshotZip: ScreenshotZipEntity) {
        screenshotZipDao.insertZipObj(screenshotZip)
    }

    fun deleteScreenshotZip(screenshotZip: ScreenshotZipEntity) {
        screenshotZipDao.deleteZipObjSync(screenshotZip)
    }

    suspend fun getZipCount(): Int {
        return screenshotZipDao.getZipCount()
    }

    suspend fun getZipsToUpload(): List<ScreenshotZipEntity> {
        return screenshotZipDao.getAllZipObjs()
    }

    suspend fun getUploadDaily(id: String): UploadDailyEntity {
        return uploadDailyDao.get(id)
    }

    suspend fun getUploadTotal(): UploadHistoryEntity {
        return uploadHistoryDao.get()
    }

    suspend fun upsertDaily(uploadDaily: UploadDailyEntity) {
        uploadDailyDao.upsert(uploadDaily)
    }

    suspend fun upsertHistory(uploadHistory: UploadHistoryEntity) {
        uploadHistoryDao.upsert(uploadHistory)
    }

    suspend fun getScreenshotCount(): Int {
        return screenshotDao.getOcrCompleteOrRestrictedCount()
    }

    suspend fun getScreenshotsToOcr(limit: Int): List<ScreenshotEntity> {
        return screenshotDao.getAllScreenshotsSortedByDateWhereOcrIsNotComplete(
            limit = limit,
            offset = 0
        )
    }

    suspend fun getAllScreenshotsSortedByDateWhereOcrIsComplete(limit: Int, offset: Int): List<ScreenshotEntity> {
        return screenshotDao.getAllScreenshotsSortedByDateWhereOcrIsComplete(
            limit = limit,
            offset = offset
        )
    }

    suspend fun getAllSessionsWithoutAppSegments(): List<String> {
        return screenshotDao.getAllSessionsWithoutAppSegments()
    }

    suspend fun getLogs(limit: Int, offset: Int): List<LogEventEntity> {
        return logEventDao.getLogsFrom(
            limit = limit,
            offset = offset
        )
    }

    suspend fun logCount(): Int {
        return logEventDao.logCount()
    }

    private suspend fun getScreenshotsBySessionId(sessionId: String): List<ScreenshotEntity> {
        return screenshotDao.getScreenshotsBySessionId(sessionId)
    }

    fun getPaginateScreenshotsById(start: Long, lastId: Int?, limit: Int): List<ScreenshotEntity> {
        // Instead of using OFFSET, use a WHERE clause with the last ID
        return if (lastId == null) {
            // First query - get the first batch
            screenshotDao.getScreenshotsBatchByTime(start, limit)
        } else {
            // Subsequent queries - get records with ID > lastId
            screenshotDao.getScreenshotsBatchByTimeAndId(start, lastId, limit)
        }
    }
}