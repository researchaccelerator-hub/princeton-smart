package com.screenlake.recorder.utilities

import android.text.InputFilter
import com.screenlake.recorder.constants.ResearchConfig

/** The panel invite code: exactly [ResearchConfig.INVITE_CODE_LENGTH] digits, leading zeros allowed. */
object InviteCode {

    fun isValid(code: String, length: Int = ResearchConfig.INVITE_CODE_LENGTH): Boolean =
        code.length == length && code.all { it in '0'..'9' }

    /** Stops a code field from taking more characters than a code has. */
    fun lengthFilter(length: Int = ResearchConfig.INVITE_CODE_LENGTH): InputFilter =
        InputFilter.LengthFilter(length)
}
