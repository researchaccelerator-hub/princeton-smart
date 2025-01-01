package com.screenlake.recorder.services

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import java.io.File

interface UploadHandler {
    suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean = false,
        testContext: Context? = null
    )

    suspend fun isNetworkConnected(): Boolean
}