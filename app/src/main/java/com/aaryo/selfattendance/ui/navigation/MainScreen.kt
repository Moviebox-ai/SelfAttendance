package com.aaryo.selfattendance.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*

import com.aaryo.selfattendance.ui.calendar.CalendarScreen
import com.aaryo.selfattendance.ui.dashboard.DashboardScreen
import com.aaryo.selfattendance.ui.profile.EditProfileScreen
import com.aaryo.selfattendance.ui.settings.AboutScreen
import com.aaryo.selfattendance.ui.settings.SettingsScreen
import com.aaryo.selfattendance.ui.salaryslip.SalarySlipScreen

@Composable
fun MainScreen() {

    val navController = rememberNavController()

    val items = listOf(

        BottomItem(
            route = Routes.DASHBOARD,
            title = "Dashboard",
            icon = Icons.Default.Home
        ),

        BottomItem(
            route = Routes.CALENDAR,
            title = "Calendar",
            icon = Icons.Default.CalendarMonth
        ),

        BottomItem(
            route = Routes.EDIT_PROFILE,
            title = "Profile",
            icon = Icons.Default.Person
        ),

        BottomItem(
            route = Routes.SETTINGS,
            title = "Settings",
            icon = Icons.Default.Settings
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(

        // ✅ Sirf NavigationBar — Banner har screen apna alag dikhayegi
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }

    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(navController) }
            composable(Routes.CALENDAR) { CalendarScreen(navController) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.ABOUT) { AboutScreen(navController) }
            composable(Routes.SALARY_SLIP) { SalarySlipScreen(navController) }
        }
    }
}
