package id.lelek.adguardbrowser

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Daftar mode server DNS AdGuard yang bisa dipilih user.
 * URL & IP bootstrap sesuai dokumentasi resmi AdGuard DNS-over-HTTPS.
 */
enum class AdGuardMode(val label: String, val url: String, val bootstrapIps: List<String>) {
    DEFAULT(
        "AdGuard Default (Ad & Tracker Blocking)",
        "https://dns.adguard.com/dns-query",
        listOf("94.140.14.14", "94.140.15.15")
    ),
    FAMILY(
        "AdGuard Family Protection",
        "https://family.adguard-dns.com/dns-query",
        listOf("94.140.14.15", "94.140.15.16")
    ),
    UNFILTERED(
        "AdGuard Non-filtering",
        "https://unfiltered.adguard-dns.com/dns-query",
        listOf("94.140.14.140", "94.140.14.141")
    )
}

/**
 * Object singleton yang mengelola koneksi DNS-over-HTTPS (DoH) ke AdGuard.
 * Semua request WebView di-resolve lewat client ini, bukan lewat DNS bawaan
 * jaringan/operator, supaya blocking iklan & tracker dari AdGuard tetap aktif.
 */
object DohManager {

    @Volatile
    private var currentMode: AdGuardMode = AdGuardMode.DEFAULT

    @Volatile
    private var dohClient: OkHttpClient = buildClient(currentMode)

    fun setMode(mode: AdGuardMode) {
        currentMode = mode
        dohClient = buildClient(mode)
    }

    fun getMode(): AdGuardMode = currentMode

    fun getClient(): OkHttpClient = dohClient

    private fun buildClient(mode: AdGuardMode): OkHttpClient {
        // Client "bootstrap" dipakai cuma buat konek ke endpoint DoH itu sendiri
        // (karena buat resolve dns.adguard.com butuh IP-nya duluan, makanya di-hardcode).
        val bootstrapClient = OkHttpClient.Builder().build()

        val bootstrapAddresses = mode.bootstrapIps.map { InetAddress.getByName(it) }

        val dns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(mode.url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapAddresses)
            .includeIPv6(false)
            .resolvePrivateAddresses(false)
            .build()

        return bootstrapClient.newBuilder()
            .dns(dns)
            .build()
    }

    /**
     * Resolve satu hostname lewat AdGuard DoH yang aktif sekarang.
     * Dipakai buat fitur "Test Resolusi DNS" biar user bisa buktiin
     * request emang lewat AdGuard, bukan DNS operator/jaringan.
     */
    fun testResolve(host: String): List<String> {
        return try {
            dohClient.dns.lookup(host).map { it.hostAddress ?: it.toString() }
        } catch (e: Exception) {
            listOf("Gagal resolve lewat AdGuard: ${e.message}")
        }
    }
}
