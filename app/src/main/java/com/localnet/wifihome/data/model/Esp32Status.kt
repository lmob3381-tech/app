package com.localnet.wifihome.data.model

import com.google.gson.annotations.SerializedName

/**
 * Status telemetri dari perangkat ESP32 itu sendiri.
 * Diambil dari endpoint GET /status. Lihat docs/ESP32_API_CONTRACT.md.
 */
data class Esp32Status(
    @SerializedName("device_name") val deviceName: String = "ESP32-NetProbe",
    @SerializedName("firmware_version") val firmwareVersion: String = "-",
    @SerializedName("uptime_sec") val uptimeSec: Long = 0L,
    @SerializedName("free_heap") val freeHeapBytes: Long = 0L,
    @SerializedName("wifi_rssi") val wifiRssi: Int = 0,
    @SerializedName("local_ip") val localIp: String = "-",
    @SerializedName("scan_in_progress") val scanInProgress: Boolean = false,
    @SerializedName("devices_found") val devicesFound: Int = 0
)
