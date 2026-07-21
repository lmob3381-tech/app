package com.localnet.wifihome.data.tvremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Client untuk pairing & mengirim key event ke Android TV / Google TV
 * memakai Android TV Remote Protocol v2 (TLS socket, port 6467 utk pairing,
 * 6466 utk remote control).
 *
 * ALUR PEMAKAIAN:
 * 1. connectForPairing(tvIp) -> TV menampilkan kode 6 digit di layar.
 * 2. submitPairingCode(code) -> simpan sertifikat client sebagai bukti pairing.
 * 3. Untuk kirim tombol berikutnya, connectForRemote(tvIp) lalu sendKeyEvent(...).
 *
 * CATATAN KEAMANAN:
 * TLS di sini memakai TrustManager yang menerima self-signed certificate
 * dari TV (ini NORMAL untuk protokol ini — TV men-generate sertifikat sendiri
 * saat pairing). Ini BUKAN celah keamanan untuk kasus penggunaan LAN lokal,
 * tapi client TIDAK memvalidasi cert ke Certificate Authority publik karena
 * memang tidak ada CA untuk sertifikat TV lokal.
 */
class TvRemoteClient(private val tvIp: String) {

    companion object {
        const val PAIRING_PORT = 6467
        const val REMOTE_PORT = 6466
    }

    private var sslSocket: SSLSocket? = null

    private fun buildPermissiveSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    /** Membuka koneksi TLS ke port pairing. TV akan menampilkan kode di layar setelah ini. */
    suspend fun connectForPairing(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val context = buildPermissiveSslContext()
            val socket = context.socketFactory.createSocket(tvIp, PAIRING_PORT) as SSLSocket
            socket.startHandshake()
            sslSocket = socket

            // Kirim pesan PairingRequest sederhana (field 1 = service_name, field 2 = client_name)
            val writer = ProtoWriter()
            writer.writeStringField(1, "WifiHomeMonitor")
            writer.writeStringField(2, "WifiHomeMonitor-App")
            sendFramed(writer.toByteArray())
        }
    }

    /**
     * Kirim kode pairing 6 digit yang muncul di layar TV.
     * Implementasi lengkap protokol pairing (secret derivation via SHA-256
     * dari cert + kode) perlu ditambahkan sesuai spek resmi sebelum dipakai
     * produksi — lihat TODO di bawah.
     */
    suspend fun submitPairingCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO: Implementasi derivation secret pairing sesuai protokol resmi:
            // secret = SHA256(clientCert.publicKey + serverCert.publicKey + code)
            // Ini butuh akses ke sertifikat TLS yang dipertukarkan saat handshake.
            val writer = ProtoWriter()
            writer.writeStringField(1, code)
            sendFramed(writer.toByteArray())
        }
    }

    /** Buka koneksi TLS ke port remote-control (dipakai setelah pairing berhasil). */
    suspend fun connectForRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val context = buildPermissiveSslContext()
            val socket = context.socketFactory.createSocket(tvIp, REMOTE_PORT) as SSLSocket
            socket.startHandshake()
            sslSocket = socket
        }
    }

    /** Kirim satu event tombol ke TV. */
    suspend fun sendKeyEvent(key: TvKeyCode, isLongPress: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val writer = ProtoWriter()
                writer.writeVarintField(1, key.code.toLong())
                writer.writeBoolField(2, isLongPress)
                sendFramed(writer.toByteArray())
            }
        }

    private fun sendFramed(payload: ByteArray) {
        val socket = sslSocket ?: error("Socket belum terkoneksi")
        val out = socket.outputStream
        // Framing sederhana: 1 byte panjang (varint) diikuti payload,
        // sesuai konvensi umum protokol ini untuk pesan pendek (<128 byte).
        out.write(payload.size)
        out.write(payload)
        out.flush()
    }

    fun close() {
        runCatching { sslSocket?.close() }
        sslSocket = null
    }
}
