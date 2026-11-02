package com.screenlake.recorder.services

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.screenlake.data.repository.AwsService
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.ScreenshotZipEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.upload.AwsConfigurationException
import com.screenlake.recorder.upload.AwsTransientException
import com.screenlake.recorder.upload.CredentialExpiredException
import com.screenlake.recorder.upload.UploadFailureClassifier
import com.screenlake.recorder.upload.Util
import com.screenlake.recorder.utilities.HardwareChecks
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealUploadHandler @Inject constructor(
    private val context: Context,
    private val awsService: AwsService,
    private val generalOperationsRepository: GeneralOperationsRepository,
    private val util: Util
) : UploadHandler {
    private var zipsToUpload: List<ScreenshotZipEntity>? = mutableListOf()
    companion object {
        private const val TAG = "UploadWorker"
        val uploadFeedback = MutableLiveData<String>()
    }

    override suspend fun prepareUploadSession(): UploadResult = try {
        util.prepareUploadSession(context)
        UploadResult.SUCCESS
    } catch (error: CredentialExpiredException) {
        generalOperationsRepository.saveLog(ConstantSettings.UPLOAD_CREDENTIAL_FAILURE, "Credential refresh and silent reauth both failed -> ${error.message}")
        UploadResult.AUTHENTICATION_FAILURE
    } catch (error: AwsTransientException) {
        generalOperationsRepository.saveLog("UPLOAD_AWS_TRANSIENT_FAILURE", error.message.orEmpty())
        UploadResult.TRANSIENT_FAILURE
    } catch (error: AwsConfigurationException) {
        generalOperationsRepository.saveLog("UPLOAD_AWS_CONFIGURATION_FAILURE", error.message.orEmpty())
        UploadResult.CONFIGURATION_FAILURE
    }

    override suspend fun uploadFile(
        file: File,
        entryId: Int?,
        user: UserEntity?,
        test: Boolean,
        testContext: Context?
    ): UploadResult {
        if (isMissingPanelistInfo(user)) {
            generalOperationsRepository.saveLog(
                "UPLOAD_MISSING_PANELIST_INFO",
                "Skipping upload for ${file.name}: missing invite code or tenant/panel assignment " +
                    "(emailHash=${user?.emailHash}, tenantId=${user?.tenantId}, panelId=${user?.panelId}). " +
                    "Uploading now would misfile the data or send it to an invalid path."
            )
            Timber.tag(TAG).e(
                "Upload skipped for ${file.name}: participant has no invite code / panel assignment yet."
            )
            return UploadResult.CONFIGURATION_FAILURE
        }

        val uploadPath = buildUploadPath(file, user, test)
        val uploadContext = testContext ?: context

        Timber.tag(TAG).d("Upload path -> $uploadPath")

        return try {
            val requestFile = file.asRequestBody()
            val firstResult = putFile(util.generates3ShareUrl(uploadContext, file.path, uploadPath), requestFile, file, entryId, uploadPath, test)
            if (firstResult != UploadResult.AUTHENTICATION_FAILURE) return firstResult

            // AWS rejected the signed request (401/403), e.g. the batch's credentials expired
            // mid-run. Sign in again silently and retry this file once.
            util.refreshUploadSession(uploadContext)
            putFile(util.generates3ShareUrl(uploadContext, file.path, uploadPath), requestFile, file, entryId, uploadPath, test)
        } catch (credError: CredentialExpiredException) {
            onUploadFailed(file)
            generalOperationsRepository.saveLog(
                ConstantSettings.UPLOAD_CREDENTIAL_FAILURE,
                "Credential refresh and silent reauth both failed for file: ${file.name} -> ${credError.message}"
            )
            Timber.tag(TAG).e(credError, "Upload skipped: credentials could not be recovered")
            WorkerProgressManager.updateProgress("Upload failed -> credentials expired")
            UploadResult.AUTHENTICATION_FAILURE
        } catch (error: AwsTransientException) {
            onUploadFailed(file)
            generalOperationsRepository.saveLog("UPLOAD_AWS_TRANSIENT_FAILURE", "${file.name}: ${error.message}")
            WorkerProgressManager.updateProgress("Upload delayed for ${file.name}: ${error.message}")
            UploadResult.TRANSIENT_FAILURE
        } catch (error: AwsConfigurationException) {
            onUploadFailed(file)
            generalOperationsRepository.saveLog("UPLOAD_AWS_CONFIGURATION_FAILURE", "${file.name}: ${error.message}")
            UploadResult.CONFIGURATION_FAILURE
        } catch (error: Exception) {
            onUploadFailed(file)
            generalOperationsRepository.saveLog(
                ConstantSettings.UPLOAD_FAILED,
                "Upload failed with message -> ${error.message} stacktrace -> ${ScreenshotData.ocrCleanUp(error.stackTraceToString())}."
            )
            Timber.tag(TAG).e(error, "Upload failed")
            WorkerProgressManager.updateProgress("Upload failed -> ${error.message}")
            classifyException(error)
        }
    }

    private suspend fun putFile(
        url: String,
        requestFile: RequestBody,
        file: File,
        entryId: Int?,
        uploadPath: String,
        test: Boolean
    ): UploadResult {
        val result = withContext(Dispatchers.IO) {
            awsService.uploadAsset(url, requestFile).execute()
        }
        return handleUploadResult(result, file, entryId, uploadPath, test)
    }

    private fun onUploadFailed(file: File) {
        if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(false)
    }

    /** Errors outside AWS's own responses: network problems retry, anything else is not retried. */
    private suspend fun classifyException(error: Exception): UploadResult = when {
        UploadFailureClassifier.isAuthenticationFailure(error) -> {
            generalOperationsRepository.saveLog("UPLOAD_AUTH_FAILURE_CONFIRMED", error.javaClass.simpleName)
            UploadResult.AUTHENTICATION_FAILURE
        }
        UploadFailureClassifier.isNetworkFailure(error) || UploadFailureClassifier.isServerFailure(error) ->
            UploadResult.TRANSIENT_FAILURE
        else -> UploadResult.CONFIGURATION_FAILURE
    }

    /**
     * Checks if the device is connected to the network.
     *
     * @return True if the device is connected to the network, false otherwise.
     */
    override suspend fun isNetworkConnected(): Boolean {
        return withContext(Dispatchers.IO) {
            HardwareChecks.isConnectedAsync(WeakReference(context))
        }
    }

    /**
     * Adds local log files to the list of zips to upload.
     *
     * This method searches for log files in the app's file directory and adds them
     * to the list of screenshot zips to be uploaded.
     */
    private fun addLocalLogFiles() {
        val dir = File(context.filesDir?.path)

        // Filter and map log files to ScreenshotZip objects
        val csvs = dir.listFiles()?.filter { it.path.contains("log_data") }?.map { ScreenshotZipEntity(file = it.path) }
        csvs?.let {
            (zipsToUpload as? MutableList<ScreenshotZipEntity>)?.addAll(it)
        }
    }

    /**
     * Handles the case where a file to be uploaded is not found.
     *
     * This method logs a warning if a file does not exist on disk and deletes
     * the associated database entry.
     *
     * @param zip The ScreenshotZip object representing the file to upload.
     * @param file The File object for the file to upload.
     */
    private suspend fun handleFileNotFound(zip: ScreenshotZipEntity, file: File) {
        if (!file.exists()) {
            Timber.tag(TAG).w("Could not upload file ${file.name} because it does not exist on disk.")
            zip.id?.let { generalOperationsRepository.deleteScreenshotZip(zip) }
        }
    }

    /**
     * True when the participant hasn't been assigned an invite code / tenant / panel yet -- a
     * fresh login only ever populates email (see GeneralOperationsRepository.recordPendingReauthUser
     * for why), so uploading before this is filled in would either write to a placeholder path or,
     * for the invite code specifically, a path containing the literal string "null".
     */
    private fun isMissingPanelistInfo(user: UserEntity?): Boolean {
        if (user == null) return true
        return user.emailHash.isNullOrBlank() || user.tenantId.isNullOrBlank() || user.panelId.isNullOrBlank()
    }

    /**
     * Builds the upload path for the given file and user.
     *
     * This method constructs the S3 upload path based on the file extension,
     * user information, and whether the upload is a test.
     *
     * @param file The file to be uploaded.
     * @param user The current user information.
     * @param test Flag indicating whether this is a test run.
     * @return The upload path as a string.
     */
    private fun buildUploadPath(file: File, user: UserEntity?, test: Boolean): String {
        val testPath = if (test) "test/" else ""
        val ext = file.extension

        return if (user?.uploadImages == false) {
            if (ext == "csv") {
                "${testPath}log_events_v2/${user.emailHash}/${getBuildVersion()}/${UUID.randomUUID()}.csv"
            } else {
                ScreenshotService.lastUploadTime.postValue(System.currentTimeMillis())
                "${testPath}tenant/${user.tenantId}_${user.tenantName}/${getBuildVersion()}/${user.emailHash}/${file.name}"
            }
        } else {
            if (ext == "csv") {
                "${testPath}academia/log_events_v2/${user?.emailHash}/${getBuildVersion()}/${UUID.randomUUID()}.csv"
            } else {
                ScreenshotService.lastUploadTime.postValue(System.currentTimeMillis())
                "${testPath}academia/tenant/${user?.tenantId}_${user?.tenantName}/panel/${user?.panelId}/${getBuildVersion()}/panelist/${user?.emailHash}/${file.name}"
            }
        }
    }

    /**
     * Handles the result of an upload operation.
     *
     * This method logs the result of the upload, deletes the file and database entry
     * if the upload is successful, and updates the upload feedback.
     *
     * @param result The response from the upload operation.
     * @param file The file that was uploaded.
     * @param entryId The ID of the ScreenshotZip entry associated with the file.
     * @param uploadPath The upload path for the file.
     * @param test Flag indicating whether this is a test run.
     */
    private suspend fun handleUploadResult(
        result: Response<ResponseBody>?,
        file: File,
        entryId: Int?,
        uploadPath: String,
        test: Boolean
    ): UploadResult {
        if (result?.isSuccessful == true) {
            // Log successful upload
            generalOperationsRepository.saveLog("LAST_UPLOAD", result.message())
            if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(true)
            file.delete()
            entryId?.let { generalOperationsRepository.deleteZip(it) }
            if (test) UploadWorker.uploadFeedback.postValue("Upload succeeded -> $uploadPath")
            Timber.tag(TAG).d("Upload succeeded")

            generalOperationsRepository.resetCredentialFailureCount()
            NotificationHelper(context).cancelNotification(ConstantSettings.CREDENTIAL_EXPIRED_NOTIFICATION_ID)
            return UploadResult.SUCCESS
        } else {
            val code = result?.code() ?: -1
            val msg = result?.message() ?: "no message"
            generalOperationsRepository.saveLog(
                "UPLOAD_HTTP_FAIL",
                "Upload failed HTTP $code ($msg) for path: $uploadPath"
            )
            if (test) UploadWorker.uploadFeedback.postValue("Upload failed -> $uploadPath")
            if (file.extension != "csv") ScreenshotService.lastUploadSuccessful.postValue(false)
            WorkerProgressManager.updateProgress("Upload failed for ${file.name}: HTTP $code")
            Timber.tag(TAG).d("Upload failed HTTP $code ($msg)")
            return when {
                code == 401 || code == 403 -> {
                    generalOperationsRepository.saveLog("UPLOAD_AUTH_FAILURE_CONFIRMED", "http_$code")
                    UploadResult.AUTHENTICATION_FAILURE
                }
                code == -1 || code == 408 || code == 429 || code in 500..599 -> UploadResult.TRANSIENT_FAILURE
                else -> UploadResult.CONFIGURATION_FAILURE
            }
        }
    }

    /**
     * Retrieves the build version string.
     *
     * @return The build version string prefixed with "V_".
     */
//    private fun getBuildVersion() = "V_${BuildConfig.VERSION_CODE}"

    private fun getBuildVersion() = "V_12"
}
