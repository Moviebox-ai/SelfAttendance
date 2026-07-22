package com.aaryo.selfattendance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.aaryo.selfattendance.ui.auth.AuthScreen
import com.aaryo.selfattendance.ui.profile.EditProfileScreen
import com.aaryo.selfattendance.ui.profile.SetupProfileScreen
import com.aaryo.selfattendance.ui.splash.SplashScreen

@Composable
fun AppNavGraph(
    notificationStartScreen: String? = null
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                navController = navController,
                notificationStartScreen = notificationStartScreen
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(navController)
        }

        composable(Routes.PROFILE) {
            SetupProfileScreen(navController)
        }

        composable(Routes.MAIN) {
            MainScreen(notificationStartScreen = notificationStartScreen)
        }

        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(navController)
        }
    }
}
