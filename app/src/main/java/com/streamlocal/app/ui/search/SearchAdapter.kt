package com.streamlocal.app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.streamlocal.app.data.SearchItem
import com.streamlocal.app.databinding.ItemSearchResultBinding
import java.util.Locale

class SearchAdapter(
    private val onItemClick: (SearchItem) -> Unit
) : ListAdapter<SearchItem, SearchAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchItem) {
            binding.textTitle.text = item.title
            val subtitle = when {
                !item.artist.isNullOrBlank() -> item.artist
                !item.uploader.isNullOrBlank() -> item.uploader
                else -> ""
            }
            binding.textSubtitle.text = subtitle
            binding.textDuration.text = formatDuration(item.duration)

            Glide.with(binding.imageThumbnail.context)
                .load(item.thumbnail)
                .placeholder(com.streamlocal.app.R.drawable.ic_thumbnail_placeholder)
                .error(com.streamlocal.app.R.drawable.ic_thumbnail_placeholder)
                .into(binding.imageThumbnail)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchItem>() {
            override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem) = oldItem == newItem
        }

        fun formatDuration(totalSeconds: Long): String {
            if (totalSeconds <= 0) return ""
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
            else String.format(Locale.getDefault(), "%d:%02d", m, s)
        }
    }
}
