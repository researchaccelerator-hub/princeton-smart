package com.screenlake.data.repository

import android.content.Context
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.api.aws.ApiAuthProviders
import com.amplifyframework.api.rest.RestOptions
import com.amplifyframework.api.rest.RestResponse
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthSession
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.core.AmplifyConfiguration
import com.amplifyframework.kotlin.core.Amplify
import com.amplifyframework.storage.s3.AWSS3StoragePlugin
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.GsonBuilder
import com.screenlake.MainActivity
import com.screenlake.data.database.dao.LogEventDao
import com.screenlake.data.model.MobileStatus
import com.screenlake.data.model.MobileStatusItem
import com.screenlake.data.model.PanelInvitesItem
import com.screenlake.data.model.SimpleResponse
import com.screenlake.data.model.UserItem
import com.screenlake.data.database.entity.LogEventEntity
import com.screenlake.data.database.entity.PanelInviteEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.R
import com.screenlake.data.repository.NativeLib.getAmplifyConfigurationJson
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.upload.Util.Companion.buildAmplifyConfiguration
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

object NativeLib {
    init {
        System.loadLibrary("screenlake")
    }

    external fun getAmplifyConfigurationJson(): String

    external fun getAWSConfigurationJson(): String
}

@Singleton
class AmplifyRepository @Inject constructor(
    context: Context,
    private val logEventDao: LogEventDao
) {

    var email: String = ""
    var username: String = ""
    var password: String = ""
    var code: String = ""

    init {
        initAmplify(context)
    }

    fun fetchAuthSession(
        onSuccess: (AuthSession) -> Unit,
        onError: (AuthException) -> Unit
    ) {
        com.amplifyframework.core.Amplify.Auth.fetchAuthSession(
            { onSuccess(it)},
            { onError(it) }
        )
    }

    suspend fun confirm(panelId: String): Boolean {
        return RestOptions.builder()
            .addHeader("Authorization", getAuth())
            .addBody("{\"item_status\":\"Confirmed\"}".toByteArray())
            .addPath("/panel-invites-mobile/${panelId}")
            .build()
            .patch(INTERNAL_API)
    }

    suspend fun confirmMobileStatusChange(mobileStatusId: String) : Boolean {
        return RestOptions.builder()
            .addHeader("Authorization", getAuth())
            .addBody("{\"mobile_status_confirmed\":\"true\"}".toByteArray())
            .addPath("/mobile-status/${mobileStatusId}")
            .build()
            .patch(INTERNAL_API)
    }

    suspend fun getMobileStatus(context: Context) : MobileStatus? {
        val panelInvite = RestOptions.builder()
            .addHeader("Authorization", getAuth())
            .addPath("/mobile-status")
            .build()
            .get(context, INTERNAL_API)

        val jsonString = panelInvite?.data?.let { String(it.rawBytes) }
        Timber.tag("DoWork").d("GET succeeded: $jsonString")

        return if (panelInvite?.code?.isSuccessful == true){

            val mobileStatus = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(jsonString, MobileStatusItem::class.java)

            if(!mobileStatus?.items.isNullOrEmpty()){
                mobileStatus?.items?.first()
            }else{
                null
            }
        }else{
            null
        }
    }

    suspend fun getUser(context: Context) : UserEntity? {
        val userString = RestOptions.builder()
            .addHeader("Authorization", getAuth())
            .addPath("/user")
            .build()
            .get(context, INTERNAL_API)

        val jsonString = userString?.data?.let { String(it.rawBytes) }
        Timber.tag("DoWork").d("GET succeeded: $jsonString")

        return if(!jsonString.isNullOrBlank()){
            val gson= GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
            val userItem = gson.fromJson(jsonString, UserItem::class.java)

            if(!userItem?.items.isNullOrEmpty()){
                userItem.items.first()
            }else{
                null
            }
        }else{
            null
        }
    }

    private suspend fun getAuth() : String? {
        val session = Amplify.Auth.fetchAuthSession()
        val authSession = (session as AWSCognitoAuthSession)
        val id = authSession.userPoolTokens.value?.idToken
        val test = authSession.awsCredentials.value
        val test2 = authSession.identityId

        Timber.d("HERE $test $test2 $id")
        return id
    }

    private suspend fun RestOptions.patch(apiName: String) : Boolean {
        // Passive wifi check
        return if(MainActivity.isWifiConnected.value == true){
            try {
                val response = Amplify.API.patch(this, apiName)
                val json = String(response.data.rawBytes)
                Timber.tag("DoWork").d("Patch succeeded: $json")

                val isSuccess = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, SimpleResponse::class.java)?.message == "Success"
                isSuccess
            } catch (ex: Exception) {
                saveLog("Exception", ex.stackTraceToString())
                FirebaseCrashlytics.getInstance().recordException(ex)
                Timber.tag("DoWork").d("Patch failed: $ex")
                false
            }
        } else {
            false
        }
    }

    private suspend fun saveLog(event: String, msg: String = "") {
        logEventDao.saveException(
            LogEventEntity(
                event,
                msg,
                email
            )
        )
    }

    private suspend fun RestOptions.get(context: Context, apiName: String): RestResponse? {
        // Passive wifi check
        if (MainActivity.isWifiConnected.value == true) {
            try {
                // TODO: Validate here
                val response = Amplify.API.get(this, apiName)

                return response
            } catch (error: Exception) {
                saveLog("Exception", ScreenshotData.ocrCleanUp(error.stackTraceToString()))
                FirebaseCrashlytics.getInstance().recordException(error)
                Timber.tag("DoWork").d("PUT failed: $error")
            }
        } else {
            return null
        }

        return null
    }

    companion object {
        private const val INTERNAL_API = "serverlessrepo-screenlake-internal-data-gateway"
    }

    private fun readTextFile(inputStream: InputStream): String {
        val outputStream = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        var len: Int
        try {
            while (inputStream.read(buf).also { len = it } != -1) {
                outputStream.write(buf, 0, len)
            }
            outputStream.close()
            inputStream.close()
        } catch (e: IOException) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Timber.tag("BaseApplication").e("Could not read amplify configuration text file: $e")
        }
        return outputStream.toString()
    }

    private fun initAmplify(context: Context) {
        try {
            val authProviders = ApiAuthProviders.builder()
                .oidcAuthProvider {
                    val future = CompletableFuture<String>()
                    com.amplifyframework.core.Amplify.Auth.fetchAuthSession(
                        { future.complete((it as AWSCognitoAuthSession).userPoolTokens.value?.idToken) },
                        { future.completeExceptionally(it) }
                    )
                    future.get()
                }.build()

            Amplify.addPlugin(
                AWSApiPlugin.builder()
                    .apiAuthProviders(authProviders)
                    .build()
            )

            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSS3StoragePlugin())

            val awsConfigFile: String = buildAmplifyConfiguration()
            val json = JSONObject(awsConfigFile)
            Amplify.configure(AmplifyConfiguration.fromJson(json), context)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Timber.tag("BaseApplication").e("Could not initialize Amplify: $e")
        }
    }
}