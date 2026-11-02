package com.screenlake.ui.fragments.onboarding

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.screenlake.R
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.databinding.FragmentNameEmailRegisterBinding
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.authentication.UserExistsCheck
import com.screenlake.recorder.constants.ResearchConfig
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.utilities.InviteCode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RegisterNameEmailFragment : Fragment() {

    @Inject
    lateinit var logEventDao: LogEventDao

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    private var _binding: FragmentNameEmailRegisterBinding? = null
    private val binding get() = _binding!!

    var email = ""
    private var emailCheckRunning = false
    private val userExistsCheck = UserExistsCheck()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, imeBottom))
            insets
        }

        val imm: InputMethodManager? =
            activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

        imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

        binding.registerNameEmailFragmentEmailCancel.setOnClickListener {
            hideKeyboard()
            cloudAuthentication.clearUserAuth()
            findNavController().navigate(R.id.closeRegisterWindow)
        }

        val htmlText = getString(R.string.agreement_text)
        binding.termsAndPrivacy.text = Html.fromHtml(htmlText , Html.FROM_HTML_MODE_COMPACT)
        binding.termsAndPrivacy.movementMethod = LinkMovementMethod.getInstance()

//        binding.registerNameEmailFragmentTerms.setOnClickListener {
//            findNavController().navigate(R.id.goToTermsOfService)
//        }

        binding.loginPanelConfirmationlFragmentEmail.filters = arrayOf(InviteCode.lengthFilter())
        binding.loginPanelConfirmationlFragmentEmail.addTextChangedListener(object :
            TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val code = binding.loginPanelConfirmationlFragmentEmail.text.toString()
                if (InviteCode.isValid(code)) {
                    binding.loginPanelConfirmationlFragmentEmail.error = null
                } else {
                    binding.loginPanelConfirmationlFragmentEmail.error =
                        getString(R.string.invalid_invite_code, ResearchConfig.INVITE_CODE_LENGTH)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.registerNameEmailFragmentNext.setOnClickListener {
            hideKeyboard()
            lifecycleScope.launch {
                if (HardwareChecks.isConnected(requireContext())) {
                    email = enteredEmail()

                    val enteredCode = binding.loginPanelConfirmationlFragmentEmail.text?.toString()?.trim()
                    if(enteredCode.isNullOrBlank()) {
                        binding.loginPanelConfirmationlFragmentEmail.error =
                            getString(R.string.confirmation_code_cannot_be_empty)
                        return@launch
                    }

                    if(!InviteCode.isValid(enteredCode)) {
                        binding.loginPanelConfirmationlFragmentEmail.error =
                            getString(R.string.code_invalid)
                        return@launch
                    }

                    amplifyRepository.code = enteredCode

                    if (binding.checkBox1.isChecked && binding.checkBox2.isChecked) {
                        checkEmailThenContinue(email)
                    } else {
                        binding.registerNameEmailFragmentNext.text =
                            getString(R.string.accept_terms_above)
                    }
                } else {
                    binding.registerNameEmailFragmentNext.text = getString(R.string.connect_to_wifi)
                }
            }
        }

        binding.checkBox2.setOnClickListener {
            if (binding.checkBox1.isChecked) {
                binding.registerNameEmailFragmentNext.text = getText(R.string.next)
            }
        }

        binding.checkBox1.setOnClickListener {
            if (binding.checkBox2.isChecked) {
                binding.registerNameEmailFragmentNext.text = getText(R.string.next)
            }
        }

    }

    private fun enteredEmail(): String =
        binding.registerNameEmailFragmentEmail.text.toString().trim().lowercase(Locale.getDefault())

    /**
     * Runs the optional user-exists check (ResearchConfig.USER_EXISTS_URL) for [checkedEmail],
     * one check at a time with Next disabled, and acts on the answer only if the email field
     * still holds that email, so an answer is never applied to a different address.
     */
    private suspend fun checkEmailThenContinue(checkedEmail: String) {
        if (emailCheckRunning) return
        emailCheckRunning = true
        binding.registerNameEmailFragmentNext.isEnabled = false
        val taken = try {
            userExistsCheck.exists(checkedEmail)
        } finally {
            emailCheckRunning = false
            _binding?.registerNameEmailFragmentNext?.isEnabled = true
        }
        if (_binding == null || enteredEmail() != checkedEmail) return

        if (taken) {
            binding.registerNameEmailFragmentEmail.error = getString(R.string.email_taken)
            binding.registerNameEmailFragmentNext.text = getText(R.string.next)
        } else {
            emailConfirmed(checkedEmail)
        }
    }

    private fun emailConfirmed(email: String) {
        if(email.isNullOrBlank()){
            binding.registerNameEmailFragmentEmail.error = getString(R.string.must_not_be_empty)
        } else {
            amplifyRepository.email = email
            findNavController().navigate(R.id.registerConfirmPassword)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNameEmailRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private suspend fun saveLog(event:String, msg: String = "") {
        logEventDao.saveException(
            LogEventEntity(
                event,
                msg,
                amplifyRepository.email
            )
        )
    }
}