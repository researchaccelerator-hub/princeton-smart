package com.screenlake.data

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.UploadHandler
import com.screenlake.recorder.upload.CredentialExpiredException
import java.io.File

class CredentialExpiredUploadHandler : UploadHandler {

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ) {
        throw CredentialExpiredException("Simulated credential expiry")
    }

    override suspend fun isNetworkConnected(): Boolean {
        return true
    }
}
