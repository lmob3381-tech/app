package com.localnet.wifihome.data.model

data class WifiStatus(
    val ssid: String = "-",
    val bssid: String = "-",
    val ipAddress: String = "-",
    val gatewayAddress: String = "-",
    val linkSpeedMbps: Int = 0,
    val frequencyMHz: Int = 0,
    val signalDbm: Int = 0,
    val signalLevelPercent: Int = 0,
    val isConnected: Boolean = false
)
