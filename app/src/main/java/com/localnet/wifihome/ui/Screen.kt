package com.localnet.wifihome.ui

sealed class Screen(val route: String, val label: String) {
    object Dashboard : Screen("dashboard", "Beranda")
    object Ping : Screen("ping", "Ping")
    object SpeedTest : Screen("speedtest", "Speed Test")
    object Devices : Screen("devices", "Perangkat")
    object TvControl : Screen("tv_control", "TV")
    object Settings : Screen("settings", "Pengaturan")

    companion object {
        val bottomNavItems = listOf(Dashboard, Ping, SpeedTest, Devices, TvControl)
    }
}
