package com.screenlake.ui.fragments.onboarding

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.amplifyframework.core.Amplify
import com.screenlake.R
import com.screenlake.databinding.FragmentEnterEmailPasswordResetBinding
import com.screenlake.recorder.utilities.HardwareChecks
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ResetPasswordForEmailFragment : Fragment() {

    private var _binding: FragmentEnterEmailPasswordResetBinding? = null
    private val binding get() = _binding!!

    var email = ""
    private var isResetRequestSent = MutableLiveData<Boolean>()
    private var resetErrorMsg = MutableLiveData<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imm: InputMethodManager? =
            activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

        imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

        binding.registerNameEmailFragmentEmailCancel.setOnClickListener {
            hideKeyboard()
            findNavController().navigate(R.id.closeRegisterWindow)
        }

        binding.resetPasswordNext.setOnClickListener {
            if (HardwareChecks.isConnected(requireContext())) {
                email = binding.resetPasswordEmailText.text.toString().trim()
                resetPasswordRequest(email)
                binding.resetPasswordNext.text = getString(R.string.sending)
            } else {
                binding.resetPasswordNext.text = getString(R.string.connect_to_wifi)
            }
        }

        isResetRequestSent.observe(viewLifecycleOwner, Observer {
            if (it) {
                hideKeyboard()
                findNavController().navigate(R.id.registerConfirmPasswordResetFragment)
            }
        })

        resetErrorMsg.observe(viewLifecycleOwner, Observer {
            binding.resetPasswordEmailText.error = it
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEnterEmailPasswordResetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetPasswordRequest(username: String){
        Amplify.Auth.resetPassword(username,
            {
                isResetRequestSent.postValue(true)
                Timber.tag("ResetPassword").d( "Password reset OK: $it")
            },
            {
                isResetRequestSent.postValue(false)
                resetErrorMsg.postValue(it.message)
                Timber.tag("ResetPassword").d( "Password reset failed $it")
            }
        )
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}