package com.screenlake.recorder.authentication

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.screenlake.data.model.EmailPasswordData
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes credentials persisted in Android Keystore-backed encrypted shared
 * preferences.
 *
 * The sole owner of the "encrypted_prefs" file and its backing MasterKey/Tink keyset -- every
 * reader and writer of stored credentials must go through this class. [CloudAuthentication] and
 * [AuthRecoveryManager] both read via this singleton; registration writes via this singleton too
 * (see [storeCredentials]), so there is exactly one file name and one keyset in play.
 *
 * The underlying [SharedPreferences] instance is created at most once and cached: the first
 * ever call to `EncryptedSharedPreferences.create()` on a device also creates the Keystore
 * master key and Tink keyset, and calling it concurrently from multiple threads on that first
 * call is known to corrupt the keyset (Tink then fails to parse it back, surfacing as
 * `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`).
 * Serializing creation behind a lock and reusing the cached instance for every subsequent call
 * eliminates that race.
 *
 * Devices whose keyset was already corrupted (for example, by the race above on an older build)
 * keep failing to create or decrypt the file. In that case the file is deleted and recreated, and
 * the credentials are reported as missing so silent reauth falls back to asking the participant
 * to sign in again.
 */
@Singleton
class EncryptedCredentialsStore @Inject constructor() {

    @Volatile
    private var cachedPreferences: SharedPreferences? = null

    /**
     * Retrieves the encrypted credentials.
     *
     * @param context The context.
     * @return The email and password data.
     */
    fun getStoredCredentials(context: WeakReference<Context>): EmailPasswordData {
        val sharedPreferences = getEncryptedSharedPreference(context)
            ?: return EmailPasswordData("", "")
        return try {
            val email = sharedPreferences.getString("email", "") ?: ""
            val password = sharedPreferences.getString("password", "") ?: ""
            EmailPasswordData(email, password)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Stored credentials could not be decrypted; resetting them")
            resetPreferences(context)
            EmailPasswordData("", "")
        }
    }

    /**
     * Persists the given credentials for later retrieval by [getStoredCredentials].
     *
     * @param context The context.
     * @param email The email to store.
     * @param password The password to store.
     */
    fun storeCredentials(context: WeakReference<Context>, email: String, password: String) {
        try {
            writeCredentials(getEncryptedSharedPreference(context) ?: return, email, password)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Stored credentials could not be updated; recreating them")
            val recoveredPreferences = resetPreferences(context) ?: return
            try {
                writeCredentials(recoveredPreferences, email, password)
            } catch (retryError: Exception) {
                Timber.tag(TAG).e(retryError, "Could not store credentials after recreating storage")
            }
        }
    }

    private fun writeCredentials(preferences: SharedPreferences, email: String, password: String) {
        preferences.edit()
            .putString("email", email)
            .putString("password", password)
            .apply()
    }

    /**
     * Retrieves the encrypted shared preferences, creating (and caching) them on first use.
     *
     * @param context The context.
     * @return The encrypted shared preferences.
     */
    private fun getEncryptedSharedPreference(context: WeakReference<Context>): SharedPreferences? {
        cachedPreferences?.let { return it }

        synchronized(lock) {
            cachedPreferences?.let { return it }

            val appContext = context.get()?.applicationContext ?: return null
            return try {
                createPreferences(appContext).also { cachedPreferences = it }
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Encrypted credential storage is invalid; recreating it")
                resetPreferences(appContext)
            }
        }
    }

    private fun resetPreferences(context: WeakReference<Context>): SharedPreferences? {
        val appContext = context.get()?.applicationContext ?: return null
        return synchronized(lock) { resetPreferences(appContext) }
    }

    /**
     * Deletes the encrypted file, which also holds its Tink keysets, and creates a fresh one.
     * Callers must hold [lock].
     *
     * @param appContext The application context.
     * @return The recreated preferences, or null if they still cannot be created.
     */
    private fun resetPreferences(appContext: Context): SharedPreferences? {
        cachedPreferences = null
        appContext.deleteSharedPreferences(FILE_NAME)
        return try {
            createPreferences(appContext).also { cachedPreferences = it }
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not recreate encrypted credential storage")
            null
        }
    }

    private fun createPreferences(appContext: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            FILE_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    companion object {
        private const val TAG = "EncryptedCredentialsStore"
        private const val FILE_NAME = "encrypted_prefs"
        private val lock = Any()
    }
}
