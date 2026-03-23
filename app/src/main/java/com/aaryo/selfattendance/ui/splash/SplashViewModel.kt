package com.aaryo.selfattendance.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val remoteConfig: RemoteConfigManager = RemoteConfigManager()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    private var started = false

    fun checkAppState() {

        if (started) return
        started = true

        viewModelScope.launch {

            try {

                // Splash delay
                delay(1200)

                // ---------- Remote Config Kill Switch ----------

                val enabled = runCatching {
                    remoteConfig.isAppEnabled()
                }.getOrDefault(true)

                if (!enabled) {
                    _blocked.value = true
                    return@launch
                }

                // ---------- Firebase User ----------

                val user = auth.currentUser

                if (user == null) {

                    _route.value = Routes.AUTH
                    return@launch
                }

                // ---------- Profile Check ----------

                val result = runCatching {
                    profileRepository.getProfile(user.uid)
                }.getOrNull()

                val profile = result?.getOrNull()

                _route.value =
                    if (profile == null || profile.name.isBlank())
                        Routes.PROFILE
                    else
                        Routes.MAIN

            } catch (_: Exception) {

                _route.value = Routes.AUTH
            }
        }
    }
}