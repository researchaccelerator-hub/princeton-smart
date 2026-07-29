package com.screenlake.recorder.authentication

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.screenlake.data.model.EmailPasswordData
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
        return if (sharedPreferences != null) {
            val email = sharedPreferences.getString("email", "") ?: ""
            val password = sharedPreferences.getString("password", "") ?: ""
            EmailPasswordData(email, password)
        } else {
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
        getEncryptedSharedPreference(context)?.edit()?.apply {
            putString("email", email)
            putString("password", password)
        }?.apply()
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

            val ctx = context.get() ?: return null
            val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
            val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

            val prefs = EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                ctx.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cachedPreferences = prefs
            return prefs
        }
    }

    companion object {
        private const val FILE_NAME = "encrypted_prefs"
        private val lock = Any()
    }
}
