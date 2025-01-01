package com.screenlake.data.model

import androidx.annotation.Keep

/**
 * Data class representing email and password credentials.
 *
 * @property email The email address.
 * @property password The password.
 */
@Keep
class EmailPasswordData(
    var email: String,
    var password: String
)