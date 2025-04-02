package com.screenlake.recorder.screenshot

import android.content.Context
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UploadDailyEntity
import com.screenlake.data.database.entity.UploadHistoryEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.ZIPPING
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCollector @Inject constructor(
    private var context: Context,
) {
    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository
    var zipsToUpload: List<ScreenshotZipEntity>? = mutableListOf()
    private var userObj: UserEntity? = null

    val handler = CoroutineExceptionHandler { _, exception ->
        println("ScreenCollector ExceptionHandler got $exception")
    }

    /**
     * Adds a screenshot to the database.
     *
     * @param screenshot The screenshot to be added.
     */
    suspend fun add(screenshot: ScreenshotEntity) {
        generalOperationsRepository.insertScreenshot(screenshot)
    }

    /**
     * Deletes a screenshot zip from the database.
     *
     * @param screenshotZip The screenshot zip to be deleted.
     */
    fun delete(screenshotZip: ScreenshotZipEntity) {
        generalOperationsRepository.deleteScreenshotZip(screenshotZip)
    }

    /**
     * Zips up screenshots and related data.
     *
     * @param offset The offset for fetching screenshots.
     * @param limit The limit for fetching screenshots.
     * @param screenshotsStart The initial list of screenshots.
     */
    suspend fun zipUpScreenShots(offset: Int, limit: Int, screenshotsStart: List<ScreenshotEntity> = mutableListOf()) {
        val TAG_ZipUpScreenShots = "ZipUpScreenShots"

        val screenshotCount = withContext(Dispatchers.Default) {
            generalOperationsRepository.getScreenshotCount()
        }

        val logCsv = getAndSaveLogs()
        if (logCsv.isNotEmpty()) {
            val logCSVFile = "log_data_${UUID.randomUUID()}.csv"
            writeFileOnInternalStorage(this@ScreenCollector.context, logCSVFile, logCsv)
        }

        if (screenshotCount >= ConstantSettings.getBatch()) {
            userObj = userObj ?: withContext(Dispatchers.Default) {
                generalOperationsRepository.getUser()
            }

            val zipFileId = UUID.randomUUID()
            val toZip = mutableListOf<File>()
            val path = context.filesDir?.path

            val screenshots = screenshotsStart.ifEmpty {
                withContext(Dispatchers.Default) {
                    generalOperationsRepository.getAllScreenshotsSortedByDateWhereOcrIsComplete(limit, offset)
                }
            }

            if (!screenshots.isNullOrEmpty()) {
                val accessibilityEvents = generalOperationsRepository.getASEvents()
                if (!accessibilityEvents.isNullOrEmpty()) {
                    val modifiedASEvents = DataTransformation.groupAccessibilityEventsAndScreenshots(accessibilityEvents, screenshots)
                    val accessibilityEventsCSVs = DataTransformation.createAccessibilityCSVs(modifiedASEvents)
                    val accessibilityEventsCSVsFile = "app_accessibility_data_csv_${zipFileId}.csv"
                    writeFileOnInternalStorage(this@ScreenCollector.context, accessibilityEventsCSVsFile, accessibilityEventsCSVs)
                    toZip.add(File(path, accessibilityEventsCSVsFile))

                    generalOperationsRepository.deleteAccessibilityEvents(accessibilityEvents.mapNotNull { it.id })
                }

                val screenshotCsv = DataTransformation.createScreenshotCsv(screenshots)
                val screenshotCSVFile = "screenshot_data_csv_${zipFileId}.csv"
                writeFileOnInternalStorage(this@ScreenCollector.context, screenshotCSVFile, screenshotCsv)
                toZip.add(File(path, screenshotCSVFile))

                if (generalOperationsRepository.getUser().uploadImages) {
                    toZip.addAll(screenshots.map { File(it.filePath) })
                }

                val sessions = withContext(Dispatchers.Default) {
                    generalOperationsRepository.getSessions()
                }

                if (!sessions.isNullOrEmpty()) {
                    val sessionCsv = DataTransformation.createSessionJson(sessions)
                    val sessionCSVFile = "session_data_csv_${zipFileId}.csv"
                    writeFileOnInternalStorage(this@ScreenCollector.context, sessionCSVFile, sessionCsv)
                    toZip.add(File(path, sessionCSVFile))
                }

                val uniqueSessions = screenshots.map { it.sessionId }.toHashSet().map { it.toString() }
                val appSegments = generalOperationsRepository.getAppSegmentsBySessionId(uniqueSessions)

                if (!appSegments.isNullOrEmpty()) {
                    val appSegmentCsv = DataTransformation.createAppSegmentCSV(appSegments)
                    val appSegmentCSVFile = "app_segment_data_csv_${zipFileId}.csv"
                    writeFileOnInternalStorage(this@ScreenCollector.context, appSegmentCSVFile, appSegmentCsv)
                    toZip.add(File(path, appSegmentCSVFile))

                    generalOperationsRepository.deleteAppSegments(appSegments.map { it.id.toString() })
                }

                Timber.tag(TAG_ZipUpScreenShots).d("IsZipping ${screenshots.size}")
                generalOperationsRepository.saveLog(ZIPPING, screenshots.count().toString())

                val file = File(path, "image_zip_${zipFileId}_${screenshots.size}.zip")
                updateDailyCounter(toZip.count().minus(1))

                ZipFile().zip(file, toZip)
                val size = file.length() / 1024 + 1
                val zipObj = ScreenshotZipEntity().apply {
                    this.file = file.toString()
                    this.localTimeStamp = TimeUtility.getCurrentTimestampDefaultTimezoneString()
                    this.timestamp = TimeUtility.getCurrentTimestampString()
                    this.user = userObj!!.email
                    this.toDelete = false
                    this.panelId = userObj!!.panelId.toString()
                    this.panelName = userObj!!.panelName
                }

                generalOperationsRepository.insertScreenshotZip(zipObj)

                Timber.tag(TAG_ZipUpScreenShots).d("Zipping up ${screenshots.size} many screenshots.")

                generalOperationsRepository.deleteScreenshots(screenshots.mapNotNull { it.id })
                if (sessions != null) {
                    generalOperationsRepository.deleteSessions(sessions.map { it.id.toString() })
                }

                toZip.forEach { it.delete() }

                notUploaded()
            } else {
                Timber.tag(TAG_ZipUpScreenShots).w("Found $screenshotCount screenshots which is less than $offset, skipping...")
            }
        }
    }

    /**
     * Retrieves and saves logs.
     *
     * @return The logs as a CSV string.
     */
    private suspend fun getAndSaveLogs(): String {
        val logCount = generalOperationsRepository.logCount()
        var logs: List<LogEventEntity>? = null
        var logTextFile = ""

        Timber.tag("LogEvent").d("LogEvents $logCount")
        if (logCount > 25) {
            logs = generalOperationsRepository.getLogs(1000, 0)
            logTextFile = DataTransformation.getAndSaveLogs(logs)
        }

        if (logTextFile.isNotEmpty() && logs != null) {
            for (batchLogs in logs.chunked(100)) {
                generalOperationsRepository.deleteLogEvents(batchLogs.mapNotNull { it.id })
            }
        }

        return logTextFile
    }

    /**
     * Updates the not uploaded count.
     */
    private suspend fun notUploaded() {
        val zipCount = generalOperationsRepository.getZipCount()
        val screenshotCount = zipCount * ConstantSettings.getBatch()
        ScreenshotService.notUploaded.postValue(screenshotCount)
    }

    /**
     * Updates the daily counter.
     *
     * @param increment The increment value.
     */
    private suspend fun updateDailyCounter(increment: Int) {
        val currentDate: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val todayHash = currentDate.toBase64()

        if (todayHash != null) {
            var uploadDaily = generalOperationsRepository.getUploadDaily(todayHash)
            var uploadTotal = generalOperationsRepository.getUploadTotal()

            if (uploadDaily == null) {
                uploadDaily =
                    UploadDailyEntity(todayHash, increment, TimeUtility.getCurrentTimestampString())
            }

            if (uploadTotal == null) {
                uploadTotal = UploadHistoryEntity("", increment)
            }

            uploadDaily.todayUploads += increment
            uploadTotal.totalUploaded += increment

            ScreenshotService.uploadedThisWeek.postValue(increment)
            ScreenshotService.uploadTotal.postValue(uploadTotal.totalUploaded)

            generalOperationsRepository.upsertDaily(uploadDaily)
            generalOperationsRepository.upsertHistory(uploadTotal)
        }
    }

    /**
     * Writes a file to internal storage.
     *
     * @param mcoContext The context.
     * @param sFileName The file name.
     * @param sBody The file content.
     * @param append Whether to append to the file.
     */
    private fun writeFileOnInternalStorage(context: Context, sFileName: String, sBody: String?, append: Boolean = false) {
        val dir = File(context.filesDir.path)
        if (!dir.exists()) {
            dir.mkdir()
        }

        try {
            val gpxfile = File(dir, sFileName)
            val writer = FileWriter(gpxfile, append)

            writer.append(sBody)
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("Exception", ScreenshotData.ocrCleanUp(e.stackTraceToString())) }
            e.printStackTrace()
        }
    }

    /**
     * Uploads zip files asynchronously.
     *
     * @param firstWifiSession Whether it is the first WiFi session.
     */
    suspend fun uploadZipFilesAsync(firstWifiSession: Boolean) {
        zipsToUpload = withContext(Dispatchers.Default) { generalOperationsRepository.getZipsToUpload() }?.take(10)?.toMutableList()
        userObj = withContext(Dispatchers.Default) { generalOperationsRepository.getUser() }

        val dir = File(context.filesDir.path)
        val files = dir.listFiles()
        val csvs = files?.filter { it.path.contains("log_data") }?.map { ScreenshotZipEntity(file = it.path) }

        if (csvs != null) {
            (zipsToUpload as MutableList<ScreenshotZipEntity>).addAll(csvs)
        }

        var count = 0.0
        if (!zipsToUpload.isNullOrEmpty()) {
            for (zip in zipsToUpload!!) {
                if (zip.file != null) {
                    val file = File(zip.file!!)
                    if (file.exists()) {
                        // Upload logic here
                    } else {
                        count++
                        if (zip.id != null) {
                            // Handle deletion
                        }
                    }
                } else {
                    count++
                }
            }
        }
    }
}