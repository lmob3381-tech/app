package com.localnet.wifihome.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localnet.wifihome.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val esp32IpFlow by settingsRepository.esp32Ip.collectAsState(initial = "")
    val tvIpFlow by settingsRepository.tvIp.collectAsState(initial = "")

    var esp32Ip by remember(esp32IpFlow) { mutableStateOf(esp32IpFlow) }
    var tvIp by remember(tvIpFlow) { mutableStateOf(tvIpFlow) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineMedium)

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Perangkat ESP32", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = esp32Ip,
                    onValueChange = { esp32Ip = it; saved = false },
                    label = { Text("IP ESP32") },
                    placeholder = { Text("misal: 192.168.1.50") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Android TV / Google TV", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = tvIp,
                    onValueChange = { tvIp = it; saved = false },
                    label = { Text("IP TV") },
                    placeholder = { Text("misal: 192.168.1.20") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Button(
            onClick = {
                scope.launch {
                    settingsRepository.setEsp32Ip(esp32Ip)
                    settingsRepository.setTvIp(tvIp)
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan")
        }

        if (saved) {
            Text("Pengaturan tersimpan.", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tips: cari IP ESP32 lewat aplikasi router/scanner sekali di awal, " +
                "lalu catat di sini. IP TV bisa dilihat di Settings > Network TV.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
