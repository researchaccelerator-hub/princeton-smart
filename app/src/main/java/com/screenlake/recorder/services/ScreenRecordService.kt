package com.screenlake.recorder.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.ACTION_PAUSE_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.ACTION_SHOW_RECORDING_FRAGMENT
import com.screenlake.recorder.constants.ConstantSettings.ACTION_START_MANUAL_UPLOAD
import com.screenlake.recorder.constants.ConstantSettings.ACTION_START_OR_RESUME_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.ACTION_STOP_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.COROUTINE_EXCEPTION_HANDLER
import com.screenlake.recorder.constants.ConstantSettings.NOTIFICATION_CHANNEL_ID
import com.screenlake.recorder.constants.ConstantSettings.NOTIFICATION_CHANNEL_NAME
import com.screenlake.recorder.constants.ConstantSettings.OUT_OF_MEMORY
import com.screenlake.recorder.constants.ConstantSettings.RECORDING_SERVICE
import com.screenlake.recorder.constants.ConstantSettings.RESTRICTED_APPS
import com.screenlake.recorder.constants.ConstantSettings.RE_ENABLE_NOTIFICATION
import com.screenlake.recorder.constants.ConstantSettings.SAFE_LAUNCH
import com.screenlake.recorder.constants.ConstantSettings.SAFE_LAUNCH_CANCELLATION
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_IMAGE_QUALITY
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_MAPPING
import com.screenlake.recorder.constants.ConstantSettings.SERVICE_STOP
import com.screenlake.data.model.AppInfo
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.screenshot.ScreenCollector
import com.screenlake.recorder.screenshot.ScreenCollectorSvc
import com.screenlake.recorder.services.AccessibilityServiceDependencies.context
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.services.util.SharedPreferencesUtil
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.utilities.BaseUtility.getForegroundTaskPackageName
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.utilities.TimeUtility.getFormattedScreenCaptureTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ScreenRecordService : LifecycleService() {
    private var lifecycleRegistry: LifecycleRegistry? = null
    private var mReceiver: BroadcastReceiver? = null
    private var isFirstRun = true
    private var serviceKilled = false
    private var serviceContext: Context = this
    private var imagesProduced = 0
    var mHandler: Handler? = null
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private var tag = "RECORD_SERVICE"
    private var countDownTimer: CountDownTimer? = null
    private val metrics = Resources.getSystem().displayMetrics
    private val mWidth = metrics.widthPixels
    private val mHeight = metrics.heightPixels
    private var lastImageSizeTimeTwo = 0L
    private var outOfMemory = false
    private var notificationID = 1
    private var virtualDisplayAttempts = 0
    private val virtualDisplayLimit = 20
    private val semaphore = Semaphore(1)
    private var virtualDisplay: VirtualDisplay? = null
    private var mImageReader: ImageReader? = null
    private val MEDIA_PROJECTION_REQUEST_CODE = 1001
    private var captureThread: Thread? = null
    private var threadLooper: Looper? = null


    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    @Inject
    lateinit var screenCollectorSvc: ScreenCollectorSvc

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var screenCollector: ScreenCollector

    val handler = CoroutineExceptionHandler { _, exception ->
        println("ScreenRecording Service ExceptionHandler got $exception")
        CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog(COROUTINE_EXCEPTION_HANDLER, "ScreenRecording Service ExceptionHandler got ${exception.message} stacktrace -> ${ScreenshotData.ocrCleanUp(exception.stackTraceToString())}") }
        FirebaseCrashlytics.getInstance().recordException(exception)
    }

    // singleton objects
    companion object {
        var sessionId = UUID.randomUUID().toString()
        val isScreenOn = MutableLiveData<Boolean>()
        val isRecording = MutableLiveData<Boolean>()
        val screenshotInterval = MutableLiveData<Long>()
        val manualUploadPercentComplete = MutableLiveData<Double>()
        val isPaused = MutableLiveData<Boolean>()
        var uploadCountMsg = MutableLiveData<Int>()
        var offlineUpdates = MutableLiveData<Int>()
        var pausedTimer = MutableLiveData<String>()
        var projection : MediaProjection? = null
        var isProjectionValid = MutableLiveData<Boolean>()
        const val framesPerSecondConst: Double = 0.3
        var framesPerSecond: Double = framesPerSecondConst
        // Settings
        var uploadOverWifi = MutableLiveData<Boolean>()
        var uploadOverPower = MutableLiveData<Boolean>()
        var pauseTiming = MutableLiveData<Long>()

        var restrictedApps = MutableLiveData<HashSet<String>>()

        val isMaintenanceOccurring = MutableLiveData<Boolean>()

        val isPowerConnected = MutableLiveData<Boolean>()

        val isBatteryLow = MutableLiveData<Boolean>()

        val uploadTotal = MutableLiveData<Int>()

        val notUploaded = MutableLiveData<Int>()

        val uploadedThisWeek = MutableLiveData<Int>()

        // TODO: Is this not getting set??
        var lastUnlockTime: Long? = null

        var appNameVsPackageName = mapOf<String, String>()

        var user = UserEntity()

        var isMediaProjectionValid = false

        //set initial values for MutableLiveData objects
        fun postInitialValues(){
            // Disabled, user should not set this value.
            // framesPerSecond = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.fps), "1.0")?.toDouble()!!
            screenshotInterval.postValue(SCREENSHOT_MAPPING[framesPerSecond])
            isPaused.postValue(false)
            uploadCountMsg.postValue(0)
            offlineUpdates.postValue(0)
            pauseTiming.postValue(300000)
            restrictedApps.postValue(hashSetOf())
        }
    }

    //set initial values for MutableLiveData objects
    private fun postInitialValues(){
        // Disabled, user should not set this value.
        // framesPerSecond = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.fps), "1.0")?.toDouble()!!
        screenshotInterval.postValue(SCREENSHOT_MAPPING[framesPerSecond])
        isPaused.postValue(false)
        uploadCountMsg.postValue(0)
        offlineUpdates.postValue(0)
        uploadOverWifi.postValue(SharedPreferencesUtil.getLimitDataUsage(this))
        uploadOverPower.postValue(SharedPreferencesUtil.getLimitPowerUsage(this))
        pauseTiming.postValue(300000)
        restrictedApps.postValue(hashSetOf())
    }

    private fun postInitialValuesKillService(){
        // framesPerSecond = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.fps), "0.5")?.toDouble()!!
        screenshotInterval.postValue(SCREENSHOT_MAPPING[framesPerSecond])
        isRecording.postValue(false)
        isPaused.postValue(false)
        offlineUpdates.postValue(0)
        uploadOverWifi.postValue(SharedPreferencesUtil.getLimitDataUsage(this))
        uploadOverPower.postValue(SharedPreferencesUtil.getLimitPowerUsage(this))
        pauseTiming.postValue(300000)
        restrictedApps.postValue(hashSetOf())
    }

    // service on create
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)

         mReceiver = SystemScreenRecordEventReceiver()
         registerReceiver(mReceiver, filter)
        serviceSetUp()
    }

    private var isThreadRunning = false

    private fun serviceSetUp() {
        postInitialValues()
        subscribeListeners()

        // Only start if not already running
        if (captureThread == null || !captureThread!!.isAlive) {
            // Start capture handling thread for imageAvailableListener
            captureThread = object : Thread() {
                override fun run() {
                    Looper.prepare()
                    threadLooper = Looper.myLooper() // Store reference to this thread's looper
                    mHandler = Handler(threadLooper!!) // Use this thread's looper, not main looper
                    Looper.loop()
                }
            }
            captureThread!!.start()
        }
    }

    // Call this when you need to cancel the thread
    fun cancelCaptureThread() {
        // First quit the looper to stop the thread cleanly
        threadLooper?.quit()

        // Optional: Wait for thread to finish with timeout
        captureThread?.let { thread ->
            try {
                thread.join(1000) // Wait up to 1 second
                if (thread.isAlive) {
                    // If thread is still alive after timeout, interrupt it
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                Timber.e("Error waiting for thread to finish $e")
            }
        }

        // Clear references
        threadLooper = null
        mHandler = null
        captureThread = null
    }

    @SuppressLint("WrongConstant")
    // launch the coroutine job to take screenshots
    private fun startScreenshotCoroutineJob(semaphore: Semaphore) = lifecycleScope.safeLaunch {
        supervisorScope {
            val firstOutOfMemoryNotification = true

            val allApps = withContext(Dispatchers.Default) {
                generalOperationsRepository.getALlApps()
            }

            appNameVsPackageName = allApps.associate { it.packageName to it.appName }


            val restricted = allApps.filter { it.isUserRestricted }.map { it.appName }.toHashSet()

            user = withContext(Dispatchers.Default) {
                generalOperationsRepository.getUser()
            }

            val zipCount = generalOperationsRepository.getZipCount()

//            if (zipCount >= 3) {
//                Toast.makeText(this@ScreenRecordService, "Please check your Download folder for Screenshots and app logs.", Toast.LENGTH_LONG).show()
//                return@supervisorScope
//            }

            restrictedApps.postValue(restricted)
            var moveForward = true
            while (isRecording.value == true) {
                yield()     // necessary to cancel the coroutine
                //.d("*** screenshot coroutine in service class ***")
                delay(3000)

                if(isProjectionValid.value == false){
                    Timber.d("Projection is invalid")
                    semaphore.release()
                    stopSelf()
                    return@supervisorScope
                }

                if((isScreenOn.value == false) || isPaused.value == true){
                    //screen is off, do nothing this iteration
                    semaphore.release()
                    cancelCaptureThread()
                    this.cancel()
                    return@supervisorScope
                }

                val begin0 = System.currentTimeMillis()

                val currentAppInUse = getForegroundTaskPackageName(serviceContext)

                moveForward = !(RESTRICTED_APPS.contains(currentAppInUse.apk))

                if (!moveForward){
                    val screenshotData = ScreenshotData.saveScreenshotData("", currentAppInUse, sessionId, user, "Restricted", true)
                    screenCollectorSvc.add(screenshotData)
                    continue
                }

                if (storageCheck(firstOutOfMemoryNotification)) continue

                Timber.d("current app in foreground: $currentAppInUse")
                Timber.d("Is Screen On? $isScreenOn")


                getScreenshotFromVirtualDisplay(currentAppInUse, begin0)
            }
        }
    }

    private suspend fun storageCheck(firstOutOfMemoryNotification: Boolean): Boolean {
        // Memory check!
        val percentageStorageUsed = HardwareChecks.getPercentageStorageUsed()
        val percentUsedLimitInterval = ConstantSettings.getPercentUsedLimitInterval()
        if (firstOutOfMemoryNotification && outOfMemory && percentageStorageUsed >= percentUsedLimitInterval) {
            showNotification(
                "Please upload screenshots",
                "Your phone is nearly out of memory, please upload screenshots or clear some space."
            )
            Timber.tag(tag).w("NOT ENOUGH AVAILABLE MEMORY!")
            generalOperationsRepository.saveLog(OUT_OF_MEMORY)
            generalOperationsRepository.saveLog(OUT_OF_MEMORY, percentageStorageUsed.toString())

            return true
        } else if (percentageStorageUsed >= percentUsedLimitInterval) {
            generalOperationsRepository.saveLog(OUT_OF_MEMORY)
            Timber.tag(tag).w("NOT ENOUGH AVAILABLE MEMORY!")
            return true
        } else if (percentageStorageUsed < percentUsedLimitInterval) {
            outOfMemory = false
        }
        return false
    }

    private suspend fun getScreenshotFromVirtualDisplay(
        currentAppInUse: AppInfo,
        begin0: Long
    ) {
        try {
            val begin1 = System.currentTimeMillis()

            mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)

            /* code starts */
            val screenshotData =
                createScreenshotImage(user, mImageReader!!, currentAppInUse) ?: return

            /* code ends */

            val end1 = System.currentTimeMillis()

            Timber.d("=====> createScreenshotImage => Elapsed time: ${end1 - begin1}")

            if (screenshotData.filePath == null) {
                Timber.d("File is empty, skipping...")
                return
            }


            val begin2 = System.currentTimeMillis()

            /* code starts */

            // sleep for 2 seconds
            screenshotData.currentAppInUse = currentAppInUse.apk

            /* code ends */

            val end2 = System.currentTimeMillis()

            Timber.d("=====> getForegroundTaskPackageName => Elapsed time: ${end2 - begin2}")
            var retryCount = 0
            val retryLimit = 10

            val screenshotFile = File(screenshotData.filePath!!)
            while (!screenshotFile.exists() && retryCount <= retryLimit) {
                Timber.tag(tag)
                    .w("File => ${screenshotData.filePath} not ready, retrying $retryCount number of times.")
                retryCount += 1
                delay(150L)
            }

            if (screenshotFile.exists()) {

                lastImageSizeTimeTwo = Math.max(lastImageSizeTimeTwo, (screenshotFile.length()))

                screenCollectorSvc.add(screenshotData)
                updateRecordFragmentNotification(imagesProduced)
                mImageReader!!.close()
            } else {
                mImageReader!!.close()
                return
            }

            val end0 = System.currentTimeMillis()
            Timber.d("=====> Workflow total time => Elapsed time: ${end0 - begin0}")
        } catch (exception: Exception) {
            Timber.tag(tag).e(exception)
            FirebaseCrashlytics.getInstance().recordException(exception); generalOperationsRepository.saveLog(
                tag,
                exception.message.toString()
            );
            generalOperationsRepository.saveLog(
                RECORDING_SERVICE,
                "${exception.message} stacktrace -> ${ScreenshotData.ocrCleanUp(exception.stackTraceToString())}"
            )
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

    //logic to capture a screenshot image
    @SuppressLint("WrongConstant", "SimpleDateFormat")
    private fun createScreenshotImage(user: UserEntity, mImageReader: ImageReader, currentAppInUse:AppInfo) : ScreenshotEntity? {
        var screenDensity = 0

        val filename = "${serviceContext.filesDir.path}/img_${UUID.randomUUID()}_${getFormattedScreenCaptureTime()}.jpg"

        val screenshotData = ScreenshotData.saveScreenshotData(filename, currentAppInUse, sessionId, user)

        screenDensity = metrics.densityDpi

        //Create the image reader
        try {

            virtualDisplay = virtualDisplay ?: projection!!.createVirtualDisplay(
                filename,
                mWidth, mHeight, screenDensity,
                0,
                mImageReader.surface, null, null )

            virtualDisplay?.surface = mImageReader.surface
            // TODO: Flag when screen recorder is stolen from Screenlake
            //capture the image and write to downloads folder
            try {
                if (virtualDisplay != null) {
                    setImageListener(filename, virtualDisplay!!, mImageReader)
                }else{
                    throw NullPointerException()
                }
            }catch (e: Exception){
                Timber.e(e)
                CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("Exception",ScreenshotData.ocrCleanUp(e.stackTraceToString())) }
                FirebaseCrashlytics.getInstance().recordException(e); // saveLog(TAG, e.message.toString());
            }
            // If we make it here, reset any retry conditions.
            if(virtualDisplayAttempts > 0){
                virtualDisplayAttempts = 0
                screenshotInterval.postValue(SCREENSHOT_MAPPING[framesPerSecond])
            }

            return screenshotData
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog("Exception", ScreenshotData.ocrCleanUp(e.stackTraceToString())) }
            // Loss projection for some reason
            if(e is SecurityException){
                isProjectionValid.postValue(false)
                notificationID = getRandomNumber(1, 100)
                showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
                FirebaseCrashlytics.getInstance().log("Virtual display failed.")
                FirebaseCrashlytics.getInstance().recordException(e); //saveLog(TAG, e.message.toString());
                Timber.e("====> Virtual display failed! <==== ${e}")
                killService("Virtual display failed -> $e")
                // TODO: Possibly kill projection
            }else{
                // Dont exit immediately. Error may disappear with time.
                if(virtualDisplayAttempts < virtualDisplayLimit){

                    virtualDisplayAttempts++

                    // Backoff retries.
                    when (virtualDisplayAttempts) {
                        5 -> {
                            screenshotInterval.postValue(10000L)
                        }
                        10 -> {
                            screenshotInterval.postValue(30000L)
                        }
                        15 -> {
                            screenshotInterval.postValue(90000L)
                        }
                    }
                }else{
                    showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
                    FirebaseCrashlytics.getInstance().log("Virtual display failed.")
                    FirebaseCrashlytics.getInstance().recordException(e); // saveLog(TAG, e.message.toString());
                    virtualDisplayAttempts = 0
                    killService("Virtual display failed after retrying -> $e")
                }
            }
        }

        return null
    }

    private fun showNotification(title: String, contextText: String, notificationId: Int = 124){
        CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog(RE_ENABLE_NOTIFICATION, contextText) }
        val builder = NotificationCompat.Builder(serviceContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_screenlake_logo)
            .setContentTitle(title)
            .setContentText(contextText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(getMainActivityPendingIntent())

        with(NotificationManagerCompat.from(serviceContext)) {
            // notificationId is a unique int for each notification that you must define
            if (ActivityCompat.checkSelfPermission(
                    this@ScreenRecordService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notify(notificationId,
                builder.build())
        }
    }

    private fun getRandomNumber(min: Int, max: Int): Int {
        return (Math.random() * (max - min) + min).toInt()
    }

    private fun killService(msg: String = "") {
        CoroutineScope(Dispatchers.IO).launch { generalOperationsRepository.saveLog(SERVICE_STOP, msg) }
        serviceKilled = true
        isFirstRun = true
        mImageReader?.close()
        mImageReader = null
        stopForegroundService()
        postInitialValuesKillService()
        stopSelf()
    }

    private fun stopForegroundService() {
        // Stop the foreground service and remove the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use the new overload with flags
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            // Fallback for older versions
            stopForeground(true)
        }

//        projection?.registerCallback(object : MediaProjection.Callback() {
//            override fun onStop() {
//                super.onStop()
//                Timber.d("MediaProjection stopped, cleaning up resources.")
//                isMediaProjectionValid = false
//                // stopSelf()
//            }
//        }, null)

        // Stop the service itself
        stopSelf()
    }

    private fun updateRecordFragmentNotification(message: Int){
        uploadCountMsg.value = message
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            generalOperationsRepository.saveLog("SCREEN_RECORD_SERVICE_ON_DESTROY", "")
        }

        ScreenRecordService.isRecording.postValue(false)

        unregisterReceiver(mReceiver)
    }

    //logic to start stop or pause the recording service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { actionIntent ->
            when (actionIntent.action) {
                ACTION_START_OR_RESUME_SERVICE -> handleStartOrResumeService()
                ACTION_PAUSE_SERVICE -> handlePauseService()
                ACTION_STOP_SERVICE -> handleStopService()
                ACTION_START_MANUAL_UPLOAD -> CoroutineScope(Dispatchers.IO).launch { handleManualUpload() }
                else -> Timber.d("Unknown action received in onStartCommand")
            }
        } ?: run {
            // Handle null intent case
            handleNullIntent()
        }

        // Handle service restart with null intent
        if (intent == null) {
            Timber.d("Service restarted with null intent")
            showNotification(
                getString(R.string.screenlake),
                getString(R.string.please_re_enable_screen_recording)
            )
            FirebaseCrashlytics.getInstance().recordException(RuntimeException("Service was killed"))
        } else {
            val mediaProjectionData: Intent? = intent.getParcelableExtra("media_projection_data")
            if (mediaProjectionData != null) {
                startForegroundServiceWithNotification()
                initializeMediaProjection(mediaProjectionData)
            }
            Timber.d("Service restarted successfully.")
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_screenlake_logo)
            .setContentTitle(getString(R.string.screenlake_is_using_the_accessibility_service_to_record_your_screen_in_the_background))
            .setContentText(getString(R.string._00_00_00))
            .setContentIntent(getMainActivityPendingIntent())

        val notification = notificationBuilder.build()
        startForeground(1, notification)
    }

    class ProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Timber.d("Media projection stopped")
            // Handle the stopped state (e.g., permission revoked)
        }
    }

    private fun initializeMediaProjection(mediaProjectionData: Intent) {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        projection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mediaProjectionData)


        // Verify the registration with logging
        try {
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Timber.d("MediaProjection stopped, cleaning up resources.")
//                    isMediaProjectionValid = false
//                    showNotification("Screenlake", "Please re-enable screen recording.", notificationID)
//                    stopSelf()
                }
            }, null)

            if (BaseUtility.isAndroidFifteen() && ScreenRecordService.isRecording.value == false) {
                serviceSetUp()
            }

            ScreenRecordService.isRecording.postValue(true)
            Timber.d("Callback registered successfully")
        } catch (e: Exception) {
            Timber.d("Failed to register callback ${e.message}")
        }
    }

    private fun setImageListener(path: String, virtualDisplay: VirtualDisplay, mImageReader: ImageReader) {
        mImageReader.setOnImageAvailableListener({ image ->
            val planes = image.acquireLatestImage()?.planes

            if (planes != null) {
                val buffer: ByteBuffer = planes[0].buffer
                val pixelStride: Int = planes[0].pixelStride
                val rowStride: Int = planes[0].rowStride
                val rowPadding: Int = rowStride - pixelStride * mWidth

                // TODO make sure changing the config value from ARGB_8888 causes no new issues.
                // create bitmap
                var bitmap: Bitmap? = Bitmap.createBitmap(
                    mWidth + rowPadding / pixelStride,
                    mHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap?.copyPixelsFromBuffer(buffer)
                buffer.clear()

                // write bitmap to a file
                val fos = FileOutputStream(
                    path
                )
                bitmap?.compress(
                    Bitmap.CompressFormat.JPEG,
                    SCREENSHOT_IMAGE_QUALITY,
                    fos
                )
                fos.close()

                bitmap?.recycle()
                bitmap = null

                imagesProduced++

                Timber.tag("SCREENSHOT").d("**** ${TimeUtility.getCurrentTimestamp()} ****")

                //close imageReader and virtualDisplay
                image.close()
                // virtualDisplay.surface = null
//                virtualDisplay.release()
            }
        }, null)
    }

    private fun getMainActivityPendingIntent() : PendingIntent {

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_REQUEST_MEDIA_PROJECTION"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return PendingIntent.getActivity(
            this,
            MEDIA_PROJECTION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(notificationManager: NotificationManager){
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun subscribeListeners() {
        // Observe recording state and start screenshot coroutine if recording begins
        isRecording.observe(this) { isRecording ->
            Timber.d("Recording state changed to: $isRecording")
            if (isRecording && semaphore.tryAcquire()) {
                startScreenshotCoroutineJob(semaphore)
            }
        }

        // Observe changes in `uploadCountMsg` and log updates
        uploadCountMsg.observe(this) { message ->
            Timber.d("uploadCountMsg updated: $message")
        }

        // Observe screen-on state and start screenshot coroutine if applicable
        isScreenOn.observe(this) { screenOn ->
            Timber.d("Screen state changed to: $screenOn")
            if (screenOn && (isRecording.value == true || isRecording.value == null) && semaphore.tryAcquire()) {
                startScreenshotCoroutineJob(semaphore)
            }
        }
    }
    
    private fun CoroutineScope.safeLaunch(block: suspend CoroutineScope.() -> Unit): Job {
        return this.launch {
            try {
                block()
            } catch (ce: CancellationException) {
                generalOperationsRepository.saveLog(SAFE_LAUNCH_CANCELLATION, "\"${ce.message} stacktrace -> ${ScreenshotData.ocrCleanUp(ce.stackTraceToString())}\"")
                Timber.w(ce)
                // You can ignore or log this exception
            } catch (exception: Exception) {
                // Here it's better to at least log the exception
                generalOperationsRepository.saveLog(SAFE_LAUNCH, exception.message.toString())
                FirebaseCrashlytics.getInstance().recordException(exception); generalOperationsRepository.saveLog(tag, "\"${exception.message} stacktrace -> ${ScreenshotData.ocrCleanUp(exception.stackTraceToString())}\"");
                Timber.w(exception)
            }
        }
    }

    private fun handleStartOrResumeService() {
        if (isFirstRun) {
            isFirstRun = false
            Timber.d("Service started for the first time")
        } else {
            Timber.d("Service resumed")
        }
    }

    private fun handlePauseService() {
        pauseRecordingForMinutes()
        Timber.d("Service paused")
    }

    private fun handleStopService() {
        killService("Service manually stopped by the user.")
        projection = null
        Timber.d("Service stopped")
    }

    private suspend fun handleManualUpload() {
        isRecording.postValue(false)
        val workRequest = OneTimeWorkRequest.Builder(ZipFileWorker::class.java).build()
        // Enqueue the WorkRequest
        WorkManager.getInstance(this@ScreenRecordService).enqueue(workRequest)
        screenCollector.uploadZipFilesAsync(false)
        Timber.d("Service stopped for manual upload")
    }

    private fun handleNullIntent() {
        Timber.d("Service restarted with null intent")
        showNotification(
            getString(R.string.screenlake),
            getString(R.string.please_re_enable_screen_recording)
        )
        FirebaseCrashlytics.getInstance()
            .recordException(RuntimeException("Service was killed and restarted with null intent"))
    }
}