package com.screenlake.recorder.authentication

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.screenlake.data.model.EmailPasswordData
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Reads credentials persisted in Android Keystore-backed encrypted shared preferences.
 *
 * Extracted from [CloudAuthentication] so other consumers (e.g. auth recovery) can read the
 * same stored credentials without duplicating the EncryptedSharedPreferences/MasterKeys setup.
 */
class EncryptedCredentialsStore @Inject constructor() {

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
                "encrypted_prefs",
                masterKeyAlias,
                it,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
