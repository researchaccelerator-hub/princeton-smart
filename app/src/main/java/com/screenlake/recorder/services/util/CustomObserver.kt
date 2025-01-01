package com.screenlake.recorder.services.util

import androidx.lifecycle.Observer

/**
 * Custom observer class for observing LiveData changes.
 */
class CustomObserver<T>(private val callback: (T) -> Unit) : Observer<T> {
    override fun onChanged(data: T) {
        callback.invoke(data)
    }
}