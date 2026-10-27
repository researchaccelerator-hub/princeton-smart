package com.screenlake.recorder.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screenlake.data.repository.GeneralOperationsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Logs the phone powering on and off as `PHONE_POWER_STATE` log events, which are uploaded with
 * the other log events. This lets researchers tell a gap caused by the phone being off from one
 * caused by the app.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val state = powerStateFor(intent.action) ?: return

        // Keep the receiver alive until the event is written; shutdown leaves little time.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generalOperationsRepository.saveLog(POWER_STATE_EVENT, state)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val POWER_STATE_EVENT = "PHONE_POWER_STATE"

        fun powerStateFor(action: String?): String? = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> "POWER_ON"
            Intent.ACTION_SHUTDOWN,
            "android.intent.action.QUICKBOOT_POWEROFF" -> "POWER_OFF"
            else -> null
        }
    }
}
