package com.screenlake.recorder.services.util

/**
 * Provides human-readable error messages for AWS Cognito Auth exceptions
 */
object CognitoErrorHelper {

    /**
     * Converts AWS Cognito exceptions to user-friendly messages
     */
    fun getReadableMessage(exception: Exception): String {
        return when {
            // Username already exists
            exception.message?.contains("UsernameExistsException") == true -> 
                "This username is already taken. Please choose a different username or try logging in if this is your account."

            exception.cause?.message?.contains("HTTP request: connection closed") == true ->
                "Please connect to the internet and try again"
            
            // Invalid password format
            exception.message?.contains("InvalidPasswordException") == true ->
                "Your password doesn't meet the requirements. Please use a stronger password with a mix of uppercase and lowercase letters, numbers, and special characters."
            
            // Invalid parameter
            exception.message?.contains("InvalidParameterException") == true ->
                "One of the fields you provided isn't formatted correctly. Please check your information and try again."
            
            // Verification code doesn't match
            exception.message?.contains("CodeMismatchException") == true ->
                "The verification code you entered doesn't match the one we sent. Please check your code or request a new one."
            
            // Verification code expired
            exception.message?.contains("ExpiredCodeException") == true ->
                "The verification code has expired. Please request a new code and try again."
            
            // Too many requests
            exception.message?.contains("TooManyRequestsException") == true ->
                "You've made too many requests in a short time. Please wait a moment before trying again."
            
            // Not authorized
            exception.message?.contains("NotAuthorizedException") == true ->
                "You don't have permission to perform this action. This might be due to incorrect credentials or account status issues."
            
            // User not found
            exception.message?.contains("ResourceNotFoundException") == true ->
                "We couldn't find your user account. Please check your username or email address."
            
            // Lambda error
            exception.message?.contains("UnexpectedLambdaException") == true ||
            exception.message?.contains("UserLambdaValidationException") == true ->
                "There was an unexpected error with our authentication service. Please try again later."
            
            // Email/phone already in use
            exception.message?.contains("AliasExistsException") == true ->
                "This email or phone number is already associated with another account. Please use a different one or recover your existing account."
            
            // Limit exceeded
            exception.message?.contains("LimitExceededException") == true ->
                "You've reached the maximum number of allowed attempts. Please try again later or contact support."
            
            // Account not verified
            exception.message?.contains("UserNotConfirmedException") == true ->
                "Your account hasn't been verified yet. Please check your email or phone for a verification code."
            
            // Code delivery failed
            exception.message?.contains("CodeDeliveryFailureException") == true ->
                "We couldn't send you a verification code. Please check that your email or phone number is correct."
            
            // Generic fallback message
            else -> "An error occurred: ${exception.message ?: "Unknown error"}"
        }
    }
}