package com.lelee.githubmanager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lelee.githubmanager.databinding.ActivityRepoDetailBinding
import kotlinx.coroutines.launch
import org.json.JSONArray

class RepoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepoDetailBinding
    private lateinit var adapter: FileAdapter

    private lateinit var owner: String
    private lateinit var repo: String
    private lateinit var branch: String
    private var currentPath: String = ""

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        branch = intent.getStringExtra("branch") ?: "main"

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = repo
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPath.isNotEmpty()) {
                    currentPath = currentPath.substringBeforeLast("/", "")
                    loadContents()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        adapter = FileAdapter(JSONArray(),
            onOpen = { name, path, isDir, sha ->
                if (isDir) {
                    currentPath = path
                    loadContents()
                } else {
                    val intent = Intent(this, FileEditorActivity::class.java)
                    intent.putExtra("owner", owner)
                    intent.putExtra("repo", repo)
                    intent.putExtra("path", path)
                    intent.putExtra("branch", branch)
                    startActivity(intent)
                }
            },
            onDelete = { name, path, sha ->
                confirmDeleteFile(name, path, sha)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadContents() }

        binding.btnUpload.setOnClickListener { pickFileLauncher.launch("*/*") }
        binding.btnNewFile.setOnClickListener { showNewFileDialog() }
        binding.btnActions.setOnClickListener {
            val intent = Intent(this, ActionsActivity::class.java)
            intent.putExtra("owner", owner)
            intent.putExtra("repo", repo)
            intent.putExtra("branch", branch)
            startActivity(intent)
        }
        binding.btnDeleteRepo.setOnClickListener { confirmDeleteRepo() }

        loadContents()
    }

    private fun loadContents() {
        binding.tvPath.text = "/$currentPath".let { if (it == "/") "/ (root)" else it }
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val contents = GitHubApi.listContents(owner, repo, currentPath, branch)
                adapter.updateData(contents)
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "Gagal load isi: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showNewFileDialog() {
        val input = EditText(this)
        input.hint = "nama_file.txt"
        AlertDialog.Builder(this)
            .setTitle("Buat File Baru")
            .setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val filename = input.text.toString().trim()
                if (filename.isNotBlank()) {
                    val fullPath = if (currentPath.isBlank()) filename else "$currentPath/$filename"
                    createEmptyFile(fullPath)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createEmptyFile(path: String) {
        lifecycleScope.launch {
            try {
                GitHubApi.putFile(owner, repo, path, "", "Create $path via GitHub Manager", null, branch)
                Toast.makeText(this@RepoDetailActivity, "File dibuat", Toast.LENGTH_SHORT).show()
                loadContents()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "Gagal buat file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uploadFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val name = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                val fullPath = if (currentPath.isBlank()) name else "$currentPath/$name"
                Toast.makeText(this@RepoDetailActivity, "Mengupload $name...", Toast.LENGTH_SHORT).show()
                GitHubApi.putFileBytes(owner, repo, fullPath, bytes, "Upload $name via GitHub Manager", null, branch)
                Toast.makeText(this@RepoDetailActivity, "Upload berhasil", Toast.LENGTH_SHORT).show()
                loadContents()
            } catch (e: Exception) {
                Toast.makeText(this@RepoDetailActivity, "Gagal upload: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex("_display_name")
            if (cursor.moveToFirst() && idx >= 0) {
                result = cursor.getString(idx)
            }
        }
        return result ?: uri.lastPathSegment
    }

    private fun confirmDeleteFile(name: String, path: String, sha: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus File")
            .setMessage("Yakin mau hapus \"$name\"?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        GitHubApi.deleteFile(owner, repo, path, sha, "Delete $name via GitHub Manager", branch)
                        Toast.makeText(this@RepoDetailActivity, "Terhapus", Toast.LENGTH_SHORT).show()
                        loadContents()
                    } catch (e: Exception) {
                        Toast.makeText(this@RepoDetailActivity, "Gagal hapus: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteRepo() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Repository")
            .setMessage("PERINGATAN: ini akan menghapus seluruh repo \"$repo\" secara permanen. Yakin?")
            .setPositiveButton("Hapus Permanen") { _, _ ->
                lifecycleScope.launch {
                    try {
                        GitHubApi.deleteRepo(owner, repo)
                        Toast.makeText(this@RepoDetailActivity, "Repo dihapus", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@RepoDetailActivity, "Gagal hapus repo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
