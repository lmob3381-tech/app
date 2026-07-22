package com.streamlocal.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String, val detail: String? = null) : Exception(message)

/**
 * Client HTTP tipis tanpa dependency tambahan (Retrofit/OkHttp) supaya ukuran source tetap kecil.
 * Base URL diambil dari Prefs setiap kali dipanggil karena bisa berubah kapan saja (tunnel ganti).
 */
object ApiClient {

    private const val TIMEOUT_MS = 15_000

    private fun baseUrlOrThrow(ctx: android.content.Context): String {
        val base = Prefs.getBaseUrl(ctx)
        if (base.isBlank()) throw ApiException("Server belum diatur")
        return base
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream ?: return ""))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) sb.append(line)
        reader.close()
        return sb.toString()
    }

    private fun handleErrorIfAny(conn: HttpURLConnection, body: String) {
        if (conn.responseCode !in 200..299) {
            val obj = runCatching { JSONObject(body) }.getOrNull()
            val msg = obj?.optString("error", "Error ${conn.responseCode}") ?: "Error ${conn.responseCode}"
            val detail = obj?.optStringOrNull("detail")
            throw ApiException(msg, detail)
        }
    }

    suspend fun search(ctx: android.content.Context, query: String, type: String, limit: Int = 20): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrThrow(ctx)
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$base/api/search?q=$q&type=$type&limit=$limit"
            val conn = openConnection(url, "GET")
            try {
                val body = readBody(conn)
                handleErrorIfAny(conn, body)
                val json = JSONObject(body)
                val arr: JSONArray = json.optJSONArray("results") ?: JSONArray()
                arr.toItemList { MediaItem.fromSearchJson(it) }
            } finally {
                conn.disconnect()
            }
        }

    suspend fun resolve(ctx: android.content.Context, sourceUrl: String): ResolveResult =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrThrow(ctx)
            val url = "$base/api/resolve"
            val conn = openConnection(url, "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("url", sourceUrl).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }
            try {
                val body = readBody(conn)
                handleErrorIfAny(conn, body)
                ResolveResult.fromJson(JSONObject(body))
            } finally {
                conn.disconnect()
            }
        }

    suspend fun history(ctx: android.content.Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrThrow(ctx)
            val url = "$base/api/history"
            val conn = openConnection(url, "GET")
            try {
                val body = readBody(conn)
                handleErrorIfAny(conn, body)
                val arr = JSONArray(body)
                arr.toItemList { MediaItem.fromHistoryJson(it) }
            } finally {
                conn.disconnect()
            }
        }

    suspend fun deleteHistory(ctx: android.content.Context, id: String): Boolean =
        withContext(Dispatchers.IO) {
            val base = baseUrlOrThrow(ctx)
            val encId = URLEncoder.encode(id, "UTF-8")
            val url = "$base/api/history/$encId"
            val conn = openConnection(url, "DELETE")
            try {
                val body = readBody(conn)
                handleErrorIfAny(conn, body)
                true
            } finally {
                conn.disconnect()
            }
        }

    /** Cek konektivitas cepat ke server (dipakai di Settings -> Tes Koneksi) */
    suspend fun ping(ctx: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = baseUrlOrThrow(ctx)
            val conn = openConnection("$base/api/history", "GET")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            val code = conn.responseCode
            conn.disconnect()
            code in 200..499
        } catch (e: Exception) {
            false
        }
    }

    fun buildStreamUrl(ctx: android.content.Context, relativePath: String): String {
        val base = Prefs.getBaseUrl(ctx)
        return if (relativePath.startsWith("http")) relativePath else "$base$relativePath"
    }
}
