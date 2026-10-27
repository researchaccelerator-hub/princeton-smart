package com.screenlake.recorder.services

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings

/**
 * What happens when uploads keep failing because the participant's sign-in cannot be renewed.
 *
 * Each upload run that ends in [UploadResult.AUTHENTICATION_FAILURE] (silent re-login already
 * tried and failed) counts once. At [ConstantSettings.CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD]
 * consecutive failures, recovery begins, once:
 * - a persisted "recovery pending" flag stops further upload attempts until the participant signs in;
 * - a notification opens the login screen, or, if notifications are blocked, the login screen
 *   shows the message on its next visit;
 * - the participant is signed out without deleting data
 *   ([GeneralOperationsRepository.forceSignOutForCredentialExpiry]): signing in again with the
 *   same account restores their invite code and panel and resumes uploads; a different account
 *   wipes the previous participant's pending data.
 */
class AuthRecoveryCoordinator(
    private val context: Context,
    private val repository: GeneralOperationsRepository
) {
    fun isPending(): Boolean = isPending(context)

    /** @return true when recovery has begun and uploads should stop until the participant signs in. */
    suspend fun onAuthenticationFailure(user: UserEntity?, reason: String): Boolean {
        val failures = repository.incrementCredentialFailureCount()
        repository.saveLog(
            ConstantSettings.UPLOAD_CREDENTIAL_FAILURE,
            "reason=$reason;consecutiveFailures=$failures"
        )
        if (failures < ConstantSettings.CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD) return false
        begin(user, reason)
        return true
    }

    private suspend fun begin(user: UserEntity?, reason: String) {
        if (isPending()) return
        preferences(context).edit().putBoolean(PREF_PENDING, true).apply()
        repository.saveLog("AUTH_RECOVERY_STARTED", reason)

        val notifications = NotificationHelper(context)
        notifications.createNotificationChannel()
        if (notifications.showAuthenticationRecoveryNotification()) {
            repository.saveLog("AUTH_RECOVERY_NOTIFICATION_SHOWN", "")
        } else {
            preferences(context).edit().putBoolean(PREF_SHOW_IN_APP, true).apply()
        }

        repository.forceSignOutForCredentialExpiry(user)
    }

    companion object {
        private const val PREFS_NAME = "auth_recovery"
        private const val PREF_PENDING = "auth_recovery_pending"
        private const val PREF_SHOW_IN_APP = "auth_recovery_show_in_app"

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun isPending(context: Context): Boolean = preferences(context).getBoolean(PREF_PENDING, false)

        /** True once, for the login screen, when the recovery notification could not be shown. */
        fun consumeInAppMessage(context: Context): Boolean {
            val prefs = preferences(context)
            val show = prefs.getBoolean(PREF_SHOW_IN_APP, false)
            if (show) prefs.edit().remove(PREF_SHOW_IN_APP).apply()
            return show
        }

        /**
         * The participant signed in again. Uploads may resume.
         * @return true if recovery was pending.
         */
        fun onLoginSucceeded(context: Context): Boolean {
            val wasPending = isPending(context)
            preferences(context).edit().remove(PREF_PENDING).remove(PREF_SHOW_IN_APP).apply()
            NotificationHelper(context).cancelNotification(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID)
            return wasPending
        }
    }
}
