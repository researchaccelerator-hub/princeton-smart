package com.screenlake.recorder.authentication

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.Amplify
import com.screenlake.data.model.EmailPasswordData
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
import kotlinx.coroutines.CompletableDeferred

@Singleton
class CloudAuthentication @Inject constructor(
    private val amplifyRepository: AmplifyRepository
) {

    private val TAG = "CloudAuthentication"

    /**
     * Checks if the current Amplify auth session has valid AWS credentials.
     * If not (e.g. SIGNED_OUT_USER_POOLS_TOKENS_INVALID after long inactivity),
     * re-authenticates using stored credentials so background workers can upload.
     */
    suspend fun ensureValidSession(context: WeakReference<Context>): Boolean {
        return try {
            val sessionDeferred = CompletableDeferred<AWSCognitoAuthSession?>()
            Amplify.Auth.fetchAuthSession(
                { sessionDeferred.complete(it as? AWSCognitoAuthSession) },
                { sessionDeferred.complete(null) }
            )
            val session = sessionDeferred.await()
            if (session?.awsCredentials?.value != null) return true

            Timber.tag(TAG).w("Auth session has no credentials; re-authenticating with stored credentials")
            signInSuspend(context)
        } catch (e: Exception) {
            Timber.tag(TAG).e("Session check failed: ${e.message}")
            false
        }
    }

    /**
     * Signs in using stored credentials and waits for the result.
     * Returns true if the sign-in completed successfully.
     */
    suspend fun signInSuspend(context: WeakReference<Context>): Boolean {
        val creds = getEncryptedCredentials(context)
        if (creds.email.isBlank() || creds.password.isBlank()) {
            Timber.tag(TAG).e("No stored credentials available for re-authentication")
            return false
        }
        val signInDeferred = CompletableDeferred<Boolean>()
        Amplify.Auth.signIn(creds.email, creds.password,
            { result ->
                Timber.tag(TAG).d("Re-authentication result: isSignInComplete=${result.isSignInComplete}")
                signInDeferred.complete(result.isSignInComplete)
            },
            { error ->
                Timber.tag(TAG).e("Re-authentication failed: ${error.message}")
                signInDeferred.complete(false)
            }
        )
        return signInDeferred.await()
    }

    /**
     * Fetches the current authentication session.
     */
    fun fetchCurrentAuthSession() {
        amplifyRepository.fetchAuthSession(
            onSuccess = { authSession ->
                val session = authSession as AWSCognitoAuthSession
                if (session.isSignedIn) {
                    Timber.d("User is signed in")
                } else if (session.awsCredentials.error is AuthException.SignedOutException || session.userSub.error is AuthException.SignedOutException) {
                    Timber.d("User is signed out")
                } else if (session.awsCredentials.error == null) {
                    Timber.d("Session is valid")
                }
            },
            onError = { error ->
                Timber.e("Fetch auth session error: $error")
            }
        )
    }

    /**
     * Automatically signs in the user.
     *
     * @param context The context.
     */
    fun autoSignIn(context: WeakReference<Context>) {
        val creds = getEncryptedCredentials(context)
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

    /**
     * Retrieves the encrypted shared preferences.
     *
     * @param context The context.
     * @return The encrypted shared preferences.
     */
    private fun getEncryptedSharedPreference(context: WeakReference<Context>): SharedPreferences? {
        val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
        val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

        return context.get()?.let {
            EncryptedSharedPreferences.create(
                "shared_preferences_filename",
                masterKeyAlias,
                it,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    /**
     * Retrieves the encrypted credentials.
     *
     * @param context The context.
     * @return The email and password data.
     */
    private fun getEncryptedCredentials(context: WeakReference<Context>): EmailPasswordData {
        val sharedPreferences = getEncryptedSharedPreference(context)
        return if (sharedPreferences != null) {
            val email = sharedPreferences.getString("email", "") ?: ""
            val password = sharedPreferences.getString("password", "") ?: ""
            EmailPasswordData(email, password)
        } else {
            EmailPasswordData("", "")
        }
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