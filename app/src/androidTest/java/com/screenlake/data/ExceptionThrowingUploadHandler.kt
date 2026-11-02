package com.screenlake.data

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.UploadHandler
import com.screenlake.recorder.services.UploadResult
import java.io.File

class ExceptionThrowingUploadHandler() : UploadHandler {

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ): UploadResult {
        // RealUploadHandler catches unexpected upload exceptions and reports them as a
        // non-retryable failure for that file.
        return UploadResult.CONFIGURATION_FAILURE
    }

    override suspend fun isNetworkConnected(): Boolean {
        return true
    }
}