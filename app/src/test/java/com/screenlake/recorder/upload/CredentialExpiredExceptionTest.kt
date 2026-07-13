package com.screenlake.recorder.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CredentialExpiredExceptionTest {

    @Test
    fun `carries message and cause`() {
        val cause = RuntimeException("original failure")
        val ex = CredentialExpiredException("credentials could not be recovered", cause)

        assertEquals("credentials could not be recovered", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `cause defaults to null`() {
        val ex = CredentialExpiredException("no cause given")

        assertEquals(null, ex.cause)
    }
}
