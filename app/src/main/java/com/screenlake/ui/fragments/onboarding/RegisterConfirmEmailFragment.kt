package com.screenlake.ui.fragments.onboarding

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.amplifyframework.core.Amplify
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.databinding.FragmentRegisterConfirmEmailBinding
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.utilities.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RegisterConfirmEmailFragment : Fragment() {

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private var isConfirmed  = MutableLiveData<Boolean>()
    private var errorMsg  = MutableLiveData<String>()
    private lateinit var binding: FragmentRegisterConfirmEmailBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerConfirmCodeNext.setOnClickListener {
            val code = binding.registerNameEmailFragmentCode.text?.toString()?.trim()
            if(!code.isNullOrBlank()){
                hideKeyboard()
                if (ConstantSettings.IS_PRODUCTION){
                    confirmSignUp(code)
                }else{
                    isConfirmed.postValue(true)
                }
                binding.registerConfirmCodeNext.text = getString(R.string.confirming)
            }else{
                binding.registerNameEmailFragmentCode.error =
                    getString(R.string.confirmation_failed_badly_formatted_code)
            }
        }

        isConfirmed.observe(viewLifecycleOwner) {
            if (it){
                lifecycleScope.launch {
                    binding.registerConfirmCodeNext.text = getString(R.string.confirmed)
                    delay(1500)

                    findNavController().navigate(R.id.registerLoadingFragment)
                }
            }else{
                binding.registerConfirmCodeNext.text = getString(R.string.confirm_cap)
                binding.registerNameEmailFragmentCode.error = errorMsg.value
            }
        }

        binding.registerConfirmEmailCodeCancel.setOnClickListener {
            hideKeyboard()
            cloudAuthentication.clearUserAuth()
            findNavController().navigate(R.id.closeRegisterWindow)
        }
    }

    fun confirmSignUp(confirmationCode: String) {
        Amplify.Auth.confirmSignUp(amplifyRepository.email, confirmationCode,
            {
                Timber.d("Confirmed sign up: $it")
                isConfirmed.postValue(true)
            },
            {
                Timber.e("Failed to confirm sign up: $it" )
                errorMsg.postValue(it.message)
                isConfirmed.postValue(false)
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterConfirmEmailBinding.inflate(inflater, container, false)
        return binding.root
    }
}