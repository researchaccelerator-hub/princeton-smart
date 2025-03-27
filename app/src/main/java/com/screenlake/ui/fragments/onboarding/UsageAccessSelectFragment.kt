package com.screenlake.ui.fragments.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenlake.R
import com.screenlake.databinding.FragmentUsuageAccessSelectBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsageAccessSelectFragment : Fragment() {

    private var _binding: FragmentUsuageAccessSelectBinding? = null
    private val binding get() = _binding!!

    val grantAccessText = "Grant Access"
    val nextText = "Next"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentUsuageAccessSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.usuageAccessSelectFragmentNext.text = grantAccessText

        if (hasPackageUsageStatsPermissions()) {
            binding.usuageAccessSelectFragmentNext.text = nextText
        }

        binding.usuageAccessSelectFragmentNext.setOnClickListener {
            when {
                binding.usuageAccessSelectFragmentNext.text == grantAccessText -> {
                    requestPackageUsageStatsPermissions()
                    binding.usuageAccessSelectFragmentNext.text = nextText
                }

                hasPackageUsageStatsPermissions() -> {
                    findNavController().navigate(R.id.powerAccessFragment)
                }

                else -> {
                    binding.usuageAccessSelectFragmentError.visibility = View.VISIBLE
                    binding.usuageAccessSelectFragmentNext.text = grantAccessText
                }
            }
        }
    }

    private fun requestPackageUsageStatsPermissions() {
        if (!hasPackageUsageStatsPermissions()) {
            val dialog = packageDialog(
                getString(R.string.for_screenlake_to_work_properly_you_must_manually_permit_usage_access_this_is_so_we_can_stop_recording_data_sensitive_apps_for_privacy_reasons),
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
            dialog.show()
        }
    }

    private fun hasPackageUsageStatsPermissions(): Boolean {
        return try {
            val packageManager: PackageManager =
                (context?.packageManager ?: activity) as PackageManager
            val applicationInfo =
                context?.let { packageManager.getApplicationInfo(it.packageName, 0) }
            val appOpsManager = context?.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
            var mode = 0
            if (applicationInfo != null && appOpsManager != null) {
                mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun packageDialog(message: String, action: String): AlertDialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.screenlake))
            .setMessage((message))
            .setIcon(com.screenlake.R.drawable.logo_just_square_small)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val intent = Intent(action)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}