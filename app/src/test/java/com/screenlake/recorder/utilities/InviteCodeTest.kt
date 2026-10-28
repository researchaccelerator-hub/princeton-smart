package com.screenlake.recorder.utilities

import com.screenlake.recorder.constants.ResearchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The invite-code length is a study setting: Princeton uses 4 digits, Minnesota 5. */
class InviteCodeTest {

    @Test
    fun `the default length is four digits`() {
        assertEquals(4, ResearchConfig.INVITE_CODE_LENGTH)
        assertTrue(InviteCode.isValid("0042"))
        assertFalse(InviteCode.isValid("00421"))
    }

    @Test
    fun `a five-digit study accepts five digits only`() {
        assertTrue(InviteCode.isValid("00421", length = 5))
        assertFalse(InviteCode.isValid("0042", length = 5))
    }

    @Test
    fun `codes are ASCII digits only`() {
        assertFalse(InviteCode.isValid("12a4"))
        assertFalse(InviteCode.isValid("12 4"))
        assertFalse(InviteCode.isValid("-123"))
        assertFalse(InviteCode.isValid("١٢٣٤")) // Arabic-Indic digits
        assertFalse(InviteCode.isValid(""))
    }
}
