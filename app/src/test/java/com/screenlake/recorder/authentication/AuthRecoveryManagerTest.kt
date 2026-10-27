package com.screenlake.recorder.authentication

import android.content.Context
import com.screenlake.data.model.EmailPasswordData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRecoveryManagerTest {

    private val encryptedCredentialsStore = mockk<EncryptedCredentialsStore>()
    private val cognitoSessionAuthenticator = mockk<CognitoSessionAuthenticator>()
    private val context = mockk<Context>(relaxed = true)

    private val manager = AuthRecoveryManager(encryptedCredentialsStore, cognitoSessionAuthenticator)

    @Test
    fun `returns false and skips sign-in when stored credentials are blank`() = runTest {
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("", "")

        val result = manager.attemptSilentReauth(context)

        assertFalse(result)
    }

    @Test
    fun `returns true when both AWSMobileClient and Amplify sign-in succeed`() = runTest {
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("a@b.com", "pw")
        coEvery { cognitoSessionAuthenticator.signInAwsMobileClient("a@b.com", "pw") } returns SignInOutcome.SIGNED_IN
        coEvery { cognitoSessionAuthenticator.signInAmplify("a@b.com", "pw") } returns SignInOutcome.SIGNED_IN

        val result = manager.attemptSilentReauth(context)

        assertTrue(result)
    }

    @Test
    fun `returns false when AWSMobileClient sign-in fails`() = runTest {
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("a@b.com", "pw")
        coEvery { cognitoSessionAuthenticator.signInAwsMobileClient("a@b.com", "pw") } returns SignInOutcome.REJECTED
        coEvery { cognitoSessionAuthenticator.signInAmplify("a@b.com", "pw") } returns SignInOutcome.SIGNED_IN

        val result = manager.attemptSilentReauth(context)

        assertFalse(result)
    }

    @Test
    fun `returns false when Amplify sign-in fails`() = runTest {
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("a@b.com", "pw")
        coEvery { cognitoSessionAuthenticator.signInAwsMobileClient("a@b.com", "pw") } returns SignInOutcome.SIGNED_IN
        coEvery { cognitoSessionAuthenticator.signInAmplify("a@b.com", "pw") } returns SignInOutcome.REJECTED

        val result = manager.attemptSilentReauth(context)

        assertFalse(result)
    }

    @Test
    fun `an unreachable Cognito is reported as unavailable, not as rejected credentials`() = runTest {
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("a@b.com", "pw")
        coEvery { cognitoSessionAuthenticator.signInAwsMobileClient("a@b.com", "pw") } returns SignInOutcome.UNAVAILABLE
        coEvery { cognitoSessionAuthenticator.signInAmplify("a@b.com", "pw") } returns SignInOutcome.REJECTED

        assertEquals(SignInOutcome.UNAVAILABLE, manager.attemptSilentReauthWithOutcome(context))
        assertFalse(manager.attemptSilentReauth(context))
    }
}
