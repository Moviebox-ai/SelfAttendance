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
fun AppNavGraph() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        // ---------- SPLASH ----------

        composable(Routes.SPLASH) {

            SplashScreen(navController)
        }

        // ---------- AUTH ----------

        composable(Routes.AUTH) {

            AuthScreen(navController)
        }

        // ---------- PROFILE SETUP ----------

        composable(Routes.PROFILE) {

            SetupProfileScreen(navController)
        }

        // ---------- MAIN APP ----------

        composable(Routes.MAIN) {

            MainScreen()
        }

        // ---------- EDIT PROFILE ----------

        composable(Routes.EDIT_PROFILE) {

            EditProfileScreen(navController)
        }
    }
}