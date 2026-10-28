package com.screenlake.recorder.services

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import java.io.File

interface UploadHandler {
    /** Loads AWS credentials once before an upload batch begins. */
    suspend fun prepareUploadSession(): UploadResult = UploadResult.SUCCESS

    suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean = false,
        testContext: Context? = null
    ): UploadResult

    suspend fun isNetworkConnected(): Boolean
}

/**
 * The outcome of an upload, so the worker can tell a failure worth retrying from one that needs
 * the participant to sign in again. A file is deleted only after [SUCCESS].
 */
enum class UploadResult {
    SUCCESS,
    /** Offline, timeout, throttling or a server error: keep the file and retry later. */
    TRANSIENT_FAILURE,
    /** AWS rejected the identity even after a silent re-login. */
    AUTHENTICATION_FAILURE,
    /** Retrying will not help yet (bad setup, missing invite code or panel). */
    CONFIGURATION_FAILURE
}
