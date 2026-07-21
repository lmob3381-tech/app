package com.localnet.wifihome.data.network

import com.localnet.wifihome.data.model.SpeedTestPhase
import com.localnet.wifihome.data.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Speedtest sederhana memakai endpoint publik Cloudflare (speed.cloudflare.com),
 * yang menyediakan endpoint __down (download test) dan __up (upload test)
 * tanpa perlu API key. Cocok untuk estimasi kasar kecepatan internet rumah.
 *
 * Catatan: ini BUKAN hasil se-akurat Ookla Speedtest (yang pakai server
 * geo-terdekat & protokol khusus), tapi cukup representatif untuk monitoring rumahan.
 */
object SpeedTestTool {

    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    private const val PING_URL = "https://speed.cloudflare.com/__down?bytes=0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun runFullTest(): Flow<SpeedTestResult> = flow {
        emit(SpeedTestResult(phase = SpeedTestPhase.PING, progressPercent = 0))

        val pingSamples = mutableListOf<Double>()
        repeat(5) {
            val t = measurePingOnce()
            if (t != null) pingSamples.add(t)
        }
        val avgPing = pingSamples.takeIf { it.isNotEmpty() }?.average()
        val jitter = if (pingSamples.size > 1) {
            pingSamples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
        } else null

        emit(
            SpeedTestResult(
                phase = SpeedTestPhase.DOWNLOAD,
                pingMs = avgPing,
                jitterMs = jitter,
                progressPercent = 20
            )
        )

        val downloadMbps = measureDownload { progress ->
            // progress 0..100 dipetakan ke rentang 20-60 di keseluruhan tes
        }

        emit(
            SpeedTestResult(
                phase = SpeedTestPhase.UPLOAD,
                pingMs = avgPing,
                jitterMs = jitter,
                downloadMbps = downloadMbps,
                progressPercent = 60
            )
        )

        val uploadMbps = measureUpload()

        emit(
            SpeedTestResult(
                phase = SpeedTestPhase.DONE,
                pingMs = avgPing,
                jitterMs = jitter,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                progressPercent = 100
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun measurePingOnce(): Double? {
        return try {
            val request = Request.Builder().url(PING_URL).head().build()
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
            }
            val end = System.nanoTime()
            (end - start) / 1_000_000.0
        } catch (e: IOException) {
            null
        }
    }

    private fun measureDownload(onProgress: (Int) -> Unit): Double? {
        return try {
            val request = Request.Builder().url(DOWNLOAD_URL).build()
            val start = System.nanoTime()
            var totalBytes = 0L

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val source = body.source()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = source.inputStream().read(buffer)
                    if (read == -1) break
                    totalBytes += read
                }
            }

            val elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedSec <= 0) return null
            val megabits = (totalBytes * 8) / 1_000_000.0
            (megabits / elapsedSec * 100).roundToInt() / 100.0
        } catch (e: IOException) {
            null
        }
    }

    private fun measureUpload(): Double? {
        return try {
            val payload = ByteArray(5_000_000) // 5 MB data acak
            val body: RequestBody = payload.toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder().url(UPLOAD_URL).post(body).build()

            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
            }
            val elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedSec <= 0) return null
            val megabits = (payload.size * 8) / 1_000_000.0
            (megabits / elapsedSec * 100).roundToInt() / 100.0
        } catch (e: IOException) {
            null
        }
    }
}
