package com.localnet.wifihome.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.wifihome.data.SettingsRepository
import com.localnet.wifihome.data.adb.AdbScreenshotClient
import com.localnet.wifihome.data.tvremote.TvKeyCode
import com.localnet.wifihome.data.tvremote.TvRemoteClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TvControlUiState(
    val tvIp: String = "",
    val isPairing: Boolean = false,
    val isPaired: Boolean = false,
    val isSendingKey: Boolean = false,
    val isCapturingScreenshot: Boolean = false,
    val lastScreenshot: Bitmap? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class TvControlViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(TvControlUiState())
    val uiState: StateFlow<TvControlUiState> = _uiState.asStateFlow()

    private var remoteClient: TvRemoteClient? = null

    init {
        viewModelScope.launch {
            val ip = settingsRepository.tvIp.first()
            _uiState.value = _uiState.value.copy(tvIp = ip)
        }
    }

    fun setTvIp(ip: String) {
        _uiState.value = _uiState.value.copy(tvIp = ip)
        viewModelScope.launch { settingsRepository.setTvIp(ip) }
    }

    /** Mulai proses pairing — TV akan menampilkan kode 6 digit di layar. */
    fun startPairing() {
        val ip = _uiState.value.tvIp
        if (ip.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Isi IP TV dulu")
            return
        }
        _uiState.value = _uiState.value.copy(isPairing = true, errorMessage = null)
        viewModelScope.launch {
            val client = TvRemoteClient(ip)
            remoteClient = client
            client.connectForPairing()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Cek layar TV untuk kode pairing 6 digit"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isPairing = false,
                        errorMessage = "Gagal memulai pairing: ${e.message}"
                    )
                }
        }
    }

    fun submitPairingCode(code: String) {
        val client = remoteClient ?: return
        viewModelScope.launch {
            client.submitPairingCode(code)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isPairing = false, isPaired = true, statusMessage = "Pairing berhasil")
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isPairing = false, errorMessage = "Pairing gagal: ${e.message}")
                }
        }
    }

    fun sendKey(key: TvKeyCode) {
        val ip = _uiState.value.tvIp
        if (ip.isBlank()) return
        _uiState.value = _uiState.value.copy(isSendingKey = true)
        viewModelScope.launch {
            val client = remoteClient ?: TvRemoteClient(ip).also { remoteClient = it }
            client.connectForRemote()
            client.sendKeyEvent(key)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = "Gagal kirim tombol: ${e.message}")
                }
            _uiState.value = _uiState.value.copy(isSendingKey = false)
        }
    }

    /** Ambil screenshot TV lewat ADB (butuh Wireless Debugging aktif di TV). */
    fun captureScreenshot(adbPort: Int = 5555) {
        val ip = _uiState.value.tvIp
        if (ip.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Isi IP TV dulu")
            return
        }
        _uiState.value = _uiState.value.copy(isCapturingScreenshot = true, errorMessage = null)
        viewModelScope.launch {
            val client = AdbScreenshotClient(ip, adbPort)
            client.captureScreenshot()
                .onSuccess { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    _uiState.value = _uiState.value.copy(
                        isCapturingScreenshot = false,
                        lastScreenshot = bitmap,
                        statusMessage = if (bitmap != null) "Screenshot berhasil" else null,
                        errorMessage = if (bitmap == null) "Gagal decode gambar dari TV" else null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isCapturingScreenshot = false,
                        errorMessage = "Gagal ambil screenshot: ${e.message}. Pastikan Wireless Debugging aktif di TV."
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        remoteClient?.close()
    }
}
