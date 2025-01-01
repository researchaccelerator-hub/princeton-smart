package com.screenlake.recorder.viewmodels

import androidx.lifecycle.*
import com.screenlake.data.repository.RestrictedAppRepository
import com.screenlake.data.model.RestrictedApp
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RestrictedAppViewModel @Inject constructor(
    private val restrictedAppRepository: RestrictedAppRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            restrictedAppRepository.insertRestrictedApps()
        }
    }

    val restrictedApps: LiveData<List<RestrictedAppPersistentEntity>> = restrictedAppRepository.allApps.asLiveData()

    suspend fun updateAppIsUserRestricted(restrictedApp: RestrictedApp) {
        return restrictedAppRepository.updateAppIsUserRestricted(restrictedApp)
    }
}