package com.localnet.wifihome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.wifihome.data.model.SpeedTestPhase
import com.localnet.wifihome.data.model.SpeedTestResult
import com.localnet.wifihome.data.network.SpeedTestTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestResult())
    val uiState: StateFlow<SpeedTestResult> = _uiState.asStateFlow()

    private var testJob: Job? = null

    fun startTest() {
        if (_uiState.value.phase in listOf(SpeedTestPhase.PING, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD)) return
        _uiState.value = SpeedTestResult(phase = SpeedTestPhase.PING)

        testJob = viewModelScope.launch {
            SpeedTestTool.runFullTest().collect { result ->
                _uiState.value = result
            }
        }
    }

    fun reset() {
        testJob?.cancel()
        _uiState.value = SpeedTestResult()
    }
}
