package com.streamlocal.app

import org.json.JSONArray
import org.json.JSONObject

data class MediaItem(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumbnail: String?,
    val duration: Long,
    val uploader: String?,
    val url: String,
    val type: String
) {
    companion object {
        fun fromSearchJson(o: JSONObject): MediaItem = MediaItem(
            id = o.optString("id"),
            title = o.optString("title"),
            artist = o.optStringOrNull("artist"),
            album = o.optStringOrNull("album"),
            thumbnail = o.optStringOrNull("thumbnail"),
            duration = o.optLong("duration", 0L),
            uploader = o.optStringOrNull("uploader"),
            url = o.optString("url"),
            type = o.optString("type", "video")
        )

        fun fromHistoryJson(o: JSONObject): MediaItem = MediaItem(
            id = o.optString("id"),
            title = o.optString("title"),
            artist = null,
            album = null,
            thumbnail = o.optStringOrNull("thumbnail"),
            duration = o.optLong("duration", 0L),
            uploader = o.optStringOrNull("uploader"),
            url = o.optStringOrNull("source_url") ?: o.optString("url"),
            type = if (o.optString("platform") == "youtube") "video" else o.optString("platform", "video")
        )
    }
}

data class ResolveResult(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val duration: Long,
    val uploader: String?,
    val sourceUrl: String,
    val platform: String,
    val streamUrlPath: String,
    val streamUrlAudioPath: String
) {
    companion object {
        fun fromJson(o: JSONObject): ResolveResult = ResolveResult(
            id = o.optString("id"),
            title = o.optString("title"),
            thumbnail = o.optStringOrNull("thumbnail"),
            duration = o.optLong("duration", 0L),
            uploader = o.optStringOrNull("uploader"),
            sourceUrl = o.optString("source_url"),
            platform = o.optString("platform"),
            streamUrlPath = o.optString("stream_url"),
            streamUrlAudioPath = o.optString("stream_url_audio")
        )
    }
}

fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return if (v.isEmpty()) null else v
}

fun JSONArray.toItemList(mapper: (JSONObject) -> MediaItem): List<MediaItem> {
    val list = mutableListOf<MediaItem>()
    for (i in 0 until length()) {
        list.add(mapper(getJSONObject(i)))
    }
    return list
}
