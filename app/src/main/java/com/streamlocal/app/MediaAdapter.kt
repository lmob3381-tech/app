package com.streamlocal.app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MediaAdapter(
    private val scope: CoroutineScope,
    private val showDelete: Boolean = false,
    private val onClick: (MediaItem) -> Unit,
    private val onDelete: ((MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = mutableListOf<MediaItem>()
    private val thumbCache = ConcurrentHashMap<String, android.graphics.Bitmap>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeById(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    inner class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: android.widget.ImageView = itemView.findViewById(R.id.imgThumb)
        private val txtTitle: android.widget.TextView = itemView.findViewById(R.id.txtTitle)
        private val txtSubtitle: android.widget.TextView = itemView.findViewById(R.id.txtSubtitle)
        private val btnDelete: android.widget.ImageButton = itemView.findViewById(R.id.btnDelete)
        private var loadJob: Job? = null

        fun bind(item: MediaItem) {
            txtTitle.text = item.title
            val durText = formatDuration(item.duration)
            val subParts = mutableListOf<String>()
            item.artist?.let { subParts.add(it) } ?: item.uploader?.let { subParts.add(it) }
            if (durText.isNotEmpty()) subParts.add(durText)
            txtSubtitle.text = subParts.joinToString(" • ")

            imgThumb.setImageDrawable(null)
            loadJob?.cancel()
            val thumbUrl = item.thumbnail
            if (!thumbUrl.isNullOrBlank()) {
                val cached = thumbCache[thumbUrl]
                if (cached != null) {
                    imgThumb.setImageBitmap(cached)
                } else {
                    loadJob = scope.launch {
                        val bmp = loadBitmap(thumbUrl)
                        if (bmp != null) {
                            thumbCache[thumbUrl] = bmp
                            imgThumb.setImageBitmap(bmp)
                        }
                    }
                }
            }

            itemView.setOnClickListener { onClick(item) }

            if (showDelete) {
                btnDelete.visibility = android.view.View.VISIBLE
                btnDelete.setOnClickListener { onDelete?.invoke(item) }
            } else {
                btnDelete.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun loadBitmap(url: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        try {
            val input: InputStream = URL(url).openStream()
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun formatDuration(seconds: Long): String {
            if (seconds <= 0) return ""
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%02d:%02d", m, s)
        }
    }
}
