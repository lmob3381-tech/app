package com.lelee.githubmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lelee.githubmanager.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RepoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        title = "Repos (${TokenStore.getUsername()})"

        adapter = RepoAdapter(JSONArray()) { name, owner, isPrivate, defaultBranch ->
            val intent = Intent(this, RepoDetailActivity::class.java)
            intent.putExtra("owner", owner)
            intent.putExtra("repo", name)
            intent.putExtra("branch", defaultBranch)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadRepos() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, CreateRepoActivity::class.java))
        }

        loadRepos()
    }

    override fun onResume() {
        super.onResume()
        loadRepos()
    }

    private fun loadRepos() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val repos = GitHubApi.listRepos()
                adapter.updateData(repos)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal memuat repo: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }
}
