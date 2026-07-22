package com.streamlocal.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Menyimpan base URL server (cloudflared tunnel berubah-ubah setiap kali server di-restart),
 * jadi ini dibuat mudah diubah dari layar Pengaturan tanpa perlu rebuild aplikasi.
 */
object Prefs {
    private const val FILE = "streamlocal_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_AUDIO_ONLY = "audio_only_default"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getBaseUrl(ctx: Context): String {
        val raw = sp(ctx).getString(KEY_BASE_URL, "") ?: ""
        return raw.trimEnd('/')
    }

    fun setBaseUrl(ctx: Context, url: String) {
        var clean = url.trim()
        if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        clean = clean.trimEnd('/')
        sp(ctx).edit().putString(KEY_BASE_URL, clean).apply()
    }

    fun hasBaseUrl(ctx: Context): Boolean = getBaseUrl(ctx).isNotBlank()

    fun getAudioOnlyDefault(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_AUDIO_ONLY, false)

    fun setAudioOnlyDefault(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean(KEY_AUDIO_ONLY, value).apply()
    }
}
