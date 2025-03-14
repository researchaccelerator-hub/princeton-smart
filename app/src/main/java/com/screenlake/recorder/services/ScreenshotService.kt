package com.screenlake.recorder.services

import android.R
import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.screenlake.MainActivity
import com.screenlake.data.database.entity.SessionTempEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.model.AppInfo
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings.RESTRICTED_APPS
import com.screenlake.recorder.screenshot.ScreenCollectorSvc
import com.screenlake.recorder.services.ScreenRecordService.Companion.appNameVsPackageName
import com.screenlake.recorder.services.ScreenRecordService.Companion.isPaused
import com.screenlake.recorder.services.ScreenRecordService.Companion.isRecording
import com.screenlake.recorder.services.ScreenRecordService.Companion.pauseTiming
import com.screenlake.recorder.services.ScreenRecordService.Companion.pausedTimer
import com.screenlake.recorder.services.ScreenRecordService.Companion.sessionId
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.BaseUtility.getForegroundTaskPackageName
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.utilities.TimeUtility.getFormattedScreenCaptureTime
import dagger.hilt.android.AndroidEntryPoint
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class ScreenshotService : Service(), ScreenStateReceiver.ScreenStateCallback {
    companion object {
        const val NOTIFICATION_ID = 1337
        const val CHANNEL_ID = "screenshot_service_channel"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val SCREENSHOT_INTERVAL_MS = 3000L
        const val RESTART_NOTIFICATION_ID = 1338
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SCREENSHOT_TIMEOUT_MS = 5000L  // 5 seconds timeout for screenshot operations
        private const val ERROR_NOTIFICATION_ID = 1339
        val isRunning = MutableLiveData<Boolean>()
        // Action for restarting media projection
        const val ACTION_RESTART_PROJECTION = "com.example.screenshotapp.RESTART_PROJECTION"
        val uploadTotal = MutableLiveData<Int>()
        val manualUploadPercentComplete = MutableLiveData<Double>()
        var uploadCountMsg = MutableLiveData<Int>()
    }

    private var countDownTimer: CountDownTimer? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var handlerThread: Thread? = null
    private var threadLooper: Looper? = null
    private var width = 0
    private var height = 0
    private var density = 0
    private var isScreenLocked = AtomicBoolean(false)
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var consecutiveErrors = AtomicInteger(0)
    var moveForward = true
    private var screenshotJob: Job? = null

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

    var user = UserEntity()
    var currentAppInUse = AppInfo()

    // Store media projection data for later restart
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
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

            // Register screen state receiver
            screenStateReceiver = ScreenStateReceiver(this)
            screenStateReceiver?.register(this)
        } catch (e: Exception) {
            Timber.e(e, "Error in onCreate")
            stopSelf()
        }
    }

    private fun pauseRecordingForMinutes()
    {
        if(isRecording.value == true && isPaused.value!!){
            countDownTimer = object : CountDownTimer(pauseTiming.value!!, 1000)  {

                override fun onTick(millisUntilFinished: Long) {
                    if(isPaused.value == false){
                        countDownTimer?.cancel()

                        if(isRecording.value == true) pausedTimer.postValue("recording")

                    }else{
                        pausedTimer.postValue("resuming in: " + ""+String.format("%d min, %d sec",
                            TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                            TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))))
                    }
                }

                override fun onFinish() {
                    isPaused.postValue(false)
                    pausedTimer.postValue("recording")
                    countDownTimer = null
                }
            }

            countDownTimer?.start()
        }else if(isPaused.value != null && !isPaused.value!!){
            countDownTimer?.cancel()
            pausedTimer.postValue("")
            isPaused.postValue(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent != null) {
                when (intent.action) {
                    ACTION_RESTART_PROJECTION -> {
                        // Media projection permission has been re-granted
                        Timber.d("Restarting media projection")
                        val newResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                        val newData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(EXTRA_DATA)
                        }

                        if (newResultCode != Activity.RESULT_CANCELED && newData != null) {
                            // Store for potential future restarts
                            resultCode = newResultCode
                            resultData = newData

                            setupMediaProjection(newResultCode, newData)
                            startCapturing()
                        } else {
                            Timber.e("Invalid result code or missing data for projection restart")
                            showErrorNotification("Could not restart screen capture", "Permission data is invalid")
                        }
                    }
                    else -> {
                        // Initial start of the service
                        val newResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                        val newData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(EXTRA_DATA)
                        }

                        if (newResultCode != Activity.RESULT_CANCELED && newData != null) {
                            // Store for potential future restarts
                            resultCode = newResultCode
                            resultData = newData

                            setupMediaProjection(newResultCode, newData)
                            startCapturing()
                        } else {
                            Timber.e("Invalid result code or missing data for initial start")
                            showErrorNotification("Could not start screen capture", "Permission data is invalid")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in onStartCommand")
            showErrorNotification("Service Start Error", "Could not start screenshot service: ${e.message}")
        }
        return START_NOT_STICKY
    }

    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Service")
            .setContentText("Taking screenshots every 3 seconds")
            .setSmallIcon(R.drawable.ic_menu_camera)
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
            // Fallback to starting foreground without type
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Timber.e(e2, "Critical error starting foreground service")
                stopSelf()
            }
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Timber.e("Media projection is null after getMediaProjection")
                showErrorNotification("Error", "Could not create media projection")
                return
            }

            // Register callback for Android 15+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Timber.d("MediaProjection stopped callback (Android 15+)")
                        coroutineScope.launch {
                            stopCapturing()
                            showProjectionStoppedNotification()
                        }
                    }
                }, handler)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up media projection")
            showErrorNotification("Setup Error", "Failed to setup screen capture: ${e.message}")
        }
    }

    private fun showProjectionStoppedNotification() {
        try {
            // Create a notification to inform the user that projection stopped
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screenshot Service Stopped")
                .setContentText("Media projection permission revoked. Tap to restart.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSmallIcon(R.drawable.ic_menu_camera)
                .setContentIntent(NotificationHelper(this).getMainActivityPendingIntent())
                .setAutoCancel(true)
                .build()

            notificationManager.notify(RESTART_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
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
        }
    }

    private fun startCapturing() {
        if (isRunning.value == true) {
            return
        }

        // Reset consecutive errors counter
        consecutiveErrors.set(0)

        coroutineScope.launch {
            try {
                // Check if screen is currently locked
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                isScreenLocked.set(keyguardManager.isKeyguardLocked)

                withContext(Dispatchers.Main) {
                    // Setup virtual display on main thread (MediaProjection requires main thread)
                    if (!setupVirtualDisplay()) {
                        Timber.e("Failed to set up virtual display")
                        showErrorNotification("Setup Error", "Failed to create virtual display")
                        return@withContext
                    }
                    isRunning.postValue(true)
                }

                // Load app data with timeout and error handling
                try {
                    withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                        val allApps = withContext(Dispatchers.Default) {
                            try {
                                generalOperationsRepository.getALlApps()
                            } catch (e: Exception) {
                                Timber.e(e, "Error fetching apps")
                                emptyList()
                            }
                        }

                        appNameVsPackageName = allApps.associate { it.packageName to it.appName }

                        ScreenRecordService.Companion.user = withContext(Dispatchers.Default) {
                            try {
                                generalOperationsRepository.getUser()
                            } catch (e: Exception) {
                                Timber.e(e, "Error fetching user")
                                UserEntity() // Fallback to empty user
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading initial data with timeout")
                    // Continue anyway, will use empty data
                }

                // Start screenshot loop with error handling
                screenshotJob = launch(errorHandler) {
                    while (isRunning.value == true && isActive) {
                        if (!isScreenLocked.get()) {
                            try {
                                takeScreenshot()
                                // Reset consecutive errors on success
                                if (consecutiveErrors.get() > 0) {
                                    consecutiveErrors.set(0)
                                }
                            } catch (e: Exception) {
                                handleScreenshotError(e)
                            }
                        } else {
                            Timber.d("Screen locked, skipping screenshot")
                        }

                        delay(SCREENSHOT_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in startCapturing")
                showErrorNotification("Capture Error", "Failed to start capture: ${e.message}")
                stopCapturing()
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
                        setupMediaProjection(resultCode, resultData!!)
                        startCapturing()
                    } else {
                        showErrorNotification(
                            "Screenshot Service Error",
                            "Too many errors occurred. Please restart the app."
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during error recovery")
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

            // Create virtual display
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            return virtualDisplay != null
        } catch (e: Exception) {
            Timber.e(e, "Error setting up virtual display")
            return false
        }
    }

    private suspend fun takeScreenshot() {
        // Use withTimeout to ensure the operation doesn't hang
        withTimeout(SCREENSHOT_TIMEOUT_MS) {
            try {
                val currentAppInUse = withContext(Dispatchers.Default) {
                    getForegroundTaskPackageName(this@ScreenshotService)
                }

                moveForward = !(RESTRICTED_APPS.contains(currentAppInUse.apk))

                if (!moveForward) {
                    val screenshotData = ScreenshotData.saveScreenshotData(
                        "",
                        currentAppInUse,
                        sessionId,
                        ScreenRecordService.Companion.user,
                        "Restricted",
                        true
                    )

                    try {
                        screenCollectorSvc.add(screenshotData)
                    } catch (e: Exception) {
                        Timber.e(e, "Error adding restricted app data to collector")
                    }
                    return@withTimeout
                }

                val image = withContext(Dispatchers.IO) {
                    imageReader?.acquireLatestImage()
                } ?: run {
                    Timber.e("Failed to acquire image, imageReader returned null")
                    return@withTimeout
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
    }

    private suspend fun saveImageToFile(image: Image, appInfo: AppInfo) {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var fileOutputStream: FileOutputStream? = null

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                // Create bitmap
                bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Create file
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                val filename = "${applicationContext.filesDir.path}/img_${UUID.randomUUID()}Screenshot_$timestamp.jpg"

                val screenshotData = ScreenshotData.saveScreenshotData(filename, appInfo, sessionId, user)

                try {
                    screenCollectorSvc.add(screenshotData)
                } catch (e: Exception) {
                    Timber.e(e, "Error adding screenshot data to collector")
                }

                val screenshotsDir = File(filename)

                // Save the bitmap to a file
                fileOutputStream = FileOutputStream(screenshotsDir)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fileOutputStream)
                Timber.d("Screenshot saved: ${screenshotsDir.absolutePath}")
            } catch (e: IOException) {
                Timber.e(e, "I/O error saving screenshot")
                throw e
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "Out of memory when creating bitmap")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error saving screenshot")
                throw e
            } finally {
                // Close resources
                try {
                    fileOutputStream?.close()
                } catch (e: IOException) {
                    Timber.e(e, "Error closing FileOutputStream")
                }

                bitmap?.recycle()
            }
        }
    }

    private fun stopCapturing() {
        isRunning.postValue(false)

        // Cancel the screenshot job
        screenshotJob?.cancel()
        screenshotJob = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing virtual display")
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing image reader")
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping media projection")
        }
        mediaProjection = null
    }

    override fun onDestroy() {
        try {
            screenStateReceiver?.unregister(this)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering screen state receiver")
        }
        screenStateReceiver = null

        stopCapturing()

        try {
            coroutineScope.cancel() // Cancel all coroutines when service is destroyed
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling coroutine scope")
        }

        super.onDestroy()
    }

    // Screen state callback implementations
    override fun onScreenOff() {
        Timber.d("Screen turned off")
        isScreenLocked.set(true)
        saveSessionSegmentsInBackground()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mediaProjection == null && resultData != null) {
            // showProjectionStoppedNotification()
        }
    }

    override fun onScreenOn() {
        Timber.d("Screen turned on, but may still be locked")
    }

    override fun onScreenUnlocked() {
        Timber.d("Screen unlocked")

        val currentTime = TimeUtility.getCurrentTimestamp()
        Timber.tag("SR_START").d("**** $currentTime ****")
        val sessionStartTime = currentTime.toInstant()
        generalOperationsRepository.currentSession = SessionTempEntity(sessionStart = sessionStartTime)

        isScreenLocked.set(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mediaProjection == null && resultData != null) {
            // showProjectionStoppedNotification()
        }
    }

    /**
     * Launches a coroutine to save all session segments in the background.
     */
    private fun saveSessionSegmentsInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            generalOperationsRepository.saveAllSessionSegments()
        }
    }
}