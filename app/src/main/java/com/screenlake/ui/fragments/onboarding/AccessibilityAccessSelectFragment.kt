package com.screenlake.ui.fragments.onboarding

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenlake.R
import com.screenlake.databinding.FragmentAccessibilityAccessSelectBinding
import com.screenlake.recorder.services.TouchAccessibilityService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccessibilityAccessSelectFragment : Fragment() {

    private lateinit var binding: FragmentAccessibilityAccessSelectBinding

    private var accessGranted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccessibilityAccessSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        accessGranted = isAccessibilityServiceEnabled(requireContext(), TouchAccessibilityService::class.java)
        updateButtonState()

        binding.accessibilityAccessSelectFragmentNext.setOnClickListener {
            if (accessGranted) {
                findNavController().navigate(R.id.screenRecordFragment)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.screenlake))
                    .setMessage(getString(R.string.for_screenlake_to_work_properly_we_need_you_to_accept_accessibility_settings))
                    .setIcon(R.drawable.logo_just_square_small)
                    .setPositiveButton(getString(R.string.agree)) { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton(getString(R.string.not_now)) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessGranted = isAccessibilityServiceEnabled(requireContext(), TouchAccessibilityService::class.java)
        updateButtonState()
    }

    private fun updateButtonState() {
        binding.accessibilityAccessSelectFragmentNext.text =
            if (accessGranted) getText(R.string.next) else getString(R.string.grant_access)
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        service: Class<out AccessibilityService?>
    ): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == context.packageName &&
                enabledServiceInfo.name == service.name) return true
        }
        return false
    }
}
