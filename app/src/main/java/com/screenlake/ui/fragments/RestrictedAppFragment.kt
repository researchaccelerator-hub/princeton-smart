package com.screenlake.ui.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.screenlake.data.database.dao.RestrictedAppDao
import com.screenlake.databinding.FragmentRestrictedAppsBinding
import com.screenlake.recorder.adapters.RestrictedAppAdapter
import com.screenlake.data.model.RestrictedApp
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.BaseUtility.toRestrictedApp

import com.screenlake.recorder.viewmodels.RestrictedAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RestrictedAppFragment : Fragment() {

    @Inject
    lateinit var restrictedAppDao: RestrictedAppDao

    private val viewModel: RestrictedAppViewModel by viewModels()
    private lateinit var binding: FragmentRestrictedAppsBinding

    private var restrictedApps = mutableListOf<RestrictedApp>()
    private var adapter: RestrictedAppAdapter? = null

    var result = mutableListOf<RestrictedApp>()

    var restricted = mutableListOf<RestrictedApp>()

    var notRestricted = mutableListOf<RestrictedApp>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestrictedAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = binding.listViewRestricted
        adapter = RestrictedAppAdapter { model ->
            //The click action you want to perform.
            model.isUserRestricted = !model.isUserRestricted
            updateAdapter(model)
            updateRestrictedApp(model)

            if (model.isUserRestricted) {
                ScreenshotService.restrictedApps.value?.remove(model.packageName)

                val copy = ScreenshotService.restrictedApps.value
                if (copy != null) {
                    model.packageName?.let { copy.add(it) }
                }
                ScreenshotService.restrictedApps.postValue(copy)
            }
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.requireContext())

        viewModel.restrictedApps.observe(this.viewLifecycleOwner) { restrictedAppsPersistent ->
            // Update the cached copy of the words in the adapter.
            if (restrictedApps.size == 0) {
                restrictedApps = restrictedAppsPersistent.map { it.convert().getIcon() }
                    .filter { it?.iconDrawable != null }.filterNotNull().toMutableList()

                restrictedApps = restrictedApps.distinctBy { it.iconDrawable }.toMutableList()
                restrictedApps =
                    restrictedApps.filter { it.packageName != "com.screenlake" }.toMutableList()

                result = mutableListOf()

                restricted = restrictedApps.filter { it.isUserRestricted }.toMutableList()

                notRestricted = restrictedApps.filter { !it.isUserRestricted }.toMutableList()

                if (restricted.size > 0) {
                    result.addAll(restricted)
                }

                result.addAll(notRestricted)

                binding.loadingBar.visibility = View.GONE
                restrictedApps.let { adapter?.submitList(result.sortedBy { it.name }) }
            }
        }
    }

    private fun updateRestrictedApp(restrictedApp: RestrictedApp) = lifecycleScope.launch {
        viewModel.updateAppIsUserRestricted(restrictedApp)
    }

    private fun updateAdapter(restrictedApp: RestrictedApp) {
        val itemToRemove =
            restrictedApps.filter { it.packageName == restrictedApp.packageName }.first()

        result.remove(itemToRemove)
        restricted = restrictedApps.filter { it.isUserRestricted }.toMutableList()
        addRestrictedApps(restricted)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun getInstalledAppIcon(context: Context, appName: String): Drawable? {
        return try {
            val icon: Drawable = context.packageManager.getApplicationIcon(appName)
            icon
        } catch (ne: PackageManager.NameNotFoundException) {

            null
        }
    }

    private fun RestrictedApp.getIcon(): RestrictedApp? {
        this.iconDrawable = this.packageName?.let { getInstalledAppIcon(requireContext(), it) }

        if (this.iconDrawable != null) {
            return this
        }

        return null
    }

    fun RestrictedAppPersistentEntity.convert(): RestrictedApp {
        return RestrictedApp(
            this.packageName,
            name = this.appName,
            isUserRestricted = this.isUserRestricted,
            authOverride = this.authOverride,
            isAuthRestricted = this.isAuthRestricted,
            id = this.id
        )
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun Activity.getInstalledApps(): List<ApplicationInfo> {
        val pm: PackageManager? = this.packageManager

        return pm?.getInstalledApplications(PackageManager.GET_META_DATA) ?: emptyList()
    }

    suspend fun Activity.insertRestrictedApps() {
        val appsInstalled = this.getInstalledApps().map { it.toRestrictedApp("") }

        appsInstalled.forEach {
            restrictedAppDao.insertRestrictedApp(it)
        }
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    fun addRestrictedApp(restrictedApp: RestrictedApp) {
        restrictedApp.packageName?.let { ScreenshotService.restrictedApps.value?.add(it) }
        ScreenshotService.restrictedApps.notifyObserver()
    }

    private fun addRestrictedApps(restrictedApps: List<RestrictedApp>) {
        ScreenshotService.restrictedApps.postValue(restrictedApps.mapNotNull { it.packageName }
            .toHashSet())
    }
}