package com.screenlake.ui.fragments.onboarding

import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.screenlake.MainActivity
import com.screenlake.R
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.AmplifyRepository
import com.screenlake.recorder.authentication.CloudAuthentication
import com.screenlake.recorder.services.util.SharedPreferencesUtil
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.viewmodels.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RegisterLoadingFragment : Fragment() {

    @Inject
    lateinit var amplifyRepository: AmplifyRepository

    @Inject
    lateinit var cloudAuthentication: CloudAuthentication

    private val userViewModel: UserViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isConfirmed.removeObservers(viewLifecycleOwner)
        val userName = UUID.randomUUID().toString()
        amplifyRepository.username = userName
        val user = UserEntity(
            email = amplifyRepository.email,
            emailHash = amplifyRepository.code,
            model = BaseUtility.getDeviceName(),
            sdk = Build.VERSION.SDK,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            isEmulator = Build.MODEL.contains("Emulator")
        )

        setUpUser(user)

        isUserSetupCompleted.observe(viewLifecycleOwner) {
             if (it) {
                isUserSetupCompleted.removeObservers(viewLifecycleOwner)
                cloudAuthentication.autoSignIn(WeakReference(this@RegisterLoadingFragment.requireContext()))

                 lifecycleScope.launch {
                     delay(1500)
                     findNavController().navigate(R.id.screenlakeInfoFragment)
                 }
            }
        }

        if(!ConstantSettings.IS_PRODUCTION){
            isConfirmed.postValue(true)
        }

        startTimerForTenSeconds()
    }

    fun startTimerForTenSeconds() {
        lifecycleScope.launch {
            // Delay for 10 seconds
            delay(10000)

            // This code will execute after 10 seconds
            performAction()
        }
    }

    fun performAction() {
        // Replace this with whatever action you want to perform after 10 seconds
        findNavController().navigate(R.id.screenlakeInfoFragment)
    }

    fun setUpUser(user: UserEntity) = lifecycleScope.launch {
        delay(1500)

        // TODO: Two user are being added.
        insertUser(user)

        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        editor.putBoolean(activity?.getString(R.string.limit_data_usage),true)
        editor.apply()

        context?.let { SharedPreferencesUtil.setLimitDataUsage(it, true) }

        user.email?.let { createAndEncryptPassword(it, amplifyRepository.password) }

        isUserSetupCompleted.postValue(true)
    }

    private suspend fun insertUser(user: UserEntity){
        val userExist = userViewModel.userExist()

        if(!userExist) {
            // Save user locally
            userViewModel.insertUser(user)
        }
    }

    private fun createAndEncryptPassword(email: String, password: String){
        val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
        val masterKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

        val sharedPreferences = EncryptedSharedPreferences.create(
            "shared_preferences_filename",
            masterKeyAlias,
            this.requireContext(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.edit().apply {
            putString("email", email)
            putString("password", password)
        }.apply()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register_loading, container, false)
    }

    companion object {
        var isConfirmed  = MutableLiveData<Boolean>()
        var isUserSetupCompleted  = MutableLiveData<Boolean>()
    }
}