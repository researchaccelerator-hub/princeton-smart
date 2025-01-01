package com.screenlake.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.UploadWorker

class TestWorkerFactoryException(
    private val generalOperationsRepository: GeneralOperationsRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            UploadWorker::class.java.name ->
                UploadWorker(
                    appContext,
                    workerParameters,
                    uploadHandler = ExceptionThrowingUploadHandler(),
                    generalOperationsRepository
                )
            else -> null
        }
    }
}
