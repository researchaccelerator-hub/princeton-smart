package com.screenlake

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.services.WorkerStarter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {
    /** Duration of wait  */
    private val SPLASH_DISPLAY_LENGTH = 2000

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var workerStarter: WorkerStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.fragment_splash_screen)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        workerStarter.invoke()
        cloudAuthentication.fetchCurrentAuthSession()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // If Android 12 or above, go directly to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            // Show custom splash screen for pre-Android 12
            setContentView(R.layout.fragment_splash_screen)
            // Proceed to MainActivity after a delay
            CoroutineScope(Dispatchers.Main).launch {
                delay(SPLASH_DISPLAY_LENGTH.toLong()) // 3 seconds
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}