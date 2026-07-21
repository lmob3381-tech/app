package com.localnet.wifihome.data.network

import com.localnet.wifihome.data.model.PingResult
import com.localnet.wifihome.data.model.PingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Menjalankan ping memakai binary /system/bin/ping yang tersedia di semua
 * perangkat Android (tidak butuh root, tidak butuh permission khusus selain INTERNET).
 *
 * Kenapa bukan implementasi ICMP manual via socket? Karena raw ICMP socket
 * butuh root di Android. Memanggil binary ping sistem adalah cara standar
 * dan legal untuk fitur seperti ini.
 */
object PingTool {

    /**
     * Melakukan ping berkelanjutan ke [host], emit satu [PingResult] per baris hasil.
     * Berhenti otomatis setelah [count] paket, atau [continuous] = true untuk jalan terus
     * sampai di-cancel oleh caller (collector coroutine di-cancel).
     */
    fun pingStream(host: String, count: Int = 10, continuous: Boolean = false): Flow<PingResult> = flow {
        val args = mutableListOf("/system/bin/ping", "-i", "1")
        if (!continuous) {
            args.add("-c"); args.add(count.toString())
        }
        args.add(host)

        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var seq = 0
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line ?: continue
                val timeMatch = Regex("""time[=<]([\d.]+)""").find(text)
                if (timeMatch != null) {
                    seq++
                    val timeMs = timeMatch.groupValues[1].toDoubleOrNull()
                    emit(PingResult(host = host, sequence = seq, timeMs = timeMs, success = true))
                } else if (text.contains("Destination Host Unreachable") ||
                    text.contains("Request timeout")
                ) {
                    seq++
                    emit(PingResult(host = host, sequence = seq, timeMs = null, success = false))
                }
            }
        } finally {
            reader.close()
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    fun summarize(results: List<PingResult>): PingSummary {
        val host = results.firstOrNull()?.host ?: "-"
        val successTimes = results.filter { it.success && it.timeMs != null }.map { it.timeMs!! }
        return PingSummary(
            host = host,
            sent = results.size,
            received = successTimes.size,
            minMs = successTimes.minOrNull() ?: 0.0,
            avgMs = if (successTimes.isNotEmpty()) successTimes.average() else 0.0,
            maxMs = successTimes.maxOrNull() ?: 0.0,
            packetLossPercent = if (results.isNotEmpty())
                (1.0 - successTimes.size.toDouble() / results.size) * 100.0
            else 0.0
        )
    }
}
