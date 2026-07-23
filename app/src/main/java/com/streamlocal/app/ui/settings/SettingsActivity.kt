package com.streamlocal.app.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.streamlocal.app.R
import com.streamlocal.app.data.ApiResult
import com.streamlocal.app.data.ServerConfig
import com.streamlocal.app.data.StreamLocalRepository
import com.streamlocal.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: StreamLocalRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = StreamLocalRepository(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.editServerUrl.setText(ServerConfig.getRawBaseUrl(this))

        binding.btnSave.setOnClickListener { saveUrl() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
    }

    private fun saveUrl() {
        val url = binding.editServerUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.msg_url_empty, Toast.LENGTH_SHORT).show()
            return
        }
        ServerConfig.setBaseUrl(this, url)
        Toast.makeText(this, R.string.msg_url_saved, Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        val url = binding.editServerUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.msg_url_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // Simpan dulu supaya ApiClient memakai URL yang baru diketik saat tes
        ServerConfig.setBaseUrl(this, url)

        binding.progressTest.visibility = android.view.View.VISIBLE
        binding.textStatus.visibility = android.view.View.GONE

        lifecycleScope.launch {
            val result = repository.testConnection()
            binding.progressTest.visibility = android.view.View.GONE
            binding.textStatus.visibility = android.view.View.VISIBLE
            when (result) {
                is ApiResult.Success -> {
                    binding.textStatus.text = getString(R.string.msg_connection_ok)
                    binding.textStatus.setTextColor(getColor(R.color.success))
                }
                is ApiResult.Failure -> {
                    binding.textStatus.text = getString(R.string.msg_connection_fail, result.message)
                    binding.textStatus.setTextColor(getColor(R.color.error))
                }
            }
        }
    }
}
