package com.aaryo.selfattendance.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.*
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ui.admin.AdminDashboardScreen
import com.aaryo.selfattendance.ui.admin.AdminPinScreen
import androidx.compose.ui.platform.LocalContext
import com.aaryo.selfattendance.ui.calendar.CalendarScreen
import com.aaryo.selfattendance.ui.dashboard.DashboardScreen
import com.aaryo.selfattendance.ui.profile.EditProfileScreen
import com.aaryo.selfattendance.ui.settings.AboutScreen
import com.aaryo.selfattendance.ui.settings.SettingsScreen
import com.aaryo.selfattendance.ui.wallet.WalletScreen

@Composable
fun MainScreen(
    notificationStartScreen: String? = null
) {
    val navController = rememberNavController()

    val items = listOf(
        BottomItem(route = Routes.DASHBOARD, title = stringResource(R.string.nav_home),     icon = Icons.Default.Home),
        BottomItem(route = Routes.CALENDAR,  title = stringResource(R.string.nav_calendar), icon = Icons.Default.CalendarMonth),
        BottomItem(route = Routes.WALLET,    title = stringResource(R.string.nav_wallet),   icon = Icons.Default.AccountBalanceWallet),
        BottomItem(route = Routes.SETTINGS,  title = stringResource(R.string.nav_settings), icon = Icons.Default.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        if (notificationStartScreen == Routes.CALENDAR) {
            navController.navigate(Routes.CALENDAR) {
                popUpTo(Routes.DASHBOARD) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController    = navController,
            startDestination = Routes.DASHBOARD,
            modifier         = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            composable(Routes.DASHBOARD)    { DashboardScreen(navController) }
            composable(Routes.CALENDAR)     { CalendarScreen(navController)  }
            composable(Routes.WALLET)       { WalletScreen()                 }
            composable(Routes.SETTINGS)     { SettingsScreen(navController)  }
            composable(Routes.ABOUT)        { AboutScreen(navController)     }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(navController) }

            // ── Admin routes — secret, not in bottom nav ──────────────
            composable(Routes.ADMIN_PIN) {
                AdminPinScreen(navController)
            }
            composable(Routes.ADMIN_DASHBOARD) {
                AdminDashboardScreen(navController)
            }
        }
    }
}
