package com.lelee.githubmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lelee.githubmanager.databinding.ItemWorkflowBinding
import org.json.JSONArray

class WorkflowAdapter(
    private var workflows: JSONArray,
    private val onRun: (id: Long, name: String) -> Unit,
    private val onToggle: (id: Long, name: String, currentState: String) -> Unit,
    private val onHistory: (id: Long, name: String) -> Unit
) : RecyclerView.Adapter<WorkflowAdapter.VH>() {

    inner class VH(val binding: ItemWorkflowBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateData(newWorkflows: JSONArray) {
        workflows = newWorkflows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWorkflowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wf = workflows.getJSONObject(position)
        val id = wf.optLong("id")
        val name = wf.optString("name")
        val state = wf.optString("state")

        holder.binding.tvName.text = name
        holder.binding.tvState.text = "Status: $state"

        holder.binding.btnRun.setOnClickListener { onRun(id, name) }
        holder.binding.btnToggle.setOnClickListener { onToggle(id, name, state) }
        holder.binding.btnHistory.setOnClickListener { onHistory(id, name) }
    }

    override fun getItemCount(): Int = workflows.length()
}
