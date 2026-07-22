package com.streamlocal.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var edtSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var txtEmpty: TextView

    private lateinit var adapter: MediaAdapter

    // 0 = video, 1 = music, 2 = history
    private var currentTab = 0
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtSearch = findViewById(R.id.edtSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnSettings = findViewById(R.id.btnSettings)
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        txtEmpty = findViewById(R.id.txtEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        setupAdapter()

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnClearSearch.setOnClickListener {
            edtSearch.setText("")
            btnClearSearch.visibility = View.GONE
        }

        edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearchOrHistory()
                true
            } else false
        }

        edtSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                val isHistory = currentTab == 2
                (edtSearch.parent as View).visibility = if (isHistory) View.GONE else View.VISIBLE
                setupAdapter()
                if (isHistory) {
                    loadHistory()
                } else {
                    showEmpty(getString(R.string.empty_search))
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        swipeRefresh.setOnRefreshListener {
            when (currentTab) {
                2 -> loadHistory()
                else -> if (lastQuery.isNotBlank()) doSearch(lastQuery) else swipeRefresh.isRefreshing = false
            }
        }

        showEmpty(getString(R.string.empty_search))
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.hasBaseUrl(this)) {
            Toast.makeText(this, R.string.no_server_set, Toast.LENGTH_LONG).show()
        }
        if (currentTab == 2) loadHistory()
    }

    private fun setupAdapter() {
        val showDelete = currentTab == 2
        adapter = MediaAdapter(
            scope = lifecycleScope,
            showDelete = showDelete,
            onClick = { item -> openPlayer(item) },
            onDelete = { item -> confirmDeleteHistory(item) }
        )
        recyclerView.adapter = adapter
    }

    private fun doSearchOrHistory() {
        val q = edtSearch.text.toString().trim()
        if (currentTab == 2) {
            loadHistory()
            return
        }
        if (q.isEmpty()) {
            showEmpty(getString(R.string.empty_search))
            return
        }
        doSearch(q)
    }

    private fun doSearch(query: String) {
        if (!Prefs.hasBaseUrl(this)) {
            Toast.makeText(this, R.string.no_server_set, Toast.LENGTH_LONG).show()
            return
        }
        lastQuery = query
        val type = if (currentTab == 1) "music" else "video"
        showLoading(true)
        lifecycleScope.launch {
            try {
                val results = ApiClient.search(this@MainActivity, query, type, 20)
                showLoading(false)
                if (results.isEmpty()) {
                    showEmpty(getString(R.string.empty_results))
                } else {
                    hideEmpty()
                    adapter.submitList(results)
                }
            } catch (e: ApiException) {
                showLoading(false)
                showEmpty(e.message ?: getString(R.string.error_generic))
            } catch (e: Exception) {
                showLoading(false)
                showEmpty(getString(R.string.error_generic))
            }
        }
    }

    private fun loadHistory() {
        if (!Prefs.hasBaseUrl(this)) {
            Toast.makeText(this, R.string.no_server_set, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
            return
        }
        showLoading(true)
        lifecycleScope.launch {
            try {
                val results = ApiClient.history(this@MainActivity)
                showLoading(false)
                swipeRefresh.isRefreshing = false
                if (results.isEmpty()) {
                    showEmpty(getString(R.string.empty_history))
                } else {
                    hideEmpty()
                    adapter.submitList(results)
                }
            } catch (e: Exception) {
                showLoading(false)
                swipeRefresh.isRefreshing = false
                showEmpty((e as? ApiException)?.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun confirmDeleteHistory(item: MediaItem) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hapus riwayat?")
            .setMessage(item.title)
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        ApiClient.deleteHistory(this@MainActivity, item.id)
                        adapter.removeById(item.id)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openPlayer(item: MediaItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("source_url", item.url)
            putExtra("title", item.title)
            putExtra("artist", item.artist ?: item.uploader)
            putExtra("thumbnail", item.thumbnail)
            putExtra("is_music_tab", currentTab == 1)
        }
        startActivity(intent)
    }

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) hideEmpty()
    }

    private fun showEmpty(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        txtEmpty.text = message
    }

    private fun hideEmpty() {
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}
