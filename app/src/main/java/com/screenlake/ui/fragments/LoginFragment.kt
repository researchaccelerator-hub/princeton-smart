package com.screenlake.ui.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.amplifyframework.api.ApiException
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.options.AWSCognitoAuthSignInOptions
import com.amplifyframework.auth.cognito.options.AuthFlowType
import com.amplifyframework.auth.result.step.AuthNextSignInStep
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.kotlin.core.Amplify
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.databinding.FragmentLoginBinding
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.utilities.hideKeyboard
import com.screenlake.recorder.viewmodels.MainViewModel
import com.screenlake.recorder.viewmodels.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    @Inject
    lateinit var logEventDao: LogEventDao

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private val mainViewModel: MainViewModel by activityViewModels()
    private val userModel: UserViewModel by viewModels()
    private lateinit var binding: FragmentLoginBinding
    private var loginFailed = MutableLiveData<Boolean>()
    private var isSubmitted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.fragment = this // Bind fragment for click handling in layout

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.isOnPastOnBoarding(false)
        setupListeners()
        observeLoginState()

        onSelectRegister()

        observerLoginState()
    }


    fun onBackClicked() {
        findNavController().navigate(R.id.registerOrLoginFragment)
    }

    private fun setupListeners() {
        binding.loginResetPassword.setOnClickListener {
            findNavController().navigate(R.id.resetPasswordForEmailFragment)
        }

        binding.loginBack.setOnClickListener {
            findNavController().navigate(R.id.registerOrLoginFragment)
        }

        binding.loginUser.setOnClickListener {
            hideKeyboard()

            val email = binding.loginEmail.editText?.text.toString()
            val password = binding.loginPassword.editText?.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && !isSubmitted) {
                isSubmitted = true
                lifecycleScope.launch {
                    val isConnected = HardwareChecks.isConnectedAsync(WeakReference(it.context))
                    if (!isConnected) {
                        isSubmitted = false
                    }
                }
                binding.loginUser.text = getString(R.string.logging_in)
            } else {
                if (email.isEmpty()) {
                    binding.loginEmail.error = getString(R.string.email_cannot_be_empty)
                } else if (password.isEmpty()) {
                    binding.loginPassword.error = getString(R.string.password_cannot_be_empty)
                }
            }
        }
    }

    fun onResetPasswordClicked() {
        // Navigate to the Reset Password Fragment
        findNavController().navigate(R.id.resetPasswordForEmailFragment)
    }

    private fun observeLoginState() {
        MainActivity.isLoggedIn.observe(viewLifecycleOwner) {
            if (it) {
                setUserLoggedOut()
                saveUserSendToHome(amplifyRepository.email)
            }
        }

        loginFailed.observe(viewLifecycleOwner) {
            alertView(getString(R.string.your_login_was_incorrect))
            revertButton()
            clearEditText()
            isSubmitted = false
        }

        MainActivity.isWifiConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected && isSubmitted) {
                lifecycleScope.launch { loginUser() }
            } else if (isSubmitted) {
                isSubmitted = false
                binding.loginUser.text = getString(R.string.connect_to_wifi)
            }
        }
    }

    private fun onSelectRegister() {
        binding.loginBack.setOnClickListener {
            findNavController().navigate(R.id.registerOrLoginFragment)
        }
    }

    private fun revertButton() {
        binding.loginUser.text = getString(R.string.login)
    }

    private fun clearEditText() {
        binding.loginEmail.editText?.text?.clear()
        binding.loginPassword.editText?.text?.clear()
    }

    private fun setUserLoggedOut() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putBoolean(getString(R.string.is_signed_out), false)
            apply()
        }
    }

    private fun observerLoginState() {
        MainActivity.isLoggedIn.observe(viewLifecycleOwner, Observer {
            if (it) {
                setUserLoggedOut()
                saveUserSendToHome(amplifyRepository.email)
            } else {
                // Toast.makeText(activity, getString(R.string.login_failed), Toast.LENGTH_LONG).show()
            }
        })
    }

    private suspend fun loginUser() {
        val email = binding.loginEmail.editText?.text.toString()
        val password = binding.loginPassword.editText?.text.toString()

        loginRegex()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            binding.loginUser.text = getString(R.string.login)
            try {
                amplifyRepository.email = email
                signIn(email, password)
            } catch (e: Exception) {
                isSubmitted = false
                CoroutineScope(Dispatchers.IO).launch {
                    saveLog(
                        "Exception",
                        e.stackTraceToString()
                    )
                }
                FirebaseCrashlytics.getInstance().recordException(e)
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
            }
        } else {
            if (email.isEmpty()) {
                binding.loginEmail.error = getString(R.string.email_cannot_be_empty)
            } else if (password.isEmpty()) {
                binding.loginPassword.error = getString(R.string.password_cannot_be_empty)
            }
        }
    }

    private fun loginRegex() {
        binding.loginEmail.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                binding.loginEmail.error = null
                binding.loginPassword.error = null
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        binding.loginPassword.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                binding.loginEmail.error = null
                binding.loginPassword.error = null
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private suspend fun saveLog(event: String, msg: String = "") {
        logEventDao.saveException(
            LogEventEntity(event, msg, amplifyRepository.email)
        )
    }

    private fun alertView(message: String) {
        AlertDialog.Builder(context)
            .setIcon(R.drawable.ic_baseline_error_outline_24)
            .setMessage(message)
            .setPositiveButton("Ok", null)
            .show()
    }

    private fun saveUserSendToHome(email: String) = lifecycleScope.launch {
        val userExist = userModel.userExist()

        if (!userExist) {
            val user = getUserFromDatastore(email)

            if (!user?.email.isNullOrBlank()) {
                userModel.insertUser(user!!)
                MainActivity.isLoggedOut.postValue(false)
                findNavController().navigate(R.id.screenRecordFragment)
            } else {
                saveLog("Exception", "User null -> ${user?.username}")
                isSubmitted = false
                MainActivity.isLoggedIn.postValue(false)
                cloudAuthentication.signOut(MainActivity.isLoggedOut)
                binding.loginUser.text = getString(R.string.login)
            }
        }
    }

    private suspend fun getUserFromDatastore(email: String): UserEntity? {
        return try {
            UserEntity(email = email)
        } catch (error: ApiException) {
            saveLog("Exception", error.stackTraceToString())
            Timber.e(error)
            FirebaseCrashlytics.getInstance().recordException(error)
            null
        }
    }

    private suspend fun signIn(email: String, password: String) {
        amplifyRepository.email = email
        amplifyRepository.password = password

        val options = AWSCognitoAuthSignInOptions.builder()
            .authFlowType(AuthFlowType.USER_SRP_AUTH)
            .build()

        try {
            val result = Amplify.Auth.signIn(email, password, options)
            saveLog("SIGNIN", result.nextStep.toString())
            handleSignInStep(result.nextStep)
        } catch (error: Exception) {
            handleSignInError(error, email)
        }
    }

    private fun handleSignInStep(nextStep: AuthNextSignInStep) {
        try {
            when (nextStep.signInStep) {
                AuthSignInStep.CONFIRM_SIGN_IN_WITH_SMS_MFA_CODE -> {
                    Timber.tag("AuthQuickstart").d("SMS code sent to ${nextStep.codeDeliveryDetails?.destination}")
                    Timber.tag("AuthQuickstart").d("Additional Info: ${nextStep.additionalInfo}")
                    // Prompt the user to enter the SMS MFA code and confirm sign-in
                }
                AuthSignInStep.CONFIRM_SIGN_IN_WITH_CUSTOM_CHALLENGE -> {
                    Timber.tag("AuthQuickstart").d("Custom challenge, additional info: ${nextStep.additionalInfo}")
                    // Handle custom challenge response
                }
                AuthSignInStep.CONFIRM_SIGN_IN_WITH_NEW_PASSWORD -> {
                    Timber.tag("AuthQuickstart").d("Sign in with new password, additional info: ${nextStep.additionalInfo}")
                    // Prompt the user for a new password
                }
                AuthSignInStep.RESET_PASSWORD -> {
                    Timber.tag("AuthQuickstart").d("Reset password, additional info: ${nextStep.additionalInfo}")
                    // Start the reset password flow
                }
                AuthSignInStep.CONFIRM_SIGN_UP -> {
                    Timber.tag("AuthQuickstart").d("Confirm signup, additional info: ${nextStep.additionalInfo}")
                    // Handle user confirmation
                }
                AuthSignInStep.DONE -> {
                    Timber.tag("AuthQuickstart").d("SignIn complete")
                    MainActivity.isLoggedIn.value = true
                    MainActivity.isLoggedOut.value = false
                }
            }
        } catch (e: Exception) {
            Timber.tag("AuthQuickstart").e("Error handling sign-in step: $e")
            // Handle the exception as needed
        }
    }

    private suspend fun handleSignInError(error: Exception, email: String) {
        saveLog("Exception", error.stackTraceToString())
        Timber.tag("AuthQuickstart").e("Unexpected error occurred: $error")

        when (error) {
            is AuthException.UserNotConfirmedException -> {
                handleUserNotConfirmed(email)
            }
            else -> {
                loginFailed.postValue(true)
                if (error.message?.contains("HTTP request") == true) {
                    Timber.e("Could not connect to the internet.")
                } else {
                    Timber.e("Failed sign in: $error")
                }
            }
        }
    }

    private suspend fun handleUserNotConfirmed(email: String) {
        try {
            val result = Amplify.Auth.resendSignUpCode(email)
            findNavController().navigate(R.id.registerConfirmEmailFragment)
            Timber.tag("AuthQuickstart").d("Code was sent again: $result.")
        } catch (error: AuthException) {
            saveLog("Exception", error.stackTraceToString())
            loginFailed.postValue(true)
            Timber.tag("AuthQuickstart").e("Failed to resend code: $error")
        }
    }
}