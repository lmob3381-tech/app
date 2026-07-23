package com.lelee.githubmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lelee.githubmanager.databinding.ActivityActionsBinding
import kotlinx.coroutines.launch
import org.json.JSONArray

class ActionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActionsBinding
    private lateinit var adapter: WorkflowAdapter
    private lateinit var owner: String
    private lateinit var repo: String
    private lateinit var branch: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        branch = intent.getStringExtra("branch") ?: "main"

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = WorkflowAdapter(JSONArray(),
            onRun = { id, name -> triggerRun(id, name) },
            onToggle = { id, name, state -> toggleWorkflow(id, name, state) },
            onHistory = { id, name ->
                val intent = Intent(this, WorkflowRunsActivity::class.java)
                intent.putExtra("owner", owner)
                intent.putExtra("repo", repo)
                intent.putExtra("workflowId", id)
                intent.putExtra("workflowName", name)
                startActivity(intent)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadWorkflows() }

        loadWorkflows()
    }

    private fun loadWorkflows() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val result = GitHubApi.listWorkflows(owner, repo)
                val workflows = result.optJSONArray("workflows") ?: JSONArray()
                adapter.updateData(workflows)
            } catch (e: Exception) {
                Toast.makeText(this@ActionsActivity, "Gagal load workflows: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun triggerRun(id: Long, name: String) {
        lifecycleScope.launch {
            try {
                GitHubApi.dispatchWorkflow(owner, repo, id, branch)
                Toast.makeText(this@ActionsActivity, "Workflow \"$name\" dijalankan di branch $branch", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@ActionsActivity, "Gagal trigger: ${e.message}\n(pastikan workflow punya trigger workflow_dispatch)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleWorkflow(id: Long, name: String, currentState: String) {
        lifecycleScope.launch {
            try {
                if (currentState == "active") {
                    GitHubApi.disableWorkflow(owner, repo, id)
                    Toast.makeText(this@ActionsActivity, "\"$name\" dinonaktifkan", Toast.LENGTH_SHORT).show()
                } else {
                    GitHubApi.enableWorkflow(owner, repo, id)
                    Toast.makeText(this@ActionsActivity, "\"$name\" diaktifkan", Toast.LENGTH_SHORT).show()
                }
                loadWorkflows()
            } catch (e: Exception) {
                Toast.makeText(this@ActionsActivity, "Gagal ubah status: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
