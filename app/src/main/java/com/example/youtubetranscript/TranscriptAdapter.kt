package com.example.youtubetranscript

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.youtubetranscript.databinding.ItemTranscriptBinding

class TranscriptAdapter : ListAdapter<TranscriptEntry, TranscriptAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemTranscriptBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: TranscriptEntry) {
            binding.timestampText.text = entry.timestamp
            binding.captionText.text = entry.text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranscriptBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TranscriptEntry>() {
            override fun areItemsTheSame(oldItem: TranscriptEntry, newItem: TranscriptEntry) =
                oldItem.startSeconds == newItem.startSeconds

            override fun areContentsTheSame(oldItem: TranscriptEntry, newItem: TranscriptEntry) =
                oldItem == newItem
        }
    }
}
