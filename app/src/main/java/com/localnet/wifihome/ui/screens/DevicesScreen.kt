package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnet.wifihome.ui.DevicesViewModel
import com.localnet.wifihome.ui.components.StatusBadge
import com.localnet.wifihome.ui.theme.TextSecondary

@Composable
fun DevicesScreen(viewModel: DevicesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Perangkat di Jaringan", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { viewModel.loadDevices() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
            }
        }

        Button(
            onClick = { viewModel.triggerRescan() },
            enabled = !state.isScanning && state.esp32Ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isScanning) "Memindai jaringan..." else "Pindai Ulang (via ESP32)")
        }

        if (state.isScanning || state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.devices.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada data perangkat.\nPastikan ESP32 sudah terhubung & lakukan pemindaian.",
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.devices) { device ->
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = TextSecondary)
                                Column {
                                    Text(device.displayName(), style = MaterialTheme.typography.titleMedium)
                                    Text(device.ip, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(device.mac, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            StatusBadge(isOnline = device.isOnline)
                        }
                    }
                }
            }
        }
    }
}
