package com.screenlake.recorder.utilities

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.screenlake.MainActivity

object PermissionHelper {
    /**
     * Checks and requests the notification permission for the given activity.
     *
     * This method first checks if the `POST_NOTIFICATIONS` permission has already been granted.
     * - If the permission is granted, no further action is taken.
     * - If the permission is not granted and the app should show a rationale, it triggers the rationale and requests the permission.
     * - If the permission is neither granted nor should the rationale be shown, it directly requests the permission.
     *
     * @param activity The activity from which the permission is being requested. This activity should implement the method
     *                 `requestNotificationPermission()` to handle the actual permission request.
     *
     * Usage example:
     * ```
     * PermissionHelper.checkAndRequestNotificationPermission(this)
     * ```
     *
     * Note: Ensure that the activity passed to this method has the necessary logic to request permissions and handle the user's response.
     */
    fun checkAndRequestNotificationPermission(activity: MainActivity) {
        when {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission granted
            }
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Explain to the user why you need the permission
                activity.requestNotificationPermission()
            }
            else -> {
                // Directly ask for the permission
                activity.requestNotificationPermission()
            }
        }
    }

}
