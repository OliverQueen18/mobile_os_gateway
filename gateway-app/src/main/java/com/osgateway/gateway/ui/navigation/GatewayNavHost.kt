package com.osgateway.gateway.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.service.GatewayForegroundService
import com.osgateway.gateway.ui.journal.JournalScreen
import com.osgateway.gateway.ui.login.LoginScreen
import com.osgateway.gateway.ui.monitoring.MonitoringScreen
import com.osgateway.gateway.ui.settings.SettingsScreen
import com.osgateway.gateway.ui.sync.SyncScreen
import com.osgateway.gateway.ui.theme.GwGold
import com.osgateway.gateway.ui.theme.GwGreen
import com.osgateway.gateway.ui.theme.GwGreenDeep
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.ui.theme.GwSurface
import com.osgateway.gateway.ui.updates.UpdatesScreen
import com.osgateway.gateway.ussd.UssdAccessibilityService
import com.osgateway.gateway.util.PermissionHelper
import com.osgateway.gateway.worker.HeartbeatWorker
import com.osgateway.gateway.worker.TaskPollingWorker

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab("sync", "Sync", Icons.Rounded.Sync, Icons.Outlined.Sync),
    Tab("journal", "Journal", Icons.Rounded.History, Icons.Outlined.History),
    Tab("monitoring", "Santé", Icons.Rounded.MonitorHeart, Icons.Outlined.MonitorHeart),
    Tab("updates", "MàJ", Icons.Rounded.SystemUpdateAlt, Icons.Outlined.SystemUpdateAlt),
    Tab("settings", "Réglages", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
fun GatewayNavHost() {
    val context = LocalContext.current
    val locator = ServiceLocator.get(context)
    val loggedIn by locator.tokenStore.loggedIn.collectAsState()
    val navController = rememberNavController()
    var bootstrapDone by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.count { it.value }
        JournalRepository.append("Permissions auto: $granted/${result.size} accordées")
        if (!UssdAccessibilityService.isEnabled()) {
            JournalRepository.append("Ouvrir Accessibilité (USSD)")
            PermissionHelper.openAccessibilitySettings(context)
        }
    }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            bootstrapDone = false
            return@LaunchedEffect
        }
        // Refresh JWT + heartbeat dès l'entrée (session déjà ouverte)
        locator.sessionKeepAlive.ensureFreshSession()
        HeartbeatWorker.enqueue(context)
        TaskPollingWorker.enqueue(context)
        GatewayForegroundService.start(context)
        if (!bootstrapDone) {
            bootstrapDone = true
            val missing = PermissionHelper.missing(context)
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(PermissionHelper.runtimePermissions())
            } else if (!UssdAccessibilityService.isEnabled()) {
                JournalRepository.append("Ouvrir Accessibilité (USSD)")
                PermissionHelper.openAccessibilitySettings(context)
            }
        }
    }

    if (!loggedIn) {
        LoginScreen(onLoggedIn = { })
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(
                color = GwSurface,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GwGreen,
                                selectedTextColor = GwGreenDeep,
                                unselectedIconColor = GwMuted,
                                unselectedTextColor = GwMuted,
                                indicatorColor = GwGold.copy(alpha = 0.28f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "sync",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable("sync") { SyncScreen() }
            composable("journal") { JournalScreen() }
            composable("monitoring") { MonitoringScreen() }
            composable("updates") { UpdatesScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
