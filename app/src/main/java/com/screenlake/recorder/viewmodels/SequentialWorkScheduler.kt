package com.screenlake.recorder.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
//import com.screenlake.recorder.services.OcrWorker
import com.screenlake.recorder.services.UploadWorker
import com.screenlake.recorder.services.ZipFileWorker
import java.util.concurrent.TimeUnit

// 2. Set up the sequential work chain in your Activity or ViewModel
class SequentialWorkScheduler : ViewModel() {
    // Collect the progress updates
    val progressUpdates = WorkerProgressManager.progressUpdates
    
//    fun startSequentialWork(context: Context) {
//        // Create work requests for each worker
//        val firstWorkRequest = OneTimeWorkRequestBuilder<OcrWorker>()
//            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
//            .build()
//
//        val secondWorkRequest = OneTimeWorkRequestBuilder<ZipFileWorker>()
//            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
//            .build()
//
//        val thirdWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
//            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
//            .build()
//
//        // Chain the work requests to run sequentially
//        WorkManager.getInstance(context)
//            .beginWith(firstWorkRequest)
//            .then(secondWorkRequest)
//            .then(thirdWorkRequest)
//            .enqueue()
//    }
}