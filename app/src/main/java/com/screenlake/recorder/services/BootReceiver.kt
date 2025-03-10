package com.screenlake.recorder.services

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.NOTIFICATION_CHANNEL_ID
import java.lang.Exception

class BootReceiver  : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

//        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_screenlake_logo)
//            .setContentTitle("Screenlake")
//            .setContentText("Please re-enable screen recording.")
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setAutoCancel(true)
//
//
//        // TODO: Test these flags.
//        val pendingIntent = NotificationHelper(context).getMainActivityPendingIntent()
//
//
//        builder.setContentIntent(pendingIntent)
//        if (ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.POST_NOTIFICATIONS
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return
//        }
//        NotificationManagerCompat.from(context).notify(13213, builder.build());
//        FirebaseCrashlytics.getInstance().recordException(Exception("BOOT_ON_RECEIVE"))
    }
}