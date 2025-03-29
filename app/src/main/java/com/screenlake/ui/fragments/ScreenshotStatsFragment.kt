package com.screenlake.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import androidx.room.Dao
import androidx.room.Query
import com.screenlake.R
import com.screenlake.data.database.ScreenshotDatabase
import com.screenlake.data.database.dao.ScreenshotDao
import com.screenlake.data.model.AppStat
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.BaseUtility
import com.screenlake.recorder.utilities.TimeUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

        lastUploadTime = view.findViewById(R.id.last_upload_time)
        lastUploadSuccess = view.findViewById(R.id.last_upload_success)

        // Setup RecyclerView
        adapter = AppStatsAdapter()
        rvAppStats.adapter = adapter
        rvAppStats.layoutManager = LinearLayoutManager(requireContext())

        // Load data
        loadScreenshotStats()
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
    private fun countFilesInDirectory(directory: File): Int {
        if (!directory.exists() || !directory.isDirectory) {
            return 0
        }
        
        var count = 0
        
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                count++
            } else if (file.isDirectory) {
                count += countFilesInDirectory(file)
            }
        }
        
        return count
    }
}

// The rest of the code remains the same...