package com.streamlocal.app.data

import com.google.gson.annotations.SerializedName

/** Satu item hasil pencarian dari GET /api/search */
data class SearchItem(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val thumbnail: String? = null,
    val duration: Long = 0,
    val uploader: String? = null,
    val url: String,
    val type: String? = null
)

/** Response dari GET /api/search */
data class SearchResponse(
    val query: String,
    val type: String,
    val results: List<SearchItem>
)

/** Body request untuk POST /api/resolve */
data class ResolveRequest(val url: String)

/** Response dari POST /api/resolve */
data class ResolveResponse(
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val duration: Long = 0,
    val uploader: String? = null,
    @SerializedName("source_url") val sourceUrl: String,
    val platform: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null,
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("stream_url_audio") val streamUrlAudio: String
)

/** Satu item riwayat dari GET /api/history — bentuknya sama dengan hasil resolve yang tersimpan */
data class HistoryItem(
    val id: String,
    val title: String,
    val thumbnail: String? = null,
    val duration: Long = 0,
    val uploader: String? = null,
    @SerializedName("source_url") val sourceUrl: String? = null,
    val platform: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null
)

/** Bentuk error standar dari server: { "error": "...", "detail": "..." } */
data class ApiErrorBody(
    val error: String? = null,
    val detail: String? = null
)
