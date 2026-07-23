package com.lelee.githubmanager

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lelee.githubmanager.databinding.ActivityFileEditorBinding
import kotlinx.coroutines.launch

class FileEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileEditorBinding
    private lateinit var owner: String
    private lateinit var repo: String
    private lateinit var path: String
    private lateinit var branch: String
    private var currentSha: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        path = intent.getStringExtra("path") ?: ""
        branch = intent.getStringExtra("branch") ?: "main"

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = path.substringAfterLast("/")
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSave.setOnClickListener { saveFile() }
        binding.btnDownload.setOnClickListener { downloadFile() }

        loadFile()
    }

    private fun loadFile() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val fileJson = GitHubApi.getFile(owner, repo, path, branch)
                currentSha = fileJson.optString("sha")
                val content = GitHubApi.decodeBase64Content(fileJson)
                binding.etContent.setText(content)
            } catch (e: Exception) {
                Toast.makeText(this@FileEditorActivity, "Gagal load file (mungkin file binary): ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveFile() {
        binding.progressBar.visibility = View.VISIBLE
        val newContent = binding.etContent.text.toString()
        lifecycleScope.launch {
            try {
                val result = GitHubApi.putFile(owner, repo, path, newContent, "Update $path via GitHub Manager", currentSha, branch)
                currentSha = result.optJSONObject("content")?.optString("sha") ?: currentSha
                Toast.makeText(this@FileEditorActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@FileEditorActivity, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun downloadFile() {
        val content = binding.etContent.text.toString()
        val filename = path.substringAfterLast("/")
        try {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                Toast.makeText(this, "Tersimpan di folder Downloads: $filename", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal membuat file download", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal download: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
