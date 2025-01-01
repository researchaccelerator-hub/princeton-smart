package com.screenlake.recorder.screenshot

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.util.ScreenshotData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCollectorService : JobService() {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var screenCollector: ScreenCollector

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private var wifiFirstSession = false

    private val handler = CoroutineExceptionHandler { _, exception ->
        Timber.tag(TAG_ScreenCollectorSvc).d("ScreenCollector Service ExceptionHandler got ${exception.stackTrace}")
        CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("Exception", ScreenshotData.ocrCleanUp(exception.stackTraceToString())) }
        FirebaseCrashlytics.getInstance().recordException(exception)
    }

    companion object {
        private const val TAG_ScreenCollectorSvc = "ScreenCollectorSvc"
    }

    /**
     * Called when the job starts.
     *
     * @param params The job parameters.
     * @return True if the job should continue running, false otherwise.
     */
    override fun onStartJob(params: JobParameters?): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Default) {
                workCoordinator()
            }
            onStopJob(params)
        }
        return true
    }

    /**
     * Called when the job stops.
     *
     * @param params The job parameters.
     * @return True if the job should be rescheduled, false otherwise.
     */
    override fun onStopJob(params: JobParameters?): Boolean {
        // Implement job stop logic here
        return false
    }

    /**
     * Adds a screenshot to the screen collector.
     *
     * @param screenshot The screenshot to be added.
     */
    suspend fun add(screenshot: ScreenshotEntity) {
        screenCollector.add(screenshot)
    }

    /**
     * Coordinates various work tasks such as uploading and zipping screenshots.
     */
    private suspend fun workCoordinator() {
        checkUploadZipFiles()
        checkScreenshotsToZip()
        checkCredentials()
    }

    /**
     * Checks and zips screenshots.
     */
    private suspend fun checkScreenshotsToZip() {
        Timber.tag(TAG_ScreenCollectorSvc).d("About to zip screenshots.")
        screenCollector.zipUpScreenShots(0, 5000)
    }

    /**
     * Checks and refreshes credentials.
     */
    private fun checkCredentials() {
        cloudAuthentication.fetchCurrentAuthSession()
    }

    /**
     * Checks and uploads zip files.
     */
    private suspend fun checkUploadZipFiles() {
        Timber.tag(TAG_ScreenCollectorSvc).d("About to upload zipFiles.")
        screenCollector.uploadZipFilesAsync(wifiFirstSession)
    }
}