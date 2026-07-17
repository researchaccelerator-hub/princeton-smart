package com.screenlake.recorder.authentication

import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthSession
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.screenlake.data.repository.AmplifyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

class CloudAuthenticationBadSessionTest {

    private val amplifyRepository = mockk<AmplifyRepository>()
    private val encryptedCredentialsStore = mockk<EncryptedCredentialsStore>()
    private val cloudAuthentication = CloudAuthentication(amplifyRepository, encryptedCredentialsStore)

    private fun sessionMock(isSignedIn: Boolean, awsCredentialsError: AuthException?): AWSCognitoAuthSession {
        val session = mockk<AWSCognitoAuthSession>()
        every { session.isSignedIn } returns isSignedIn
        val awsCredentialsResult = mockk<com.amplifyframework.auth.result.AuthSessionResult<com.amazonaws.auth.AWSCredentials>>()
        every { awsCredentialsResult.error } returns awsCredentialsError
        every { session.awsCredentials } returns awsCredentialsResult
        val userSubResult = mockk<com.amplifyframework.auth.result.AuthSessionResult<String>>()
        every { userSubResult.error } returns null
        every { session.userSub } returns userSubResult
        return session
    }

    @Test
    fun `calls onBadSession when signed out`() {
        val onSuccessSlot = slot<(AuthSession) -> Unit>()
        every { amplifyRepository.fetchAuthSession(capture(onSuccessSlot), any()) } answers {
            onSuccessSlot.captured(sessionMock(isSignedIn = false, awsCredentialsError = AuthException.SignedOutException()))
        }

        var badSessionCalled = false
        cloudAuthentication.fetchCurrentAuthSession(onBadSession = { badSessionCalled = true })

        assert(badSessionCalled) { "Expected onBadSession to fire for a signed-out session" }
    }

    @Test
    fun `does not call onBadSession when session is valid`() {
        val onSuccessSlot = slot<(AuthSession) -> Unit>()
        every { amplifyRepository.fetchAuthSession(capture(onSuccessSlot), any()) } answers {
            onSuccessSlot.captured(sessionMock(isSignedIn = true, awsCredentialsError = null))
        }

        var badSessionCalled = false
        cloudAuthentication.fetchCurrentAuthSession(onBadSession = { badSessionCalled = true })

        assert(!badSessionCalled) { "Did not expect onBadSession to fire for a valid, signed-in session" }
    }

    @Test
    fun `calls onBadSession when fetchAuthSession errors`() {
        val onErrorSlot = slot<(AuthException) -> Unit>()
        every { amplifyRepository.fetchAuthSession(any(), capture(onErrorSlot)) } answers {
            onErrorSlot.captured(AuthException("boom", RuntimeException("boom"), "boom"))
        }

        var badSessionCalled = false
        cloudAuthentication.fetchCurrentAuthSession(onBadSession = { badSessionCalled = true })

        assert(badSessionCalled) { "Expected onBadSession to fire when fetchAuthSession itself errors" }
    }
}
