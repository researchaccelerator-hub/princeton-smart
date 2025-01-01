package com.screenlake.recorder.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val mutableSelectedItem = MutableLiveData<Boolean>()
    val selectedItem: LiveData<Boolean> get() = mutableSelectedItem

    fun isOnPastOnBoarding(item: Boolean) {
        mutableSelectedItem.value = item
    }

    fun isOnPastOnBoardingBackground(item: Boolean) {
        mutableSelectedItem.postValue(item)
    }
}