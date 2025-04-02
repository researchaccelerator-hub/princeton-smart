package com.screenlake.recorder.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.screenlake.data.database.dao.AccessibilityEventDao
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.recorder.behavior.TrackingManager
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.enums.BehavioralEvents
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.AccessibilityServiceDependencies.context
import com.screenlake.recorder.services.AccessibilityServiceDependencies.eventHandler
import com.screenlake.recorder.services.AccessibilityServiceDependencies.ioDispatcher
import com.screenlake.recorder.services.ScreenshotService.Companion.isRunning
import com.screenlake.recorder.services.util.AccessibilityEventUtils
import com.screenlake.recorder.services.util.CustomObserver
import com.screenlake.recorder.utilities.TimeUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.lang.ref.WeakReference
import java.time.Instant
import java.util.*
import javax.inject.Inject
import kotlin.text.contains

object AccessibilityServiceDependencies {
    var ioDispatcher: CoroutineDispatcher? = null
    var eventHandler: AccessibilityEventHandler? = null
    var trackingManager: TrackingManager? = null
    var context:  WeakReference<Context>? = null
}

@AndroidEntryPoint
class TouchAccessibilityService() : AccessibilityService() {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var accessibilityEventDao: AccessibilityEventDao

    @Inject
    lateinit var trackingManager: TrackingManager

    companion object {
        val isScreenOn = MutableLiveData<Boolean>()
        var prevUrl = ""
        var prevMeta = ""
        var appAccessibilitySessionId = UUID.randomUUID().toString()
        var framesPerSecond = ConstantSettings.SCREENSHOT_MAPPING[ScreenshotService.framesPerSecond] ?: 3333L
        var sessionStartTime: Long? = null
        var appIntervalId = UUID.randomUUID().toString()
        var user: UserEntity? = null

        fun isAccessibilitySettingsOn(mContext: Context): Boolean {
            return isAccessServiceEnabled(mContext)
        }

        private fun isAccessServiceEnabled(context: Context): Boolean {
            val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return prefString != null && prefString.contains(
                context.packageName + "/" + TouchAccessibilityService::class.java.name
            )
        }
    }

    private val handler = CoroutineExceptionHandler { _, exception ->
        Timber.e(exception, "ScreenRecording Service ExceptionHandler caught an exception")
        // Uncomment to log to Firebase Crashlytics
        // FirebaseCrashlytics.getInstance().recordException(exception)
    }

    private val workCoordinatorLimit = 30000L
    private val workCoordinatorMutex = Mutex()

    private var mReceiver: BroadcastReceiver? = null
    private var workCoordinatorCounter = 0L
    private var job: Job? = null

    // Custom observer for observing changes in screen state
    @RequiresApi(Build.VERSION_CODES.R)
    private val customObserver = CustomObserver<Boolean> { data ->
        if (data) {
            startService()
        } else {
            stopService()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        eventHandler?.handleEvent(event)
    }

    override fun onInterrupt() {
        // Handle accessibility service interruption
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public override fun onServiceConnected() {
        // Initialize the tracking manager if not injected

        if (AccessibilityServiceDependencies.context == null) {
            AccessibilityServiceDependencies.context = WeakReference(this)
        }

        Timber.d("Accessibility Service Connected")

        // Register broadcast receiver to listen for screen state changes
        registerScreenStateReceiver()

        Handler(Looper.getMainLooper()).post {
            // Call observeForever on the main thread
            isScreenOn.observeForever(customObserver)

        }

        // Set initial screen state to on
        isScreenOn.postValue(true)

        super.onServiceConnected()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onDestroy() {
        // Unregister broadcast receiver and remove observer
        mReceiver?.let { context?.get()?.unregisterReceiver(it) }
        Handler(Looper.getMainLooper()).post {
            // Call observeForever on the main thread
            isScreenOn.removeObserver(customObserver)

        }
        super.onDestroy()
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        mReceiver = SystemAccessibilityEventReceiver(context?.get())
        context?.get()?.registerReceiver(mReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startService() {
        job = CoroutineScope(ioDispatcher?.plus(handler) ?: Dispatchers.IO).launch {
            try {
                startAccessibilityService()
            } catch (e: CancellationException) {
                Timber.d("Accessibility Service Job Cancelled")
            }
        }
    }

    private fun stopService() {
        job?.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startAccessibilityService() {
        user = getUser()
        saveEvent(
            AccessibilityEventEntity(
                user = user?.emailHash,
                eventTime = Instant.now().epochSecond,
                eventType = "SESSION_START",
                appIntervalId = appIntervalId,
                accessibilitySessionId = appAccessibilitySessionId
            )
        )

        sessionStartTime = Instant.now().epochSecond
        var firstRun = true
        var screenOffDetected = true

        while (true) {
            delay(framesPerSecond)

            // Update appIntervalId on subsequent runs
            if (!firstRun) {
                appIntervalId = UUID.randomUUID().toString()
            } else {
                firstRun = false
            }

            if (isRunning.value != true) {
                Timber.d("<<<< ScreenshotService not running >>>>")
                continue
            }

            // Handle screen-off events
            if (isScreenOn.value != true) {
                if (screenOffDetected) {
                    trackingManager.handleAccessibilityEvent(
                        AccessibilityEventEntity().apply {
                            behavorType = BehavioralEvents.SESSION_OVER
                        }
                    )
                    screenOffDetected = false
                    Timber.d("<<<< Screen Off Event Detected >>>>")
                }
                return
            }

            // Reset detection for screen-off events if screen is on
            screenOffDetected = true

            // Process the active root node if available
            rootInActiveWindow?.let { rootNode ->
                Timber.d("Processing Accessibility Node at ${TimeUtility.getCurrentTimestamp()}")
                trackingManager.handleAccessibilityNode(rootNode)
            }
        }
    }

    private suspend fun saveEvent(event: AccessibilityEventEntity) {
        ioDispatcher?.let {
            accessibilityEventDao.save(event)
        }
    }

    private suspend fun getUser(): UserEntity? {
        return ioDispatcher?.let {
            generalOperationsRepository.getUser()
        }
    }
}
