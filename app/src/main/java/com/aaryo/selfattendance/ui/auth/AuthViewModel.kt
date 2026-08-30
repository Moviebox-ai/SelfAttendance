package com.aaryo.selfattendance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel wapas plain ViewModel hai (AndroidViewModel nahi) taaki Compose ka
 * default viewModel() bina factory ke kaam kare aur crash na ho.
 *
 * Context dependency ko constructor se hata ke checkUserProfile() ke parameter
 * mein le jaaya gaya hai — AuthScreen khud PreferencesManager pass karta hai.
 */
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(),
    private val profileRepo: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun clearError() {
        _errorMessage.value = null
    }

    // Google sign-in trigger: holds the idToken to process
    private val _googleIdToken = MutableStateFlow<String?>(null)
    val googleIdToken: StateFlow<String?> = _googleIdToken


    // ---------------- EMAIL LOGIN ----------------

    fun login(
        email: String,
        password: String,
        navController: NavController,
        prefs: PreferencesManager
    ) {

        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email and password required"
            return
        }

        viewModelScope.launch {

            _loading.value = true
            _errorMessage.value = null

            try {

                val result = repo.login(
                    email.trim(),
                    password.trim()
                )

                result.onSuccess {
                    checkUserProfile(navController, prefs)
                }.onFailure {
                    _errorMessage.value = it.message ?: "Login failed"
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _loading.value = false
            }
        }
    }


    // ---------------- REGISTER ----------------

    fun register(
        email: String,
        password: String,
        navController: NavController,
        prefs: PreferencesManager
    ) {

        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Email and password required"
            return
        }

        viewModelScope.launch {

            _loading.value = true
            _errorMessage.value = null

            try {

                val result = repo.register(
                    email.trim(),
                    password.trim()
                )

                result.onSuccess {

                    // Naya account hamesha 0 se start kare — pehle clear karo,
                    // phir Firebase sync (naye account mein Firebase bhi 0 hoga).
                    prefs.clearUserData()
                    runCatching {
                        RewardRepository.syncFromFirebase(prefs)
                    }

                    withContext(Dispatchers.Main) {
                        val targetRoute = if (prefs.appMode == PreferencesManager.MODE_EMPLOYER) {
                            Routes.EMPLOYER_MAIN
                        } else {
                            Routes.PROFILE
                        }
                        navController.navigate(targetRoute) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }

                }.onFailure {
                    _errorMessage.value = it.message ?: "Registration failed"
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _loading.value = false
            }
        }
    }


    // ---------------- GOOGLE SIGN IN ----------------

    fun signInWithGoogle(
        idToken: String,
        navController: NavController,
        prefs: PreferencesManager
    ) {

        viewModelScope.launch {

            _loading.value = true
            _errorMessage.value = null

            try {

                val result = repo.firebaseAuthWithGoogle(idToken)

                result.onSuccess {
                    checkUserProfile(navController, prefs)
                }.onFailure {
                    _errorMessage.value = it.message ?: "Google sign-in failed"
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _loading.value = false
            }
        }
    }


    // ---------------- CHECK PROFILE ----------------

    private suspend fun checkUserProfile(
        navController: NavController,
        prefs: PreferencesManager
    ) {

        try {

            val uid = auth.currentUser?.uid ?: return

            // Pehle local user-specific data clear karo (coins, spin, premium, streak).
            // Iske baad syncFromFirebase() call hoti hai — local=0 hone ki wajah se
            // `if (remoteBal >= 0)` hamesha TRUE hoga aur naye account ki Firebase
            // values correctly restore hongi. Bina clear kiye, purane account ke coins
            // naye account ko mil jaate the (SharedPreferences sabke liye common hai).
            prefs.clearUserData()

            runCatching {
                RewardRepository.syncFromFirebase(prefs)
            }

            // Safety net: guarantees every account (new or pre-existing)
            // ends up with a unique AX ID (AX-XXXXXXX) for server-side lookup.
            runCatching { repo.ensureUniqueId(uid) }

            val exists = profileRepo.profileExists(uid)

            withContext(Dispatchers.Main) {

                if (prefs.appMode == PreferencesManager.MODE_EMPLOYER) {
                    navController.navigate(Routes.EMPLOYER_MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                } else if (exists) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(Routes.PROFILE) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Profile check failed"
        }
    }
}
