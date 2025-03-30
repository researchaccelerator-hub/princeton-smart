package com.screenlake.recorder.viewmodels

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Define a companion object to hold the progress updates Flow
class WorkerProgressManager {
    companion object {
        val progressUpdates = MutableLiveData<String>("")

        fun updateProgress(message: String) {
            progressUpdates.postValue(progressUpdates.value + ", $message")
        }

        fun clear() {
            progressUpdates.postValue("")
        }
    }
}