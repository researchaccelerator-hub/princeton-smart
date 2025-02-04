package com.screenlake.data.repository

import android.content.Context
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.data.model.RestrictedApp
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.recorder.ocr.Assets
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.utilities.BaseUtility.toRestrictedApp
import kotlinx.coroutines.flow.Flow
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestrictedAppRepository @Inject constructor(
    private val context: Context,
    private val restrictedAppDao: RestrictedAppDao
) {
    val allApps: Flow<List<RestrictedAppPersistentEntity>> = restrictedAppDao.getAllRestrictedAppsFlow()

    suspend fun updateAppIsUserRestricted(restrictedAPP: RestrictedApp) {
        restrictedAPP.id?.let {
            restrictedAppDao.updateRestrictedApp(
                it,
                restrictedAPP.isUserRestricted
            )
        }
    }

    suspend fun insertRestrictedApps() {
        val appsInstalled = BaseUtility.getInstalledApps(context)
        val pm = context.packageManager
        val packages = appsInstalled.map { packageInfo ->
            val currAppName = pm.getApplicationLabel(packageInfo).toString()
            packageInfo.toRestrictedApp(currAppName)
        }
        packages.forEach {
            restrictedAppDao.insertRestrictedApp(it)
        }
    }
}