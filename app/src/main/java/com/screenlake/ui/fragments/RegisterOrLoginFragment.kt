package com.screenlake.ui.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.screenlake.R
import com.screenlake.databinding.LoginOrRegisterBinding
import com.screenlake.recorder.viewmodels.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterOrLoginFragment : Fragment() {

    private var _binding: LoginOrRegisterBinding? = null
    private val binding get() = _binding!!

    private val userModel: UserViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loginLayout.visibility = View.INVISIBLE

        lifecycleScope.launch {
            if (userModel.userExist()) {
                findNavController().navigate(R.id.screenRecordFragment)
            } else {
                binding.loginLayout.visibility = View.VISIBLE
            }
        }

        binding.sendToLoginButton.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        binding.sendToRegisterButton.setOnClickListener {
            findNavController().navigate(R.id.registerNameEmailFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LoginOrRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
