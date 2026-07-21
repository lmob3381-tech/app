package com.localnet.wifihome.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.wifihome.data.AppRepository
import com.localnet.wifihome.data.SettingsRepository
import com.localnet.wifihome.data.model.Esp32Status
import com.localnet.wifihome.data.model.WifiStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DashboardUiState(
    val wifiStatus: WifiStatus = WifiStatus(),
    val esp32Status: Esp32Status? = null,
    val esp32Connected: Boolean = false,
    val esp32Ip: String = "",
    val isLoadingEsp32: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshWifiStatus()
        viewModelScope.launch {
            val savedIp = settingsRepository.esp32Ip.first()
            if (savedIp.isNotBlank()) {
                _uiState.value = _uiState.value.copy(esp32Ip = savedIp)
                refreshEsp32Status(savedIp)
            }
        }
        // Auto-refresh status WiFi setiap 5 detik selagi dashboard aktif
        viewModelScope.launch {
            while (true) {
                delay(5000)
                refreshWifiStatus()
            }
        }
    }

    fun refreshWifiStatus() {
        val status = repository.wifiInfoProvider.getCurrentStatus()
        _uiState.value = _uiState.value.copy(wifiStatus = status)
    }

    fun setEsp32Ip(ip: String) {
        _uiState.value = _uiState.value.copy(esp32Ip = ip)
        viewModelScope.launch {
            settingsRepository.setEsp32Ip(ip)
        }
        refreshEsp32Status(ip)
    }

    fun refreshEsp32Status(ip: String = _uiState.value.esp32Ip) {
        if (ip.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoadingEsp32 = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.fetchEsp32Status(ip)
            result.onSuccess { status ->
                _uiState.value = _uiState.value.copy(
                    esp32Status = status,
                    esp32Connected = true,
                    isLoadingEsp32 = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    esp32Connected = false,
                    isLoadingEsp32 = false,
                    errorMessage = e.message ?: "Gagal terhubung ke ESP32"
                )
            }
        }
    }
}
