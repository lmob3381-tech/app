package com.localnet.wifihome.data.network

import com.google.gson.Gson
import com.localnet.wifihome.data.model.NetworkDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    object Connected : WsConnectionState()
    data class Error(val message: String) : WsConnectionState()
}

/**
 * Klien WebSocket ke ESP32 untuk menerima update device list secara realtime,
 * tanpa perlu polling REST /devices berulang-ulang.
 *
 * Format pesan yang diharapkan dari ESP32 (lihat docs/ESP32_API_CONTRACT.md):
 * {"type": "device_list", "devices": [ {...NetworkDevice...}, ... ]}
 * {"type": "scan_started"}
 * {"type": "scan_finished", "count": N}
 */
class Esp32WebSocketClient(private val wsUrl: String) {

    private val gson = Gson()
    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket perlu timeout tak terbatas
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState

    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices

    fun connect() {
        if (webSocket != null) return
        _connectionState.value = WsConnectionState.Connecting

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = WsConnectionState.Connected
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                _connectionState.value = WsConnectionState.Disconnected
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.Error(t.message ?: "Koneksi WebSocket gagal")
                webSocket = null
            }
        })
    }

    private fun handleMessage(text: String) {
        runCatching {
            val map = gson.fromJson(text, Map::class.java)
            when (map["type"]) {
                "device_list" -> {
                    val jsonDevices = gson.toJson(map["devices"])
                    val list = gson.fromJson(jsonDevices, Array<NetworkDevice>::class.java).toList()
                    _devices.value = list
                }
                // "scan_started" / "scan_finished" bisa ditangani di ViewModel via connectionState
                // atau ditambah StateFlow terpisah kalau perlu granularitas lebih.
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        webSocket = null
        _connectionState.value = WsConnectionState.Disconnected
    }
}
