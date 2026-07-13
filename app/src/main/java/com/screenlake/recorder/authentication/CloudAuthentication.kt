package com.screenlake.recorder.authentication

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.Amplify
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.services.util.CognitoErrorHelper
import com.screenlake.ui.fragments.onboarding.RegisterConfirmPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudAuthentication @Inject constructor(
    private val amplifyRepository: AmplifyRepository,
    private val encryptedCredentialsStore: EncryptedCredentialsStore
) {

    /**
     * Fetches the current authentication session.
     *
     * @param onBadSession Invoked when the session is signed out, errored, or otherwise
     * not usable — callers can use this to trigger recovery. Defaults to a no-op so existing
     * callers that only want the logging behavior are unaffected.
     */
    fun fetchCurrentAuthSession(onBadSession: () -> Unit = {}) {
        amplifyRepository.fetchAuthSession(
            onSuccess = { authSession ->
                val session = authSession as AWSCognitoAuthSession
                if (session.isSignedIn) {
                    Timber.d("User is signed in")
                } else if (session.awsCredentials.error is AuthException.SignedOutException || session.userSub.error is AuthException.SignedOutException) {
                    Timber.d("User is signed out")
                    onBadSession()
                } else if (session.awsCredentials.error == null) {
                    Timber.d("Session is valid")
                } else {
                    Timber.w("Session is not signed in and not a recognized signed-out state: ${session.awsCredentials.error}")
                    onBadSession()
                }
            },
            onError = { error ->
                Timber.e("Fetch auth session error: $error")
                onBadSession()
            }
        )
    }

    /**
     * Automatically signs in the user.
     *
     * @param context The context.
     */
    fun autoSignIn(context: WeakReference<Context>) {
        val creds = encryptedCredentialsStore.getStoredCredentials(context)
        signInAsync(creds.email, creds.password)
    }

    /**
     * Clears the user authentication data.
     */
    fun clearUserAuth() {
        // Implement the logic to clear user authentication data
    }

    /**
     * Signs out the user.
     *
     * @param isLoggedOut The live data to update the logged-out status.
     */
    fun signOut(isLoggedOut: MutableLiveData<Boolean>) {
        Amplify.Auth.signOut(
            { isLoggedOut.postValue(true) },
            { error -> Timber.e("Sign out error: $error") }
        )
    }

    /**
     * Signs up the user with the provided password.
     *
     * @param password The password.
     */
    fun signUp(password: String, context: Context) {
        val options = AuthSignUpOptions.builder()
            .userAttribute(AuthUserAttributeKey.email(), amplifyRepository.email)
            .build()

        Amplify.Auth.signUp(amplifyRepository.email, password, options,
            { result ->
                Timber.d("Sign up result: $result")
                RegisterConfirmPassword.isRegisteredIn.postValue(true)
             },
            { error ->
                RegisterConfirmPassword.isRegisteredIn.postValue(false)
                RegisterConfirmPassword.loginErrorMsg.postValue(error.message)
                val errorMessage = CognitoErrorHelper.getReadableMessage(error)
                runOnUiThread {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
                Timber.e("Sign up error: $error")
            }
        )
    }

    companion object {
        /**
         * Signs in the user with the provided email and password.
         *
         * @param email The email.
         * @param password The password.
         */
        fun signIn(email: String, password: String) {
            Amplify.Auth.signIn(email, password,
                { result -> Timber.d("Sign in result: $result") },
                { error -> Timber.e("Sign in error: $error") }
            )
        }

        /**
         * Signs in the user asynchronously with the provided email and password.
         *
         * @param email The email.
         * @param password The password.
         */
        fun signInAsync(email: String, password: String) {
            Amplify.Auth.signIn(email, password,
                { result -> Timber.d("Sign in result: $result") },
                { error -> Timber.e("Sign in error: $error") }
            )
        }
    }
}