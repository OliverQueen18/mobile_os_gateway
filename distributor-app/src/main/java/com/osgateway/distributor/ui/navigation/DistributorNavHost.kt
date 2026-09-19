package com.osgateway.distributor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.history.HistoryScreen
import com.osgateway.distributor.ui.home.HomeScreen
import com.osgateway.distributor.ui.login.ForgotPasswordScreen
import com.osgateway.distributor.ui.login.LoginScreen
import com.osgateway.distributor.ui.login.RegisterScreen
import com.osgateway.distributor.ui.login.ResetPasswordScreen
import com.osgateway.distributor.ui.notifications.NotificationsScreen
import com.osgateway.distributor.ui.profile.ProfileScreen
import com.osgateway.distributor.ui.receipt.ReceiptScreen
import com.osgateway.distributor.ui.registration.RegistrationStatusScreen
import com.osgateway.distributor.ui.search.SearchScreen
import com.osgateway.distributor.ui.stats.StatsScreen
import com.osgateway.distributor.ui.theme.OsGold
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.transaction.NewTransactionScreen
import com.osgateway.distributor.ui.transaction.OperatorSelectScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val approvedTabs = listOf(
    Tab("home", "Accueil", Icons.Default.Home),
    Tab("history", "Historique", Icons.Default.History),
    Tab("notif", "Notifs", Icons.Default.Notifications),
    Tab("profile", "Profil", Icons.Default.Person),
)

private val pendingTabs = listOf(
    Tab("registration", "Dossier", Icons.Default.Assignment),
    Tab("notif", "Notifs", Icons.Default.Notifications),
    Tab("profile", "Profil", Icons.Default.Person),
)

@Composable
fun DistributorNavHost() {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val loggedIn by locator.tokenStore.loggedIn.collectAsState()

    if (!loggedIn) {
        AuthNavHost()
        return
    }

    var gateReady by remember { mutableStateOf(false) }
    var registrationApproved by remember { mutableStateOf(true) }

    LaunchedEffect(loggedIn) {
        gateReady = false
        registrationApproved = true
        if (loggedIn) {
            locator.sessionKeepAlive.ensureFreshSession()
        }
        if (!locator.networkMonitor.isOnline()) {
            val cached = locator.statsCache.load()?.distributor
            registrationApproved = cached?.isRegistrationApproved() != false
            gateReady = true
            return@LaunchedEffect
        }
        try {
            val me = locator.transactionApi.myDistributor()
            val data = me.data
            if (me.success && data != null) {
                locator.statsCache.save(distributor = data)
                registrationApproved = data.isRegistrationApproved()
            } else {
                // Keep app usable if profile endpoint fails; home will retry.
                registrationApproved = true
            }
        } catch (_: Exception) {
            val cached = locator.statsCache.load()?.distributor
            registrationApproved = cached?.isRegistrationApproved() != false
        }
        gateReady = true
    }

    if (!gateReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OsNavy)
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val tabs = if (registrationApproved) approvedTabs else pendingTabs
    val startDestination = if (registrationApproved) "home" else "registration"
    val hideBottomBar = current?.startsWith("receipt") == true ||
        current?.startsWith("new") == true ||
        current?.startsWith("select-operator") == true ||
        current == "search" ||
        current == "stats"

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = OsNavy,
                    tonalElevation = NavigationBarDefaults.Elevation,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OsNavy,
                                selectedTextColor = OsNavy,
                                indicatorColor = OsGold.copy(alpha = 0.35f),
                                unselectedIconColor = OsMuted,
                                unselectedTextColor = OsMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable("registration") {
                RegistrationStatusScreen(
                    navController = navController,
                    onApproved = { registrationApproved = true },
                )
            }
            composable("home") { HomeScreen(navController) }
            composable("history") { HistoryScreen(navController) }
            composable("search") { SearchScreen(navController) }
            composable("stats") { StatsScreen() }
            composable("notif") { NotificationsScreen() }
            composable("profile") { ProfileScreen(navController) }
            composable(
                route = "select-operator/{type}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "DEPOT"
                    },
                ),
            ) { entry ->
                OperatorSelectScreen(
                    navController = navController,
                    transactionType = entry.arguments?.getString("type").orEmpty(),
                )
            }
            composable(
                route = "new/{type}/{operator}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "DEPOT"
                    },
                    navArgument("operator") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                NewTransactionScreen(
                    navController = navController,
                    initialType = entry.arguments?.getString("type").orEmpty(),
                    initialOperator = entry.arguments?.getString("operator").orEmpty(),
                )
            }
            // Compat: ancienne route sans opérateur → sélection
            composable(
                route = "new/{type}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "DEPOT"
                    },
                ),
            ) { entry ->
                val type = entry.arguments?.getString("type").orEmpty()
                LaunchedEffect(type) {
                    navController.navigate("select-operator/$type") {
                        popUpTo("new/$type") { inclusive = true }
                    }
                }
            }
            composable("receipt/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReceiptScreen(transactionId = id, navController = navController)
            }
        }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("forgot") { ForgotPasswordScreen(navController) }
        composable(
            route = "reset/{email}/{code}",
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("code") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            ResetPasswordScreen(
                navController = navController,
                emailArg = entry.arguments?.getString("email").orEmpty(),
                codeArg = entry.arguments?.getString("code").orEmpty(),
            )
        }
    }
}
