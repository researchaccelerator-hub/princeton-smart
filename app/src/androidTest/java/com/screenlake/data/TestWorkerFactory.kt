package com.screenlake.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.UploadWorker
import com.screenlake.recorder.services.ZipFileWorker

class TestWorkerFactory(
    private val generalOperationsRepository: GeneralOperationsRepository,
    private val isNetworkConnected: Boolean = true
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            ZipFileWorker::class.java.name ->
                ZipFileWorker(appContext, workerParameters, generalOperationsRepository)
            UploadWorker::class.java.name ->
                UploadWorker(
                    appContext,
                    workerParameters,
                    uploadHandler = TestUploadHandler(generalOperationsRepository, isNetworkConnected),
                    generalOperationsRepository,
                )
            ExceptionThrowingUploadHandler::class.java.name ->
                UploadWorker(
                    appContext,
                    workerParameters,
                    uploadHandler = ExceptionThrowingUploadHandler(),
                    generalOperationsRepository,
                )
            else -> null
        }
    }
}
