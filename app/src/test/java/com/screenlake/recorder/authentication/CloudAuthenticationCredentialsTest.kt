package com.screenlake.recorder.authentication

import android.content.Context
import androidx.lifecycle.MutableLiveData
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Silent sign-in uses the stored credentials, so a manual login must store the ones that just
 * worked, and a sign-out must remove them so a signed-out participant's password does not stay
 * on the phone.
 */
class CloudAuthenticationCredentialsTest {

    private val store = mockk<EncryptedCredentialsStore>(relaxed = true)
    private val cloudAuthentication = CloudAuthentication(mockk(relaxed = true), store, mockk<Context>(relaxed = true))

    @Test
    fun `a successful login stores the credentials that signed in`() {
        cloudAuthentication.rememberCredentials("participant@example.com", "new-password")

        verify { store.storeCredentials(any(), "participant@example.com", "new-password") }
    }

    @Test
    fun `blank credentials are never stored`() {
        cloudAuthentication.rememberCredentials("participant@example.com", "")
        cloudAuthentication.rememberCredentials("", "pw")

        verify(exactly = 0) { store.storeCredentials(any(), any(), any()) }
    }

    @Test
    fun `signing out clears the stored credentials first`() {
        // Amplify is not configured in unit tests, so its sign-out call fails after the clear.
        runCatching { cloudAuthentication.signOut(MutableLiveData()) }

        verify { store.clearCredentials(any()) }
    }
}
