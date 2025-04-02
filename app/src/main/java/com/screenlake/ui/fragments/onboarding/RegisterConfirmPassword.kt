package com.screenlake.ui.fragments.onboarding

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.databinding.FragmentRegisterConfirmPasswordBinding
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.utilities.HardwareChecks
import dagger.hilt.android.AndroidEntryPoint
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class RegisterConfirmPassword : Fragment() {

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    private lateinit var binding: FragmentRegisterConfirmPasswordBinding
    private lateinit var password1: String
    private lateinit var password2: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isRegisteredIn.postValue(false)

        binding.registerConfirmEmailCancel.setOnClickListener {
            hideKeyboard()
            loginErrorMsg.removeObservers(this)
            loginErrorMsg.postValue("")
            cloudAuthentication.clearUserAuth()
            findNavController().navigate(R.id.closeRegisterWindow)
        }

        isRegisteredIn.observe(viewLifecycleOwner, Observer {
            if(it){
                isRegisteredIn.removeObservers(viewLifecycleOwner)
                findNavController().navigate(R.id.registerConfirmEmailFragment)
            }else{
                binding.registerConfirmPasswordNext.text = getString(R.string.next)
                binding.registerConfirmPasswordNext.error = loginErrorMsg.value
            }
        })

        loginErrorMsg.observe(viewLifecycleOwner, Observer {
            if(!it.isEmpty()){
                if(it.contains(getString(R.string.exists))){
                    alertView(it + getString(R.string.if_your_account_is_not_confirmed_log_in_normally_with_your_email_and_password_and_your_confirmation_code_will_be_re_sent))
                }else{
                    alertView(it)
                }

            }
        })

        binding.registerConfirmPasswordFirst.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                // get the password when we start typing
                val password: String = binding.registerConfirmPasswordFirst.text.toString()
                if (!ConstantSettings.IS_PRODUCTION) return
                validatepass(password)
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        binding.registerConfirmPasswordNext.setOnClickListener {
            password1 = binding.registerConfirmPasswordFirst.text.toString().trim()
            password2 = binding.registerConfirmPasswordSecond.text.toString().trim()

            if(password1.isBlank()){
                binding.registerConfirmPasswordFirst.setError(getString(R.string.must_not_be_empty), null)
            }else if(password2.isBlank()){
                binding.registerConfirmPasswordSecond.setError(getString(R.string.must_not_be_empty), null)
            }else if(password1.isBlank() && password2.isBlank()){
                binding.registerConfirmPasswordFirst.setError(getString(R.string.must_not_be_empty), null)
                binding.registerConfirmPasswordSecond.setError(getString(R.string.must_not_be_empty), null)
            }else if(password1 == password2 && validatepass(password1)){
                signUpUser(password1, this.requireContext())
            }else{
                binding.registerConfirmPasswordSecond.setError(getString(R.string.passwords_must_match), null)
            }
        }
    }

    private fun signUpUser(password: String, context: Context) {
        amplifyRepository.password = password
        if(!ConstantSettings.IS_PRODUCTION){
            isRegisteredIn.postValue(true)
        }else{
            if(HardwareChecks.isConnected(requireContext())){
                binding.registerConfirmPasswordNext.text = getString(R.string.loading)
                cloudAuthentication.signUp(password, context)
            }else{
                binding.registerConfirmPasswordNext.text = getString(R.string.connect_to_wifi)
            }
        }
    }

    private fun alertView(message: String) {
        val dialog = AlertDialog.Builder(context).setIcon(R.drawable.ic_baseline_error_outline_24)
            .setMessage(message) //     .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            //      public void onClick(DialogInterface dialoginterface, int i) {
            //          dialoginterface.cancel();
            //          }})
            .setPositiveButton(
                getString(R.string.ok),
                DialogInterface.OnClickListener { dialoginterface, i ->
                    binding.registerConfirmPasswordNext.text = getString(R.string.next)
                }).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterConfirmPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun validatepass(password: String) : Boolean {

        // check for pattern
        val uppercase: Pattern = Pattern.compile("[A-Z]")
        val lowercase: Pattern = Pattern.compile("[a-z]")
        val digit: Pattern = Pattern.compile("[0-9]")
        val special: Pattern = Pattern.compile("[@#\$%^&+!=]")


        // if lowercase character is not present
        if (!lowercase.matcher(password).find()) {
            binding.registerConfirmPasswordFirst.setError(getString(R.string.password_must_include_a_lower_case), null)
            return false
        }

        // if uppercase character is not present
        if (!uppercase.matcher(password).find()) {
            binding.registerConfirmPasswordFirst.setError(getString(R.string.password_must_include_a_upper_case), null)
            return false
        }

        // if digit is not present
        if (!digit.matcher(password).find()) {
            binding.registerConfirmPasswordFirst.setError(getString(R.string.password_must_include_a_number), null)
            return false
        }

        // if special character is not present
        if (!special.matcher(password).find()) {
            binding.registerConfirmPasswordFirst.setError(getString(R.string.password_must_include_a_special_character), null)
            return false
        }

        // if password length is less than 8
        if (password.length < 8) {
            binding.registerConfirmPasswordFirst.setError(getString(R.string.password_must_be_8_characters_long), null)
            return false
        }

        return true
    }

    private fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        var isRegisteredIn  = MutableLiveData<Boolean>()
        var loginErrorMsg  = MutableLiveData<String>()
    }
}