package com.streamlocal.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.streamlocal.app.R
import com.streamlocal.app.data.ApiResult
import com.streamlocal.app.data.ServerConfig
import com.streamlocal.app.data.StreamLocalRepository
import com.streamlocal.app.databinding.ActivityMainBinding
import com.streamlocal.app.ui.player.PlayerActivity
import com.streamlocal.app.ui.history.HistoryActivity
import com.streamlocal.app.ui.search.SearchAdapter
import com.streamlocal.app.ui.search.SearchUiState
import com.streamlocal.app.ui.search.SearchViewModel
import com.streamlocal.app.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: SearchAdapter
    private lateinit var repository: StreamLocalRepository

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        repository = StreamLocalRepository(this)

        setSupportActionBar(binding.toolbar)

        setupRecycler()
        setupTabs()
        setupSearchInput()
        setupSwipeRefresh()

        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        viewModel.uiState.observe(this) { state -> render(state) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_history) {
            startActivity(Intent(this, HistoryActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        if (!ServerConfig.isConfigured(this)) {
            Toast.makeText(this, R.string.msg_no_server_configured, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecycler() {
        adapter = SearchAdapter { item -> onResultClick(item) }
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setType(if (tab.position == 0) "video" else "music")
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupSearchInput() {
        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(v.text?.toString().orEmpty())
                true
            } else false
        }
        binding.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                debounceRunnable = Runnable { viewModel.search(s?.toString().orEmpty()) }
                debounceHandler.postDelayed(debounceRunnable!!, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.search(viewModel.currentQuery)
        }
    }

    private fun render(state: SearchUiState) {
        binding.swipeRefresh.isRefreshing = false
        when (state) {
            is SearchUiState.Idle -> {
                binding.progressLoading.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.setText(R.string.empty_search)
                adapter.submitList(emptyList())
            }
            is SearchUiState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
                binding.textEmpty.visibility = View.GONE
            }
            is SearchUiState.Success -> {
                binding.progressLoading.visibility = View.GONE
                if (state.items.isEmpty()) {
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.textEmpty.setText(R.string.empty_results)
                } else {
                    binding.textEmpty.visibility = View.GONE
                }
                adapter.submitList(state.items)
            }
            is SearchUiState.Error -> {
                binding.progressLoading.visibility = View.GONE
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = getString(R.string.msg_load_error, state.message)
                adapter.submitList(emptyList())
            }
        }
    }

    private fun onResultClick(item: com.streamlocal.app.data.SearchItem) {
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = repository.resolve(item.url)) {
                is ApiResult.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_TITLE, result.data.title)
                        putExtra(PlayerActivity.EXTRA_UPLOADER, result.data.uploader)
                        putExtra(PlayerActivity.EXTRA_THUMBNAIL, result.data.thumbnail)
                        putExtra(PlayerActivity.EXTRA_DURATION, result.data.duration)
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, ServerConfig.resolveUrl(this@MainActivity, result.data.streamUrl))
                        putExtra(PlayerActivity.EXTRA_STREAM_URL_AUDIO, ServerConfig.resolveUrl(this@MainActivity, result.data.streamUrlAudio))
                        putExtra(PlayerActivity.EXTRA_IS_MUSIC, viewModel.currentType == "music")
                    }
                    startActivity(intent)
                }
                is ApiResult.Failure -> {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this@MainActivity, getString(R.string.msg_load_error, result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
