package com.aaryo.selfattendance.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.*
import com.aaryo.selfattendance.R
import androidx.compose.ui.platform.LocalContext
import com.aaryo.selfattendance.ui.calendar.CalendarScreen
import com.aaryo.selfattendance.ui.dashboard.DashboardScreen
import com.aaryo.selfattendance.ui.profile.EditProfileScreen
import com.aaryo.selfattendance.ui.rewards.RewardsScreen
import com.aaryo.selfattendance.ui.settings.AboutScreen
import com.aaryo.selfattendance.ui.settings.SettingsScreen
import com.aaryo.selfattendance.ui.salarycalculator.SalaryCalculatorScreen
import com.aaryo.selfattendance.ads.BannerAdComposable
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ui.referral.ReferAndEarnScreen

@Composable
fun MainScreen(
    notificationStartScreen: String? = null
) {
    val navController = rememberNavController()

    val items = listOf(
        BottomItem(route = Routes.DASHBOARD,   title = stringResource(R.string.nav_home),        icon = Icons.Default.Home),
        BottomItem(route = Routes.CALENDAR,    title = stringResource(R.string.nav_calendar),    icon = Icons.Default.CalendarMonth),
        BottomItem(route = Routes.REWARDS,     title = stringResource(R.string.nav_rewards),     icon = Icons.Default.CardGiftcard),
        BottomItem(route = Routes.SETTINGS,    title = stringResource(R.string.nav_settings),    icon = Icons.Default.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(notificationStartScreen) {
        when (notificationStartScreen) {
            Routes.CALENDAR -> navController.navigate(Routes.CALENDAR) {
                popUpTo(Routes.DASHBOARD) { inclusive = false }
                launchSingleTop = true
            }
            Routes.DASHBOARD -> {
                navController.popBackStack(route = Routes.DASHBOARD, inclusive = false)
            }
        }
    }

    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    Scaffold(
        bottomBar = {
            Column {
                if (remoteConfig.showBannerAd()) {
                    key(currentRoute) {
                        BannerAdComposable()
                    }
                }
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
            composable(Routes.REWARDS)      { RewardsScreen(navController)   }
            composable(Routes.SETTINGS)     { SettingsScreen(navController)  }
            composable(Routes.ABOUT)        { AboutScreen(navController)     }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(navController) }

            composable(Routes.SALARY_CALCULATOR){ SalaryCalculatorScreen(navController) }

            composable(Routes.REFER_AND_EARN) { ReferAndEarnScreen(navController) }
        }
    }
}
