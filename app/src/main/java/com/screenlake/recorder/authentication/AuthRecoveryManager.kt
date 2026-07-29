package com.screenlake.recorder.authentication

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRecoveryManager @Inject constructor(
    private val encryptedCredentialsStore: EncryptedCredentialsStore,
    private val cognitoSessionAuthenticator: CognitoSessionAuthenticator
) {
    private val mutex = Mutex()

    suspend fun attemptSilentReauth(context: Context): Boolean = mutex.withLock {
        val creds = encryptedCredentialsStore.getStoredCredentials(WeakReference(context))
        if (creds.email.isBlank() || creds.password.isBlank()) {
            Timber.tag(TAG).w("No stored credentials available for silent reauth.")
            return@withLock false
        }

        val mobileClientOk = cognitoSessionAuthenticator.signInAwsMobileClient(creds.email, creds.password)
        val amplifyOk = cognitoSessionAuthenticator.signInAmplify(creds.email, creds.password)

        if (!mobileClientOk) Timber.tag(TAG).w("AWSMobileClient re-auth failed.")
        if (!amplifyOk) Timber.tag(TAG).w("Amplify re-auth failed.")

        mobileClientOk && amplifyOk
    }

    companion object {
        private val TAG = AuthRecoveryManager::class.java.simpleName
    }
}
