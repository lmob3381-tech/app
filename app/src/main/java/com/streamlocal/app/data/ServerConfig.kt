package com.streamlocal.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Menyimpan Base URL server StreamLocal (mis. http://127.0.0.1:4200 atau
 * https://xxxx.trycloudflare.com). Karena cloudflared tunnel URL bisa berubah-ubah,
 * ini TIDAK di-hardcode di kode ataupun di-baked saat build — murni diatur pengguna
 * lewat Settings, dan langsung dipakai saat itu juga tanpa perlu rebuild aplikasi.
 */
object ServerConfig {
    private const val PREFS_NAME = "streamlocal_prefs"
    private const val KEY_BASE_URL = "base_url"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ambil base URL yang tersimpan, sudah dinormalisasi (selalu diakhiri "/"), atau null kalau belum diatur. */
    fun getBaseUrl(context: Context): String? {
        val raw = prefs(context).getString(KEY_BASE_URL, null) ?: return null
        return normalize(raw)
    }

    fun getRawBaseUrl(context: Context): String {
        return prefs(context).getString(KEY_BASE_URL, "") ?: ""
    }

    fun setBaseUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    fun isConfigured(context: Context): Boolean = !getBaseUrl(context).isNullOrBlank()

    /** Pastikan format valid untuk Retrofit: harus ada scheme dan diakhiri "/" */
    private fun normalize(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        if (!u.endsWith("/")) {
            u = "$u/"
        }
        return u
    }

    /** Gabungkan base URL dengan path relatif dari server (mis. stream_url) menjadi URL absolut. */
    fun resolveUrl(context: Context, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        val base = getBaseUrl(context)?.trimEnd('/') ?: return relativeOrAbsolute
        val path = if (relativeOrAbsolute.startsWith("/")) relativeOrAbsolute else "/$relativeOrAbsolute"
        return base + path
    }
}
