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
    suspend fun signInAwsMobileClient(email: String, password: String): SignInOutcome
    suspend fun signInAmplify(email: String, password: String): SignInOutcome
}

/**
 * Why a sign-in did or did not work, so a phone that is offline (or a Cognito outage) is never
 * treated as wrong credentials.
 */
enum class SignInOutcome {
    SIGNED_IN,
    /** Cognito rejected the stored credentials (wrong password, user gone, reset required). */
    REJECTED,
    /** Could not reach Cognito, timed out, throttled, or another error; try again later. */
    UNAVAILABLE;

    companion object {
        /** Both SDKs must be signed in; any unavailable one makes the whole attempt unavailable. */
        fun combine(first: SignInOutcome, second: SignInOutcome): SignInOutcome = when {
            first == SIGNED_IN && second == SIGNED_IN -> SIGNED_IN
            first == UNAVAILABLE || second == UNAVAILABLE -> UNAVAILABLE
            else -> REJECTED
        }
    }
}
