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
import com.screenlake.data.model.AppStat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Adapter for the RecyclerView
class AppStatsAdapter : RecyclerView.Adapter<AppStatsAdapter.ViewHolder>() {
    
    private var appStats: List<AppStat> = emptyList()
    
    fun submitList(stats: List<AppStat>) {
        appStats = stats
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = appStats[position]
        holder.bind(stat)
    }
    
    override fun getItemCount(): Int = appStats.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAppName: TextView = itemView.findViewById(R.id.tv_app_name)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)
        
        fun bind(stat: AppStat) {
            tvAppName.text = stat.appPackage
            tvCount.text = "${stat.count} screenshots"
        }
    }
}