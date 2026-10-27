package com.screenlake.recorder.authentication

import com.amazonaws.mobile.client.AWSMobileClient
import com.amplifyframework.auth.AuthException
import com.screenlake.recorder.upload.UploadFailureClassifier
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.results.SignInResult
import com.amazonaws.mobile.client.results.SignInState
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [CognitoSessionAuthenticator] backed by the live AWS Mobile Client and
 * Amplify Auth SDKs. Thin adapter with no branching logic of its own — it
 * just bridges each SDK's callback-based sign-in call into a suspend boolean.
 */
@Singleton
class RealCognitoSessionAuthenticator @Inject constructor() : CognitoSessionAuthenticator {

    override suspend fun signInAwsMobileClient(email: String, password: String): SignInOutcome =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var outcome = SignInOutcome.UNAVAILABLE
            try {
                AWSMobileClient.getInstance().signIn(email, password, null, object : Callback<SignInResult> {
                    override fun onResult(result: SignInResult?) {
                        outcome = if (result?.signInState == SignInState.DONE) SignInOutcome.SIGNED_IN else SignInOutcome.REJECTED
                        latch.countDown()
                    }

                    override fun onError(e: Exception) {
                        Timber.tag(TAG).w("AWSMobileClient re-auth sign-in failed: ${e.message}")
                        outcome = if (UploadFailureClassifier.isAuthenticationFailure(e)) SignInOutcome.REJECTED else SignInOutcome.UNAVAILABLE
                        latch.countDown()
                    }
                })
                latch.await(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Timber.tag(TAG).w("AWSMobileClient re-auth sign-in threw: ${e.message}")
            }
            outcome
        }

    override suspend fun signInAmplify(email: String, password: String): SignInOutcome =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var outcome = SignInOutcome.UNAVAILABLE
            try {
                Amplify.Auth.signIn(email, password,
                    { result ->
                        outcome = if (result.isSignInComplete) SignInOutcome.SIGNED_IN else SignInOutcome.REJECTED
                        latch.countDown()
                    },
                    { error ->
                        Timber.tag(TAG).w("Amplify re-auth sign-in failed: ${error.message}")
                        outcome = if (isRejection(error)) SignInOutcome.REJECTED else SignInOutcome.UNAVAILABLE
                        latch.countDown()
                    }
                )
                latch.await(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Amplify re-auth sign-in threw: ${e.message}")
            }
            outcome
        }

    /** Errors that mean the stored credentials no longer work, as opposed to Cognito being unreachable. */
    private fun isRejection(error: AuthException): Boolean =
        error is AuthException.NotAuthorizedException ||
            error is AuthException.UserNotFoundException ||
            error is AuthException.UserNotConfirmedException ||
            error is AuthException.PasswordResetRequiredException ||
            error is AuthException.InvalidPasswordException

    companion object {
        private val TAG = RealCognitoSessionAuthenticator::class.java.simpleName
    }
}
