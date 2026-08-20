package com.placeholder.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        // These two constants are rewritten by scripts/gen_webview_config.py
        const val TARGET_URL = "https://example.com"
        const val JS_ENABLED = true
    }

    private lateinit var webView: WebView
    private var filePathCallback: androidx.webkit.ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val results: Array<Uri>? = if (result.resultCode == RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) arrayOf(uri) else null
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op, WebView PermissionRequest handled separately */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(webView)

        webView.settings.javaScriptEnabled = JS_ENABLED
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep navigation inside the WebView
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Handle in-page permission requests (camera/mic via getUserMedia, etc.)
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val resources = request.resources
                    val androidPermissionsNeeded = mutableListOf<String>()
                    for (r in resources) {
                        when (r) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> androidPermissionsNeeded.add(Manifest.permission.CAMERA)
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> androidPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    val missing = androidPermissionsNeeded.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        runtimePermissionLauncher.launch(missing.toTypedArray())
                    }
                    request.grant(resources)
                }
            }

            // Handle <input type="file"> uploads from the web page
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: androidx.webkit.ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback = filePathCallbackParam
                val intent = fileChooserParams?.createIntent()
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        // Ask for commonly-needed runtime permissions up front (only the ones declared in the manifest)
        val wanted = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter { isPermissionDeclared(it) }

        val notGranted = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            runtimePermissionLauncher.launch(notGranted.toTypedArray())
        }

        webView.loadUrl(TARGET_URL)
    }

    private fun isPermissionDeclared(permission: String): Boolean {
        return try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            false
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
