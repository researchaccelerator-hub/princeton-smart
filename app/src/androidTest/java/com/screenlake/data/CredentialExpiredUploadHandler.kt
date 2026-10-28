package com.screenlake.data

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.UploadHandler
import com.screenlake.recorder.services.UploadResult
import java.io.File

class CredentialExpiredUploadHandler : UploadHandler {

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ): UploadResult {
        // RealUploadHandler reports an unrecoverable credential failure as a result, not an exception.
        return UploadResult.AUTHENTICATION_FAILURE
    }

    override suspend fun isNetworkConnected(): Boolean {
        return true
    }
}
