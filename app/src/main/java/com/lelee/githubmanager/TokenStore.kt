package com.lelee.githubmanager

import android.content.Context
import android.content.SharedPreferences

object TokenStore {
    private const val PREF = "gh_manager_prefs"
    private const val KEY_TOKEN = "gh_token"
    private const val KEY_USERNAME = "gh_username"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String {
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String {
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasToken(): Boolean = getToken().isNotBlank()
}
