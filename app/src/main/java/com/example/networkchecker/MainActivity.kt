package com.example.networkchecker

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var tvWifiInfo: TextView
    private lateinit var tvNetworkInfo: TextView
    private lateinit var tvPingResult: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var etHost: EditText
    private lateinit var btnPing: Button
    private lateinit var progressPing: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val locationPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvWifiInfo = findViewById(R.id.tvWifiInfo)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        tvPingResult = findViewById(R.id.tvPingResult)
        tvPublicIp = findViewById(R.id.tvPublicIp)
        etHost = findViewById(R.id.etHost)
        btnPing = findViewById(R.id.btnPing)
        progressPing = findViewById(R.id.progressPing)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        requestPermissionsIfNeeded()

        btnPing.setOnClickListener {
            val host = etHost.text.toString().trim()
            if (host.isEmpty()) {
                Toast.makeText(this, "Masukkan host atau IP tujuan", Toast.LENGTH_SHORT).show()
            } else {
                runPing(host)
            }
        }

        swipeRefresh.setOnRefreshListener {
            refreshAll()
        }

        refreshAll()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), locationPermissionCode)
        }
    }

    private fun refreshAll() {
        loadWifiInfo()
        loadNetworkInfo()
        loadPublicIp()
        swipeRefresh.isRefreshing = false
    }

    // ---------------- WIFI INFO ----------------

    private fun loadWifiInfo() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifiConnected) {
            tvWifiInfo.text = "Tidak terhubung ke WiFi (mungkin memakai data seluler)."
            return
        }

        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Tidak diketahui"
        val bssid = wifiInfo.bssid ?: "-"
        val rssi = wifiInfo.rssi
        val linkSpeed = wifiInfo.linkSpeed
        val frequency = try { wifiInfo.frequency } catch (e: Exception) { -1 }
        val ipAddress = Formatter.formatIpAddress(wifiInfo.ipAddress)
        val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
        val signalQuality = when (signalLevel) {
            4 -> "Sangat Baik"
            3 -> "Baik"
            2 -> "Sedang"
            1 -> "Lemah"
            else -> "Sangat Lemah"
        }

        val sb = StringBuilder()
        sb.append("SSID          : $ssid\n")
        sb.append("BSSID         : $bssid\n")
        sb.append("IP Lokal      : $ipAddress\n")
        sb.append("Sinyal (RSSI) : $rssi dBm ($signalQuality)\n")
        sb.append("Kecepatan Link: $linkSpeed Mbps\n")
        if (frequency > 0) sb.append("Frekuensi     : $frequency MHz\n")

        tvWifiInfo.text = sb.toString()
    }

    // ---------------- IP / NETWORK INFO ----------------

    private fun loadNetworkInfo() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)

        val sb = StringBuilder()

        val transport = when {
            capabilities == null -> "Tidak ada koneksi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Data Seluler"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Lainnya"
        }
        sb.append("Jenis Koneksi : $transport\n")

        if (capabilities != null) {
            val downKbps = capabilities.linkDownstreamBandwidthKbps
            val upKbps = capabilities.linkUpstreamBandwidthKbps
            sb.append("Estimasi Down : ${downKbps / 1000.0} Mbps\n")
            sb.append("Estimasi Up   : ${upKbps / 1000.0} Mbps\n")
            sb.append("Internet      : ${if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Tervalidasi" else "Tidak tervalidasi"}\n")
        }

        linkProperties?.let { lp ->
            sb.append("Interface     : ${lp.interfaceName ?: "-"}\n")
            val dnsList = lp.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
            if (dnsList.isNotEmpty()) sb.append("DNS Server    : $dnsList\n")
            lp.routes.firstOrNull { it.isDefaultRoute }?.gateway?.let { gw ->
                sb.append("Gateway       : ${gw.hostAddress}\n")
            }
        }

        // Semua alamat IP lokal dari semua interface
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        sb.append("${intf.displayName}: ${addr.hostAddress}\n")
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("Gagal membaca interface: ${e.message}\n")
        }

        tvNetworkInfo.text = sb.toString()
    }

    // ---------------- PING ----------------

    private fun runPing(host: String) {
        btnPing.isEnabled = false
        progressPing.visibility = ProgressBar.VISIBLE
        tvPingResult.text = "Melakukan ping ke $host ...\n"

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                pingHost(host)
            }
            tvPingResult.text = result
            progressPing.visibility = ProgressBar.GONE
            btnPing.isEnabled = true
        }
    }

    private fun pingHost(host: String): String {
        val sb = StringBuilder()
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "4", host))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            process.waitFor()
            if (sb.isEmpty()) {
                sb.append(fallbackReachabilityCheck(host))
            }
            sb.toString()
        } catch (e: Exception) {
            fallbackReachabilityCheck(host) + "\n(Catatan: ping shell gagal, memakai metode cadangan)\n" + e.message
        }
    }

    private fun fallbackReachabilityCheck(host: String): String {
        val sb = StringBuilder()
        try {
            val start = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(3000)
            val time = System.currentTimeMillis() - start
            sb.append("Host: ${address.hostAddress}\n")
            sb.append(if (reachable) "Status: Terjangkau (${time} ms)\n" else "Status: Tidak terjangkau / timeout\n")
        } catch (e: Exception) {
            sb.append("Gagal resolve host: ${e.message}\n")
        }
        return sb.toString()
    }

    // ---------------- PUBLIC IP ----------------

    private fun loadPublicIp() {
        CoroutineScope(Dispatchers.Main).launch {
            tvPublicIp.text = "Mengambil IP publik..."
            val ip = withContext(Dispatchers.IO) { fetchPublicIp() }
            tvPublicIp.text = ip
        }
    }

    private fun fetchPublicIp(): String {
        return try {
            val url = URL("https://api.ipify.org")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val ip = reader.readLine()
                reader.close()
                "IP Publik: $ip"
            } else {
                "Gagal mengambil IP publik (kode: $responseCode)"
            }
        } catch (e: Exception) {
            "Tidak ada koneksi internet atau gagal mengambil IP publik.\n(${e.message})"
        }
    }
}
