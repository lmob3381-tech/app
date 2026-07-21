package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnet.wifihome.ui.DashboardViewModel
import com.localnet.wifihome.ui.components.StatCard
import com.localnet.wifihome.ui.components.StatusBadge
import com.localnet.wifihome.ui.theme.NetBlue
import com.localnet.wifihome.ui.theme.NetGreen
import com.localnet.wifihome.ui.theme.TextSecondary

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel(), onOpenSettings: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WiFi Home Monitor", style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = { viewModel.refreshWifiStatus() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                }
            }
        }

        // Kartu status WiFi utama
        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = if (state.wifiStatus.isConnected) NetGreen else TextSecondary)
                    Column {
                        Text(state.wifiStatus.ssid, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.wifiStatus.isConnected) "Terhubung" else "Tidak terhubung",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoColumn("IP Address", state.wifiStatus.ipAddress)
                    InfoColumn("Gateway", state.wifiStatus.gatewayAddress)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoColumn("Sinyal", "${state.wifiStatus.signalLevelPercent}% (${state.wifiStatus.signalDbm} dBm)")
                    InfoColumn("Kecepatan Link", "${state.wifiStatus.linkSpeedMbps} Mbps")
                }
            }
        }

        // Grid statistik cepat
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Frekuensi",
                    value = "${state.wifiStatus.frequencyMHz} MHz"
                )
            }
            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    label = "BSSID",
                    value = state.wifiStatus.bssid
                )
            }
        }

        // Status ESP32
        Text("Perangkat ESP32 (Network Probe)", style = MaterialTheme.typography.titleMedium)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.esp32Ip.isBlank()) {
                    Text(
                        "IP ESP32 belum diatur. Buka menu Pengaturan untuk menghubungkan.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.esp32Status?.deviceName ?: state.esp32Ip)
                        StatusBadge(isOnline = state.esp32Connected)
                    }
                    if (state.isLoadingEsp32) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.esp32Status?.let { s ->
                        Text("Uptime: ${formatUptime(s.uptimeSec)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("Perangkat ditemukan: ${s.devicesFound}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("RSSI ESP32: ${s.wifiRssi} dBm", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { viewModel.refreshEsp32Status() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cek Status ESP32")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}j ${m}m"
}
