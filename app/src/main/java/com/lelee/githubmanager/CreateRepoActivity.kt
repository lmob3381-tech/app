package com.lelee.githubmanager

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lelee.githubmanager.databinding.ActivityCreateRepoBinding
import kotlinx.coroutines.launch

class CreateRepoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRepoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRepoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreate.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val desc = binding.etDesc.text.toString().trim()
            val isPrivate = binding.cbPrivate.isChecked

            if (name.isBlank()) {
                Toast.makeText(this, "Nama repo wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    GitHubApi.createRepo(name, desc, isPrivate)
                    Toast.makeText(this@CreateRepoActivity, "Repo berhasil dibuat", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@CreateRepoActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
}
