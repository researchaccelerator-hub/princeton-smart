package com.screenlake

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.amplifyframework.core.Amplify
import com.screenlake.databinding.ActivityMainBinding
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.NotificationHelper
import com.screenlake.recorder.services.RecordingState
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.PermissionHelper
import com.screenlake.recorder.viewmodels.MainViewModel
import com.screenlake.recorder.viewmodels.RestrictedAppViewModel
import com.screenlake.ui.fragments.ScreenRecordFragment
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ScreenRecordFragment.MediaProjectionCallback {

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    private lateinit var binding: ActivityMainBinding // ViewBinding instance
    private val mainViewModel: MainViewModel by viewModels()
    private val viewModel: RestrictedAppViewModel by viewModels()
    private lateinit var manager: MediaProjectionManager
    private val MEDIA_PROJECTION_REQUEST_CODE = 1002

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != MEDIA_PROJECTION_REQUEST_CODE) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            logProjectionPromptEvent("result=granted")
            ScreenshotService.suppressMediaProjectionPromptUntilUnlock.set(false)
            ScreenshotService.isProjectionValid.postValue(true)
            startScreenRecordService(resultCode, data)
        } else {
            logProjectionPromptEvent("result=denied")
            ScreenshotService.suppressMediaProjectionPromptUntilUnlock.set(true)
            ScreenshotService.isMediaProjectionPromptInFlight.set(false)
            // Starting the service here after a process kill would start a media projection
            // foreground service without consent, which Android 14+ rejects. Consent stays
            // pending either way, so the next app open asks again.
            if (RecordingState.serviceAliveInProcess) {
                startService(
                    Intent(this, ScreenshotService::class.java).apply {
                        action = ScreenshotService.ACTION_MEDIA_PROJECTION_DENIED
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        requestPendingProjectionConsentIfNeeded()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showToast(getString(R.string.notification_permission_granted))
        } else {
            // showToast(getString(R.string.notification_permission_denied))
        }
    }

    companion object {
        var isLoggedIn = MutableLiveData<Boolean>()
        var isLoggedOut = MutableLiveData<Boolean>()
        val isWifiConnected = MutableLiveData<Boolean>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (savedInstanceState == null) {
//            // Get NavController
//
//
//            // Process intent and create appropriate arguments bundle
//            val restartProjection = intent?.action == "ACTION_RESTART_PROJECTION"
//            val args = Bundle().apply {
//                putBoolean("restartProjection", restartProjection)
//            }
//        }

        viewModel

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: content draws behind system bars (mandatory on Android 15+).
        // Toolbar grows to cover the status bar; BottomNav absorbs the nav bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.myToolbar.updatePadding(top = bars.top)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        initializeUI()
        handleAppUpdate()
        setObservers()

        if (intent.action == ScreenshotService.ACTION_REQUEST_MEDIA_PROJECTION) {
            ScreenshotService.suppressMediaProjectionPromptUntilUnlock.set(false)
            manager =
                this.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            requestMediaProjection()
        }
        if (intent.action == NotificationHelper.ACTION_OPEN_LOGIN) {
            openLoginForRecovery()
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        supportActionBar?.hide()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionHelper.checkAndRequestNotificationPermission(this)
        }

        requestNotificationPermission()
    }

    private fun requestMediaProjection() {
        if (!ScreenshotService.isMediaProjectionPromptInFlight.compareAndSet(false, true)) {
            logProjectionPromptEvent("request=skipped;reason=already_in_flight")
            return
        }

        manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            logProjectionPromptEvent("request=system_dialog_launch")
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE
            )
        } catch (e: Exception) {
            ScreenshotService.isMediaProjectionPromptInFlight.set(false)
            logProjectionPromptEvent("request=launch_failed;error=${e.javaClass.simpleName}")
            throw e
        }
    }

    private fun requestPendingProjectionConsentIfNeeded() {
        // On a cold start after a kill this can run before the watchdog marks consent pending.
        if (RecordingState.isExpected(this) && !RecordingState.serviceAliveInProcess) {
            ScreenshotService.markMediaProjectionRequestPending(this)
        }
        if (!ScreenshotService.isMediaProjectionRequestPending(this)) return
        if (ScreenshotService.isMediaProjectionPromptInFlight.get()) return

        // A denial returns directly to this Activity and immediately invokes onResume().
        // Consume one resume so we do not loop the system dialog, then allow the next
        // foreground entry (or the next unlock) to request consent again.
        if (ScreenshotService.suppressMediaProjectionPromptUntilUnlock.getAndSet(false)) {
            logProjectionPromptEvent("request=deferred;reason=denial_return_resume")
            return
        }

        // Recovery restarts capture directly, so it needs a participant with an invite code,
        // the same requirement the record button checks.
        lifecycleScope.launch {
            val hasParticipant = runCatching {
                generalOperationsRepository.userExists() &&
                    !generalOperationsRepository.getUser().emailHash.isNullOrEmpty()
            }.getOrDefault(false)
            if (!hasParticipant) {
                logProjectionPromptEvent("request=skipped;reason=no_participant")
                return@launch
            }
            logProjectionPromptEvent("request=foreground_resume_recovery")
            requestMediaProjection()
        }
    }

    private fun openLoginForRecovery() {
        binding.navHostFragment.findNavController().navigate(R.id.loginFragment)
    }

    private fun logProjectionPromptEvent(message: String) {
        Timber.i("Projection consent prompt: $message")
        lifecycleScope.launch(Dispatchers.IO) {
            generalOperationsRepository.saveLog("SCREEN_SHARING_PROMPT", message)
        }
    }

    private fun startScreenRecordService(resultCode: Int, data: Intent?) {
        val serviceIntent = Intent(this@MainActivity, ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_DATA, data)
            action = ScreenshotService.ACTION_RESTART_PROJECTION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
        } else {
            this@MainActivity.startService(serviceIntent)
        }
    }

    private fun initializeUI() {
        binding.myToolbar.visibility = View.GONE
        binding.bottomNav.visibility = View.GONE
        binding.bottomNav.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.home -> binding.navHostFragment.findNavController().navigate(R.id.screenRecordFragment)
                R.id.settings -> binding.navHostFragment.findNavController().navigate(R.id.settingsWrapper)
                R.id.stats -> binding.navHostFragment.findNavController().navigate(R.id.screenshotStatsFragment)
            }
            true
        }
    }

    private fun handleAppUpdate() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastKnownVersion = prefs.getInt("lastKnownAppVersion", 0)
//        if (lastKnownVersion < BuildConfig.VERSION_CODE) {
//            prefs.edit().putInt("lastKnownAppVersion", BuildConfig.VERSION_CODE).apply()
//            showNotification(
//                "App Updated",
//                "Please re-enable screen recording.",
//                this,
//                getMainActivityPendingIntent(),
//                111
//            )
//        }
    }

    private fun setObservers() {
        mainViewModel.selectedItem.observe(this) {
            if (it) {
                binding.bottomNav.visibility = View.VISIBLE
                binding.myToolbar.visibility = View.VISIBLE
            } else {
                binding.bottomNav.visibility = View.GONE
                binding.myToolbar.visibility = View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> true
        R.id.action_restricted_apps -> true
        R.id.logout -> {
            Amplify.Auth.signOut(
                { Timber.tag("AuthQuickStart").d("Sign out failed") },
                { Timber.tag("AuthQuickStart").d("Sign out failed: $it") }
            )
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showToast(getString(R.string.notification_permission_not_available))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getMainActivityPendingIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).apply {
            action = ConstantSettings.ACTION_SHOW_RECORDING_FRAGMENT
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Store the new intent

        when (intent?.action) {
            // Sent by the restart notification via NotificationHelper.getMainActivityPendingIntent().
            // Handles the case where the activity is already running when the user taps the
            // notification — onCreate() handles cold-start, this handles the warm/hot path.
            ScreenshotService.ACTION_REQUEST_MEDIA_PROJECTION -> {
                ScreenshotService.suppressMediaProjectionPromptUntilUnlock.set(false)
                manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                requestMediaProjection()
            }
            // The upload recovery notification ("Sign in to resume uploads").
            NotificationHelper.ACTION_OPEN_LOGIN -> openLoginForRecovery()
            // Legacy path: restart triggered programmatically from within the app.
            "ACTION_RESTART_PROJECTION" -> {
                val fragment = ScreenRecordFragment.newInstance().apply {
                    arguments = Bundle().apply {
                        putBoolean("restartProjection", true)
                    }
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .commit()
            }
        }
    }

    override fun onMediaProjectionRequested() {

    }

    override fun onMediaProjectionResult(resultCode: Int, data: Intent?) {

    }

}
