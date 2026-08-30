package com.aaryo.selfattendance.ui.employer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.ads.BannerAdComposable
import com.aaryo.selfattendance.ui.navigation.BottomItem
import com.aaryo.selfattendance.ui.navigation.Routes
import com.aaryo.selfattendance.ui.settings.ManageSubscriptionScreen

@Composable
fun EmployerMainScreen() {
    val navController = rememberNavController()

    val items = listOf(
        BottomItem(route = Routes.EMPLOYER_DASHBOARD, title = "Dashboard", icon = Icons.Default.Dashboard),
        BottomItem(route = Routes.STAFF_ATTENDANCE,   title = "Attendance", icon = Icons.Default.HowToReg),
        BottomItem(route = Routes.STAFF_LIST,         title = "Staff",      icon = Icons.Default.People),
        BottomItem(route = Routes.ADVANCE_KHATA,      title = "Khata",      icon = Icons.Default.AccountBalanceWallet),
        BottomItem(route = Routes.SALARY_PAYROLL,     title = "Payroll",    icon = Icons.Default.ReceiptLong)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        currentRoute?.let { AdsController.onScreenChanged(it) }
    }

    Scaffold(
        bottomBar = {
            Column {
                BannerAdComposable()
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.EMPLOYER_DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.EMPLOYER_DASHBOARD,
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            composable(Routes.EMPLOYER_DASHBOARD) {
                EmployerDashboardScreen(navController)
            }
            composable(Routes.STAFF_ATTENDANCE) {
                StaffAttendanceScreen(navController)
            }
            composable(Routes.STAFF_LIST) {
                StaffListScreen(navController)
            }
            composable(Routes.ADVANCE_KHATA) {
                AdvanceKhataScreen(navController)
            }
            composable(Routes.SALARY_PAYROLL) {
                SalaryPayrollScreen(navController)
            }
            composable(
                route = Routes.STAFF_DETAIL,
                arguments = listOf(navArgument("staffId") { type = NavType.LongType })
            ) { backStackEntry ->
                val staffId = backStackEntry.arguments?.getLong("staffId") ?: 0L
                StaffDetailScreen(employeeId = staffId, navController = navController)
            }
            composable(Routes.MANAGE_SUBSCRIPTION) {
                ManageSubscriptionScreen(navController = navController)
            }
            composable(Routes.EMPLOYER_SETTINGS) {
                EmployerSettingsScreen(navController = navController)
            }
        }
    }
}
