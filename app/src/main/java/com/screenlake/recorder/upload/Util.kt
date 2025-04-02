package com.screenlake.recorder.upload

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.amazonaws.HttpMethod
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
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * Handles basic helper functions used throughout the app.
 */
class Util {
    private var sMobileClient: AWSCredentialsProvider? = null
    private var amazonS3Client: AmazonS3Client? = null

    /**
     * Gets an instance of a S3 client which is constructed using the given
     * Context.
     *
     * @param context An Context instance.
     * @return A default S3 client.
     */
    private fun getS3Client(context: Context): AmazonS3Client? {
        if (amazonS3Client == null) {
            amazonS3Client = AmazonS3Client(getCredProvider(context),Region.getRegion(BuildConfig.AMAZON_REGION_NAME))
            amazonS3Client!!.setRegion(Region.getRegion(BuildConfig.AMAZON_REGION_NAME))
        }
        return amazonS3Client
    }

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
     * Generates a presigned URL for uploading a file to Amazon S3.
     *
     * This function creates a presigned URL that can be used to upload a file to a specified path in an Amazon S3 bucket.
     * The URL is valid for 1 hour from the time of creation.
     *
     * @param applicationContext The application context.
     * @param path The local path of the file to be uploaded.
     * @param uploadPath The path in the S3 bucket where the file should be uploaded.
     * @return The presigned URL as a string.
     */
    fun generates3ShareUrl(applicationContext: Context, path: String?, uploadPath:String): String {
        val url: URL? = try {
            val s3client: AmazonS3? = getS3Client(applicationContext)
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
            val url = s3client?.generatePresignedUrl(generatePresignedUrlRequest).toString()
            Timber.tag(TAG).d("Generated Url - ${url.toString()}")
            return url

        } catch (e: Exception) {
            Timber.d("Error generating presigned URL: $e")
            return ""
        }
        return url.toString()
    }

    /**
     * Gets an instance of AWSMobileClient which is
     * constructed using the given Context.
     *
     * @param context Android context
     * @return AWSMobileClient which is a credentials provider
     */
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
            // Initialize AWSMobileClient with the custom configuration
            AWSMobileClient.getInstance().initialize(context, awsConfig, object : Callback<UserStateDetails?> {
                override fun onResult(result: UserStateDetails?) {
                    latch.countDown()
                    Timber.d("AWSMobileClient initialized successfully.")
                }

                override fun onError(e: Exception) {
                    Timber.e("Error initializing AWSMobileClient: ${e.message}")
                    latch.countDown()
                }
            })

            try {
                latch.await()
                sMobileClient = AWSMobileClient.getInstance()
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
    }
}