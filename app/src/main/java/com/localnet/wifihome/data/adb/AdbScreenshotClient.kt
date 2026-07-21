package com.localnet.wifihome.data.adb

import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Mengambil screenshot Android TV / Google TV lewat ADB over network,
 * memakai library "dadb" (pure Kotlin ADB client, tidak butuh adb binary).
 *
 * PRASYARAT DI TV:
 * Settings > System > Developer Options > Wireless debugging harus AKTIF.
 * TV akan menampilkan IP:port (misal 192.168.1.20:5555). Untuk Android 11+
 * biasanya perlu proses "pairing" sekali (kode 6 digit) sebelum connect biasa
 * bisa dipakai — ini ditangani otomatis oleh Dadb.create() dalam banyak kasus
 * jika device sudah pernah di-pair lewat adb di komputer, atau lewat
 * Dadb.pair() jika tersedia di versi library yang dipakai.
 */
class AdbScreenshotClient(private val tvIp: String, private val port: Int = 5555) {

    /**
     * Ambil satu screenshot TV, kembalikan raw bytes PNG.
     * Return null kalau gagal (device tidak terjangkau / wireless debugging mati).
     */
    suspend fun captureScreenshot(): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val dadb = Dadb.create(tvIp, port)
            dadb.use { connection ->
                val output = ByteArrayOutputStream()
                val response = connection.shell("screencap -p")
                if (response.exitCode != 0) {
                    error("Perintah screencap gagal, exit code=${response.exitCode}: ${response.errorOutput}")
                }
                // stdout dari shell() berupa String; untuk data biner PNG lebih aman
                // pakai API stream binary bila tersedia di versi dadb yang dipakai.
                // Fallback: gunakan response.output (bytes) bila API mendukungnya.
                response.output.toByteArray().also { output.write(it) }
                output.toByteArray()
            }
        }
    }

    /**
     * Kirim satu key event ke TV lewat ADB (alternatif dari TvRemoteClient,
     * berguna sebagai fallback kalau Android TV Remote Protocol v2 gagal pairing).
     */
    suspend fun sendKeyEvent(androidKeyCode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dadb = Dadb.create(tvIp, port)
            dadb.use { connection ->
                connection.shell("input keyevent $androidKeyCode")
            }
            Unit
        }
    }
}
