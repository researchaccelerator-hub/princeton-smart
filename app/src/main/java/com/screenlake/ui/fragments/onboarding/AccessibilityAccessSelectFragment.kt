package com.screenlake.ui.fragments.onboarding

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.screenlake.R
import com.screenlake.databinding.FragmentAccessibilityAccessSelectBinding
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.TouchAccessibilityService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccessibilityAccessSelectFragment : Fragment() {

    private lateinit var binding: FragmentAccessibilityAccessSelectBinding
    private lateinit var grantAccessText: String
    private lateinit var nextText: CharSequence

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccessibilityAccessSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        grantAccessText = getString(R.string.grant_access)
        nextText = getText(R.string.next)

        binding.accessibilityAccessSelectFragmentNext.text = grantAccessText

        val isEnabled = isAccessibilityServiceEnabled(this.requireContext(), TouchAccessibilityService::class.java)
        if (isEnabled){
            binding.accessibilityAccessSelectFragmentNext.text = nextText
        }

        binding.accessibilityAccessSelectFragmentNext.setOnClickListener {
            when (binding.accessibilityAccessSelectFragmentNext.text) {
                grantAccessText -> {
                    if(!isEnabled){
                        val dialog = packageDialog(getString(R.string.for_screenlake_to_work_properly_we_need_you_to_accept_accessibility_settings), Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        dialog.show()
                    }
                    binding.accessibilityAccessSelectFragmentNext.text = nextText
                }
                nextText -> {
                    findNavController().navigate(R.id.screenRecordFragment)
                }
                else -> {
                    binding.usuageAccessSelectFragmentError.visibility = View.VISIBLE
                    binding.accessibilityAccessSelectFragmentNext.text = grantAccessText
                }
            }
        }
    }

    private fun packageDialog(message : String, action: String): AlertDialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.screenlake))
            .setMessage((message))
            .setIcon(R.drawable.ic_launcher_foreground)
            .setPositiveButton(getString(R.string.agree)) { _, _ ->

                val intent = Intent(action)
                startActivity(intent)
            }

            .setNegativeButton(getString(R.string.not_now)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        service: Class<out AccessibilityService?>
    ): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(
                    service.name
                )
            ) return true
        }
        return false
    }
}