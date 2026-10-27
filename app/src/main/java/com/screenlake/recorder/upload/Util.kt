package com.screenlake.recorder.upload

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ResponseHeaderOverrides
import com.screenlake.BuildConfig
import com.screenlake.data.repository.NativeLib
import com.screenlake.recorder.authentication.AuthRecoveryManager
import com.screenlake.recorder.utilities.HardwareChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles basic helper functions used throughout the app.
 */
@Singleton
class Util @Inject constructor(private val authRecoveryManager: AuthRecoveryManager) {
    private var sMobileClient: AWSCredentialsProvider? = null
    private var amazonS3Client: AmazonS3Client? = null

    /** Network check; replaceable in tests. */
    internal var isOnline: (Context) -> Boolean = { HardwareChecks.isConnected(it) }

    /**
     * Determines the MIME type of a file based on its URL.
     *
     * This function extracts the file extension from the URL and uses it to determine the MIME type.
     *
     * @param url The URL of the file.
     * @return The MIME type of the file, or null if the MIME type could not be determined.
     */
    private fun getMimeType(url: String?): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    /**
     * Prepares one upload batch: loads AWS credentials once and builds an S3 client that signs
     * every upload in the batch with them, so a batch makes one credential call instead of one
     * per file.
     *
     * Failures are sorted so only a real credential problem can end in a sign-out:
     * - offline, or a network or server error: [AwsTransientException], keep the files and retry.
     * - the credential call fails or hangs (a session revoked server-side makes it block, so it
     *   is bounded by a timeout): try a silent re-login with the stored credentials, then
     *   [CredentialExpiredException] if that does not help.
     * - AWS SDK not initialized: [AwsConfigurationException].
     */
    suspend fun prepareUploadSession(applicationContext: Context) {
        getCredProvider(applicationContext)
            ?: throw AwsConfigurationException("AWSMobileClient initialization did not complete")

        val credentials = try {
            acquireCredentials()
        } catch (error: Exception) {
            if (!isOnline(applicationContext) || UploadFailureClassifier.isNetworkFailure(error) ||
                UploadFailureClassifier.isServerFailure(error)
            ) {
                throw AwsTransientException("Credentials unavailable: offline or a network/server error", error)
            }
            Timber.tag(TAG).w("Credential refresh failed before upload: ${error.message}")
            recoverWithSilentReauth(applicationContext, error)
        }
        amazonS3Client = buildS3Client(credentials)
    }

    /** After AWS rejects a signed upload (401/403): sign in again silently and reload credentials. */
    suspend fun refreshUploadSession(applicationContext: Context) {
        amazonS3Client = buildS3Client(recoverWithSilentReauth(applicationContext, null))
    }

    private suspend fun recoverWithSilentReauth(applicationContext: Context, cause: Throwable?): AWSCredentials {
        if (!authRecoveryManager.attemptSilentReauth(applicationContext)) {
            throw CredentialExpiredException(
                "Cognito credential refresh failed and silent reauth did not recover it.",
                cause
            )
        }
        return try {
            acquireCredentials()
        } catch (retryEx: Exception) {
            throw CredentialExpiredException("Cognito credentials still invalid after silent reauth.", retryEx)
        }
    }

    /**
     * Generates a presigned S3 PUT URL for [uploadPath], signed with the current batch's
     * credentials (prepared on first use if [prepareUploadSession] was not called).
     *
     * @param applicationContext The application context.
     * @param path The local file path, used for the content type.
     * @param uploadPath The S3 key to upload to.
     * @return The presigned URL.
     */
    suspend fun generates3ShareUrl(applicationContext: Context, path: String?, uploadPath: String): String {
        val s3client = amazonS3Client ?: run {
            prepareUploadSession(applicationContext)
            amazonS3Client ?: throw AwsConfigurationException("Upload session was not prepared")
        }

        val expiration = Date()
        var msec = expiration.time
        msec += 1000 * 60 * 60.toLong() // 1 hour.
        expiration.time = msec
        val overrideHeader = ResponseHeaderOverrides()
        overrideHeader.contentType = getMimeType(path)
        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(BuildConfig.AMAZON_BUCKET_NAME, uploadPath, HttpMethod.PUT)
        generatePresignedUrlRequest.method = HttpMethod.PUT // Default.
        generatePresignedUrlRequest.expiration = expiration
        generatePresignedUrlRequest.responseHeaders = overrideHeader

        return try {
            // Signing uses the batch's fixed credentials, so it does not call into the SDK's
            // credential provider; the timeout only guards against an unexpected stall.
            withTimeout(AWS_OPERATION_TIMEOUT_MS) {
                val url = runInterruptible(Dispatchers.IO) {
                    s3client.generatePresignedUrl(generatePresignedUrlRequest).toString()
                }
                Timber.tag(TAG).d("Generated presigned URL for upload")
                url
            }
        } catch (timeoutEx: TimeoutCancellationException) {
            throw AwsTransientException("Presigned URL generation timed out", timeoutEx)
        } catch (e: Exception) {
            throw AwsTransientException("Presigned URL generation failed", e)
        }
    }

    private suspend fun acquireCredentials(): AWSCredentials =
        withTimeout(AWS_OPERATION_TIMEOUT_MS) {
            runInterruptible(Dispatchers.IO) {
                AWSMobileClient.getInstance().credentials
            }
        }

    private fun buildS3Client(credentials: AWSCredentials): AmazonS3Client {
        val provider = object : AWSCredentialsProvider {
            override fun getCredentials(): AWSCredentials = credentials
            override fun refresh() = Unit
        }
        return AmazonS3Client(provider, Region.getRegion(BuildConfig.AMAZON_REGION_NAME)).apply {
            setRegion(Region.getRegion(BuildConfig.AMAZON_REGION_NAME))
        }
    }

    /**
     * Gets an instance of AWSMobileClient which is
     * constructed using the given Context.
     *
     * @param context Android context
     * @return AWSMobileClient which is a credentials provider
     */
    @Synchronized
    private fun getCredProvider(context: Context): AWSCredentialsProvider? {
        if (sMobileClient == null) {
            val awsConfigFile: String = buildAWSConfiguration()
            val json = JSONObject(awsConfigFile)

            val awsConfig: AWSConfiguration = try {
                // Load the configuration file programmatically
                AWSConfiguration(json)
            } catch (e: Exception) {
                Timber.e("Failed to load awsconfiguration.json: ${e.message}")
                return null
            }


            val latch = CountDownLatch(1)
            var initialized = false
            // Initialize AWSMobileClient with the custom configuration
            AWSMobileClient.getInstance().initialize(context, awsConfig, object : Callback<UserStateDetails?> {
                override fun onResult(result: UserStateDetails?) {
                    initialized = true
                    latch.countDown()
                    Timber.d("AWSMobileClient initialized successfully.")
                }

                override fun onError(e: Exception) {
                    Timber.e("Error initializing AWSMobileClient: ${e.message}")
                    latch.countDown()
                }
            })

            try {
                val completed = latch.await(15, TimeUnit.SECONDS)
                if (completed && initialized) {
                    sMobileClient = AWSMobileClient.getInstance()
                } else {
                    // A failed initialization is not cached, so the next batch tries again.
                    Timber.e("AWSMobileClient initialization failed or timed out.")
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        return sMobileClient
    }

    companion object {
        fun buildAmplifyConfiguration(): String {
            val amplifyConfig = JSONObject()

            // Add the general metadata
            amplifyConfig.put("UserAgent", "aws-amplify-cli/2.0")
            amplifyConfig.put("Version", "1.0")

            // Add the storage plugin configuration
            val storagePlugins = JSONObject()
            val awsS3StoragePlugin = JSONObject()
            awsS3StoragePlugin.put("bucket", BuildConfig.AMAZON_BUCKET_NAME)
            awsS3StoragePlugin.put("region", BuildConfig.AMAZON_REGION_NAME)
            storagePlugins.put("awsS3StoragePlugin", awsS3StoragePlugin)
            amplifyConfig.put("storage", JSONObject().put("plugins", storagePlugins))

            // Add the auth plugin configuration
            val authPlugins = JSONObject()
            val awsCognitoAuthPlugin = JSONObject()
            awsCognitoAuthPlugin.put("UserAgent", "aws-amplify-cli/0.1.0")
            awsCognitoAuthPlugin.put("Version", "0.1.0")

            // IdentityManager
            awsCognitoAuthPlugin.put("IdentityManager", JSONObject().put("Default", JSONObject()))

            // CredentialsProvider
            val credentialsProvider = JSONObject()
            val cognitoIdentity = JSONObject()
            val cognitoIdentityDefault = JSONObject()
            cognitoIdentityDefault.put("PoolId", BuildConfig.COGNITO_IDENTITY_POOL_ID)
            cognitoIdentityDefault.put("Region", BuildConfig.AMAZON_REGION_NAME)
            cognitoIdentity.put("Default", cognitoIdentityDefault)
            credentialsProvider.put("CognitoIdentity", cognitoIdentity)
            awsCognitoAuthPlugin.put("CredentialsProvider", credentialsProvider)

            // CognitoUserPool
            val cognitoUserPool = JSONObject()
            val cognitoUserPoolDefault = JSONObject()
            cognitoUserPoolDefault.put("PoolId", BuildConfig.COGNITO_POOL_ID)
            cognitoUserPoolDefault.put("AppClientId", BuildConfig.COGNITO_APP_CLIENT_ID)
            cognitoUserPoolDefault.put("Region", BuildConfig.AMAZON_REGION_NAME)
            cognitoUserPool.put("Default", cognitoUserPoolDefault)
            awsCognitoAuthPlugin.put("CognitoUserPool", cognitoUserPool)

            // Auth
            val authDefault = JSONObject()
            authDefault.put("authenticationFlowType", "USER_SRP_AUTH")
            authDefault.put("socialProviders", JSONArray())
            authDefault.put("usernameAttributes", JSONArray().put("EMAIL"))
            authDefault.put("signupAttributes", JSONArray())
            val passwordProtectionSettings = JSONObject()
            passwordProtectionSettings.put("passwordPolicyMinLength", 8)
            passwordProtectionSettings.put("passwordPolicyCharacters", JSONArray().put("REQUIRES_LOWERCASE")
                .put("REQUIRES_UPPERCASE")
                .put("REQUIRES_NUMBERS")
                .put("REQUIRES_SYMBOLS"))
            authDefault.put("passwordProtectionSettings", passwordProtectionSettings)
            authDefault.put("mfaConfiguration", "OFF")
            authDefault.put("mfaTypes", JSONArray())
            authDefault.put("verificationMechanisms", JSONArray().put("EMAIL"))
            awsCognitoAuthPlugin.put("Auth", JSONObject().put("Default", authDefault))

            // Add awsCognitoAuthPlugin to plugins
            authPlugins.put("awsCognitoAuthPlugin", awsCognitoAuthPlugin)
            amplifyConfig.put("auth", JSONObject().put("plugins", authPlugins))

            return amplifyConfig.toString(4) // Pretty-print with 4-space indentation
        }

        fun buildAWSConfiguration(): String {
            val awsConfig = JSONObject()

            // Add general metadata
            awsConfig.put("UserAgent", "aws-amplify-cli/0.1.0")
            awsConfig.put("Version", "0.1.0")

            // Add IdentityManager
            awsConfig.put("IdentityManager", JSONObject().put("Default", JSONObject()))

            // Add CredentialsProvider configuration
            val credentialsProvider = JSONObject()
            val cognitoIdentity = JSONObject()
            cognitoIdentity.put("Default", JSONObject().apply {
                put("PoolId", BuildConfig.COGNITO_IDENTITY_POOL_ID)
                put("Region", BuildConfig.AMAZON_REGION_NAME)
            })
            credentialsProvider.put("CognitoIdentity", cognitoIdentity)
            awsConfig.put("CredentialsProvider", credentialsProvider)

            // Add CognitoUserPool configuration
            val cognitoUserPool = JSONObject()
            cognitoUserPool.put("Default", JSONObject().apply {
                put("PoolId", BuildConfig.COGNITO_POOL_ID)
                put("AppClientId", BuildConfig.COGNITO_APP_CLIENT_ID)
                put("Region", BuildConfig.AMAZON_REGION_NAME)
            })
            awsConfig.put("CognitoUserPool", cognitoUserPool)

            // Add Auth configuration
            val authConfig = JSONObject()
            authConfig.put("Default", JSONObject().apply {
                put("authenticationFlowType", "USER_SRP_AUTH")
                put("socialProviders", JSONArray())
                put("usernameAttributes", JSONArray().put("EMAIL"))
                put("signupAttributes", JSONArray())
                put("passwordProtectionSettings", JSONObject().apply {
                    put("passwordPolicyMinLength", 8)
                    put("passwordPolicyCharacters", JSONArray().apply {
                        put("REQUIRES_LOWERCASE")
                        put("REQUIRES_NUMBERS")
                        put("REQUIRES_SYMBOLS")
                        put("REQUIRES_UPPERCASE")
                    })
                })
                put("mfaConfiguration", "OFF")
                put("mfaTypes", JSONArray().put("SMS"))
                put("verificationMechanisms", JSONArray().put("EMAIL"))
            })
            awsConfig.put("Auth", authConfig)

            // Add S3TransferUtility configuration
            val s3TransferUtility = JSONObject()
            s3TransferUtility.put("Default", JSONObject().apply {
                put("Bucket", BuildConfig.AMAZON_BUCKET_NAME)
                put("Region", BuildConfig.AMAZON_REGION_NAME)
            })
            awsConfig.put("S3TransferUtility", s3TransferUtility)

            return awsConfig.toString(4) // Pretty-print with 4-space indentation
        }

        private val TAG = Util::class.java.simpleName
        private const val AWS_OPERATION_TIMEOUT_MS = 15_000L
    }
}