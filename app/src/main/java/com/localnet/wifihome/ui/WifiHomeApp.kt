package com.localnet.wifihome.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localnet.wifihome.ui.screens.*

private fun iconFor(route: String): ImageVector = when (route) {
    Screen.Dashboard.route -> Icons.Default.Home
    Screen.Ping.route -> Icons.Default.NetworkPing
    Screen.SpeedTest.route -> Icons.Default.Speed
    Screen.Devices.route -> Icons.Default.Devices
    Screen.TvControl.route -> Icons.Default.Tv
    else -> Icons.Default.Home
}

@Composable
fun WifiHomeApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(onOpenSettings = { navController.navigate(Screen.Settings.route) })
            }
            composable(Screen.Ping.route) { PingScreen() }
            composable(Screen.SpeedTest.route) { SpeedTestScreen() }
            composable(Screen.Devices.route) { DevicesScreen() }
            composable(Screen.TvControl.route) { TvControlScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        Screen.bottomNavItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(iconFor(screen.route), contentDescription = screen.label) },
                label = { Text(screen.label) }
            )
        }
    }
}
