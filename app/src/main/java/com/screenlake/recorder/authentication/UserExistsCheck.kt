package com.screenlake.recorder.authentication

import com.google.gson.GsonBuilder
import com.screenlake.data.model.UserExistItem
import com.screenlake.recorder.constants.ResearchConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * Optional check, before sign-up, that an email is not already registered with the study's own
 * service (see [ResearchConfig.USER_EXISTS_URL]). Off when no endpoint is set. Cognito rejects a
 * duplicate sign-up either way; this only lets registration say so before the password step.
 */
class UserExistsCheck(
    private val endpoint: String = ResearchConfig.USER_EXISTS_URL,
    private val client: OkHttpClient = OkHttpClient()
) {

    val isEnabled: Boolean get() = endpoint.isNotBlank()

    /**
     * True only when the endpoint answers that [email] is registered. False when the check is
     * off, the endpoint cannot be reached, or the answer is not clear, so sign-up continues.
     */
    suspend fun exists(email: String): Boolean {
        if (!isEnabled) return false
        val url = endpoint.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("email", email)?.build()
        if (url == null) {
            Timber.w("USER_EXISTS_URL is not a valid URL; skipping the check.")
            return false
        }
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    response.isSuccessful && isTaken(response.body?.string())
                }
            } catch (e: IOException) {
                Timber.w(e, "User-exists check failed; continuing sign-up.")
                false
            }
        }
    }

    companion object {
        private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

        /** Reads {"items":[{"message":true}]}; anything else counts as not taken. */
        internal fun isTaken(json: String?): Boolean = runCatching {
            gson.fromJson(json, UserExistItem::class.java)?.items?.firstOrNull()?.message == true
        }.getOrDefault(false)
    }
}
