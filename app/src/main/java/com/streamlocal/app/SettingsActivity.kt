package com.streamlocal.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var edtBaseUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var btnBack: ImageButton
    private lateinit var txtConnStatus: TextView
    private lateinit var switchDefaultAudio: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        edtBaseUrl = findViewById(R.id.edtBaseUrl)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        btnBack = findViewById(R.id.btnBack)
        txtConnStatus = findViewById(R.id.txtConnStatus)
        switchDefaultAudio = findViewById(R.id.switchDefaultAudio)

        edtBaseUrl.setText(Prefs.getBaseUrl(this))
        switchDefaultAudio.isChecked = Prefs.getAudioOnlyDefault(this)

        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val url = edtBaseUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Isi URL server dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setBaseUrl(this, url)
            edtBaseUrl.setText(Prefs.getBaseUrl(this))
            Toast.makeText(this, R.string.server_saved, Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val url = edtBaseUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Isi URL server dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setBaseUrl(this, url)
            testConnection()
        }

        switchDefaultAudio.setOnCheckedChangeListener { _, checked ->
            Prefs.setAudioOnlyDefault(this, checked)
        }
    }

    private fun testConnection() {
        txtConnStatus.visibility = android.view.View.VISIBLE
        txtConnStatus.text = getString(R.string.loading)
        txtConnStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        btnTest.isEnabled = false
        lifecycleScope.launch {
            val ok = ApiClient.ping(this@SettingsActivity)
            btnTest.isEnabled = true
            if (ok) {
                txtConnStatus.text = getString(R.string.connection_ok)
                txtConnStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.success))
            } else {
                txtConnStatus.text = getString(R.string.connection_fail)
                txtConnStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error))
            }
        }
    }
}
