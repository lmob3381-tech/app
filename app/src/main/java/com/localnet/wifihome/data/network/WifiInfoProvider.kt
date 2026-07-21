package com.localnet.wifihome.data.network

import android.content.Context
import android.net.wifi.WifiManager
import com.localnet.wifihome.data.model.WifiStatus
import java.net.InetAddress

/**
 * Membaca status koneksi WiFi saat ini memakai WifiManager.
 * Butuh permission ACCESS_FINE_LOCATION agar SSID/BSSID tidak muncul sebagai "<unknown ssid>".
 */
class WifiInfoProvider(context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getCurrentStatus(): WifiStatus {
        val info = wifiManager.connectionInfo ?: return WifiStatus(isConnected = false)

        val ip = intToIp(info.ipAddress)
        val dhcpInfo = wifiManager.dhcpInfo
        val gateway = dhcpInfo?.gateway?.let { intToIp(it) } ?: "-"

        val rawSsid = info.ssid?.removeSurrounding("\"") ?: "-"
        val signalPercent = WifiManager.calculateSignalLevel(info.rssi, 100)

        return WifiStatus(
            ssid = if (rawSsid == "<unknown ssid>") "-" else rawSsid,
            bssid = info.bssid ?: "-",
            ipAddress = ip,
            gatewayAddress = gateway,
            linkSpeedMbps = info.linkSpeed,
            frequencyMHz = info.frequency,
            signalDbm = info.rssi,
            signalLevelPercent = signalPercent,
            isConnected = ip != "0.0.0.0"
        )
    }

    private fun intToIp(ip: Int): String {
        return try {
            InetAddress.getByAddress(
                byteArrayOf(
                    (ip and 0xFF).toByte(),
                    (ip shr 8 and 0xFF).toByte(),
                    (ip shr 16 and 0xFF).toByte(),
                    (ip shr 24 and 0xFF).toByte()
                )
            ).hostAddress ?: "-"
        } catch (e: Exception) {
            "-"
        }
    }
}
