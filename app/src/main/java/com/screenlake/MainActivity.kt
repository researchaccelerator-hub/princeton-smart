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
import androidx.lifecycle.MutableLiveData
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.amplifyframework.core.Amplify
import com.screenlake.databinding.ActivityMainBinding
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.ScreenRecordService
import com.screenlake.recorder.utilities.PermissionHelper
import com.screenlake.recorder.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // ViewBinding instance
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var manager: MediaProjectionManager
    private val MEDIA_PROJECTION_REQUEST_CODE = 1002

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.isProjectionValid.postValue(true)
            startScreenRecordService(data)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showToast(getString(R.string.notification_permission_granted))
        } else {
            showToast(getString(R.string.notification_permission_denied))
        }
    }

    companion object {
        var isLoggedIn = MutableLiveData<Boolean>()
        var isLoggedOut = MutableLiveData<Boolean>()
        val isWifiConnected = MutableLiveData<Boolean>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun startScreenRecordService(projectionData: Intent?) {
        Intent(this, ScreenRecordService::class.java).apply {
            this.action = action
            putExtra("media_projection_data", projectionData)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ContextCompat.startForegroundService(this@MainActivity, this)
        }
    }

    private fun initializeUI() {
        binding.myToolbar.visibility = View.GONE
        binding.bottomNav.visibility = View.GONE
        binding.bottomNav.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.home -> binding.navHostFragment.findNavController().navigate(R.id.screenRecordFragment)
                R.id.settings -> binding.navHostFragment.findNavController().navigate(R.id.settingsWrapper)
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
}
