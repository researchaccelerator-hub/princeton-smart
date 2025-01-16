package com.screenlake.data

import android.content.Context
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.UploadHandler
import com.screenlake.recorder.services.UploadWorker
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

// TestUploadHandler implementation
class TestUploadHandler(
    private val generalOperationsRepository: GeneralOperationsRepository,
    private val isNetworkConnected: Boolean
) : UploadHandler {

    companion object {
        private const val TAG = "TestUploadHandler"
    }

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ) {
        if (isNetworkConnected) {
            // Simulate a successful upload
            Timber.tag(TAG).d("Simulating upload of file ${file.name}")

            // Simulate upload delay
            delay(1000)

            file.delete()
            entryId?.let { generalOperationsRepository.deleteZip(it) }
            UploadWorker.uploadFeedback.postValue("Upload succeeded -> Simulated path")
            Timber.tag(TAG).d("Upload simulated as successful")
        }else{

        }
    }

    override suspend fun isNetworkConnected(): Boolean {
        return isNetworkConnected
    }
}