package com.screenlake.recorder.authentication

import com.amazonaws.mobile.client.AWSMobileClient
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

    override suspend fun signInAwsMobileClient(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var success = false
            try {
                AWSMobileClient.getInstance().signIn(email, password, null, object : Callback<SignInResult> {
                    override fun onResult(result: SignInResult?) {
                        success = result?.signInState == SignInState.DONE
                        latch.countDown()
                    }

                    override fun onError(e: Exception) {
                        Timber.tag(TAG).w("AWSMobileClient re-auth sign-in failed: ${e.message}")
                        latch.countDown()
                    }
                })
                latch.await(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Timber.tag(TAG).w("AWSMobileClient re-auth sign-in threw: ${e.message}")
            }
            success
        }

    override suspend fun signInAmplify(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
            var success = false
            try {
                Amplify.Auth.signIn(email, password,
                    { result ->
                        success = result.isSignInComplete
                        latch.countDown()
                    },
                    { error ->
                        Timber.tag(TAG).w("Amplify re-auth sign-in failed: ${error.message}")
                        latch.countDown()
                    }
                )
                latch.await(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Amplify re-auth sign-in threw: ${e.message}")
            }
            success
        }

    companion object {
        private val TAG = RealCognitoSessionAuthenticator::class.java.simpleName
    }
}
