package com.screenlake.recorder.upload

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** Only a real credential rejection may count toward signing a participant out. */
class UploadFailureClassifierTest {

    private fun service(code: String, status: Int, type: AmazonServiceException.ErrorType) =
        AmazonServiceException("x").apply { errorCode = code; statusCode = status; errorType = type }

    @Test
    fun `a rejected token is an authentication failure`() {
        val error = service("NotAuthorizedException", 400, AmazonServiceException.ErrorType.Client)
        assertTrue(UploadFailureClassifier.isAuthenticationFailure(error))
        assertFalse(UploadFailureClassifier.isServerFailure(error))
    }

    @Test
    fun `Cognito throttling is temporary even though it is a 400 client error`() {
        val error = service("TooManyRequestsException", 400, AmazonServiceException.ErrorType.Client)
        assertTrue(UploadFailureClassifier.isServerFailure(error))
        assertFalse(UploadFailureClassifier.isAuthenticationFailure(error))
    }

    @Test
    fun `no network is a network failure, wherever it is in the cause chain`() {
        val error = AmazonClientException("Unable to execute HTTP request", UnknownHostException("cognito"))
        assertTrue(UploadFailureClassifier.isNetworkFailure(error))
        assertFalse(UploadFailureClassifier.isAuthenticationFailure(error))
    }
}
