package com.screenlake.recorder.upload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.screenlake.recorder.authentication.AuthRecoveryManager
import com.screenlake.recorder.authentication.SignInOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (not a bare JVM test) because preparing an upload session calls
 * getCredProvider(), which builds a real JSONObject via
 * Util.buildAWSConfiguration() and calls the real (mocked-instance) AWSMobileClient.initialize().
 * A bare JVM test hits Android's stub org.json.JSONObject ("Method put ... not mocked");
 * Robolectric provides a working shadow so that path can actually run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UtilCredentialRecoveryTest {

    private val authRecoveryManager = mockk<AuthRecoveryManager>()
    private lateinit var context: Context
    private lateinit var mobileClient: AWSMobileClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mobileClient = mockk(relaxed = true)
        mockkStatic(AWSMobileClient::class)
        every { AWSMobileClient.getInstance() } returns mobileClient

        // Simulate AWSMobileClient.initialize() completing immediately and successfully, so
        // getCredProvider()'s latch counts down right away instead of waiting on a real callback.
        val callbackSlot = slot<Callback<UserStateDetails?>>()
        every { mobileClient.initialize(any(), any(), capture(callbackSlot)) } answers {
            callbackSlot.captured.onResult(null)
        }

        every { mobileClient.credentials } throws RuntimeException("token cache expired")
    }

    @After
    fun tearDown() {
        unmockkStatic(AWSMobileClient::class)
    }

    @Test
    fun `throws CredentialExpiredException when silent reauth fails`() = runTest {
        coEvery { authRecoveryManager.attemptSilentReauthWithOutcome(context) } returns SignInOutcome.REJECTED

        val util = Util(authRecoveryManager).apply { isOnline = { true } }

        assertThrows(CredentialExpiredException::class.java) {
            runBlocking {
                util.generates3ShareUrl(context, "/tmp/file.zip", "some/upload/path.zip")
            }
        }
    }

    /**
     * Regression test for a real device-testing finding: AWSMobileClient.getCredentials() can
     * block forever (an un-timed-out internal latch, confirmed by decompiling the SDK) when the
     * session has been revoked server-side. This simulates that hang directly with a
     * CountDownLatch that never counts down, and proves generates3ShareUrl() still terminates
     * (via the 15s timeout in checkCredentials()) instead of hanging forever. If the timeout
     * mechanism regresses, this test times out at 30s rather than hanging the whole build.
     */
    @Test(timeout = 30_000)
    fun `checkCredentials times out instead of hanging forever when the SDK call blocks`() = runTest {
        val neverCountsDown = java.util.concurrent.CountDownLatch(1)
        every { mobileClient.credentials } answers {
            neverCountsDown.await()
            error("unreachable — latch never counts down")
        }
        coEvery { authRecoveryManager.attemptSilentReauthWithOutcome(context) } returns SignInOutcome.REJECTED

        val util = Util(authRecoveryManager).apply { isOnline = { true } }

        assertThrows(CredentialExpiredException::class.java) {
            runBlocking {
                util.generates3ShareUrl(context, "/tmp/file.zip", "some/upload/path.zip")
            }
        }
    }

    /**
     * The second hang site found in review was presigning, which called back into the SDK's
     * credential provider for every file. Presigning now uses the credentials loaded once for the
     * batch, so the SDK is asked for credentials once however many files are signed.
     */
    @Test
    fun `credentials are loaded once per batch, not once per file`() = runBlocking {
        var calls = 0
        every { mobileClient.credentials } answers {
            calls++
            com.amazonaws.auth.BasicAWSCredentials("AKIDEXAMPLE", "secret")
        }
        val util = Util(authRecoveryManager)

        util.prepareUploadSession(context)
        repeat(3) { util.generates3ShareUrl(context, "/tmp/file$it.zip", "some/upload/path$it.zip") }

        assertEquals(1, calls)
        Unit
    }

    @Test
    fun `offline is a temporary failure and does not try a silent re-login`() = runTest {
        val util = Util(authRecoveryManager).apply { isOnline = { false } }

        assertThrows(AwsTransientException::class.java) {
            runBlocking { util.prepareUploadSession(context) }
        }
        coVerify(exactly = 0) { authRecoveryManager.attemptSilentReauthWithOutcome(any()) }
    }

    @Test
    fun `a successful silent re-login recovers the batch`() = runBlocking {
        var calls = 0
        every { mobileClient.credentials } answers {
            calls++
            if (calls == 1) throw RuntimeException("token cache expired")
            com.amazonaws.auth.BasicAWSCredentials("AKIDEXAMPLE", "secret")
        }
        coEvery { authRecoveryManager.attemptSilentReauthWithOutcome(context) } returns SignInOutcome.SIGNED_IN
        val util = Util(authRecoveryManager).apply { isOnline = { true } }

        util.prepareUploadSession(context)

        assertTrue(util.generates3ShareUrl(context, "/tmp/file.zip", "some/upload/path.zip").isNotEmpty())
        Unit
    }

    @Test
    fun `an unreachable Cognito during silent re-login is a temporary failure`() = runTest {
        coEvery { authRecoveryManager.attemptSilentReauthWithOutcome(context) } returns SignInOutcome.UNAVAILABLE
        val util = Util(authRecoveryManager).apply { isOnline = { true } }

        assertThrows(AwsTransientException::class.java) {
            runBlocking { util.prepareUploadSession(context) }
        }
    }
}
