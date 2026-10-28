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

    suspend fun attemptSilentReauth(context: Context): Boolean =
        attemptSilentReauthWithOutcome(context) == SignInOutcome.SIGNED_IN

    /** Like [attemptSilentReauth], but says whether a failure was a rejection or Cognito being unreachable. */
    suspend fun attemptSilentReauthWithOutcome(context: Context): SignInOutcome = mutex.withLock {
        val creds = encryptedCredentialsStore.getStoredCredentials(WeakReference(context))
        if (creds.email.isBlank() || creds.password.isBlank()) {
            Timber.tag(TAG).w("No stored credentials available for silent reauth.")
            return@withLock SignInOutcome.REJECTED
        }

        val mobileClient = cognitoSessionAuthenticator.signInAwsMobileClient(creds.email, creds.password)
        val amplify = cognitoSessionAuthenticator.signInAmplify(creds.email, creds.password)

        if (mobileClient != SignInOutcome.SIGNED_IN) Timber.tag(TAG).w("AWSMobileClient re-auth failed: $mobileClient.")
        if (amplify != SignInOutcome.SIGNED_IN) Timber.tag(TAG).w("Amplify re-auth failed: $amplify.")

        SignInOutcome.combine(mobileClient, amplify)
    }

    companion object {
        private val TAG = AuthRecoveryManager::class.java.simpleName
    }
}
