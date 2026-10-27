package com.screenlake.recorder.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.NOTIFICATION_CHANNEL_ID
import com.screenlake.recorder.constants.ConstantSettings.NOTIFICATION_CHANNEL_NAME

class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel(){
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun showNotification(title: String, message: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(context, ConstantSettings.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_just_square_small)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(getMainActivityPendingIntent())

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * Tells the participant that uploads stopped because they need to sign in again, and opens
     * the login screen when tapped.
     *
     * @return false if notifications are not allowed.
     */
    fun showAuthenticationRecoveryNotification(): Boolean {
        val message = context.getString(R.string.auth_recovery_message)
        val builder = NotificationCompat.Builder(context, ConstantSettings.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_just_square_small)
            .setContentTitle(context.getString(R.string.auth_recovery_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(getLoginPendingIntent())

        if (!canPost(ConstantSettings.NOTIFICATION_CHANNEL_ID)) return false
        NotificationManagerCompat.from(context).notify(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID, builder.build())
        return true
    }

    /**
     * Whether a notification on [channelId] will actually be shown: notifications on for the app,
     * the runtime permission granted (Android 13+ only), and the channel not turned off.
     */
    private fun canPost(channelId: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = manager.getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun getLoginPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_LOGIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun getMainActivityPendingIntent() : PendingIntent {

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ScreenshotService.ACTION_REQUEST_MEDIA_PROJECTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_OPEN_LOGIN = "com.screenlake.action.OPEN_LOGIN"
    }
}
