package com.screenlake.recorder.upload

/** A failure that should clear up on its own (offline, timeout, 5xx); keep the files and retry. */
class AwsTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The app's AWS setup is wrong or incomplete; retrying will not help. */
class AwsConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause)
