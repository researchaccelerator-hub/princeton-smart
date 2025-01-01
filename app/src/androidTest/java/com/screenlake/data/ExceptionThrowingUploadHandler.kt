package com.screenlake.data

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.UploadHandler
import java.io.File

class ExceptionThrowingUploadHandler() : UploadHandler {

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ) {
        // Simulate an exception during upload
        throw Exception("Simulated upload exception")
    }

    override suspend fun isNetworkConnected(): Boolean {
        return true
    }
}