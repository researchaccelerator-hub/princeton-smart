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
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.amplifyframework.core.Amplify
import com.screenlake.databinding.ActivityMainBinding
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.PermissionHelper
import com.screenlake.recorder.viewmodels.MainViewModel
import com.screenlake.recorder.viewmodels.RestrictedAppViewModel
import com.screenlake.ui.fragments.ScreenRecordFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ScreenRecordFragment.MediaProjectionCallback {

    private lateinit var binding: ActivityMainBinding // ViewBinding instance
    private val mainViewModel: MainViewModel by viewModels()
    private val viewModel: RestrictedAppViewModel by viewModels()
    private lateinit var manager: MediaProjectionManager
    private val MEDIA_PROJECTION_REQUEST_CODE = 1002

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            ScreenshotService.isProjectionValid.postValue(true)
            startScreenRecordService(resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
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

        initializeUI()
        handleAppUpdate()
        setObservers()

        if (intent.action == "ACTION_REQUEST_MEDIA_PROJECTION") {
            manager =
                this.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            requestMediaProjection()
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        supportActionBar?.hide()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionHelper.checkAndRequestNotificationPermission(this)
        }

        requestNotificationPermission()
    }

    private fun requestMediaProjection() {
        val permissionIntent = manager.createScreenCaptureIntent()
        startActivityForResult(permissionIntent, MEDIA_PROJECTION_REQUEST_CODE)
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

        // If this is a restart projection intent, create a new fragment instance
        if (intent?.action == "ACTION_RESTART_PROJECTION") {
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

    override fun onMediaProjectionRequested() {

    }

    override fun onMediaProjectionResult(resultCode: Int, data: Intent?) {

    }
}
