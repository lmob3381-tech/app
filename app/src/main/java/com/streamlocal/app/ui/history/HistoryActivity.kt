package com.streamlocal.app.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.streamlocal.app.R
import com.streamlocal.app.data.ApiResult
import com.streamlocal.app.data.HistoryItem
import com.streamlocal.app.data.ServerConfig
import com.streamlocal.app.data.StreamLocalRepository
import com.streamlocal.app.databinding.ActivityHistoryBinding
import com.streamlocal.app.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var repository: StreamLocalRepository
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = StreamLocalRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = HistoryAdapter(
            onItemClick = { openItem(it) },
            onDeleteClick = { deleteItem(it) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        lifecycleScope.launch {
            when (val result = repository.history()) {
                is ApiResult.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    if (result.data.isEmpty()) {
                        binding.textEmpty.visibility = View.VISIBLE
                    }
                    adapter.submitList(result.data)
                }
                is ApiResult.Failure -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.textEmpty.text = getString(R.string.msg_load_error, result.message)
                }
            }
        }
    }

    private fun openItem(item: HistoryItem) {
        val sourceUrl = item.sourceUrl
        if (sourceUrl.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_load_error, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = repository.resolve(sourceUrl)) {
                is ApiResult.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    val intent = Intent(this@HistoryActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_TITLE, result.data.title)
                        putExtra(PlayerActivity.EXTRA_UPLOADER, result.data.uploader)
                        putExtra(PlayerActivity.EXTRA_THUMBNAIL, result.data.thumbnail)
                        putExtra(PlayerActivity.EXTRA_DURATION, result.data.duration)
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, ServerConfig.resolveUrl(this@HistoryActivity, result.data.streamUrl))
                        putExtra(PlayerActivity.EXTRA_STREAM_URL_AUDIO, ServerConfig.resolveUrl(this@HistoryActivity, result.data.streamUrlAudio))
                        putExtra(PlayerActivity.EXTRA_IS_MUSIC, item.platform == "youtube_music")
                    }
                    startActivity(intent)
                }
                is ApiResult.Failure -> {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this@HistoryActivity, getString(R.string.msg_load_error, result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteItem(item: HistoryItem) {
        lifecycleScope.launch {
            when (repository.deleteHistory(item.id)) {
                is ApiResult.Success -> loadHistory()
                is ApiResult.Failure -> Toast.makeText(this@HistoryActivity, R.string.msg_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
