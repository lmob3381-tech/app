package com.localnet.wifihome.data.network

import com.localnet.wifihome.data.model.Esp32Status
import com.localnet.wifihome.data.model.NetworkDevice
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Kontrak REST API yang harus diimplementasikan oleh firmware ESP32.
 * Detail lengkap payload & contoh JSON ada di docs/ESP32_API_CONTRACT.md
 * — dokumen itu WAJIB dibaca AI/dev yang menulis firmware ESP32-nya.
 */
interface Esp32Api {

    /** Status kesehatan & info dasar ESP32. */
    @GET("/status")
    suspend fun getStatus(): Response<Esp32Status>

    /** Daftar perangkat hasil ARP scan terakhir (cached, tidak trigger scan baru). */
    @GET("/devices")
    suspend fun getDevices(): Response<List<NetworkDevice>>

    /** Trigger ARP scan baru secara paksa (async di sisi ESP32, hasil diambil lewat /devices). */
    @POST("/scan")
    suspend fun triggerScan(): Response<Unit>

    /**
     * Minta ESP32 melakukan ping ke host tertentu dari sisi jaringan lokal
     * (berguna untuk ukur latency internal LAN, bukan dari HP).
     */
    @POST("/ping")
    suspend fun pingFromEsp32(
        @Query("host") host: String,
        @Query("count") count: Int = 4
    ): Response<Map<String, Any>>
}
