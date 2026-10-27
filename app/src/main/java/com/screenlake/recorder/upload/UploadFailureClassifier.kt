package com.screenlake.recorder.upload

import com.amazonaws.AmazonServiceException
import java.io.IOException

/**
 * Sorts AWS and network errors by type and error code instead of by message text, so an
 * offline phone or a server error is never mistaken for expired credentials.
 */
object UploadFailureClassifier {

    private val AUTH_ERROR_CODES = setOf(
        "NotAuthorizedException",
        "ExpiredToken",
        "ExpiredTokenException",
        "InvalidClientTokenId",
        "InvalidAccessKeyId",
        "SignatureDoesNotMatch",
        "AccessDenied",
        "TokenRefreshRequired",
    )

    /** A network problem anywhere in the cause chain: retry later, credentials are not at fault. */
    fun isNetworkFailure(error: Throwable): Boolean = causes(error).any { it is IOException }

    /** AWS rejected the identity: refresh or sign in again. */
    fun isAuthenticationFailure(error: Throwable): Boolean = causes(error).any {
        it is AmazonServiceException &&
            (it.errorCode in AUTH_ERROR_CODES || it.statusCode == 401 || it.statusCode == 403)
    }

    /** An AWS server-side error (5xx) or throttling: retry later. */
    fun isServerFailure(error: Throwable): Boolean = causes(error).any {
        it is AmazonServiceException &&
            (it.errorType == AmazonServiceException.ErrorType.Service || it.statusCode == 429)
    }

    private fun causes(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(10)
}
