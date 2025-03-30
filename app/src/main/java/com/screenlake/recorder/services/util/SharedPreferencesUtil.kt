package com.screenlake.recorder.services.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenlake.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object SharedPreferencesUtil {
    private val Context.dataStore by preferencesDataStore(name = "settings")
    fun getLimitDataUsage(context: Context): Boolean = runBlocking {
        val dataStore = context.dataStore
        val onboardingSeenKey = booleanPreferencesKey(context.getString(R.string.limit_data_usage))
        dataStore.data.first()[onboardingSeenKey] ?: false
    }

    fun getLimitPowerUsage(context: Context): Boolean = runBlocking {
        val dataStore = context.dataStore
        val onboardingSeenKey = booleanPreferencesKey(context.getString(R.string.limit_power_usage))
        dataStore.data.first()[onboardingSeenKey] ?: false
    }

    fun setLimitPowerUsage(context: Context, value: String) = runBlocking {
        val onboardingSeenKey = stringPreferencesKey(context.getString(R.string.limit_data_usage))
        context.dataStore.edit { settings ->
            settings[onboardingSeenKey] = value
        }
    }

    fun getBatteryOptimizationDisabled(context: Context): Boolean = runBlocking {
        val dataStore = context.dataStore
        val batteryOptKey = booleanPreferencesKey(context.getString(R.string.battery_optimization_disabled))
        dataStore.data.first()[batteryOptKey] ?: false
    }

    fun setBatteryOptimizationDisabled(context: Context, value: Boolean) = runBlocking {
        val batteryOptKey = booleanPreferencesKey(context.getString(R.string.battery_optimization_disabled))
        context.dataStore.edit { settings ->
            settings[batteryOptKey] = value
        }
    }

    fun setLimitDataUsage(context: Context, value: Boolean) = runBlocking {
        val onboardingSeenKey = booleanPreferencesKey(context.getString(R.string.limit_power_usage))
        context.dataStore.edit { settings ->
            settings[onboardingSeenKey] = value
        }
    }
}