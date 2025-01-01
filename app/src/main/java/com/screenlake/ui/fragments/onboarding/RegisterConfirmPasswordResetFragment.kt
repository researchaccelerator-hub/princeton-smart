package com.screenlake.ui.fragments.onboarding

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.amplifyframework.auth.AuthException
import com.amplifyframework.core.Amplify
import com.screenlake.R
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.databinding.FragmentConfirmPasswordResetBinding
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class RegisterConfirmPasswordResetFragment : Fragment() {

    @Inject
    lateinit var logEventDao: LogEventDao

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    private var _binding: FragmentConfirmPasswordResetBinding? = null
    private val binding get() = _binding!!

    private lateinit var password1: String
    private lateinit var password2: String
    private var isResetRequestSent = MutableLiveData<Boolean>()
    private var resetErrorMsg = MutableLiveData<String>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.confirmPasswordResetCodeCancel.setOnClickListener {
            hideKeyboard()
            cloudAuthentication.clearUserAuth()
            findNavController().navigate(R.id.closeRegisterWindow)
        }

        binding.registerConfirmPasswordFirstReset.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val password: String = binding.registerConfirmPasswordFirstReset.text.toString()
                validatepass(password)
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        binding.confirmPasswordResetCodeNextReset.setOnClickListener {
            password1 = binding.registerConfirmPasswordFirstReset.text.toString().trim()
            password2 = binding.registerConfirmPasswordSecondReset.text.toString().trim()

            val code = binding.confirmPasswordResetCodeTextReset?.text.toString().trim()
            if (code.isNullOrBlank()) {
                binding.confirmPasswordResetCodeReset.error =
                    getString(R.string.confirm_failed_badly_formatted_code)
                return@setOnClickListener
            }

            val mustNotBeEmpty = getString(R.string.must_not_be_empty)
            if (password1.isNullOrBlank()) {
                binding.registerConfirmPasswordFirstReset.setError(mustNotBeEmpty, null)
            } else if (password2.isNullOrBlank()) {
                binding.registerConfirmPasswordSecondReset.setError(mustNotBeEmpty, null)
            } else if (password1.isNullOrBlank() && password2.isNullOrBlank()) {
                binding.registerConfirmPasswordFirstReset.setError(mustNotBeEmpty, null)
                binding.registerConfirmPasswordSecondReset.setError(mustNotBeEmpty, null)
            } else if (password1 == password2 && validatepass(password1)) {
                resetPassword(password1, code)
            } else {
                binding.registerConfirmPasswordSecondReset.setError(getString(R.string.passwords_must_match), null)
            }
        }

        isResetRequestSent.observe(viewLifecycleOwner, Observer {
            if (it) {
                lifecycleScope.launch {
                    hideKeyboard()
                    binding.confirmPasswordResetCodeNextReset.text = getString(R.string.success)
                    delay(1500L)
                    findNavController().navigate(R.id.loginFragment)
                }
            }
        })

        resetErrorMsg.observe(viewLifecycleOwner, Observer {
            binding.confirmPasswordResetCodeReset.error = it
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConfirmPasswordResetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetPassword(newPassword: String, confirmationCode: String) {
        Amplify.Auth.confirmResetPassword(
            newPassword,
            confirmationCode,
            {
                isResetRequestSent.postValue(true)
                Timber.tag("ResetPassword").d("New password confirmed")
            }
        ) { error: AuthException ->
            isResetRequestSent.postValue(false)
            resetErrorMsg.postValue(error.message)
            CoroutineScope(Dispatchers.Default).launch { saveLog("Exception", ScreenshotData.ocrCleanUp(error.stackTraceToString())) }
            Timber.tag("ResetPassword").d(error.toString())
        }
    }

    private suspend fun saveLog(event: String, msg: String = "") {
        logEventDao.saveException(
            LogEventEntity(
                event,
                msg,
                amplifyRepository.email
            )
        )
    }

    private fun validatepass(password: String): Boolean {
        val uppercase: Pattern = Pattern.compile("[A-Z]")
        val lowercase: Pattern = Pattern.compile("[a-z]")
        val digit: Pattern = Pattern.compile("[0-9]")
        val special: Pattern = Pattern.compile("[@#\$%^&+!=]")

        if (!lowercase.matcher(password).find()) {
            binding.registerConfirmPasswordFirstReset.setError(getText(R.string.password_must_include_a_lower_case), null)
            return false
        }

        if (!uppercase.matcher(password).find()) {
            binding.registerConfirmPasswordFirstReset.setError(getText(R.string.password_must_include_a_upper_case), null)
            return false
        }

        if (!digit.matcher(password).find()) {
            binding.registerConfirmPasswordFirstReset.setError(getText(R.string.password_must_include_a_number), null)
            return false
        }

        if (!special.matcher(password).find()) {
            binding.registerConfirmPasswordFirstReset.setError(getText(R.string.password_must_include_a_special_character), null)
            return false
        }

        if (password.length < 8) {
            binding.registerConfirmPasswordFirstReset.setError(getText(R.string.password_must_be_8_characters_long), null)
            return false
        }

        return true
    }
}