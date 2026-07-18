package id.lelek.adguardbrowser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var dnsStatusText: TextView
    private lateinit var prefs: SharedPreferences

    private val homeUrl = "https://www.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("adguard_browser_prefs", Context.MODE_PRIVATE)

        val savedModeName = prefs.getString("dns_mode", AdGuardMode.DEFAULT.name)
        DohManager.setMode(
            try {
                AdGuardMode.valueOf(savedModeName ?: AdGuardMode.DEFAULT.name)
            } catch (e: IllegalArgumentException) {
                AdGuardMode.DEFAULT
            }
        )

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        webView = findViewById(R.id.webview)
        addressBar = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        dnsStatusText = findViewById(R.id.dns_status_text)

        setupWebView()
        updateDnsStatusLabel()

        addressBar.setOnEditorActionListener { _, _, _ ->
            loadFromAddressBar()
            true
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<View>(R.id.btn_forward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<View>(R.id.btn_reload).setOnClickListener { webView.reload() }
        findViewById<View>(R.id.btn_home).setOnClickListener { webView.loadUrl(homeUrl) }

        webView.loadUrl(homeUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {

            /**
             * Ini inti dari fitur "konek ke AdGuard DNS": tiap request GET dari
             * WebView di-resolve & di-fetch lewat OkHttp client yang DNS-nya
             * sudah diarahkan ke AdGuard DoH (bukan DNS bawaan Android/operator).
             * Kalau gagal (mis. bukan GET, atau AdGuard lagi nggak bisa dihubungi),
             * fallback balik ke WebView default supaya browsing tetap jalan.
             */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (!prefs.getBoolean("dns_protection_enabled", true)) return null
                if (request.method != "GET") return null
                val url = request.url.toString()
                if (!url.startsWith("http")) return null

                return try {
                    val builder = Request.Builder().url(url)
                    for ((key, value) in request.requestHeaders) {
                        builder.header(key, value)
                    }
                    val response = DohManager.getClient().newCall(builder.build()).execute()
                    val body = response.body ?: return null
                    val contentType = response.header("Content-Type")
                    val mime = contentType?.substringBefore(";")?.trim()?.ifEmpty { null } ?: "text/html"
                    val charset = contentType?.substringAfter("charset=", "utf-8")?.trim()?.ifEmpty { null } ?: "utf-8"
                    WebResourceResponse(mime, charset, body.byteStream())
                } catch (e: IOException) {
                    null
                } catch (e: Exception) {
                    null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                addressBar.setText(url)
            }
        }
    }

    private fun loadFromAddressBar() {
        var input = addressBar.text.toString().trim()
        if (input.isEmpty()) return

        val looksLikeUrl = input.contains(".") && !input.contains(" ")
        input = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            looksLikeUrl -> "https://$input"
            else -> "https://www.google.com/search?q=" + Uri.encode(input)
        }
        webView.loadUrl(input)
    }

    private fun updateDnsStatusLabel() {
        val enabled = prefs.getBoolean("dns_protection_enabled", true)
        val mode = DohManager.getMode()
        dnsStatusText.text = if (enabled) {
            "🛡 AdGuard DNS aktif — ${mode.label}"
        } else {
            "⚠ Proteksi AdGuard DNS sedang OFF"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_dns -> {
                val enabled = prefs.getBoolean("dns_protection_enabled", true)
                prefs.edit().putBoolean("dns_protection_enabled", !enabled).apply()
                updateDnsStatusLabel()
                Toast.makeText(
                    this,
                    if (!enabled) "Proteksi AdGuard DNS diaktifkan" else "Proteksi AdGuard DNS dimatikan",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_dns_mode -> {
                showDnsModeDialog()
                true
            }
            R.id.action_test_dns -> {
                testDnsResolution()
                true
            }
            R.id.action_bookmarks -> {
                showBookmarksDialog()
                true
            }
            R.id.action_add_bookmark -> {
                val url = webView.url
                if (url != null) {
                    BookmarkManager.add(this, url)
                    Toast.makeText(this, "Ditambahkan ke bookmark", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDnsModeDialog() {
        val modes = AdGuardMode.values()
        val labels = modes.map { it.label }.toTypedArray()
        val currentIndex = modes.indexOf(DohManager.getMode())

        AlertDialog.Builder(this)
            .setTitle("Pilih Mode AdGuard DNS")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selected = modes[which]
                DohManager.setMode(selected)
                prefs.edit().putString("dns_mode", selected.name).apply()
                updateDnsStatusLabel()
                dialog.dismiss()
                Toast.makeText(this, "Mode DNS: ${selected.label}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun testDnsResolution() {
        Toast.makeText(this, "Mengetes resolusi lewat AdGuard DNS...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                DohManager.testResolve("example.com")
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Hasil Resolusi via ${DohManager.getMode().label}")
                .setMessage("example.com ->\n" + result.joinToString("\n"))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showBookmarksDialog() {
        val bookmarks = BookmarkManager.getAll(this)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "Belum ada bookmark", Toast.LENGTH_SHORT).show()
            return
        }
        val items = bookmarks.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(items) { _, which ->
                webView.loadUrl(items[which])
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
