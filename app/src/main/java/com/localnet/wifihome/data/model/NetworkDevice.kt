package com.localnet.wifihome.data.model

import com.google.gson.annotations.SerializedName

/**
 * Representasi satu perangkat yang terdeteksi di jaringan lokal.
 * Data ini berasal dari ESP32 (hasil ARP scan) via endpoint GET /devices
 * atau dari pesan WebSocket bertipe "device_list".
 *
 * PENTING: nama field harus sama persis dengan JSON yang dikirim ESP32.
 * Lihat docs/ESP32_API_CONTRACT.md untuk kontrak lengkap.
 */
data class NetworkDevice(
    @SerializedName("ip") val ip: String,
    @SerializedName("mac") val mac: String,
    @SerializedName("hostname") val hostname: String? = null,
    @SerializedName("vendor") val vendor: String? = null,
    @SerializedName("last_seen") val lastSeenEpochSec: Long = 0L,
    @SerializedName("is_online") val isOnline: Boolean = true
) {
    /** Nama tampilan: pakai hostname kalau ada, kalau tidak pakai vendor, terakhir IP. */
    fun displayName(): String = when {
        !hostname.isNullOrBlank() && hostname != "?" -> hostname
        !vendor.isNullOrBlank() -> vendor
        else -> ip
    }
}
