package com.lelee.githubmanager

import android.app.AlertDialog
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lelee.githubmanager.databinding.ActivityWorkflowRunsBinding
import kotlinx.coroutines.launch
import org.json.JSONArray

class WorkflowRunsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkflowRunsBinding
    private lateinit var adapter: RunAdapter
    private lateinit var owner: String
    private lateinit var repo: String
    private var workflowId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkflowRunsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        workflowId = intent.getLongExtra("workflowId", 0)
        val workflowName = intent.getStringExtra("workflowName") ?: "Workflow"

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "Riwayat: $workflowName"
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RunAdapter(JSONArray(),
            onCancel = { id -> doAction("cancel") { GitHubApi.cancelRun(owner, repo, id) } },
            onRerun = { id -> doAction("rerun") { GitHubApi.rerunRun(owner, repo, id) } },
            onArtifacts = { id -> showArtifacts(id) },
            onDelete = { id -> confirmDeleteRun(id) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadRuns() }

        loadRuns()
    }

    private fun loadRuns() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val result = GitHubApi.listWorkflowRuns(owner, repo, workflowId)
                val runs = result.optJSONArray("workflow_runs") ?: JSONArray()
                adapter.updateData(runs)
            } catch (e: Exception) {
                Toast.makeText(this@WorkflowRunsActivity, "Gagal load runs: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun doAction(label: String, action: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                action()
                Toast.makeText(this@WorkflowRunsActivity, "$label berhasil", Toast.LENGTH_SHORT).show()
                loadRuns()
            } catch (e: Exception) {
                Toast.makeText(this@WorkflowRunsActivity, "Gagal $label: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteRun(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Run")
            .setMessage("Yakin mau hapus riwayat run ini?")
            .setPositiveButton("Hapus") { _, _ ->
                doAction("hapus run") { GitHubApi.deleteRun(owner, repo, id) }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showArtifacts(runId: Long) {
        lifecycleScope.launch {
            try {
                val result = GitHubApi.listArtifacts(owner, repo, runId)
                val artifacts = result.optJSONArray("artifacts") ?: JSONArray()
                if (artifacts.length() == 0) {
                    Toast.makeText(this@WorkflowRunsActivity, "Tidak ada artifact untuk run ini", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val names = ArrayList<String>()
                val ids = ArrayList<Long>()
                for (i in 0 until artifacts.length()) {
                    val a = artifacts.getJSONObject(i)
                    names.add(a.optString("name") + " (${a.optLong("size_in_bytes") / 1024} KB)")
                    ids.add(a.optLong("id"))
                }
                AlertDialog.Builder(this@WorkflowRunsActivity)
                    .setTitle("Pilih Artifact untuk Download")
                    .setItems(names.toTypedArray()) { _, which ->
                        downloadArtifact(ids[which], names[which].substringBefore(" ("))
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@WorkflowRunsActivity, "Gagal load artifacts: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadArtifact(artifactId: Long, name: String) {
        Toast.makeText(this, "Mengunduh $name...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val bytes = GitHubApi.downloadArtifact(owner, repo, artifactId)
                val filename = "$name.zip"
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    Toast.makeText(this@WorkflowRunsActivity, "Tersimpan di Downloads: $filename", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@WorkflowRunsActivity, "Gagal simpan file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WorkflowRunsActivity, "Gagal download: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
