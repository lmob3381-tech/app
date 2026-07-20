package com.lmob.gitrepomanager.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the GitHub Personal Access Token securely on-device using
 * EncryptedSharedPreferences (AES256-GCM), backed by the Android Keystore.
 *
 * The token never leaves the device except as an Authorization header
 * sent directly to api.github.com over HTTPS.
 */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _tokenFlow = MutableStateFlow(getToken())
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _tokenFlow.value = token
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _tokenFlow.value = null
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    companion object {
        private const val PREFS_FILE_NAME = "gitrepo_secure_prefs"
        private const val KEY_TOKEN = "github_pat_token"
    }
}
