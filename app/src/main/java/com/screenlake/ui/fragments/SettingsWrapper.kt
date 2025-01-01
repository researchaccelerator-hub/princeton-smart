package com.screenlake.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import com.screenlake.R
import com.screenlake.databinding.FragmentSettingsWrapperBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsWrapper : Fragment() {

    private lateinit var binding: FragmentSettingsWrapperBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fragSettings.findNavController().navigate(R.id.settingsFragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsWrapperBinding.inflate(inflater, container, false)
        return binding.root
    }

}