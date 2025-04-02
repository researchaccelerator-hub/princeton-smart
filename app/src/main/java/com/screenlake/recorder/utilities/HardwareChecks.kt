package com.screenlake.recorder.utilities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.screenlake.MainActivity
import com.screenlake.recorder.services.ScreenshotService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference

object HardwareChecks {
    private const val TAG = "HardwareChecks"

    /**
     * Checks if the device is connected to a power source.
     *
     * This method determines whether the device is plugged into AC, USB, or a wireless charger.
     *
     * @param context A weak reference to the application context.
     * @return True if the device is connected to a power source, false otherwise.
     */
    fun isPowerConnected(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val result = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        ScreenshotService.isPowerConnected.postValue(result)
        return result
    }

    /**
     * Checks if the device is connected to the internet.
     *
     * This method determines network connectivity status via Wi-Fi or cellular.
     *
     * @param context A weak reference to the application context.
     * @return True if the device is connected to the internet, false otherwise.
     */
    fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    MainActivity.isWifiConnected.postValue(true)
                    Timber.tag("Internet").d("NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    MainActivity.isWifiConnected.postValue(true)
                    Timber.tag("Internet").d("NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                }
            }
        }
        return false
    }

    /**
     * Asynchronously checks if the device is connected to the internet and verifies online status.
     *
     * This coroutine performs a network capability check followed by a ping test to determine
     * actual internet access.
     *
     * @param context A weak reference to the application context.
     * @return True if the device is connected to the internet and online, false otherwise.
     */
    suspend fun isConnectedAsync(context: WeakReference<Context>): Boolean = withContext(Dispatchers.Default) {
        val connectivityManager = context.get()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Timber.tag("Internet").d("NetworkCapabilities.TRANSPORT_WIFI")
                    val isOnline = isOnline()
                    MainActivity.isWifiConnected.postValue(isOnline)
                    return@withContext isOnline
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Timber.tag("Internet").d("NetworkCapabilities.TRANSPORT_WIFI")
                    val isOnline = isOnline()
                    MainActivity.isWifiConnected.postValue(isOnline)
                    return@withContext isOnline
                }
            }
        }

        MainActivity.isWifiConnected.postValue(false)
        return@withContext false
    }

    /**
     * Calculates the percentage of storage used on the device.
     *
     * This method retrieves the total and available storage space on the device and calculates
     * the percentage of used storage.
     *
     * @return The percentage of storage used as a double value. Returns 0.0 if storage is unavailable.
     */
    fun getPercentageStorageUsed(): Double {
        val path = Environment.getDataDirectory().absolutePath
        val stat = StatFs(path)

        val availableBlocks = stat.availableBlocksLong * stat.blockSizeLong
        val storageSize: Long = stat.blockCountLong * stat.blockSizeLong

        return if (availableBlocks == 0L) {
            0.0
        } else {
            val percentage = (availableBlocks.toDouble() / storageSize.toDouble() * 100)
            Timber.tag("Storage").d("Available Storage => $percentage")
            percentage
        }
    }

    /**
     * Checks if the device is online by executing a ping to a known server.
     *
     * This private utility method uses a ping command to verify if the device can reach the internet.
     *
     * @return True if the device is online and can reach the specified server, false otherwise.
     */
    private fun isOnline(): Boolean {
        try {
            val p1 = Runtime.getRuntime().exec("ping -c 1 www.google.com")
            val returnVal = p1.waitFor()
            return returnVal == 0
        } catch (e: Exception) {
            // Log the exception and handle accordingly
            Timber.tag(TAG).e(e, "Ping command failed")
        }
        return false
    }
}
