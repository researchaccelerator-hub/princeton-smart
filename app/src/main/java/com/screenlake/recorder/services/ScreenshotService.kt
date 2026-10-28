package com.screenlake.recorder.services

import android.R
import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.googlecode.tesseract.android.TessBaseAPI
import com.screenlake.MainActivity
import com.screenlake.data.database.ScreenshotState
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.SessionTempEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.model.AppInfo
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ResearchConfig
import com.screenlake.recorder.constants.ConstantSettings.ACTION_PAUSE_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.ACTION_STOP_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.RESTRICTED_APPS
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_MAPPING
import com.screenlake.recorder.ocr.Assets
import com.screenlake.recorder.ocr.Recognize
import com.screenlake.recorder.screenshot.ScreenCollectorSvc
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.BaseUtility.getForegroundTaskPackageName
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.utilities.record
import com.screenlake.recorder.utilities.ScreenshotBitmap
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.UUID
import java.util.Collections
import java.time.Instant
import kotlinx.coroutines.joinAll
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.screenlake.recorder.services.util.SharedPreferencesUtil
import com.screenlake.recorder.utilities.silence
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.collections.chunked
import android.content.Context

@AndroidEntryPoint
class ScreenshotService : Service(), ScreenStateReceiver.ScreenStateCallback {
    companion object {
        var lastOcrTime = 0L
        var manualOcr = MutableLiveData<Boolean>()
        var screenshotZipDuplicateTracker = HashSet<String>()
        var optimizeUploads = MutableLiveData<Boolean>()
        var isActionScreenOff = MutableLiveData<Boolean>()
        var lastCaptureTime = 0L
        var lastIsAliveTime = 0L
        var lastReEnableNotificationSent = 0L
        private var imagesProduced = 0
        val isScreenOn = MutableLiveData<Boolean>()
        val isRecording = MutableLiveData<Boolean>()
        val screenshotInterval = MutableLiveData<Long>()
        val isPaused = MutableLiveData<Boolean>()
        var offlineUpdates = MutableLiveData<Int>()
        var pausedTimer = MutableLiveData<String>()
        // Written by ScreenRecordFragment.updateMediaProjectionInService(); the service
        // uses its own private mediaProjection instance, but this companion reference
        // must remain for the fragment to compile.
        var projection: MediaProjection? = null
        var isProjectionValid = MutableLiveData<Boolean>()
        // Settings
        var uploadOverWifi = MutableLiveData<Boolean>()
        var uploadOverPower = MutableLiveData<Boolean>()
        var pauseTiming = MutableLiveData<Long>()

        var lastUploadSuccessful = MutableLiveData<Boolean>()
        var lastUploadTime = MutableLiveData<Long>()

        val isMaintenanceOccurring = MutableLiveData<Boolean>()

        val isPowerConnected = MutableLiveData<Boolean>()

        val isBatteryLow = MutableLiveData<Boolean>()

        val uploadedThisWeek = MutableLiveData<Int>()

        var appNameVsPackageName = mapOf<String, String>()

        var isMediaProjectionValid = MutableLiveData<Boolean>()

        val framesPerSecondConst: Double get() = ResearchConfig.ACTIVE_PRESET.fps
        var framesPerSecond: Double = ResearchConfig.ACTIVE_PRESET.fps
        val notUploaded = MutableLiveData<Int>()
        var sessionId = UUID.randomUUID().toString()
        const val NOTIFICATION_ID = 1337
        const val CHANNEL_ID = "screenshot_service_channel"
        const val INTERRUPTION_CHANNEL_ID = "screen_sharing_interruption_v2"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        /** Time between screenshots, from the study's capture preset. */
        val SCREENSHOT_INTERVAL_MS: Long = ResearchConfig.ACTIVE_PRESET.intervalMs
        const val RESTART_NOTIFICATION_ID = 1338
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SCREENSHOT_TIMEOUT_MS = 5000L  // 5 seconds timeout for screenshot operations
        private const val ERROR_NOTIFICATION_ID = 1339
        private const val SCREEN_UNLOCK_PROBE_DELAY_MS = 500L
        private const val MAX_SCREEN_UNLOCK_PROBES = 10
        private val CONSENT_REQUIRED_STATES = setOf(
            ProjectionInterruptionState.CONSENT_REQUIRED,
            ProjectionInterruptionState.SCREEN_LOCKED_CONSENT_REQUIRED
        )
        val isRunning = MutableLiveData<Boolean>()
        // Action for restarting media projection
        const val ACTION_RESTART_PROJECTION = "com.example.screenshotapp.RESTART_PROJECTION"
        const val ACTION_REQUEST_MEDIA_PROJECTION = "ACTION_REQUEST_MEDIA_PROJECTION"
        const val ACTION_RESUME_SERVICE = "ACTION_RESUME_SERVICE"
        const val ACTION_MEDIA_PROJECTION_DENIED = "ACTION_MEDIA_PROJECTION_DENIED"
        private const val PREFS_NAME = "screenshot_service_state"
        private const val PREF_PENDING_MEDIA_PROJECTION_REQUEST = "pending_media_projection_request"
        val shouldRequestMediaProjection = MutableLiveData<Boolean>()
        val projectionInterruptionState = MutableLiveData(ProjectionInterruptionState.IDLE)
        val isMediaProjectionPromptInFlight = AtomicBoolean(false)
        val suppressMediaProjectionPromptUntilUnlock = AtomicBoolean(false)
        val uploadTotal = MutableLiveData<Int>()
        val manualUploadPercentComplete = MutableLiveData<Double>()
        var uploadCountMsg = MutableLiveData<Int>()
        var restrictedApps = MutableLiveData<HashSet<String>>()
        var user = UserEntity()
        var lastUnlockTime: Long? = null

        private const val TAG = "OcrWorker"
        private const val BATCH_SIZE = 10  // Number of images to process per batch
        private const val OCR_IMAGE_THRESHOLD = 5  // Minimum number of images to trigger OCR
        private const val OCR_FETCH_BATCH_SIZE = 100

        fun postInitialValues(){
            screenshotInterval.postValue(SCREENSHOT_MAPPING[ScreenshotService.Companion.framesPerSecond])
            isPaused.postValue(false)
            uploadCountMsg.postValue(0)
            isRunning.postValue(false)
            offlineUpdates.postValue(0)
            pauseTiming.postValue(300000)
            restrictedApps.postValue(hashSetOf())
            manualOcr.postValue(false)
            isActionScreenOff.postValue(true)
            optimizeUploads.postValue(false)
            uploadOverWifi.postValue(ResearchConfig.UPLOAD_OVER_WIFI_ONLY)
            uploadOverPower.postValue(ResearchConfig.UPLOAD_OVER_POWER_ONLY)
        }

        fun isRestrictedApp(appAPK: String?): Boolean {

            if (appAPK.isNullOrEmpty()) return false

            if (ResearchConfig.ALLOWED_APPS_OVERRIDE.contains(appAPK)) return false

            val nameFromApk = ScreenshotService.appNameVsPackageName.getOrDefault(appAPK, "")
            val userRestricted = ScreenshotService.restrictedApps.value?.contains(nameFromApk) ?: false
            val isRestrictedApp = RESTRICTED_APPS.contains(appAPK)
                    || ResearchConfig.ADDITIONAL_BLOCKED_APPS.contains(appAPK)
                    || userRestricted

            if (isRestrictedApp) {
                Timber.tag("RestrictedApps").d("*** Restricted App: %s ***", nameFromApk)
            }

            return isRestrictedApp
        }

        fun isMediaProjectionRequestPending(context: Context): Boolean {
            return shouldRequestMediaProjection.value == true ||
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(PREF_PENDING_MEDIA_PROJECTION_REQUEST, false)
        }

        fun markMediaProjectionRequestPending(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PENDING_MEDIA_PROJECTION_REQUEST, true)
                .apply()
            shouldRequestMediaProjection.postValue(true)
        }

        fun clearMediaProjectionRequest(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PENDING_MEDIA_PROJECTION_REQUEST, false)
                .apply()
            shouldRequestMediaProjection.postValue(false)
        }
    }

    private var imagesProduced = 0
    private var countDownTimer: CountDownTimer? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var handlerThread: Thread? = null
    private var width = 0
    private var height = 0
    private var density = 0
    private var isScreenLocked = AtomicBoolean(false)
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var shutdownReceiver: BootReceiver? = null
    private var consecutiveErrors = AtomicInteger(0)
    private var suppressNextProjectionStopPrompt = AtomicBoolean(false)
    private var suppressNextVirtualDisplayStop = AtomicBoolean(false)
    private var isResettingCapture = AtomicBoolean(false)
    private val interruptionSessionFinalized = AtomicBoolean(false)
    private val projectionStateLock = Any()
    private var currentProjectionState = ProjectionInterruptionState.IDLE
    private val projectionPromptHandler = Handler(Looper.getMainLooper())
    private var screenUnlockProbe: Runnable? = null
    var moveForward = true
    private var screenshotJob: Job? = null
    // Marker rows still being written; segmentation waits for them so they get a segment
    // (the zip queries need one) in the same pass.
    private val pendingMarkerWrites: MutableList<Job> = Collections.synchronizedList(mutableListOf())
    private var ocrJob: Job? = null
    private val ocrLock = Any()
    var ocrSubmitted = false
    private var captureThread: Thread? = null
    private var threadLooper: Looper? = null
    var mHandler: Handler? = null
    var processedCount = 0


    // Create an exception handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        if (exception !is CancellationException) {
            Timber.e(exception, "Coroutine error: ${exception.message}")
            handleScreenshotError(exception as Exception)
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + errorHandler)

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var screenCollectorSvc: ScreenCollectorSvc

    @Inject
    lateinit var recognize: Recognize

    var currentAppInUse = AppInfo()

    // Store media projection data for later restart
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    // Prevents multiple concurrent code paths from each posting a restart notification.
    // Reset when a fresh consent token is successfully consumed in setupMediaProjection().
    private var restartNotificationShown = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        RecordingState.serviceAliveInProcess = true
        try {
            createNotificationChannel()
            startForeground()

            // Get screen metrics with error handling
            try {
                val metrics = DisplayMetrics()
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.defaultDisplay.getMetrics(metrics)
                width = metrics.widthPixels
                height = metrics.heightPixels
                density = metrics.densityDpi
            } catch (e: Exception) {
                Timber.e(e, "Error getting display metrics")
                // Use default values if metrics cannot be obtained
                width = 1080
                height = 1920
                density = 240
            }

            postInitialValues()

            // Register screen state receiver
            screenStateReceiver = ScreenStateReceiver(this)
            screenStateReceiver?.register(this)

            // Log POWER_OFF; shutdown is not delivered to the manifest-registered BootReceiver.
            shutdownReceiver = BootReceiver().also { receiver ->
                val filter = IntentFilter().apply { BootReceiver.RUNTIME_ACTIONS.forEach(::addAction) }
                ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in onCreate")
            stopSelf()
        }
    }

    private fun pauseRecordingForMinutes()
    {
        if(isRunning.value == true && isPaused.value!!){
            countDownTimer = object : CountDownTimer(pauseTiming.value!!, 1000)  {

                override fun onTick(millisUntilFinished: Long) {
                    if(isPaused.value == false){
                        handleResumeService()
                    }else{
                        pausedTimer.postValue("resuming in: " + ""+String.format("%d min, %d sec",
                            TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                            TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))))
                    }
                }

                override fun onFinish() {
                    handleResumeService()
                }
            }

            countDownTimer?.start()
        }else if(isPaused.value != null && !isPaused.value!!){
            countDownTimer?.cancel()
            pausedTimer.postValue("")
            isPaused.postValue(false)
        }
    }

    private fun handlePauseService() {
        val wasPaused = isPaused.value == true
        isPaused.value = true
        transitionProjectionState(ProjectionInterruptionState.USER_PAUSED, "user_pause")
        if (isRunning.value == true && !wasPaused) {
            logScreenshotState(ScreenshotState.PAUSE_START)
        }
        pauseRecordingForMinutes()
        Timber.d("Service paused")
    }

    private fun handleResumeService() {
        if (isPaused.value != true) return

        countDownTimer?.cancel()
        countDownTimer = null
        isPaused.value = false
        transitionProjectionState(
            if (mediaProjection != null && virtualDisplay != null) {
                ProjectionInterruptionState.CAPTURING
            } else {
                ProjectionInterruptionState.CONSENT_REQUIRED
            },
            "user_resume"
        )
        pausedTimer.value = "recording"

        logScreenshotState(ScreenshotState.PAUSE_END)
        resetCapturing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            silence {
                optimizeUploads.postValue(SharedPreferencesUtil.getBatteryOptimizationDisabled(this@ScreenshotService))
            }

            if (intent != null) {
                when (intent.action) {
                    ACTION_STOP_SERVICE -> {
                        // Stop the pause countdown first so it cannot end the pause mid-save.
                        countDownTimer?.cancel()
                        countDownTimer = null
                        RecordingState.markStoppedIntentionally(this@ScreenshotService)
                        // A stopped study needs no screen sharing consent.
                        clearMediaProjectionRequest(this@ScreenshotService)
                        cancelProjectionStoppedNotification()
                        coroutineScope.launch {
                            saveSessionSegmentsInBackground()
                            generalOperationsRepository.buildCurrentSession(framesPerSecondConst, "USER_STOP")
                            generalOperationsRepository.saveSession(generalOperationsRepository.currentSession)
                            stopCapturing()
                            // Clear the pause only after capture is cancelled, so a paused session
                            // never takes screenshots while it is being saved. Not done in
                            // stopCapturing(), which also runs before error restarts.
                            isPaused.postValue(false)
                            pausedTimer.postValue("")
                        }
                    }
                    ACTION_PAUSE_SERVICE -> handlePauseService()
                    ACTION_RESUME_SERVICE -> handleResumeService()
                    ACTION_MEDIA_PROJECTION_DENIED -> {
                        markMediaProjectionRequestPending(this)
                        logProjectionPromptEvent("result=denied")
                        // The notification that opened the consent flow is auto-cancelled by
                        // Android, but restartNotificationShown still remembers it. Reset both
                        // sides before reposting so a denial always leaves an obvious action.
                        cancelProjectionStoppedNotification()
                        showProjectionStoppedNotification()
                        logProjectionPromptEvent("visibility=notification_reposted_after_denial")
                    }

                    ACTION_RESTART_PROJECTION -> {
                        Timber.d("Restarting media projection")
                        val newResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                        val newData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(EXTRA_DATA)
                        }

                        if (newResultCode != Activity.RESULT_CANCELED && newData != null) {
                            resultCode = newResultCode
                            resultData = newData
                            setupMediaProjection(newResultCode, newData)
                        } else {
                            Timber.e("Permission screensharing denied by user.")
                        }

                        // Does nothing unless the projection was set up above.
                        startCapturing()
                    }
                    else -> {
                        val newResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                        val newData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(EXTRA_DATA)
                        }

                        if (newResultCode != Activity.RESULT_CANCELED && newData != null) {
                            resultCode = newResultCode
                            resultData = newData
                            setupMediaProjection(newResultCode, newData)
                        } else {
                            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("MEDIA_PROJECTION", "Permission denied for initial start") }
                            Timber.e("Permission screensharing denied for initial start")
                        }

                        // Does nothing unless the projection was set up above.
                        startCapturing()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in onStartCommand")
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("MEDIA_PROJECTION", "${e.message} -> ${e.stackTrace}") }
            showErrorNotification("Service Start Error", "Could not start screenshot service: ${e.message}")
        }
        return START_NOT_STICKY
    }

    private fun screenshotIntervalText(): String {
        val seconds = (SCREENSHOT_INTERVAL_MS / 1000).toInt()
        return resources.getQuantityString(com.screenlake.R.plurals.taking_screenshots_every_n_seconds, seconds, seconds)
    }

    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Service")
            .setContentText(screenshotIntervalText())
            .setSmallIcon(com.screenlake.R.drawable.logo_just_square_small)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting foreground service")
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("MEDIA_PROJECTION", "${e.message} -> ${e.stackTrace}") }
            // Fallback to starting foreground without type
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Timber.e(e2, "Critical error starting foreground service")
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("MEDIA_PROJECTION", "${e.message} -> ${e.stackTrace}") }
                stopSelf()
            }
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        if (mediaProjection != null) {
            Timber.w("setupMediaProjection: projection already active, ignoring duplicate call")
            isMediaProjectionPromptInFlight.set(false)
            return
        }
        // Fresh consent token received — allow the restart notification to be shown again
        // if projection is lost in this new session.
        restartNotificationShown = false
        interruptionSessionFinalized.set(false)
        RecordingState.markStarted(this)
        transitionProjectionState(ProjectionInterruptionState.STARTING, "fresh_consent_received")
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // On API 34+, the consent token is single-use. This call consumes it permanently.
                // Any subsequent call with the same token will throw SecurityException or return null.
                Timber.d("setupMediaProjection: consuming single-use token (API 34+)")
            }
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Timber.e("Media projection is null after getMediaProjection")
                isMediaProjectionPromptInFlight.set(false)
                showErrorNotification("Error", "Could not create media projection")
                transitionProjectionState(ProjectionInterruptionState.ERROR, "get_media_projection_returned_null")
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Timber.d("MediaProjection.Callback.onStop() fired")
                    isMediaProjectionValid.value = false

                    val stopWasRequestedByApp = suppressNextProjectionStopPrompt.getAndSet(false)
                    val locked = isScreenLocked.get() || isDeviceCurrentlyLocked()
                    if (!stopWasRequestedByApp) {
                        transitionProjectionState(
                            if (locked) ProjectionInterruptionState.SCREEN_LOCKED_CONSENT_REQUIRED
                            else ProjectionInterruptionState.CONSENT_REQUIRED,
                            "media_projection_on_stop"
                        )
                        markMediaProjectionRequestPending(this@ScreenshotService)
                        showProjectionStoppedNotificationWhenAppropriate("media_projection_stopped")
                        finalizeInterruptionSession(
                            if (locked) "SCREEN_OFF_MEDIA_PROJECTION_REVOKED"
                            else "MEDIA_PROJECTION_REVOKED"
                        )
                    }

                    releaseProjectionResources()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Timber.e(e, "Error setting up media projection")
            isMediaProjectionPromptInFlight.set(false)
            isMediaProjectionValid.postValue(false)
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("MEDIA_PROJECTION", "${e.message} -> ${e.stackTrace}") }
            showErrorNotification("Setup Error", "Failed to setup screen capture: ${e.message}")
            transitionProjectionState(ProjectionInterruptionState.ERROR, "setup_media_projection_exception")
        }
    }

    private fun showProjectionStoppedNotification() {
        if (restartNotificationShown) return
        restartNotificationShown = true
        try {
            // Create a notification to inform the user that projection stopped
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(this, INTERRUPTION_CHANNEL_ID)
                .setContentTitle("Study Recording Paused")
                .setContentText("Tap to resume data collection")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSmallIcon(com.screenlake.R.drawable.logo_just_square_small)
                .setContentIntent(NotificationHelper(this).getMainActivityPendingIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .build()

            notificationManager.notify(RESTART_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("PROJECTION_NOTIFICATION", "${e.message} -> ${e.stackTrace}") }
            Timber.e(e, "Error showing projection stopped notification")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Screenshot Service"
            val description = "Takes periodic screenshots"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val interruptionChannel = NotificationChannel(
                INTERRUPTION_CHANNEL_ID,
                "Screen sharing interrupted",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Alerts when screen sharing needs to be resumed"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(interruptionChannel)
        }
    }

    private fun showErrorNotification(title: String, message: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Error showing error notification")
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("PROJECTION_NOTIFICATION", "${e.message} -> ${e.stackTrace}") }
        }
    }

    private fun startCapturing() {
        if (
            isRunning.value == true &&
            virtualDisplay != null &&
            imageReader != null
        ) {
            return
        }

        // Reset consecutive errors counter
        consecutiveErrors.set(0)

        coroutineScope.launch {
            try {
                // Check if screen is currently locked
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                isScreenLocked.set(keyguardManager.isKeyguardLocked)

                val virtualDisplayReady = withContext(Dispatchers.Main) {
                    // Setup virtual display on main thread (MediaProjection requires main thread)
                    if (!setupVirtualDisplay()) {
                        Timber.e("Failed to set up virtual display (Screensharing not enabled)")
                        // Let the participant retry consent instead of leaving the gate closed.
                        isMediaProjectionPromptInFlight.set(false)
                        false
                    } else {
                        // A new consent token is only recovered after its virtual display starts.
                        isRunning.value = true
                        transitionProjectionState(ProjectionInterruptionState.CAPTURING, "virtual_display_started")
                        clearMediaProjectionRequest(this@ScreenshotService)
                        isMediaProjectionPromptInFlight.set(false)
                        cancelProjectionStoppedNotification()
                        logProjectionPromptEvent("result=granted;capture=started")
                        true
                    }
                }

                if (!virtualDisplayReady) return@launch

                // Load app data with timeout and error handling
                try {
                    val allApps = withContext(Dispatchers.Default) {
                        try {
                            generalOperationsRepository.getALlApps()
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching apps")
                            emptyList()
                        }
                    }

                    val restricted = allApps.filter { it.isUserRestricted }.map { it.appName }.toHashSet()
                    restrictedApps.postValue(restricted)

                    appNameVsPackageName = allApps.associate { it.packageName to it.appName }

                    user = withContext(Dispatchers.Default) {
                        try {
                            generalOperationsRepository.getUser()
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching user")
                            UserEntity() // Fallback to empty user
                        }
                    }

                    if (screenshotJob?.isActive != true) {
                        startProcessingWithRestarts()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading initial data with timeout")
                    CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("START_CAPTURE", "${e.message} -> ${e.stackTrace}") }
                    // Continue anyway, will use empty data
                }

                // Start screenshot loop with error handling

            } catch (e: Exception) {
                Timber.e(e, "Error in startCapturing")
                showErrorNotification("Capture Error", "Failed to start capture: ${e.message}")
                stopCapturing()
            }
        }
    }

    private fun startProcessingWithRestarts() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    screenshotJob = launch(errorHandler) {
                        // Runs while the service runs, whether or not screen sharing is active;
                        // takeScreenshot() logs a NoScreenSharing row when it is not.
                        while (isRunning.value == true && isActive) {
                            if (isPaused.value != true && !isScreenLocked.get()) {
                                try {
                                    withTimeout(SCREENSHOT_TIMEOUT_MS) {
                                        lastIsAliveTime = System.currentTimeMillis()
                                        takeScreenshot()
                                    }
                                    if (consecutiveErrors.get() > 0) {
                                        consecutiveErrors.set(0)
                                    }
                                } catch (e: Exception) {
                                    Timber.d("Screenshot failed or timed out: ${e.message}")
                                }
                            } else if (isPaused.value == true) {
                                Timber.d("Recording paused. Skipping screenshot.")
                            }

                            if (manualOcr.value == true && !ocrSubmitted) {
                                ocrSubmitted = true
                                beginOcr()
                            }

                            // Strict interval maintained independent of capture status
                            delay(SCREENSHOT_INTERVAL_MS)
                        }
                    }
                    break
                } catch (e: TimeoutCancellationException) {
                    Timber.d("Master loop timed out, restarting...")
                    delay(1000)
                    continue
                }
            }
        }
    }

    private fun handleScreenshotError(e: Exception) {
        val count = consecutiveErrors.incrementAndGet()
        Timber.e(e, "Screenshot error ($count/$MAX_RETRY_ATTEMPTS): ${e.message}")

        if (count >= MAX_RETRY_ATTEMPTS) {
            // Too many consecutive errors, try to restart the capture
            Timber.e("Too many consecutive errors, restarting capture")

            coroutineScope.launch {
                try {
                    stopCapturing()
                    delay(1000) // Wait a bit before restarting

                    // Only try to restart if we have valid data
                    if (resultCode != Activity.RESULT_CANCELED && resultData != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // On API 34+, the consent token is single-use and has already been
                            // consumed by the original getMediaProjection() call. Attempting to
                            // reuse it will throw a SecurityException or silently return null.
                            // Instead, prompt the user for fresh consent.
                            showProjectionStoppedNotification()
                        } else {
                            setupMediaProjection(resultCode, resultData!!)
                            startCapturing()
                        }
                    } else {
                        showErrorNotification(
                            "Screenshot Service Error",
                            "Too many errors occurred. Please restart the app."
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during error recovery")
                    CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("SCREENSHOT_ERROR", "${e.message} -> ${e.stackTrace}") }
                    showErrorNotification(
                        "Critical Error",
                        "Service needs to be restarted manually: ${e.message}"
                    )
                }
            }
        }
    }

    private fun setupVirtualDisplay(): Boolean {
        try {
            if (mediaProjection == null) {
                Timber.e("MediaProjection is null when setting up virtual display")
                return false
            }

            // Create image reader to capture screenshots
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            // Create the session's single virtual display. Pauses are recoverable, but
            // onStopped() is terminal and API 34+ requires fresh consent for a new display.
            suppressNextVirtualDisplayStop.set(false)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        transitionProjectionState(
                            if (isDeviceCurrentlyLocked()) {
                                ProjectionInterruptionState.SCREEN_LOCKED_CAPTURE_SUSPENDED
                            } else {
                                ProjectionInterruptionState.STARTING
                            },
                            "virtual_display_paused"
                        )
                    }

                    override fun onResumed() {
                        transitionProjectionState(
                            ProjectionInterruptionState.CAPTURING,
                            "virtual_display_resumed"
                        )
                    }

                    override fun onStopped() {
                        if (suppressNextVirtualDisplayStop.getAndSet(false)) {
                            Timber.d("VirtualDisplay stopped as requested by the app")
                            return
                        }
                        Timber.w("VirtualDisplay stopped permanently")
                        handleVirtualDisplayStopped()
                    }
                },
                Handler(Looper.getMainLooper())
            )

            return virtualDisplay != null
        } catch (e: Exception) {
            Timber.e(e, "Error setting up virtual display")
            return false
        }
    }

    private suspend fun takeScreenshot() {
        try {
            val currentAppInUse = withContext(Dispatchers.Default) {
                getForegroundTaskPackageName(this@ScreenshotService)
            }

            if (isPaused.value == true) {
                Timber.d("Recording paused.")
                return
            }

            moveForward = !(ScreenshotService.isRestrictedApp(currentAppInUse.apk))

            if (!moveForward) {
                val screenshotData = ScreenshotData.saveScreenshotData(
                    "",
                    currentAppInUse,
                    sessionId,
                    user,
                    ScreenshotState.RESTRICTED,
                    true
                )

                imagesProduced++
                uploadCountMsg.postValue(imagesProduced)

                try {
                    screenCollectorSvc.add(screenshotData)
                } catch (e: Exception) {
                    Timber.e(e, "Error adding restricted app data to collector")
                }
                return
            }

            var image = captureScreenshotWithRetry(2, 500)

            if (
                image == null &&
                mediaProjection != null &&
                !isScreenLocked.get() &&
                !isMediaProjectionRequestPending(this@ScreenshotService)
            ) {
                Timber.d("No image available, resetting capture surface before retrying")
                withContext(Dispatchers.Main) { resetCapturing() }
                delay(500)
                image = captureScreenshotWithRetry(2, 500)
            }

            if (image == null) {
                val projectionUnavailable =
                    mediaProjection == null ||
                        isMediaProjectionRequestPending(this@ScreenshotService)

                if (projectionUnavailable) {
                    // The SCREEN_SHARING_STATE log already records why capture stopped.
                    Timber.d("No screen sharing consent; skipping screenshot")
                } else {
                    Timber.w(
                        "Projection remains active but no image was available after retry; " +
                            "skipping transient missing frame"
                    )
                }
                return
            }

            try {
                saveImageToFile(image, currentAppInUse)
            } finally {
                // Always close the image to avoid memory leaks
                withContext(Dispatchers.IO) {
                    try {
                        image.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing image")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in takeScreenshot")
            throw e
        }
    }


    private suspend fun captureScreenshotWithRetry(maxRetries: Int = 5, delayMs: Long = 500) : Image? {
        var attempts = 0

        while (attempts < maxRetries) {
            try {
                val image = imageReader?.acquireLatestImage()

                if (image == null) {
                    Timber.d("No image available, attempt ${attempts+1}/$maxRetries, retrying in ${delayMs}ms")
                    attempts++
                    delay(delayMs)
                    continue
                }

                isMediaProjectionValid.postValue(true)

                return image

            } catch (e: Exception) {
                Timber.e(e, "Error acquiring image, attempt ${attempts+1}/$maxRetries")
                attempts++
                delay(delayMs)
            }
        }

        Timber.e("Failed to acquire image after $maxRetries attempts")
        return null
    }

    private suspend fun saveImageToFile(image: Image, appInfo: AppInfo) {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var fileOutputStream: FileOutputStream? = null

            try {
                val plane = image.planes[0]

                // Create bitmap, without the ImageReader's row padding
                bitmap = ScreenshotBitmap.fromPlane(plane.buffer, plane.pixelStride, plane.rowStride, width, height)

                // Create file
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                val filename = "${applicationContext.filesDir.path}/img_${UUID.randomUUID()}Screenshot_$timestamp.jpg"

                val screenshotData = ScreenshotData.saveScreenshotData(filename, appInfo,
                    sessionId, user)

                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        withTimeout(3000) {
                            screenCollectorSvc.add(screenshotData)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error adding screenshot data to collector ${e.message}")
                }

                val screenshotsDir = File(filename)

                // Save the bitmap to a file
                fileOutputStream = FileOutputStream(screenshotsDir)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fileOutputStream)
                Timber.d("Screenshot saved: ${screenshotsDir.absolutePath}")
            } catch (e: IOException) {
                e.record()
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("IMAGE_READER", "${e.message} -> ${e.stackTrace}") }
                Timber.e(e, "I/O error saving screenshot")
                throw e
            } catch (e: OutOfMemoryError) {
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("IMAGE_READER", "${e.message} -> ${e.stackTrace}") }
                Timber.e(e, "Out of memory when creating bitmap")
                throw e
            } catch (e: Exception) {
                e.record()
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("IMAGE_READER", "${e.message} -> ${e.stackTrace}") }
                Timber.e(e, "Error saving screenshot")
                throw e
            } finally {
                // Close resources
                try {
                    fileOutputStream?.close()
                } catch (e: IOException) {
                    e.record()
                    CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("IMAGE_READER", "${e.message} -> ${e.stackTrace}") }
                    Timber.e(e, "Error closing FileOutputStream")
                }

                bitmap?.recycle()
            }
        }
    }

    private fun resetCapturing() {
        if (isResettingCapture.getAndSet(true)) return

        // Android 14+ allows only one VirtualDisplay per MediaProjection session.
        // Keep that display alive and replace only its ImageReader surface.
        var newImageReader: ImageReader? = null
        try {
            if (mediaProjection == null || isMediaProjectionRequestPending(this)) {
                Timber.d("resetCapturing: media projection is no longer active")
                return
            }

            val display = virtualDisplay
            if (display == null) {
                // Before Android 14 a projection can create another display, e.g. after one
                // stopped while locked and paused. From 14 on, onStopped() asks for consent.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && setupVirtualDisplay()) {
                    transitionProjectionState(ProjectionInterruptionState.CAPTURING, "legacy_reset_virtual_display_recreated")
                } else {
                    Timber.w("resetCapturing: virtual display is unavailable")
                }
                return
            }

            val replacementImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            newImageReader = replacementImageReader
            display.setSurface(replacementImageReader.surface)

            val previousImageReader = imageReader
            imageReader = replacementImageReader
            newImageReader = null

            try {
                previousImageReader?.close()
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("STOP_CAPTURE_RESET", "${e.message} -> ${e.stackTrace}") }
                e.record()
                Timber.e(e, "Error closing previous image reader during reset")
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("STOP_CAPTURE_RESET", "${e.message} -> ${e.stackTrace}") }
            e.record()
            Timber.e(e, "Error replacing image reader surface during reset")
        } finally {
            try {
                newImageReader?.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing replacement image reader after reset failure")
            }
            isResettingCapture.set(false)
        }
    }


    private fun stopCapturing() {
        cancelScreenUnlockProbe()
        isRunning.postValue(false)
        transitionProjectionState(ProjectionInterruptionState.STOPPED, "capture_stopped")

        // Cancel the screenshot job
        screenshotJob?.cancel()
        screenshotJob = null

        try {
            if (virtualDisplay != null) suppressNextVirtualDisplayStop.set(true)
            virtualDisplay?.release()
        } catch (e: Exception) {
            suppressNextVirtualDisplayStop.set(false)
            e.record()
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("STOP_CAPTURE", "${e.message} -> ${e.stackTrace}") }
            Timber.e(e, "Error releasing virtual display")
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("STOP_CAPTURE", "${e.message} -> ${e.stackTrace}") }
            e.record()
            Timber.e(e, "Error closing image reader")
        }
        imageReader = null

        try {
            val projectionToStop = mediaProjection
            if (projectionToStop != null) {
                suppressNextProjectionStopPrompt.set(true)
                projectionToStop.stop()
            }
        } catch (e: Exception) {
            suppressNextProjectionStopPrompt.set(false)
            e.record()
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("STOP_CAPTURE", "${e.message} -> ${e.stackTrace}") }
            Timber.e(e, "Error stopping media projection")
        }
        mediaProjection = null
        isMediaProjectionValid.postValue(false)
    }

    override fun onDestroy() {
        RecordingState.onServiceDestroyed(this)
        try {
            isMediaProjectionValid.postValue(false)
            screenStateReceiver?.unregister(this)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering screen state receiver")
        }
        screenStateReceiver = null

        try {
            shutdownReceiver?.let { unregisterReceiver(it) }
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Error unregistering shutdown receiver")
        }
        shutdownReceiver = null

        CoroutineScope(Dispatchers.IO).launch {
            saveSessionSegmentsInBackground()
            generalOperationsRepository.buildCurrentSession(framesPerSecondConst, "SERVICE_DESTROYED")
            generalOperationsRepository.saveSession(generalOperationsRepository.currentSession)
        }

        stopCapturing()

        try {
            coroutineScope.cancel() // Cancel all coroutines when service is destroyed
        } catch (e: Exception) {
            e.record()
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("ON_DESTROY", "${e.message} -> ${e.stackTrace}") }
            Timber.e(e, "Error cancelling coroutine scope")
        }

        super.onDestroy()
    }

    // Screen state callback implementations
    /**
     * Writes a marker row to screenshot_data when the capture state changes (lock, unlock,
     * pause, screen sharing lost). Marker rows have no image; their `state` says what changed.
     */
    private fun logScreenshotState(state: String) {
        // The service outlives a Stop or sign-out until it is destroyed; record nothing then.
        // Consent-required interruptions keep recording expected, so they are still recorded.
        if (!RecordingState.isExpected(this)) return
        val markerSessionId = sessionId
        val eventTime = Instant.now()
        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                val appInfo = withContext(Dispatchers.Default) {
                    getForegroundTaskPackageName(this@ScreenshotService)
                }
                val marker = ScreenshotData.saveScreenshotData(
                    "", appInfo, markerSessionId, user, state, eventTime = eventTime
                ).apply { isOcrComplete = true }
                screenCollectorSvc.add(marker)
            } catch (e: Exception) {
                Timber.e(e, "Error writing $state marker row")
            }
        }
        pendingMarkerWrites.add(job)
        job.invokeOnCompletion { pendingMarkerWrites.remove(job) }
    }

    private fun handleVirtualDisplayStopped() {
        val stoppedDisplay = virtualDisplay
        virtualDisplay = null
        try {
            stoppedDisplay?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing stopped virtual display")
        }

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing image reader for stopped virtual display")
        }
        imageReader = null

        val projectionStillPresent = mediaProjection != null
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            projectionStillPresent
        ) {
            if (isDeviceCurrentlyLocked()) {
                transitionProjectionState(
                    ProjectionInterruptionState.SCREEN_LOCKED_CAPTURE_SUSPENDED,
                    "legacy_virtual_display_stopped_while_locked"
                )
            } else {
                val recovered = setupVirtualDisplay()
                transitionProjectionState(
                    if (recovered) ProjectionInterruptionState.CAPTURING
                    else ProjectionInterruptionState.ERROR,
                    if (recovered) "legacy_virtual_display_recreated"
                    else "legacy_virtual_display_recreation_failed"
                )
            }
            return
        }

        markMediaProjectionRequestPending(this)
        transitionProjectionState(
            if (isDeviceCurrentlyLocked()) {
                ProjectionInterruptionState.SCREEN_LOCKED_CONSENT_REQUIRED
            } else {
                ProjectionInterruptionState.CONSENT_REQUIRED
            },
            "virtual_display_stopped_requires_fresh_consent"
        )
        showProjectionStoppedNotificationWhenAppropriate("virtual_display_stopped")

        // API 34+ permits only one VirtualDisplay per MediaProjection session. End the
        // unusable projection so its callback performs the remaining resource cleanup.
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping projection after virtual display termination")
            releaseProjectionResources()
        }
    }

    private fun showProjectionStoppedNotificationWhenAppropriate(reason: String) {
        if (isDeviceCurrentlyLocked()) {
            suppressMediaProjectionPromptUntilUnlock.set(true)
            logProjectionPromptEvent("visibility=deferred_while_locked;reason=$reason")
            return
        }

        showProjectionStoppedNotification()
        logProjectionPromptEvent("visibility=notification_shown;reason=$reason")
    }

    private fun cancelProjectionStoppedNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(RESTART_NOTIFICATION_ID)
        restartNotificationShown = false
    }

    private fun logProjectionPromptEvent(message: String) {
        Timber.i("Projection consent prompt: $message")
        coroutineScope.launch(Dispatchers.IO) {
            generalOperationsRepository.saveLog("SCREEN_SHARING_PROMPT", message)
        }
    }

    private fun isDeviceCurrentlyLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked
    }

    private fun finalizeInterruptionSession(reason: String) {
        if (!interruptionSessionFinalized.compareAndSet(false, true)) {
            Timber.d("Interruption session already finalized; ignoring duplicate reason=$reason")
            return
        }

        // Segments are built at the start of beginOcr() (AC-1043), not here, so a screen-off
        // does not build them twice.
        coroutineScope.launch(Dispatchers.IO) {
            generalOperationsRepository.buildCurrentSession(framesPerSecondConst, reason)
            generalOperationsRepository.saveSession(generalOperationsRepository.currentSession)
            sessionId = UUID.randomUUID().toString()
        }
    }

    private fun transitionProjectionState(
        newState: ProjectionInterruptionState,
        reason: String
    ) {
        val transitionMessage = synchronized(projectionStateLock) {
            val oldState = currentProjectionState
            if (oldState == newState) return

            currentProjectionState = newState
            projectionInterruptionState.postValue(newState)
            if (newState in CONSENT_REQUIRED_STATES && oldState !in CONSENT_REQUIRED_STATES) {
                logScreenshotState(ScreenshotState.NO_SCREEN_SHARING)
            }
            "${oldState.name}->${newState.name};" +
                "reason=$reason;" +
                "screenLocked=${isScreenLocked.get()};" +
                "studyRunning=${isRunning.value == true};" +
                "userPaused=${isPaused.value == true};" +
                "projectionPresent=${mediaProjection != null};" +
                "virtualDisplayPresent=${virtualDisplay != null};" +
                "consentPending=${isMediaProjectionRequestPending(this)};" +
                "promptInFlight=${isMediaProjectionPromptInFlight.get()}"
        }

        Timber.i("Screen-sharing state: $transitionMessage")
        coroutineScope.launch(Dispatchers.IO) {
            generalOperationsRepository.saveLog("SCREEN_SHARING_STATE", transitionMessage)
        }
    }

    override fun onScreenOff() {
        Timber.d("Screen turned off")
        cancelScreenUnlockProbe()
        logScreenshotState(ScreenshotState.SCREEN_LOCKED)
        // IMPORTANT: Do NOT call mediaProjection.stop() or stopCapturing() here.
        // On Android 14+, stopping the projection requires fresh user consent to restart.
        // The capture loop skips frames while isScreenLocked = true. Android 15 QPR1+
        // can stop projection automatically on lock; MediaProjection.Callback.onStop() is
        // authoritative and starts the fresh-consent recovery flow in that case.
        isScreenLocked.set(true)
        transitionProjectionState(
            if (mediaProjection != null && !isMediaProjectionRequestPending(this)) {
                ProjectionInterruptionState.SCREEN_LOCKED_CAPTURE_SUSPENDED
            } else {
                ProjectionInterruptionState.SCREEN_LOCKED_CONSENT_REQUIRED
            },
            "screen_off"
        )

        finalizeInterruptionSession("SCREEN_OFF")

        if (isMediaProjectionRequestPending(this)) {
            showProjectionStoppedNotificationWhenAppropriate("screen_off_with_consent_pending")
        }

        if (manualOcr.value == false) {
            beginOcr()
        }
    }

    override fun onScreenOn() {
        Timber.d("Screen turned on, but may still be locked")
        scheduleScreenUnlockProbe()
    }

    override fun onScreenUnlocked() {
        cancelScreenUnlockProbe()
        val firstUnlockSignal = isScreenLocked.compareAndSet(true, false)
        if (!firstUnlockSignal) {
            Timber.d("Duplicate screen-unlocked signal; rechecking projection recovery")
            if (isMediaProjectionRequestPending(this)) {
                suppressMediaProjectionPromptUntilUnlock.set(false)
                handleProjectionInterruption()
            }
            return
        }

        Timber.d("Screen unlocked")
        // Rearm ScreenStateReceiver's screen-off gate; the keyguard probe path has no
        // ACTION_USER_PRESENT to do it.
        isActionScreenOff.postValue(true)
        logScreenshotState(ScreenshotState.SCREEN_UNLOCKED)

        lastUnlockTime = System.currentTimeMillis()

        val currentTime = TimeUtility.getCurrentTimestamp()
        Timber.tag("SR_START").d("**** $currentTime ****")
        val sessionStartTime = currentTime.toInstant()
        generalOperationsRepository.currentSession = SessionTempEntity(sessionStart = sessionStartTime)
        interruptionSessionFinalized.set(false)

        if (isMediaProjectionRequestPending(this)) {
            transitionProjectionState(ProjectionInterruptionState.CONSENT_REQUIRED, "screen_unlocked")
            // Remind the participant; the consent dialog opens when they tap the notification
            // or open the app, and MainActivity's gate prevents a second dialog.
            suppressMediaProjectionPromptUntilUnlock.set(false)
            handleProjectionInterruption()
        } else if (
            isRunning.value == true &&
            isPaused.value != true &&
            mediaProjection != null &&
            virtualDisplay != null
        ) {
            transitionProjectionState(ProjectionInterruptionState.CAPTURING, "screen_unlocked_projection_alive")
            resetCapturing()
        } else if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isRunning.value == true &&
            isPaused.value != true &&
            mediaProjection != null &&
            virtualDisplay == null
        ) {
            val recovered = setupVirtualDisplay()
            transitionProjectionState(
                if (recovered) ProjectionInterruptionState.CAPTURING
                else ProjectionInterruptionState.ERROR,
                if (recovered) "legacy_unlock_virtual_display_recreated"
                else "legacy_unlock_virtual_display_recreation_failed"
            )
        }
    }

    private fun handleProjectionInterruption() {
        // Consent is requested only when the participant acts: tapping this notification or
        // opening the app (MainActivity.requestPendingProjectionConsentIfNeeded). The app does
        // not launch the consent dialog from the background. Repost in case the participant
        // dismissed it; setOnlyAlertOnce keeps a still-visible one from alerting again.
        restartNotificationShown = false
        showProjectionStoppedNotification()
        logProjectionPromptEvent("visibility=notification_shown;reason=projection_interrupted")
    }

    private fun scheduleScreenUnlockProbe(attempt: Int = 0) {
        cancelScreenUnlockProbe()
        val probe = Runnable {
            screenUnlockProbe = null
            if (!isScreenLocked.get()) return@Runnable

            if (!isDeviceCurrentlyLocked()) {
                logProjectionPromptEvent("unlock=detected_by_keyguard_probe;attempt=$attempt")
                onScreenUnlocked()
            } else if (attempt + 1 < MAX_SCREEN_UNLOCK_PROBES) {
                scheduleScreenUnlockProbe(attempt + 1)
            } else {
                logProjectionPromptEvent("unlock=probe_timeout;attempts=$MAX_SCREEN_UNLOCK_PROBES")
            }
        }
        screenUnlockProbe = probe
        projectionPromptHandler.postDelayed(probe, SCREEN_UNLOCK_PROBE_DELAY_MS)
    }

    private fun cancelScreenUnlockProbe() {
        screenUnlockProbe?.let(projectionPromptHandler::removeCallbacks)
        screenUnlockProbe = null
    }

        /**
     * Initializes the Tesseract OCR engine if not already initialized.
     */
    private fun initializeTesseract() {
        val dataPath = Assets.getTessDataPath(applicationContext)
        val language = Assets.language

        recognize.initTesseract(dataPath, language, TessBaseAPI.OEM_LSTM_ONLY)
    }

    private fun beginOcr() {
        synchronized(ocrLock) {
            if (ocrJob?.isActive == true) {
                Timber.tag(TAG).d("OCR is already running; ignoring duplicate request.")
                return
            }
            ocrJob = coroutineScope.launch(Dispatchers.IO) {
                val manualRequest = manualOcr.value == true
                var processedAny = false
                try {
                    synchronized(pendingMarkerWrites) { pendingMarkerWrites.toList() }.joinAll()
                    // The run works on the screenshots that exist now. Capture continues during a
                    // manual run, and without a bound the queue might never empty, so zip and
                    // upload would never start. Taken before segmenting so every row in the
                    // workset gets a segment.
                    val worksetMaxId = generalOperationsRepository.getMaxScreenshotId()

                    // Screenshots only become zip-eligible once they belong to an app segment (see
                    // ScreenshotDao's batch queries). The manual-upload trigger (button2 -> uploadCommand())
                    // calls straight into this function without going through a session-ending event
                    // first, so segments must be built here rather than assumed to already exist.
                    generalOperationsRepository.saveAllSessionSegments()

                    processedCount = 0
                    var screenshots = generalOperationsRepository.getScreenshotsToOcr(OCR_FETCH_BATCH_SIZE, worksetMaxId)
                    Timber.tag(TAG).d("Starting OCR: ${screenshots.size} images in the first batch.")

                    if ((screenshots.size >= OCR_IMAGE_THRESHOLD && isOlderThanTwoHours(lastOcrTime)) || manualRequest) {
                        lastOcrTime = System.currentTimeMillis()

                        // A manual request already started a fresh log with its request entry.
                        if (!manualRequest) {
                            WorkerProgressManager.clear()
                        }
                        initializeTesseract()
                        delay(3000)

                        // Oldest first, one batch at a time, until none are left. Automatic OCR runs
                        // only while the phone is locked; a manual request runs to the end.
                        var batchIndex = 0
                        while (screenshots.isNotEmpty() && isActive) {
                            if (!manualRequest && !isScreenLocked.get()) {
                                Timber.tag(TAG).d("Stopping automatic OCR because the screen was unlocked.")
                                break
                            }
                            batchIndex++
                            WorkerProgressManager.updateProgress("Starting OCR batch $batchIndex")
                            val batch = recognizeImages(screenshots, manualRequest)
                            if (batch.processed > 0) processedAny = true
                            if (batch.engineUnavailable || batch.processed == 0) break
                            screenshots = generalOperationsRepository.getScreenshotsToOcr(OCR_FETCH_BATCH_SIZE, worksetMaxId)
                        }
                        Timber.tag(TAG).d("Ending OCR.")
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    generalOperationsRepository.saveLog("OCR_ERROR", "OCR failed: ${ex.stackTraceToString()}")
                    ex.record()
                } finally {
                    // A manual request always zips and uploads, even with nothing new to OCR,
                    // so the Upload button never silently does nothing.
                    if (processedAny || manualRequest) {
                        enqueueZipAndUpload()
                    }
                    System.gc()
                    ocrSubmitted = false
                    manualOcr.postValue(false)
                    synchronized(ocrLock) {
                        ocrJob = null
                    }
                }
            }
        }
    }

    private suspend fun enqueueZipAndUpload() {
        WorkerProgressManager.updateProgress("OCR finished. Starting zip.")
        try {
            ZipFileWorker.enqueueThenUpload(this@ScreenshotService)
            Timber.d("ScreenshotService: zip and upload enqueued")
        } catch (e: Exception) {
            Timber.e(e, "ScreenshotService: could not enqueue zip and upload")
            WorkerProgressManager.updateProgress("Could not start zip and upload: ${e.message}")
        }
    }

    private class OcrBatchResult(val processed: Int, val engineUnavailable: Boolean)

    private suspend fun recognizeImages(images: List<ScreenshotEntity>, allowWhenUnlocked: Boolean): OcrBatchResult {
        if (!recognize.isInitialized) {
            Timber.tag(TAG).d("Tesseract is not initialized.")
            return OcrBatchResult(0, engineUnavailable = true)
        }

        val startTime = System.currentTimeMillis()
        var processedInBatch = 0
        for (screenshot in images) {
            if (!allowWhenUnlocked && !isScreenLocked.get()) break
            if (!processScreenshot(screenshot)) {
                return OcrBatchResult(processedInBatch, engineUnavailable = true)
            }
            processedInBatch++
            processedCount++
            WorkerProgressManager.updateProgress("Processing OCR image $processedCount.")
        }

        Timber.d("OCR completed for $processedInBatch images in ${System.currentTimeMillis() - startTime} ms.")
        return OcrBatchResult(processedInBatch, engineUnavailable = false)
    }

    /**
     * Runs OCR on one screenshot. Every row is finished, whatever happens, so the next fetch
     * does not return it again: a missing image or a timeout is stored with empty text (the
     * row's metadata still uploads), and a Tesseract error deletes the row (Recognize).
     *
     * @return false only when the OCR engine is unavailable; the row is left for a later run.
     */
    private suspend fun processScreenshot(screenshot: ScreenshotEntity): Boolean {
        try {
            Timber.tag(TAG).d("Processing image: ${screenshot.filePath}")
            if (recognize.processImage(screenshot)) {
                generalOperationsRepository.setScreenToOcrComplete(screenshot)
                return true
            }
            if (!recognize.isInitialized || recognize.stopped) {
                return false
            }
            screenshot.text = ""
            deleteImageIfTextOnly(screenshot)
            generalOperationsRepository.setScreenToOcrComplete(screenshot)
            logOcrFailure(screenshot.filePath, "Stored without OCR text")
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            ex.record()
            Timber.tag(TAG).w("Error processing image: ${screenshot.filePath}, ${ex.message}")
            screenshot.text = ""
            deleteImageIfTextOnly(screenshot)
            screenshot.id?.let { generalOperationsRepository.setScreenToOcrComplete(screenshot) }
            logOcrFailure(screenshot.filePath, "Error processing image: ${ex.stackTraceToString()}")
        }
        return true
    }

    /**
     * Without image uploads the zip step never collects images, so the image would stay on
     * the phone. Successful OCR deletes it in Recognize; failed rows need the same.
     */
    private suspend fun deleteImageIfTextOnly(screenshot: ScreenshotEntity) {
        val uploadImages = runCatching { generalOperationsRepository.getUser().uploadImages }.getOrDefault(true)
        if (!uploadImages) screenshot.filePath?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
    }

    private suspend fun logOcrFailure(filePath: String?, msg: String) {
        Timber.tag(TAG).w("Could not process OCR for file: $filePath")
        generalOperationsRepository.saveLog("OCRProcess", "Could not process OCR for filePath $filePath -> $msg")
    }

    /**
     * Launches a coroutine to save all session segments in the background.
     */
    private suspend fun saveSessionSegmentsInBackground() {
        withContext(Dispatchers.IO) {
            generalOperationsRepository.saveAllSessionSegments()
        }
    }

    /**
     * Checks if the provided timestamp is older than 5 minutes compared to the current time.
     *
     * @param timestamp The timestamp to check, in milliseconds since epoch
     * @return true if the timestamp is more than 5 minutes old, false otherwise
     */
    fun isOlderThanTwoHours(timestamp: Long): Boolean {
        val fiveMinutesInMillis = 2 * 60 * 60 * 1000L // 5 minutes in milliseconds
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp

        return (elapsedTime > fiveMinutesInMillis) || optimizeUploads.value == false
    }

    private fun releaseProjectionResources() {
        try {
            if (virtualDisplay != null) suppressNextVirtualDisplayStop.set(true)
            virtualDisplay?.release()
        } catch (e: Exception) {
            suppressNextVirtualDisplayStop.set(false)
        }
        virtualDisplay = null

        try { imageReader?.close() } catch (e: Exception) {}
        imageReader = null

        mediaProjection = null

        isMediaProjectionValid.postValue(false)
    }


}
