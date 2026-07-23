package com.lelee.githubmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lelee.githubmanager.databinding.ItemFileBinding
import org.json.JSONArray

class FileAdapter(
    private var items: JSONArray,
    private val onOpen: (name: String, path: String, isDir: Boolean, sha: String) -> Unit,
    private val onDelete: (name: String, path: String, sha: String) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    inner class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateData(newItems: JSONArray) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items.getJSONObject(position)
        val name = item.optString("name")
        val path = item.optString("path")
        val type = item.optString("type") // "file" or "dir"
        val sha = item.optString("sha")
        val isDir = type == "dir"

        holder.binding.tvIcon.text = if (isDir) "📁" else "📄"
        holder.binding.tvName.text = name

        holder.binding.root.setOnClickListener {
            onOpen(name, path, isDir, sha)
        }
        holder.binding.btnDelete.setOnClickListener {
            onDelete(name, path, sha)
        }
    }

    override fun getItemCount(): Int = items.length()
}
