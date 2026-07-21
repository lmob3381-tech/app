package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localnet.wifihome.data.tvremote.TvKeyCode
import com.localnet.wifihome.ui.TvControlViewModel
import com.localnet.wifihome.ui.theme.TextSecondary

@Composable
fun TvControlScreen(viewModel: TvControlViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var pairingCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Remote TV Google", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.tvIp,
            onValueChange = { viewModel.setTvIp(it) },
            label = { Text("IP Android TV") },
            placeholder = { Text("misal: 192.168.1.20") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        state.statusMessage?.let {
            Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // Pairing section
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pairing (sekali saja)", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.startPairing() }, enabled = !state.isPairing, modifier = Modifier.fillMaxWidth()) {
                    Text("Mulai Pairing")
                }
                if (state.isPairing) {
                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = { pairingCodeInput = it },
                        label = { Text("Kode 6 digit dari layar TV") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.submitPairingCode(pairingCodeInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kirim Kode")
                    }
                }
            }
        }

        // D-pad remote
        Card {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kontrol Arah", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.sendKey(TvKeyCode.DPAD_UP) }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Atas")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.DPAD_LEFT) }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Kiri")
                    }
                    OutlinedButton(onClick = { viewModel.sendKey(TvKeyCode.DPAD_CENTER) }) {
                        Text("OK")
                    }
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.DPAD_RIGHT) }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Kanan")
                    }
                }
                IconButton(onClick = { viewModel.sendKey(TvKeyCode.DPAD_DOWN) }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bawah")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.sendKey(TvKeyCode.BACK) }) { Text("Kembali") }
                    OutlinedButton(onClick = { viewModel.sendKey(TvKeyCode.HOME) }) { Text("Home") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.VOLUME_DOWN) }) {
                        Icon(Icons.Default.VolumeDown, contentDescription = "Volume turun")
                    }
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.VOLUME_MUTE) }) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Mute")
                    }
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.VOLUME_UP) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Volume naik")
                    }
                    IconButton(onClick = { viewModel.sendKey(TvKeyCode.POWER) }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power")
                    }
                }
            }
        }

        // Screenshot section
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Screenshot TV (via ADB Wireless Debugging)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aktifkan dulu: Settings > System > Developer Options > Wireless debugging di TV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Button(
                    onClick = { viewModel.captureScreenshot() },
                    enabled = !state.isCapturingScreenshot,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isCapturingScreenshot) "Mengambil gambar..." else "Ambil Screenshot")
                }
                state.lastScreenshot?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Screenshot TV",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
