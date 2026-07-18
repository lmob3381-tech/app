package id.lelek.adguardbrowser

import android.content.Context

/**
 * Penyimpanan bookmark sederhana pakai SharedPreferences.
 * Cukup buat kebutuhan personal browser, nggak perlu database.
 */
object BookmarkManager {
    private const val PREFS = "adguard_browser_bookmarks"
    private const val KEY = "bookmark_set"

    fun add(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun remove(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(url)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun getAll(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY, emptySet())?.sorted() ?: emptyList()
    }
}
