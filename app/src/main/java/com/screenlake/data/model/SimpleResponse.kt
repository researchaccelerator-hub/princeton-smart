package com.screenlake.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose

@Keep
data class SimpleResponse(@Expose val message: String)