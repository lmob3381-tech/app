package com.localnet.wifihome.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.wifihome.data.AppRepository
import com.localnet.wifihome.data.SettingsRepository
import com.localnet.wifihome.data.model.NetworkDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DevicesUiState(
    val esp32Ip: String = "",
    val devices: List<NetworkDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val ip = settingsRepository.esp32Ip.first()
            _uiState.value = _uiState.value.copy(esp32Ip = ip)
            if (ip.isNotBlank()) {
                loadDevices(ip)
            }
        }
    }

    fun loadDevices(ip: String = _uiState.value.esp32Ip) {
        if (ip.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Set IP ESP32 dulu di Pengaturan")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            repository.fetchDevices(ip)
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(devices = devices, isLoading = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal memuat daftar perangkat"
                    )
                }
        }
    }

    fun triggerRescan() {
        val ip = _uiState.value.esp32Ip
        if (ip.isBlank()) return
        _uiState.value = _uiState.value.copy(isScanning = true)
        viewModelScope.launch {
            repository.triggerScan(ip)
            // Beri waktu ESP32 melakukan ARP scan sebelum ambil hasil.
            kotlinx.coroutines.delay(3000)
            loadDevices(ip)
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }
}
