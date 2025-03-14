package com.screenlake.recorder.viewmodels

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Define a companion object to hold the progress updates Flow
class WorkerProgressManager {
    companion object {
        private val _progressUpdates = MutableStateFlow<String>("")
        val progressUpdates: StateFlow<String> = _progressUpdates

        fun updateProgress(message: String) {
            _progressUpdates.value = message
        }
    }
}