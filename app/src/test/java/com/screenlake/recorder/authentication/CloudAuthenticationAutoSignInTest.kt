package com.screenlake.recorder.authentication

import com.screenlake.data.model.EmailPasswordData
import com.screenlake.data.repository.AmplifyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Test
import java.lang.ref.WeakReference

class CloudAuthenticationAutoSignInTest {

    private val amplifyRepository = mockk<AmplifyRepository>()
    private val encryptedCredentialsStore = mockk<EncryptedCredentialsStore>()
    private val cloudAuthentication = CloudAuthentication(amplifyRepository, encryptedCredentialsStore)

    @After
    fun tearDown() {
        unmockkObject(CloudAuthentication.Companion)
    }

    @Test
    fun `autoSignIn reads stored credentials and forwards them to signInAsync`() {
        val context = mockk<android.content.Context>(relaxed = true)
        every { encryptedCredentialsStore.getStoredCredentials(any()) } returns EmailPasswordData("a@b.com", "pw")
        mockkObject(CloudAuthentication.Companion)
        every { CloudAuthentication.signInAsync(any(), any()) } returns Unit

        cloudAuthentication.autoSignIn(WeakReference(context))

        verify { encryptedCredentialsStore.getStoredCredentials(any()) }
        verify { CloudAuthentication.signInAsync("a@b.com", "pw") }
    }
}
