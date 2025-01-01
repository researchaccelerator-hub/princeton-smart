package com.screenlake.recorder.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.screenlake.databinding.AppItemRowBinding
import com.screenlake.databinding.AppItemRowHeaderBinding
import com.screenlake.data.model.RestrictedApp

class RestrictedAppAdapter(private val onItemClicked: (RestrictedApp) -> Unit) :
    ListAdapter<RestrictedApp, RecyclerView.ViewHolder>(WordsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 1) {
            WordViewHolder.create(parent)
        } else {
            HeaderViewHolder.createHeader(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHeader != true) {
            1
        } else {
            2
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val current = getItem(position)
        if (holder is HeaderViewHolder) {
            holder.bindHeaderName(current.packageName)
        } else if (holder is WordViewHolder) {
            holder.bind(current)
            holder.binding.checkBox.setOnClickListener { onItemClicked(current) }
        }
    }

    class WordViewHolder(val binding: AppItemRowBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: RestrictedApp) {
            binding.app = app // Automatically sets values using data binding
        }

        companion object {
            fun create(parent: ViewGroup): WordViewHolder {
                val binding = AppItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return WordViewHolder(binding)
            }
        }
    }

    class HeaderViewHolder(val binding: AppItemRowHeaderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bindHeaderName(packageName: String?) {
            binding.headerName.text = packageName?.split(".")?.last() // Sets header text
        }

        companion object {
            fun createHeader(parent: ViewGroup): HeaderViewHolder {
                val binding = AppItemRowHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return HeaderViewHolder(binding)
            }
        }
    }

    class WordsComparator : DiffUtil.ItemCallback<RestrictedApp>() {
        override fun areItemsTheSame(oldItem: RestrictedApp, newItem: RestrictedApp): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: RestrictedApp, newItem: RestrictedApp): Boolean {
            return oldItem.packageName == newItem.packageName
        }
    }
}
