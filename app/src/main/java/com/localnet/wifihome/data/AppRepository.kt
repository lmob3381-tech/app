package com.localnet.wifihome.data

import android.content.Context
import com.localnet.wifihome.data.model.Esp32Status
import com.localnet.wifihome.data.model.NetworkDevice
import com.localnet.wifihome.data.network.Esp32WebSocketClient
import com.localnet.wifihome.data.network.RetrofitClient
import com.localnet.wifihome.data.network.WifiInfoProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * Titik akses tunggal (single source of truth) untuk semua data di app:
 * status WiFi lokal, komunikasi ke ESP32, dsb.
 *
 * Base URL ESP32 disimpan lewat SettingsRepository (DataStore) dan bisa
 * diubah user dari Settings screen (default: http://192.168.4.1/ atau IP
 * yang dikonfigurasi user setelah ESP32 konek ke WiFi rumah).
 */
class AppRepository(private val context: Context) {

    val wifiInfoProvider = WifiInfoProvider(context)

    private var wsClient: Esp32WebSocketClient? = null

    fun connectToEsp32Realtime(esp32Ip: String) {
        wsClient?.disconnect()
        val wsUrl = "ws://$esp32Ip:81/ws" // asumsi port WebSocket ESP32 = 81, lihat kontrak API
        wsClient = Esp32WebSocketClient(wsUrl).also { it.connect() }
    }

    fun realtimeDevices(): StateFlow<List<NetworkDevice>>? = wsClient?.devices

    fun disconnectRealtime() {
        wsClient?.disconnect()
        wsClient = null
    }

    suspend fun fetchEsp32Status(esp32Ip: String): Result<Esp32Status> = runCatching {
        val api = RetrofitClient.createEsp32Api("http://$esp32Ip/")
        val response = api.getStatus()
        if (response.isSuccessful) {
            response.body() ?: error("Response kosong dari ESP32")
        } else {
            error("ESP32 merespons error: ${response.code()}")
        }
    }

    suspend fun fetchDevices(esp32Ip: String): Result<List<NetworkDevice>> = runCatching {
        val api = RetrofitClient.createEsp32Api("http://$esp32Ip/")
        val response = api.getDevices()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            error("ESP32 merespons error: ${response.code()}")
        }
    }

    suspend fun triggerScan(esp32Ip: String): Result<Unit> = runCatching {
        val api = RetrofitClient.createEsp32Api("http://$esp32Ip/")
        val response = api.triggerScan()
        if (!response.isSuccessful) error("Trigger scan gagal: ${response.code()}")
    }
}
