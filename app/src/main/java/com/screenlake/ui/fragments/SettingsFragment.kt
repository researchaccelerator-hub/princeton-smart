package com.screenlake.ui.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import com.screenlake.R
import com.screenlake.MainActivity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.constants.ConstantSettings.SCREENSHOT_MAPPING
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.collections.get

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    // Create an extension property for DataStore
    private val Context.dataStore by preferencesDataStore(name = "settings")

    @Inject
    lateinit var generalOperationsRepository: GeneralOperationsRepository

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var dialog: AlertDialog

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            initSummary(preferenceScreen)

            // Propagate settings to the service
            when (key) {
                getString(R.string.fps) -> {
                    val framesPerSecond = prefs.getString(key, "0.2")?.toDoubleOrNull()
                    ScreenshotService.screenshotInterval.postValue(SCREENSHOT_MAPPING[framesPerSecond])
                }

                getString(R.string.limit_data_usage) -> {
                    ScreenshotService.uploadOverWifi.postValue(prefs.getBoolean(key, true))
                }

                getString(R.string.limit_power_usage) -> {
                    ScreenshotService.uploadOverPower.postValue(prefs.getBoolean(key, false))
                }

                getString(R.string.payment_handle) -> {
                    handlePaymentHandlePreference(prefs)
                }

                getString(R.string.payment_handle_type) -> {
                    handlePaymentHandleTypePreference(prefs)
                }
            }
        }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSummary(preferenceScreen)

        // Handle back press
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Custom back button handling if needed
                }
            }
        )

        findPreference<Preference>("apps_being_recorded")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                findNavController().navigate(R.id.rescrictedAppFragment)
                true
            }

        findPreference<Preference>("Logout")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showSignOutDialog()
                true
            }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val paymentType = prefs.getString(getString(R.string.payment_handle_type), null)
        findPreference<Preference>(getString(R.string.payment_handle))?.isEnabled =
            !paymentType.isNullOrBlank()
    }

    /**
     * Sets the user as logged out in the shared preferences.
     */
    private fun setUserLoggedOut() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putBoolean(getString(R.string.is_signed_out), true)
            apply()
        }
    }

    /**
     * Signs out the user and navigates to the login or registration screen.
     */
    private fun signOutUser() = lifecycleScope.launch {
        cloudAuthentication.signOut(MainActivity.isLoggedOut)
        MainActivity.isLoggedIn.postValue(false)

        sendCommandToRecordService(ConstantSettings.ACTION_STOP_SERVICE)
        setUserLoggedOut()
        async { clearPhone() }.await()

        delay(2000L)
        dialog.dismiss()

        fragmentManager?.apply {
            val first = getBackStackEntryAt(0)
            first?.id?.let { popBackStack(it, FragmentManager.POP_BACK_STACK_INCLUSIVE) }
        }

        mainViewModel.isOnPastOnBoarding(false)
        findNavController().navigate(R.id.registerOrLoginFragment)
    }

    /**
     * Displays a sign-out confirmation dialog to the user.
     */
    private fun showSignOutDialog() {
        AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Logout")
            .setMessage(getString(R.string.are_you_sure_you_want_to_log_out_of_screenlake_this_will_delete_all_user_data_from_the_phone))
            .setPositiveButton("Yes") { _, _ ->
                startLoadingDialog()
                signOutUser()
            }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Initializes summaries for each preference in the group.
     *
     * @param preference The preference group or individual preference.
     */
    private fun initSummary(preference: Preference) {
        if (preference is PreferenceGroup) {
            for (i in 0 until preference.preferenceCount) {
                initSummary(preference.getPreference(i))
            }
        } else {
            updatePrefSummary(preference)
        }
    }

    /**
     * Updates the summary for a given preference.
     *
     * @param preference The preference whose summary needs to be updated.
     */
    private fun updatePrefSummary(preference: Preference) {
        when (preference) {
            is ListPreference -> preference.summary = preference.entry
            is EditTextPreference -> {
                preference.summary =
                    if (preference.title.toString().contains("password", ignoreCase = true)) {
                        "******"
                    } else {
                        preference.text
                    }
            }

            is MultiSelectListPreference -> {
                // Add handling if necessary
            }
        }
    }

    /**
     * Clears phone data using the GenOp repository.
     */
    private suspend fun clearPhone() {
        generalOperationsRepository.clearPhone()
    }

    /**
     * Sends a command to the ScreenshotService to perform a specific action.
     *
     * @param action The action string to be sent.
     */
    private fun sendCommandToRecordService(action: String) {
        Intent(requireContext(), ScreenshotService::class.java).apply {
            this.action = action
            requireContext().startService(this)
        }
    }

    /**
     * Starts a loading dialog when an operation is in progress.
     */
    private fun startLoadingDialog() {
        val builder = AlertDialog.Builder(activity)
        val inflater = layoutInflater
        builder.setView(inflater.inflate(R.layout.loading, null))
        builder.setCancelable(true)
        dialog = builder.create()
        dialog.show()
    }

    /**
     * Handles the payment handle preference update.
     */
    private fun handlePaymentHandlePreference(prefs: SharedPreferences) {
        val prefKey = prefs.getString(getString(R.string.payment_handle_type), "")
        if (!prefKey.isNullOrBlank()) {
            // Firebase operations (if needed)
        } else {
            findPreference<Preference>(getString(R.string.payment_handle))?.isEnabled = false
        }
    }

    /**
     * Handles the payment handle type preference update.
     */
    private fun handlePaymentHandleTypePreference(prefs: SharedPreferences) {
        val prefKey = prefs.getString(getString(R.string.payment_handle_type), "")
        if (!prefKey.isNullOrBlank()) {
            findPreference<Preference>(getString(R.string.payment_handle))?.isEnabled = true
        } else {
            prefs.edit().putString(getString(R.string.payment_handle), "").apply()
            initSummary(preferenceScreen)
            findPreference<Preference>(getString(R.string.payment_handle))?.isEnabled = false
        }
    }
}
