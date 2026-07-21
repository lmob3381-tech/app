package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnet.wifihome.data.model.SpeedTestPhase
import com.localnet.wifihome.ui.SpeedTestViewModel
import com.localnet.wifihome.ui.components.StatCard
import com.localnet.wifihome.ui.theme.NetBlue
import com.localnet.wifihome.ui.theme.TextSecondary

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val isRunning = state.phase in listOf(SpeedTestPhase.PING, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Speed Test", style = MaterialTheme.typography.headlineMedium)

        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = state.progressPercent / 100f,
                modifier = Modifier.fillMaxSize(0.8f),
                strokeWidth = 10.dp,
                color = NetBlue
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (state.phase) {
                        SpeedTestPhase.IDLE -> "Siap"
                        SpeedTestPhase.PING -> "Mengukur Ping..."
                        SpeedTestPhase.DOWNLOAD -> "Download..."
                        SpeedTestPhase.UPLOAD -> "Upload..."
                        SpeedTestPhase.DONE -> "Selesai"
                        SpeedTestPhase.ERROR -> "Gagal"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.phase == SpeedTestPhase.DOWNLOAD || state.phase == SpeedTestPhase.UPLOAD) {
                    Text("${state.progressPercent}%", color = TextSecondary)
                }
            }
        }

        Button(
            onClick = { if (isRunning) viewModel.reset() else viewModel.startTest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Batalkan" else "Mulai Tes")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Ping",
                value = state.pingMs?.let { "${"%.0f".format(it)} ms" } ?: "-"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Jitter",
                value = state.jitterMs?.let { "${"%.1f".format(it)} ms" } ?: "-"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Download",
                value = state.downloadMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "-"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Upload",
                value = state.uploadMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "-"
            )
        }

        Text(
            "Catatan: tes ini memakai server publik Cloudflare, hasilnya estimasi kasar (bukan hasil presisi seperti Ookla Speedtest).",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
