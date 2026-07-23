package com.streamlocal.app.network

import com.streamlocal.app.data.HistoryItem
import com.streamlocal.app.data.ResolveRequest
import com.streamlocal.app.data.ResolveResponse
import com.streamlocal.app.data.SearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Sesuai dokumentasi API.md StreamLocal.
 * Base URL diatur dinamis lewat ServerConfig (bisa berubah kapan saja saat
 * cloudflared tunnel-nya berganti), lihat ApiClient untuk pembuatan instance-nya.
 */
interface StreamLocalApi {

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "video", // "video" | "music"
        @Query("limit") limit: Int = 12
    ): Response<SearchResponse>

    @POST("api/resolve")
    suspend fun resolve(@Body body: ResolveRequest): Response<ResolveResponse>

    @GET("api/history")
    suspend fun history(): Response<List<HistoryItem>>

    @DELETE("api/history/{id}")
    suspend fun deleteHistory(@Path("id") id: String): Response<Unit>
}
