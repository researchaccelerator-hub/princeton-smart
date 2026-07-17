package com.screenlake.recorder.authentication

/**
 * Bridges the two AWS Cognito-backed authentication SDKs used by this app
 * (the legacy [com.amazonaws.mobile.client.AWSMobileClient] and the modern
 * [com.amplifyframework.core.Amplify] Auth category) behind a single
 * suspending sign-in contract.
 *
 * Consumed by `AuthRecoveryManager` to re-establish both SDKs' sessions when
 * stored credentials have expired.
 */
interface CognitoSessionAuthenticator {
    suspend fun signInAwsMobileClient(email: String, password: String): Boolean
    suspend fun signInAmplify(email: String, password: String): Boolean
}
