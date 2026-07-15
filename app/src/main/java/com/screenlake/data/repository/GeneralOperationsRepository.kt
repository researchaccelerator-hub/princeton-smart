package com.screenlake.data.repository

import android.content.Context
import android.content.SharedPreferences
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

    suspend fun buildCurrentSession(localFPS: Double, stopReason: String = "UNKNOWN") {
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

        currentSession.stopReason = stopReason

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

    fun incrementCredentialFailureCount(): Int {
        val prefs = context.getSharedPreferences(CREDENTIAL_RECOVERY_PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(CREDENTIAL_FAILURE_COUNT_KEY, 0) + 1
        prefs.edit().putInt(CREDENTIAL_FAILURE_COUNT_KEY, next).apply()
        return next
    }

    fun resetCredentialFailureCount() {
        val prefs = context.getSharedPreferences(CREDENTIAL_RECOVERY_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(CREDENTIAL_FAILURE_COUNT_KEY, 0).apply()
    }

    fun getCredentialFailureCount(): Int {
        val prefs = context.getSharedPreferences(CREDENTIAL_RECOVERY_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(CREDENTIAL_FAILURE_COUNT_KEY, 0)
    }

    /**
     * Forces a sign-out and clears the local user record after credential recovery has
     * permanently failed (the consecutive-failure notification threshold), so the app routes to
     * the login screen the next time it's opened. Pending screenshot/upload data is deliberately
     * left in place here -- it's only wiped later, in [reconcilePendingReauthUser], if a
     * DIFFERENT user ends up logging back in.
     *
     * Also records the outgoing user's invite code (emailHash) and tenant/panel assignment, since
     * a fresh login only ever creates a bare UserEntity with just an email (LoginFragment does not
     * fetch the rest of the profile from the backend) -- without this, the same participant
     * reconnecting would lose their invite code and have to be re-prompted, and until then any
     * upload would go out under a missing/placeholder identifier.
     *
     * Resets the failure counter so this doesn't re-fire (re-signing-out an already-signed-out
     * session, re-showing the same notification) on every subsequent attempt while the user has
     * not yet re-logged in -- it escalates again only after another full run of failures.
     */
    suspend fun forceSignOutForCredentialExpiry(currentUser: UserEntity?) {
        recordPendingReauthUser(currentUser)
        cloudAuthentication.signOut(MainActivity.isLoggedOut)
        MainActivity.isLoggedIn.postValue(false)
        deleteUser()
        resetCredentialFailureCount()
    }

    /**
     * Called after a fresh login completes, before the new [UserEntity] is inserted. If a prior
     * forced sign-out (see [forceSignOutForCredentialExpiry]) is pending reconciliation, compares
     * the newly signed-in user's email against the one that was signed out:
     * - Same user: restores their invite code (emailHash) and tenant/panel assignment onto [user]
     *   in place (a fresh login only populates email), so they aren't silently uploading under a
     *   missing/placeholder identifier and don't have to re-enter their invite code. Pending
     *   research data is left untouched.
     * - Different user: wipes the previous participant's pending research data, since it may
     *   contain sensitive screen captures, and leaves [user] as-is -- a genuinely new participant
     *   is expected to (re-)enter their own invite code through the normal flow.
     */
    suspend fun reconcilePendingReauthUser(user: UserEntity) {
        val prefs = context.getSharedPreferences(CREDENTIAL_RECOVERY_PREFS, Context.MODE_PRIVATE)
        val pendingUser = prefs.getString(PENDING_REAUTH_USER_KEY, null) ?: return

        val newEmail = user.email
        if (newEmail.isNullOrBlank()) {
            Timber.tag("ReconcilePendingReauthUser").w(
                "Cannot reconcile pending reauth user: incoming user has no email. Leaving pending research data and marker untouched."
            )
            return
        }

        if (pendingUser == normalizeEmail(newEmail)) {
            user.emailHash = prefs.getString(PENDING_REAUTH_EMAIL_HASH_KEY, null)
            user.tenantId = prefs.getString(PENDING_REAUTH_TENANT_ID_KEY, null) ?: user.tenantId
            user.tenantName = prefs.getString(PENDING_REAUTH_TENANT_NAME_KEY, null) ?: user.tenantName
            user.panelId = prefs.getString(PENDING_REAUTH_PANEL_ID_KEY, null) ?: user.panelId
            user.panelName = prefs.getString(PENDING_REAUTH_PANEL_NAME_KEY, null) ?: user.panelName
        } else {
            Timber.tag("ReconcilePendingReauthUser").w(
                "Different user logged in after a forced credential-expiry sign-out; wiping pending research data."
            )
            wipePendingResearchData()
        }

        clearPendingReauthMarkers(prefs)
    }

    private fun recordPendingReauthUser(user: UserEntity?) {
        val email = user?.email
        if (email.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(CREDENTIAL_RECOVERY_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PENDING_REAUTH_USER_KEY, normalizeEmail(email))
            .putString(PENDING_REAUTH_EMAIL_HASH_KEY, user.emailHash)
            .putString(PENDING_REAUTH_TENANT_ID_KEY, user.tenantId)
            .putString(PENDING_REAUTH_TENANT_NAME_KEY, user.tenantName)
            .putString(PENDING_REAUTH_PANEL_ID_KEY, user.panelId)
            .putString(PENDING_REAUTH_PANEL_NAME_KEY, user.panelName)
            .apply()
    }

    private fun clearPendingReauthMarkers(prefs: SharedPreferences) {
        prefs.edit()
            .remove(PENDING_REAUTH_USER_KEY)
            .remove(PENDING_REAUTH_EMAIL_HASH_KEY)
            .remove(PENDING_REAUTH_TENANT_ID_KEY)
            .remove(PENDING_REAUTH_TENANT_NAME_KEY)
            .remove(PENDING_REAUTH_PANEL_ID_KEY)
            .remove(PENDING_REAUTH_PANEL_NAME_KEY)
            .apply()
    }

    // Emails are compared case- and whitespace-insensitively so the same participant retyping
    // their email with different casing on re-login isn't misidentified as a different user,
    // which would otherwise wipe their own legitimate pending research data.
    private fun normalizeEmail(email: String) = email.trim().lowercase()

    private suspend fun wipePendingResearchData() {
        val path = context.filesDir?.path

        if (path != null) {
            File(path).walk().filter {
                it.name.endsWith("jpg")
                        || it.name.endsWith("zip")
                        || it.name.endsWith("csv")
                        || (it.name.endsWith("json") && it.name.contains("screenshot_data"))
            }.forEach {
                it.delete()
                Timber.tag("ReconcilePendingReauthUser").d("Deleted file ${it.name} from phone.")
            }
        }

        deleteAllScreenshot()
        deleteAllScreenshotZip()
        deleteAllPanels()
        deleteAllSessions()
        deleteAllAppSegments()
        deleteAllAccessibilityEvents()
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

    companion object {
        private const val CREDENTIAL_RECOVERY_PREFS = "credential_recovery_prefs"
        private const val CREDENTIAL_FAILURE_COUNT_KEY = "consecutive_credential_failures"
        private const val PENDING_REAUTH_USER_KEY = "pending_reauth_user_email"
        private const val PENDING_REAUTH_EMAIL_HASH_KEY = "pending_reauth_email_hash"
        private const val PENDING_REAUTH_TENANT_ID_KEY = "pending_reauth_tenant_id"
        private const val PENDING_REAUTH_TENANT_NAME_KEY = "pending_reauth_tenant_name"
        private const val PENDING_REAUTH_PANEL_ID_KEY = "pending_reauth_panel_id"
        private const val PENDING_REAUTH_PANEL_NAME_KEY = "pending_reauth_panel_name"
    }
}