package com.screenlake.ui.fragments.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenlake.R
import com.screenlake.databinding.FragmentPowerAccessSelectBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PowerAccessSelectFragment : Fragment() {

    private var _binding: FragmentPowerAccessSelectBinding? = null
    private val binding get() = _binding!!

    val grantAccessText = "Grant Access"
    val nextText = "Next"

    // Use the Activity Result API for a cleaner implementation
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check the result if needed
        if (isBatteryOptimizationDisabled(this@PowerAccessSelectFragment.requireContext())) {
            Toast.makeText(requireContext(), "Battery optimization disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Battery optimization still enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPowerAccessSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.powerAccessSelectFragmentNext.text = grantAccessText

        if (isBatteryOptimizationDisabled(this@PowerAccessSelectFragment.requireContext())) {
            binding.powerAccessSelectFragmentNext.text = nextText
        }

        binding.powerAccessSelectFragmentNext.setOnClickListener {
            when {
                binding.powerAccessSelectFragmentNext.text == grantAccessText -> {
                    requestDisableBatteryOptimization()
                    binding.powerAccessSelectFragmentNext.text = nextText
                }

                isBatteryOptimizationDisabled(this@PowerAccessSelectFragment.requireContext()) -> {
                    findNavController().navigate(R.id.accessibilityAccessSelectFragment)
                }

                else -> {
                    binding.powerAccessSelectFragmentError.visibility = View.VISIBLE
                    binding.powerAccessSelectFragmentNext.text = grantAccessText
                }
            }
        }
    }

    private fun requestPackageUsageStatsPermissions() {
        if (isBatteryOptimizationDisabled(this@PowerAccessSelectFragment.requireContext())) {
            // Battery optimization is disabled for your app
            // Your app can run in the background without restrictions
        } else {
            // Battery optimization is enabled for your app
            // Consider asking the user to disable it
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context?.packageName}")
            }
            startActivity(intent)
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
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
            .setIcon(R.drawable.ic_launcher_foreground)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val intent = Intent(action)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
    }

    /**
     * Request to disable battery optimization
     */
    fun requestDisableBatteryOptimization() {
        if (!isBatteryOptimizationDisabled(this@PowerAccessSelectFragment.requireContext())) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                // Handle exception (e.g., intent not supported on this device)
                Toast.makeText(
                    requireContext(),
                    "Could not open battery optimization settings: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Fallback to general battery settings
                try {
                    val fallbackIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    startActivity(fallbackIntent)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Could not open battery settings: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Battery optimization is already disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}