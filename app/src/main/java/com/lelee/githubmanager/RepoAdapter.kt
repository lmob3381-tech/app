package com.lelee.githubmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lelee.githubmanager.databinding.ItemRepoBinding
import org.json.JSONArray

class RepoAdapter(
    private var repos: JSONArray,
    private val onClick: (name: String, owner: String, isPrivate: Boolean, defaultBranch: String) -> Unit
) : RecyclerView.Adapter<RepoAdapter.VH>() {

    inner class VH(val binding: ItemRepoBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateData(newRepos: JSONArray) {
        repos = newRepos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRepoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val repo = repos.getJSONObject(position)
        val name = repo.optString("name")
        val desc = repo.optString("description", "")
        val isPrivate = repo.optBoolean("private", false)
        val stars = repo.optInt("stargazers_count", 0)
        val lang = repo.optString("language", "-")
        val owner = repo.optJSONObject("owner")?.optString("login") ?: ""
        val defaultBranch = repo.optString("default_branch", "main")

        holder.binding.tvRepoName.text = name
        holder.binding.tvRepoDesc.text = if (desc.isBlank() || desc == "null") "Tidak ada deskripsi" else desc
        holder.binding.tvRepoMeta.text = "${if (isPrivate) "Private" else "Public"} · $lang · ★$stars"

        holder.binding.root.setOnClickListener {
            onClick(name, owner, isPrivate, defaultBranch)
        }
    }

    override fun getItemCount(): Int = repos.length()
}
