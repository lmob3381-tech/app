package com.streamlocal.app.data

import android.content.Context
import com.google.gson.Gson
import com.streamlocal.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val detail: String? = null) : ApiResult<Nothing>()
}

class StreamLocalRepository(private val context: Context) {

    private val gson = Gson()

    private fun parseError(body: ResponseBody?): ApiErrorBody {
        return try {
            val text = body?.string()
            if (text.isNullOrBlank()) ApiErrorBody("Kesalahan tidak diketahui")
            else gson.fromJson(text, ApiErrorBody::class.java)
        } catch (e: Exception) {
            ApiErrorBody("Kesalahan tidak diketahui")
        }
    }

    suspend fun search(query: String, type: String, limit: Int = 12): ApiResult<SearchResponse> =
        withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.create(context)
                val resp = api.search(query, type, limit)
                if (resp.isSuccessful && resp.body() != null) {
                    ApiResult.Success(resp.body()!!)
                } else {
                    val err = parseError(resp.errorBody())
                    ApiResult.Failure(err.error ?: "Pencarian gagal", err.detail)
                }
            } catch (e: ApiClient.NoServerConfiguredException) {
                ApiResult.Failure(e.message ?: "Server belum diatur")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Tidak bisa terhubung ke server")
            }
        }

    suspend fun resolve(url: String): ApiResult<ResolveResponse> =
        withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.create(context)
                val resp = api.resolve(ResolveRequest(url))
                if (resp.isSuccessful && resp.body() != null) {
                    ApiResult.Success(resp.body()!!)
                } else {
                    val err = parseError(resp.errorBody())
                    ApiResult.Failure(err.error ?: "Gagal resolve", err.detail)
                }
            } catch (e: ApiClient.NoServerConfiguredException) {
                ApiResult.Failure(e.message ?: "Server belum diatur")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Tidak bisa terhubung ke server")
            }
        }

    suspend fun history(): ApiResult<List<HistoryItem>> =
        withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.create(context)
                val resp = api.history()
                if (resp.isSuccessful && resp.body() != null) {
                    ApiResult.Success(resp.body()!!)
                } else {
                    val err = parseError(resp.errorBody())
                    ApiResult.Failure(err.error ?: "Gagal memuat riwayat", err.detail)
                }
            } catch (e: ApiClient.NoServerConfiguredException) {
                ApiResult.Failure(e.message ?: "Server belum diatur")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Tidak bisa terhubung ke server")
            }
        }

    suspend fun deleteHistory(id: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.create(context)
                val resp = api.deleteHistory(id)
                if (resp.isSuccessful) {
                    ApiResult.Success(Unit)
                } else {
                    val err = parseError(resp.errorBody())
                    ApiResult.Failure(err.error ?: "Gagal menghapus", err.detail)
                }
            } catch (e: ApiClient.NoServerConfiguredException) {
                ApiResult.Failure(e.message ?: "Server belum diatur")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Tidak bisa terhubung ke server")
            }
        }

    /** Cek konektivitas cepat dengan search kosong minimal, dipakai tombol "Tes Koneksi" di Settings. */
    suspend fun testConnection(): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.create(context)
                val resp = api.history()
                if (resp.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Failure("Server merespons dengan error (${resp.code()})")
            } catch (e: ApiClient.NoServerConfiguredException) {
                ApiResult.Failure(e.message ?: "Server belum diatur")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Tidak bisa terhubung")
            }
        }
}
