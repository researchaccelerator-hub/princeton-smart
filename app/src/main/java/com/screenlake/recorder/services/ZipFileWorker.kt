package com.screenlake.recorder.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.screenshot.DataTransformation
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.utilities.ZipFile
import com.screenlake.recorder.utilities.silence
import com.screenlake.recorder.utilities.withLogging
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.util.*

/**
 * A worker class that handles zipping up screenshots and related data for processing.
 *
 * This worker is responsible for collecting data from the database, transforming it
 * into the appropriate format, and creating zip files for further use.
 */
@HiltWorker
class ZipFileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val generalOperationsRepository: GeneralOperationsRepository
) : CoroutineWorker(context, workerParams) {

    private var userObj: UserEntity? = null
    private var runId = ""

    companion object {
        private const val TAG = "ZipFileWorker"
        val mutex = Mutex()
        var isRunning = false
    }

    /**
     * Perform the work to zip up screenshots and related data.
     *
     * @return Result indicating success or failure of the operation.
     */
    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Zip Worker has started.")

        if (!mutex.tryLock()) {
            Timber.tag(TAG).w("Another zipUpScreenshots is already running.")
            generalOperationsRepository.saveLog("Another zipUpScreenshots is already running.", "")
            return Result.success()
        }

        return try {
            runId = UUID.randomUUID().toString()
            if (isRunning) {
                Timber.tag(TAG).w("zipUpScreenshots already running.")
                generalOperationsRepository.saveLog("runId $runId","zipUpScreenshots already running.")
                Result.success()
            } else {
                isRunning = true
                Timber.tag(TAG).d("Zip Worker has started.")
                zipUpScreenshots(50)
                Timber.tag(TAG).d("Zip Worker has finished.")
                generalOperationsRepository.saveLog("ZIP_WORKER_RUN_${System.currentTimeMillis()}", runId)
                WorkerProgressManager.updateProgress("Finished zipping up screenshots.")
                Result.success()
            }
        } catch (ex: Exception) {
            generalOperationsRepository.saveLog("FAILED_ZIP_WORKER $runId", ex.stackTraceToString())
            Timber.tag(TAG).e(ex, "Zip Worker failed.")
            Result.failure()
        } finally {
            isRunning = false
            mutex.unlock()
        }
    }

    /**
     * Zips up screenshots and related data starting from the specified offset.
     *
     * This method collects screenshots, accessibility events, sessions, and app segments,
     * and writes them to CSV files which are then zipped together.
     *
     * @param offset The starting point for processing screenshots.
     * @param limit The maximum number of screenshots to process.
     * @param screenshotsStart Optional list of screenshots to start processing from.
     * @param testing Flag indicating whether this is a test run.
     * @param file Optional file for testing purposes.
     */
    private suspend fun zipUpScreenshots(
        limit: Int,
        screenshotsStart: List<ScreenshotEntity> = emptyList(),
        testing: Boolean = false,
        file: File? = null
    ) {
        val screenshotCount = withContext(Dispatchers.IO) { generalOperationsRepository.getScreenshotCount() }
        Timber.tag(TAG).d("Found $screenshotCount to zip. Batch is set to ${ConstantSettings.getBatch()}")

        val path = (if (testing) file?.path else applicationContext.filesDir?.path) ?: ""
        writeLogsToCsv(path)

        // Initial offset and count tracking
        var lastProcessedId: Int? = null

        if (screenshotCount > 0) {
            userObj = userObj ?: withContext(Dispatchers.IO) { generalOperationsRepository.getUser() }
            var hasMoreScreenshots = true

            // Loop until all screenshots are processed
            while (hasMoreScreenshots) {
                val zipFileId = UUID.randomUUID() // New UUID for each batch
                val toZip = mutableListOf<File>()

                val screenshots = generalOperationsRepository.getPaginateScreenshotsById(0L, lastProcessedId, limit)

                generalOperationsRepository.saveLog("<< $runId >>Zip file id: $zipFileId, files [${screenshots.map { id }.joinToString(separator = ", ")}]", "")

                if (screenshots.isNotEmpty()) {
                    val log1 = "<< $runId >>Zip file id: $zipFileId, Processing batch of ${screenshots.size} screenshots (lastId: ${lastProcessedId ?: "null"})"
                    generalOperationsRepository.saveLog(log1)
                    Timber.tag(TAG).d(log1)

                    findDuplicates(screenshots, zipFileId)

                    processAccessibilityEvents(screenshots, path, zipFileId, toZip)
                    processScreenshots(screenshots, path, zipFileId, toZip)
                    processSessions(screenshots, path, zipFileId, toZip)
                    processAppSegments(screenshots, path, zipFileId, toZip)

                    // Create zip file for this batch
                    createZipFile(toZip, path, zipFileId, screenshots.size, screenshots.mapNotNull { it.id })

                    // Update lastProcessedId for next batch instead of using offset
                    lastProcessedId = screenshots.lastOrNull()?.id

                    // Check if we've reached the end
                    if (screenshots.size < limit) {
                        hasMoreScreenshots = false
                    }
                } else {
                    // No more screenshots to process
                    hasMoreScreenshots = false
                }
            }
        } else {
            Timber.tag(TAG).w("Found $screenshotCount screenshots which is less than ${ConstantSettings.getBatch()}, skipping...")
        }
    }

    fun findDuplicates(screenshots: List<ScreenshotEntity>, zipFileId: UUID) {
        for (screenshot in screenshots) {
            if (screenshot.fileName.toString().isEmpty()) return
            if (ScreenshotService.screenshotZipDuplicateTracker.contains(screenshot.fileName.toString())) {
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("<< $runId >>Zip file id: $zipFileId: Duplicate screenshot found: ${screenshot.fileName}") }
            } else {
                ScreenshotService.screenshotZipDuplicateTracker.add(screenshot.fileName.toString())
            }
        }
    }

    /**
     * Writes logs to a CSV file if there are logs to save.
     *
     * @param path The path where the logs should be saved.
     */
    private suspend fun writeLogsToCsv(path: String) {
        val logCsv = getAndSaveLogs()
        if (logCsv.isNotEmpty()) {
            val logCSVFile = "log_data_${UUID.randomUUID()}.csv"
            writeFileOnInternalStorage(logCSVFile, logCsv, path)
        }
    }

    /**
     * Retrieves a list of screenshots to be processed.
     *
     * @param screenshotsStart The initial list of screenshots to start from.
     * @param offset The offset for database retrieval.
     * @param limit The limit for database retrieval.
     * @return A list of screenshots to be processed.
     */
    private suspend fun getScreenshots(screenshotsStart: List<ScreenshotEntity>, offset: Int, limit: Int): List<ScreenshotEntity> {
        return if (screenshotsStart.isNotEmpty()) {
            screenshotsStart
        } else {
            withContext(Dispatchers.IO) {
                generalOperationsRepository.getAllScreenshotsSortedByDateWhereOcrIsComplete(limit, offset)
            }
        }
    }

    /**
     * Processes accessibility events and writes them to a CSV file.
     *
     * @param screenshots The list of screenshots to associate with accessibility events.
     * @param path The path where the CSV should be saved.
     * @param zipFileId The unique identifier for the zip file.
     * @param toZip The list of files to be included in the zip.
     */
    private suspend fun processAccessibilityEvents(
        screenshots: List<ScreenshotEntity>,
        path: String,
        zipFileId: UUID,
        toZip: MutableList<File>
    ) {
        val accessibilityEvents = generalOperationsRepository.getASEvents()
        if (accessibilityEvents.isNotEmpty()) {
            val modifiedASEvents = DataTransformation.groupAccessibilityEventsAndScreenshots(accessibilityEvents, screenshots)
            val accessibilityEventsCSVs = DataTransformation.createAccessibilityCSVs(modifiedASEvents)
            val accessibilityEventsCSVsFile = "app_accessibility_data_csv_${zipFileId}.csv"
            writeFileOnInternalStorage(accessibilityEventsCSVsFile, accessibilityEventsCSVs, path)
            toZip.add(File(path, accessibilityEventsCSVsFile))
            generalOperationsRepository.deleteAccessibilityEvents(accessibilityEvents.mapNotNull { it.id })
        }
    }

    /**
     * Processes screenshots and writes them to a CSV file.
     *
     * @param screenshots The list of screenshots to process.
     * @param path The path where the CSV should be saved.
     * @param zipFileId The unique identifier for the zip file.
     * @param toZip The list of files to be included in the zip.
     */
    private fun processScreenshots(
        screenshots: List<ScreenshotEntity>,
        path: String,
        zipFileId: UUID,
        toZip: MutableList<File>
    ) {
        screenshots.forEach { it.zipFileId = "image_zip_${zipFileId}_${screenshots.size}.zip" }

        val screenshotCsv = DataTransformation.createScreenshotCsv(screenshots)
        val screenshotCSVFile = "screenshot_data_csv_${zipFileId}.csv"
        writeFileOnInternalStorage(screenshotCSVFile, screenshotCsv, path)
        toZip.add(File(path, screenshotCSVFile) )

        if (userObj?.uploadImages == true) {
            for (screenshot in screenshots) {
                if (screenshot.isAppRestricted == false && !screenshot.fileName.isNullOrEmpty()) {
                    val file = File(screenshot.filePath!!)
                    if (file.exists()) {
                        toZip.add(file)
                    }
                }
            }
        }

        generalOperationsRepository.deleteScreenshots(screenshots.mapNotNull { it.id }.toList())
    }

    /**
     * Processes sessions and writes them to a CSV file.
     *
     * @param screenshots The list of screenshots to associate with sessions.
     * @param path The path where the CSV should be saved.
     * @param zipFileId The unique identifier for the zip file.
     * @param toZip The list of files to be included in the zip.
     */
    private suspend fun processSessions(
        screenshots: List<ScreenshotEntity>,
        path: String,
        zipFileId: UUID,
        toZip: MutableList<File>
    ) {
        val sessionIds = screenshots.mapNotNull { it.sessionId }.distinct()
        val sessions = withContext(Dispatchers.IO) { generalOperationsRepository.getSessionsById(sessionIds) }
        if (!sessions.isNullOrEmpty()) {
            val sessionCsv = DataTransformation.createSessionJson(sessions)
            val sessionCSVFile = "session_data_csv_${zipFileId}.csv"
            writeFileOnInternalStorage(sessionCSVFile, sessionCsv, path)
            toZip.add(File(path, sessionCSVFile))
            generalOperationsRepository.deleteSessionsId(sessions.mapNotNull { it.id }.toList())
        }
    }

    /**
     * Processes app segments and writes them to a CSV file.
     *
     * @param screenshots The list of screenshots to associate with app segments.
     * @param path The path where the CSV should be saved.
     * @param zipFileId The unique identifier for the zip file.
     * @param toZip The list of files to be included in the zip.
     */
    private suspend fun processAppSegments(
        screenshots: List<ScreenshotEntity>,
        path: String,
        zipFileId: UUID,
        toZip: MutableList<File>
    ) {
        val uniqueSessions = screenshots.mapNotNull { it.sessionId }.toHashSet().map { it.toString() }
        val appSegments = generalOperationsRepository.getAppSegmentsBySessionId(uniqueSessions)
        if (appSegments.isNotEmpty()) {
            val appSegmentCsv = DataTransformation.createAppSegmentCSV(appSegments)
            val appSegmentCSVFile = "app_segment_data_csv_${zipFileId}.csv"
            writeFileOnInternalStorage(appSegmentCSVFile, appSegmentCsv, path)
            toZip.add(File(path, appSegmentCSVFile))
            generalOperationsRepository.deleteAppSegments(appSegments.map { it.id.toString() })
        }
    }

    /**
     * Creates a zip file from the specified files and updates the database.
     *
     * @param toZip The list of files to include in the zip.
     * @param path The path where the zip should be saved.
     * @param zipFileId The unique identifier for the zip file.
     * @param screenshotCount The number of screenshots included in the zip.
     */
    private fun createZipFile(toZip: MutableList<File>, path: String, zipFileId: UUID, screenshotCount: Int, screenshots: List<Int>) {
        val zipFile = File(path, "image_zip_${zipFileId}_${screenshotCount}.zip")
        ZipFile().zip(zipFile, toZip)

        val zipObj = ScreenshotZipEntity().apply {
            this.file = zipFile.toString()
            this.localTimeStamp = TimeUtility.getCurrentTimestampDefaultTimezoneString()
            this.timestamp = TimeUtility.getCurrentTimestampString()
            this.user = userObj?.email ?: ""
            this.toDelete = false
            this.panelId = userObj?.panelId?.toString() ?: ""
            this.panelName = userObj?.panelName ?: ""
        }

        generalOperationsRepository.insertScreenshotZip(zipObj)
//
        toZip.forEach { it.withLogging("Zip Worker", "Delete") { file -> file.delete() } }
        Timber.tag(TAG).d("Zip file created with ${toZip.size} files.")
    }

    /**
     * Writes the given content to a file on internal storage.
     *
     * @param fileName The name of the file to write.
     * @param body The content to write to the file.
     * @param path The path where the file should be saved.
     * @param append Whether to append to the file if it exists.
     */
    private fun writeFileOnInternalStorage(fileName: String, body: String, path: String, append: Boolean = false) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdir()
        }

        try {
            val file = File(dir, fileName)
            FileWriter(file, append).use {
                it.append(body)
                it.flush()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error writing file on internal storage")
        }
    }

    /**
     * Retrieves logs from the database, formats them, and deletes them once saved.
     *
     * @return A CSV string of logs if available, otherwise an empty string.
     */
    private suspend fun getAndSaveLogs(): String {
        val logCount = generalOperationsRepository.logCount()
        Timber.tag("LogEvent").d("LogEvents $logCount")

        return if (logCount > 1) {
            generalOperationsRepository.getLogs(1000, 0)?.let { logs ->
                val csv = DataTransformation.getAndSaveLogs(logs)
                logs.chunked(100).forEach { batchLogs ->
                    generalOperationsRepository.deleteLogEvents(batchLogs.mapNotNull { it.id })
                }
                csv
            } ?: ""
        } else {
            ""
        }
    }
}
