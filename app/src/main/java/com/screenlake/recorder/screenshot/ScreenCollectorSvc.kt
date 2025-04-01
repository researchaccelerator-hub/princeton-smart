package com.screenlake.recorder.screenshot

import android.content.Context
import android.os.Binder
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.IS_CONNECTED
import com.screenlake.recorder.constants.ConstantSettings.IS_POWERED
import com.screenlake.recorder.constants.ConstantSettings.IS_RECORDING
import com.screenlake.recorder.constants.ConstantSettings.OCR_DONE
import com.screenlake.recorder.constants.ConstantSettings.OCR_NOT
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_MAPPING
import com.screenlake.data.enums.MobIleStatusEnum
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.HardwareChecks
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCollectorSvc @Inject constructor(
    private val context: Context,
    private val amplifyRepository: AmplifyRepository,
    private val screenshotDao: ScreenshotDao,
    private val generalOperationsRepository: GeneralOperationsRepository,
    private val screenCollector: ScreenCollector
) {

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private val nextIncrement = ScreenshotService.screenshotInterval.value ?: SCREENSHOT_MAPPING[ScreenshotService.framesPerSecond]!!
    private var uploadCounter = 0L
    private var zipCounter = 0L
    private var metricCounter = 0L
    private var credentialCounter = 0L
    private var wifiFirstSession = false
    private var firstUpload = false
    private var isUploadingDuration = 0L
    private var isZippingDuration = 0L
    private val workCoordinatorLimit = 30000L

    companion object {
        private const val TAG_ScreenCollectorSvc = "ScreenCollectorSvc"
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
    suspend fun workCoordinator() {
        uploadCounter += workCoordinatorLimit
        zipCounter += workCoordinatorLimit

        if (ScreenshotService.isMaintenanceOccurring.value == true) {
            return
        }

        val uploadInterval = 60000L
        if (isSettingsResolved()) {
            if (!wifiFirstSession) {
                uploadCounter = uploadInterval
                wifiFirstSession = true
            }
        } else {
            wifiFirstSession = false
        }

        val zipInterval = 60000L
        Timber.tag(TAG_ScreenCollectorSvc).d("SummaryOfCounters => uploadCounter:$uploadCounter, zipCounter:$zipCounter, zipInterval:$zipInterval uploadInterval:$uploadInterval")

        checkUploadZipFiles(uploadInterval)
        checkScreenshotsToZip(zipInterval)
        triggerMetrics()
        checkCredentials()
    }

    /**
     * Gets the count of screenshots where OCR is complete.
     *
     * @return The count of screenshots with completed OCR.
     */
    private suspend fun getOCRScreenshotCount() {
        screenshotDao.getOcrCompleteOrRestrictedCount()
    }


    /**
     * Checks and zips screenshots if the zip counter exceeds the zip interval.
     *
     * @param zipInterval The interval at which screenshots should be zipped.
     */
    private suspend fun checkScreenshotsToZip(zipInterval: Long) {
        if (zipCounter >= zipInterval) {
            isZippingDuration = 0L
            Timber.tag(TAG_ScreenCollectorSvc).d("About to zip screenshots => zipCounter:$zipCounter, (zipCounter >= zipInterval):${zipCounter >= zipInterval}")
            withContext(Dispatchers.IO) { screenCollector.zipUpScreenShots(0, 5000) }
            zipCounter = 0L
        }
    }

    /**
     * Triggers the emission of metrics at specified intervals.
     */
    private suspend fun triggerMetrics() {
        if (metricCounter >= ConstantSettings.getMetricInterval()) {
            Timber.tag("Metrics").d("Emmitting metrics.")
            System.gc()

            generalOperationsRepository.saveLog(IS_RECORDING, ScreenshotService.isRunning.value.toString())
            generalOperationsRepository.saveLog(IS_CONNECTED, HardwareChecks.isConnected(context).toString())
            generalOperationsRepository.saveLog(IS_POWERED, ScreenshotService.isPowerConnected.value.toString())
            generalOperationsRepository.saveLog(OCR_NOT, getOCRScreenshotCount().toString())
            generalOperationsRepository.saveLog(OCR_DONE, generalOperationsRepository.getScreenshotCount().toString())

            val status = amplifyRepository.getMobileStatus(context)
            val statusEnum = MobIleStatusEnum.TERMINATED.toString()
            if (status?.mobileStatus == statusEnum) {
                ScreenshotService.isRunning.postValue(false)
                amplifyRepository.confirmMobileStatusChange(status.id)
            }

            Binder.flushPendingCommands()
            metricCounter = 0L
        } else {
            metricCounter += workCoordinatorLimit
        }
    }

    /**
     * Checks and refreshes credentials at specified intervals.
     */
    private fun checkCredentials() {
        if (credentialCounter >= ConstantSettings.getCredentialInterval()) {
            cloudAuthentication.fetchCurrentAuthSession()
            credentialCounter = 0L
        } else {
            credentialCounter += nextIncrement
        }
    }

    /**
     * Checks and uploads zip files if the upload counter exceeds the upload interval.
     *
     * @param uploadInterval The interval at which zip files should be uploaded.
     */
    private suspend fun checkUploadZipFiles(uploadInterval: Long) {
        if (isSettingsResolved() && uploadCounter >= uploadInterval) {
            uploadCounter = 0L
            firstUpload = false
            isUploadingDuration = 0L
            Timber.tag(TAG_ScreenCollectorSvc).d("About to upload zipFiles => (uploadCounter >= uploadInterval):${uploadCounter >= uploadInterval}")
            screenCollector.uploadZipFilesAsync(wifiFirstSession)
        }
    }

    /**
     * Checks if the settings are resolved for uploading or zipping.
     *
     * @return True if the settings are resolved, false otherwise.
     */
    private fun isSettingsResolved(): Boolean {
        return when {
            ScreenshotService.uploadOverPower.value == true -> HardwareChecks.isPowerConnected(this@ScreenCollectorSvc.context)
            ScreenshotService.uploadOverWifi.value == true -> HardwareChecks.isConnected(this@ScreenCollectorSvc.context)
            else -> true
        }
    }
}