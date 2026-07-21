package com.localnet.wifihome.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wifihome_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val ESP32_IP_KEY = stringPreferencesKey("esp32_ip")
        private val TV_IP_KEY = stringPreferencesKey("tv_ip")
    }

    val esp32Ip: Flow<String> = context.dataStore.data.map { it[ESP32_IP_KEY] ?: "" }
    val tvIp: Flow<String> = context.dataStore.data.map { it[TV_IP_KEY] ?: "" }

    suspend fun setEsp32Ip(ip: String) {
        context.dataStore.edit { it[ESP32_IP_KEY] = ip }
    }

    suspend fun setTvIp(ip: String) {
        context.dataStore.edit { it[TV_IP_KEY] = ip }
    }
}
