package com.screenlake.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.LifecycleOwner
import androidx.room.Dao
import androidx.room.Query
import com.screenlake.R
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.model.AppStat
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.services.util.SharedPreferencesUtil
import com.screenlake.recorder.services.util.SharedPreferencesUtil.setBatteryOptimizationDisabled
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.utilities.TimeUtility
import com.screenlake.recorder.viewmodels.WorkerProgressManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ScreenshotStatsFragment : Fragment() {


    private lateinit var tvTotalCount: TextView
    private lateinit var tvRestrictedCount: TextView
    private lateinit var tvUnrestrictedCount: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var tvOcrCompleteCount: TextView
    private lateinit var tvNullAppSegmentCount: TextView
    private lateinit var lastOcrTimeView: TextView
    private lateinit var rvAppStats: RecyclerView
    private lateinit var adapter: AppStatsAdapter
    private lateinit var lastUploadTime: TextView
    private lateinit var lastUploadSuccess: TextView
    private lateinit var uploadButton: Button
    private lateinit var viewUploadProgressTextView: TextView

    @Inject
    lateinit var screenshotDao: ScreenshotDao
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_screenshot_stats, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        tvTotalCount = view.findViewById(R.id.tv_total_count)
        tvRestrictedCount = view.findViewById(R.id.tv_restricted_count)
        tvUnrestrictedCount = view.findViewById(R.id.tv_unrestricted_count)
        tvFileCount = view.findViewById(R.id.tv_file_count)
        tvOcrCompleteCount = view.findViewById(R.id.tv_ocr_complete_count)
        tvNullAppSegmentCount = view.findViewById(R.id.tv_null_app_segment_count)
        rvAppStats = view.findViewById(R.id.rv_app_stats)
        lastOcrTimeView = view.findViewById(R.id.last_ocr_time)
        uploadButton = view.findViewById<Button>(R.id.btn_upload_data)

        lastUploadTime = view.findViewById(R.id.last_upload_time)
        lastUploadSuccess = view.findViewById(R.id.last_upload_success)
        viewUploadProgressTextView = view.findViewById(R.id.tv_view_upload_progress)

        uploadButton.setOnClickListener {
            if(ScreenshotService.isRunning.value == true) {
                ScreenshotService.manualOcr.postValue(true)
                uploadButton.isEnabled = false
            } else {
                Toast.makeText(requireContext(), "Screen recording must be running", Toast.LENGTH_SHORT).show()
            }
        }

        ScreenshotService.manualOcr.observe(viewLifecycleOwner) { manualOcr ->
            uploadButton.isEnabled = !manualOcr

            if (manualOcr) {
                uploadButton.text = "Uploading..."
                viewUploadProgressTextView.visibility = View.VISIBLE
            } else {
                uploadButton.text = "Upload"
                viewUploadProgressTextView.visibility = View.INVISIBLE
            }
        }

        // Setup RecyclerView
        adapter = AppStatsAdapter()
        rvAppStats.adapter = adapter
        rvAppStats.layoutManager = LinearLayoutManager(requireContext())

        viewUploadProgressTextView = view.findViewById<TextView>(R.id.tv_view_upload_progress)

        viewUploadProgressTextView.setOnClickListener {
            // For demo, pass a static or dynamic log string
            val sampleLogs = """
            [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
                        [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
                        [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
                        [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
                        [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
                        [10:01 AM] Starting upload...
            [10:02 AM] 50% complete
            [10:03 AM] 100% complete
            [10:04 AM] Upload successful!
        """.trimIndent()

            showUploadProgressDialog(sampleLogs)
        }

        val batteryOptimizationSwitch = view.findViewById<SwitchCompat>(R.id.switch_battery_optimization)
        batteryOptimizationSwitch.isChecked = SharedPreferencesUtil.getBatteryOptimizationDisabled(requireContext())
        batteryOptimizationSwitch.setOnCheckedChangeListener { _, isChecked ->
            setBatteryOptimizationDisabled(this@ScreenshotStatsFragment.requireContext(), isChecked)

            ScreenshotService.optimizeUploads.postValue(isChecked)
        }

        // Load data
        loadScreenshotStats()
    }

    private fun showUploadProgressDialog(logs: String) {
        // Inflate the custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_upload_progress, null)

        // Reference to the TextView in the layout
        val logsTextView = dialogView.findViewById<TextView>(R.id.tv_upload_logs)

        logsTextView.isVerticalScrollBarEnabled = true

        // Set the text logs
        logsTextView.text = logs


        WorkerProgressManager.progressUpdates.observe(viewLifecycleOwner) { logString ->
            updateLogDisplay(logsTextView, logString)
        }

        WorkerProgressManager

        // Build the AlertDialog
        AlertDialog.Builder(requireContext())
            .setTitle("Upload Progress")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadScreenshotStats() {
        lifecycleScope.launch {
            // Load database statistics
            val totalCount = screenshotDao.getTotalCount()
            val restrictedCount = screenshotDao.getRestrictedCount()
            val unrestrictedCount = screenshotDao.getUnrestrictedCount()
            val ocrCompleteCount = screenshotDao.getOcrCompleteCount()
            val nullAppSegmentCount = screenshotDao.getNullAppSegmentCount()
            val appStats = screenshotDao.getScreenshotCountsByApp()
            val lastOcrTime = if (ScreenshotService.lastOcrTime == 0L) "0" else TimeUtility.getFormattedHhMmSs(ScreenshotService.lastOcrTime)


            val result = appStats.map {
                AppStat(BaseUtility.getAppNameFromPackage(this@ScreenshotStatsFragment.requireContext(), it.appPackage), it.count)
            }
            
            // Count files in internal storage
            val fileCount = withContext(Dispatchers.IO) {
                countFilesInDirectory(requireContext().applicationContext.filesDir)
            }

            // Update UI
            tvTotalCount.text = "Total Screenshots: $totalCount"
            tvRestrictedCount.text = "App Restricted: $restrictedCount"
            tvUnrestrictedCount.text = "Not Restricted: $unrestrictedCount"
            tvFileCount.text = "Files in Internal Storage: $fileCount"
            tvOcrCompleteCount.text = "OCR Completed: $ocrCompleteCount"
            tvNullAppSegmentCount.text = "No App Segment: $nullAppSegmentCount"
            lastOcrTimeView.text = "Last OCR run time: $lastOcrTime & manual ocr = ${ScreenshotService.manualOcr.value}"
            lastUploadTime.text = "Last upload time: ${TimeUtility.getFormattedHhMmSs(ScreenshotService.lastUploadTime.value ?: 0)}"
            lastUploadSuccess.text = "Last upload success: ${ScreenshotService.lastUploadSuccessful.value}"
            adapter.submitList(appStats)
        }
    }
    
    /**
     * Recursively counts all files in a directory and its subdirectories
     */
    private fun countFilesInDirectory(directory: File): String {
        // We'll keep counters as top-level vars so they can be updated in recursion
        var imageCount = 0
        var zipCount = 0

        // A helper (recursive) function that increments counts
        fun recurseFiles(dir: File) {
            // Safety check
            if (!dir.exists() || !dir.isDirectory) return

            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // Recurse into subdirectories
                    recurseFiles(file)
                } else if (file.isFile) {
                    // Check file extension in lowercase
                    val lowerName = file.name.lowercase()
                    when {
                        // You can add more image extensions as needed
                        lowerName.endsWith(".jpg")
                                || lowerName.endsWith(".jpeg")
                                || lowerName.endsWith(".png")
                                || lowerName.endsWith(".gif") -> {
                            imageCount++
                        }
                        lowerName.endsWith(".zip") -> {
                            zipCount++
                        }
                    }
                }
            }
        }

        // Start recursion from the top-level directory
        recurseFiles(directory)

        // Return the desired string format
        return "Image count: $imageCount, Zip count: $zipCount"
    }

    private fun updateLogDisplay(logTextView: TextView, logString: String) {
        val logs = logString.split(",").filter { it.isNotBlank() }
        val formattedLogs = logs.joinToString("\n") { log ->
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            "[$timestamp] $log"
        }

        // Update the TextView with the formatted logs
        logTextView.text = formattedLogs

        // Auto-scroll to the bottom
        val scrollView = logTextView.parent as? ScrollView
        scrollView?.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}

// The rest of the code remains the same...