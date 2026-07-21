package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnet.wifihome.ui.PingViewModel
import com.localnet.wifihome.ui.theme.NetGreen
import com.localnet.wifihome.ui.theme.NetRed
import com.localnet.wifihome.ui.theme.TextSecondary

@Composable
fun PingScreen(viewModel: PingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ping Tool", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.host,
            onValueChange = { viewModel.setHost(it) },
            label = { Text("Host / IP tujuan") },
            placeholder = { Text("misal: 8.8.8.8 atau 192.168.1.1") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRunning,
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.startPing() },
                enabled = !state.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Mulai Ping")
            }
            OutlinedButton(
                onClick = { viewModel.stopPing() },
                enabled = state.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Berhenti")
            }
        }

        if (state.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.summary?.let { summary ->
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ringkasan", style = MaterialTheme.typography.titleMedium)
                    Text("Terkirim: ${summary.sent}, Diterima: ${summary.received}")
                    Text("Packet loss: ${"%.1f".format(summary.packetLossPercent)}%")
                    Text("Min/Avg/Max: ${"%.1f".format(summary.minMs)} / ${"%.1f".format(summary.avgMs)} / ${"%.1f".format(summary.maxMs)} ms")
                }
            }
        }

        Text("Riwayat", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.results.reversed()) { result ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${result.sequence} ${result.host}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (result.success) "${"%.1f".format(result.timeMs)} ms" else "Timeout",
                        color = if (result.success) NetGreen else NetRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
