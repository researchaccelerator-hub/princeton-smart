package com.screenlake.recorder.services

import android.app.job.JobParameters
import android.app.job.JobService
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MetricService : JobService() {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    private var user: UserEntity? = null
    private val tag = "MetricService"
    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.tag(tag).d("Job ${params?.jobId} has started.")

        // Launch the coroutine in the IO Dispatcher
        job = CoroutineScope(Dispatchers.IO).launch {
            runMetricJob(params)
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Cancel the job if the job is stopped unexpectedly
        job?.cancel()
        Timber.tag(tag).d("Job ${params?.jobId} was stopped.")
        return false
    }

    /**
     * Runs the metric job in a coroutine. Saves log information and finishes the job.
     *
     * @param params The JobParameters passed to the job by the system.
     */
    private suspend fun runMetricJob(params: JobParameters?) {
        try {
            // Log metric details to genOp
            generalOperationsRepository.saveLog("METRIC_SERVICE_RUN_RECORDING", ScreenshotService.isRunning.value.toString())
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "Error while running the metric job.")
        } finally {
            // Finish the job once the metric operation is completed
            jobFinished(params, false)
            Timber.tag(tag).d("Job ${params?.jobId} has finished.")
        }
    }
}
