package com.aaryo.selfattendance.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CRASH FIX: Changed from ViewModel → AndroidViewModel.
 *
 * Constructor has ONLY (application: Application) — no extra params — so that
 * ViewModelProvider.AndroidViewModelFactory can instantiate it via reflection.
 * (Extra params with Kotlin defaults still generate a single 3-arg constructor
 *  which the factory cannot find, causing an IllegalArgumentException crash.)
 *
 * Dependencies are moved to property initializers inside the class body.
 *
 * BUG FIX: syncFromFirebase() is called BEFORE navigating to Routes.MAIN.
 * This ensures SharedPreferences hold the correct Firebase values BEFORE
 * DashboardScreen / CalendarScreen award daily / attendance coins, preventing
 * them from writing (0 + new_coins) back to Firebase and destroying the balance.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {

    // Deps initialised here — NOT in the constructor — to keep constructor = (Application)
    private val profileRepository = ProfileRepository()
    private val remoteConfig      = RemoteConfigManager.getInstance()
    private val auth              = FirebaseAuth.getInstance()

    private val _route   = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    private var started = false

    fun checkAppState() {
        if (started) return
        started = true

        viewModelScope.launch {
            try {
                delay(1200)

                val enabled = runCatching { remoteConfig.isAppEnabled() }.getOrDefault(true)
                if (!enabled) {
                    _blocked.value = true
                    return@launch
                }

                val user = auth.currentUser
                if (user == null) {
                    _route.value = Routes.AUTH
                    return@launch
                }

                // Restore coin balance + premium unlocks from Firebase BEFORE
                // any screen is shown. Awaited — navigation only happens after
                // prefs are populated with the correct Firebase values.
                runCatching {
                    RewardRepository.syncFromFirebase(PreferencesManager(getApplication()))
                }

                val profile = runCatching {
                    profileRepository.getProfile(user.uid)
                }.getOrNull()?.getOrNull()

                _route.value = if (profile == null || profile.name.isBlank())
                    Routes.PROFILE
                else
                    Routes.MAIN

            } catch (_: Exception) {
                _route.value = Routes.AUTH
            }
        }
    }
}
