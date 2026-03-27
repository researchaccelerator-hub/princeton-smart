package com.screenlake.ui.fragments.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.screenlake.R
import com.screenlake.databinding.FragmentPowerAccessSelectBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PowerAccessSelectFragment : Fragment() {

    private var _binding: FragmentPowerAccessSelectBinding? = null
    private val binding get() = _binding!!

    private var accessGranted = false

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        accessGranted = isBatteryOptimizationDisabled(requireContext())
        updateButtonState()
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

        accessGranted = isBatteryOptimizationDisabled(requireContext())
        updateButtonState()

        binding.powerAccessSelectFragmentNext.setOnClickListener {
            if (accessGranted) {
                findNavController().navigate(R.id.accessibilityAccessSelectFragment)
            } else {
                requestDisableBatteryOptimization()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessGranted = isBatteryOptimizationDisabled(requireContext())
        updateButtonState()
    }

    private fun updateButtonState() {
        binding.powerAccessSelectFragmentNext.text =
            if (accessGranted) getString(R.string.next) else getString(R.string.grant_access)
        binding.powerAccessSelectFragmentError.visibility = View.INVISIBLE
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun requestDisableBatteryOptimization() {
        if (isBatteryOptimizationDisabled(requireContext())) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            } catch (e: Exception) {
                // Settings not available on this device; user will see button unchanged
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
