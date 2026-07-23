package com.lelee.githubmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lelee.githubmanager.databinding.ItemRunBinding
import org.json.JSONArray

class RunAdapter(
    private var runs: JSONArray,
    private val onCancel: (id: Long) -> Unit,
    private val onRerun: (id: Long) -> Unit,
    private val onArtifacts: (id: Long) -> Unit,
    private val onDelete: (id: Long) -> Unit
) : RecyclerView.Adapter<RunAdapter.VH>() {

    inner class VH(val binding: ItemRunBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateData(newRuns: JSONArray) {
        runs = newRuns
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRunBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val run = runs.getJSONObject(position)
        val id = run.optLong("id")
        val runNumber = run.optInt("run_number")
        val status = run.optString("status")
        val conclusion = run.optString("conclusion", "-")
        val createdAt = run.optString("created_at")
        val event = run.optString("event")

        holder.binding.tvTitle.text = "Run #$runNumber ($event)"
        holder.binding.tvStatus.text = "Status: $status · Hasil: $conclusion"
        holder.binding.tvDate.text = createdAt

        holder.binding.btnCancel.setOnClickListener { onCancel(id) }
        holder.binding.btnRerun.setOnClickListener { onRerun(id) }
        holder.binding.btnArtifacts.setOnClickListener { onArtifacts(id) }
        holder.binding.btnDelete.setOnClickListener { onDelete(id) }
    }

    override fun getItemCount(): Int = runs.length()
}
