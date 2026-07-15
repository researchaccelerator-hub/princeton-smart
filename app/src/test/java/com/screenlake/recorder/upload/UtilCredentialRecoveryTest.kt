package com.screenlake.recorder.upload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.screenlake.recorder.authentication.AuthRecoveryManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (not a bare JVM test) because generates3ShareUrl() always calls
 * getS3Client() -> getCredProvider(), which builds a real JSONObject via
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
        coEvery { authRecoveryManager.attemptSilentReauth(context) } returns false

        val util = Util(authRecoveryManager)

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
        coEvery { authRecoveryManager.attemptSilentReauth(context) } returns false

        val util = Util(authRecoveryManager)

        assertThrows(CredentialExpiredException::class.java) {
            runBlocking {
                util.generates3ShareUrl(context, "/tmp/file.zip", "some/upload/path.zip")
            }
        }
    }
}
