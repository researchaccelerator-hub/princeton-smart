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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.GsonBuilder
import com.screenlake.R
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.databinding.FragmentNameEmailRegisterBinding
import com.screenlake.data.model.UserExistItem
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.HardwareChecks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
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
    var userExist = MutableLiveData<Boolean>()
    private val client = OkHttpClient()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        binding.loginPanelConfirmationlFragmentEmail.addTextChangedListener(object :
            TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val code = binding.loginPanelConfirmationlFragmentEmail.text.toString()
                if (validateCode(code)) {
                    binding.loginPanelConfirmationlFragmentEmail.error = null
                } else {
                    binding.loginPanelConfirmationlFragmentEmail.error =
                        getString(R.string.invalid_code_please_enter_a_4_digit_code_between_0000_and_9999)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.registerNameEmailFragmentNext.setOnClickListener {
            hideKeyboard()
            lifecycleScope.launch {
                if (HardwareChecks.isConnected(requireContext())) {
                    email = binding.registerNameEmailFragmentEmail.text.toString().trim()
                        .lowercase(Locale.getDefault())

                    val enteredCode = binding.loginPanelConfirmationlFragmentEmail.text?.toString()?.trim()
                    if(enteredCode.isNullOrBlank()) {
                        binding.loginPanelConfirmationlFragmentEmail.error =
                            getString(R.string.confirmation_code_cannot_be_empty)
                        return@launch
                    }

                    if(!validateCode(enteredCode)) {
                        binding.loginPanelConfirmationlFragmentEmail.error =
                            getString(R.string.code_invalid)
                        return@launch
                    }

                    amplifyRepository.code = enteredCode

                    if (binding.checkBox1.isChecked && binding.checkBox2.isChecked) {
                        userExist.postValue(false)
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

        userExist.observe(viewLifecycleOwner, Observer {
            if (it) {
                binding.registerNameEmailFragmentEmail.error = getString(R.string.email_taken)
                binding.registerNameEmailFragmentNext.text = getText(R.string.next)
            } else {
                emailConfirmed(email)
            }
        })
    }

    fun validateCode(code: String): Boolean {
        if (code.length != 4) {
            return false
        }
        if (!code.all { it.isDigit() }) {
            return false
        }
        val numericCode = code.toInt()
        return numericCode in 0..9999
    }

    private fun emailConfirmed(email: String) {
        if(email.isNullOrBlank()){
            binding.registerNameEmailFragmentEmail.error = getString(R.string.must_not_be_empty)
        } else {
            amplifyRepository.email = email
            findNavController().navigate(R.id.registerConfirmPassword)
        }
    }

    fun doesUserExist(url: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                CoroutineScope(Dispatchers.Default).launch { saveLog("Exception", ScreenshotData.ocrCleanUp(e.stackTraceToString())) }
                userExist.postValue(false)
                binding.registerNameEmailFragmentNext.text = getString(R.string.next)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.body?.string()
                    val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
                    val doesUserExist = gson.fromJson(result, UserExistItem::class.java).items
                    userExist.postValue(doesUserExist.first().message)
                } catch (ex: Exception) {
                    CoroutineScope(Dispatchers.IO).launch { saveLog("Exception", ScreenshotData.ocrCleanUp(ex.stackTraceToString())) }
                    FirebaseCrashlytics.getInstance().log(response?.body.toString())
                    FirebaseCrashlytics.getInstance().recordException(ex)
                }
            }
        })
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