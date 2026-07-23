package com.lelee.githubmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lelee.githubmanager.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (TokenStore.hasToken()) {
            goToMain()
            return
        }

        binding.btnLogin.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            if (token.isBlank()) {
                binding.tvStatus.text = "Token tidak boleh kosong"
                return@setOnClickListener
            }
            login(token)
        }
    }

    private fun login(token: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = ""
        TokenStore.saveToken(token)

        lifecycleScope.launch {
            try {
                val user = GitHubApi.getAuthenticatedUser()
                TokenStore.saveUsername(user.optString("login"))
                binding.progressBar.visibility = View.GONE
                goToMain()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                TokenStore.clear()
                binding.tvStatus.text = "Login gagal: token tidak valid atau tidak ada koneksi.\n${e.message}"
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
