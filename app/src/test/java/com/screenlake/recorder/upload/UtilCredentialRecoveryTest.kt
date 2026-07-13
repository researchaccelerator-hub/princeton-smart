package com.screenlake.recorder.upload

import android.content.Context
import com.amazonaws.mobile.client.AWSMobileClient
import com.screenlake.recorder.authentication.AuthRecoveryManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class UtilCredentialRecoveryTest {

    private val authRecoveryManager = mockk<AuthRecoveryManager>()
    private val context = mockk<Context>(relaxed = true)
    private lateinit var mobileClient: AWSMobileClient

    @Before
    fun setUp() {
        mobileClient = mockk(relaxed = true)
        mockkStatic(AWSMobileClient::class)
        every { AWSMobileClient.getInstance() } returns mobileClient
        every { mobileClient.credentials } throws RuntimeException("token cache expired")
    }

    @After
    fun tearDown() {
        unmockkStatic(AWSMobileClient::class)
    }

    @Test
    fun `throws CredentialExpiredException when silent reauth fails`() = runTest {
        coEvery { authRecoveryManager.attemptSilentReauth(context) } returns false

        val util = Util(authRecoveryManager)

        assertThrows(CredentialExpiredException::class.java) {
            kotlinx.coroutines.runBlocking {
                util.generates3ShareUrl(context, "/tmp/file.zip", "some/upload/path.zip")
            }
        }
    }
}
