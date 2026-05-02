package com.aaryo.selfattendance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(),
    private val profileRepo: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // Google sign-in trigger: holds the idToken to process
    private val _googleIdToken = MutableStateFlow<String?>(null)
    val googleIdToken: StateFlow<String?> = _googleIdToken


    // ---------------- EMAIL LOGIN ----------------

    fun login(
        email: String,
        password: String,
        navController: NavController
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

                    checkUserProfile(navController)

                }.onFailure {

                    _errorMessage.value =
                        it.message ?: "Login failed"
                }

            } catch (e: Exception) {

                _errorMessage.value =
                    e.message ?: "Unexpected error"

            } finally {

                _loading.value = false
            }
        }
    }


    // ---------------- REGISTER ----------------

    fun register(
        email: String,
        password: String,
        navController: NavController
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

                    withContext(Dispatchers.Main) {

                        navController.navigate(Routes.PROFILE) {

                            popUpTo(Routes.AUTH) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    }

                }.onFailure {

                    _errorMessage.value =
                        it.message ?: "Registration failed"
                }

            } catch (e: Exception) {

                _errorMessage.value =
                    e.message ?: "Unexpected error"

            } finally {

                _loading.value = false
            }
        }
    }


    // ---------------- GOOGLE SIGN IN ----------------

    fun signInWithGoogle(
        idToken: String,
        navController: NavController
    ) {

        viewModelScope.launch {

            _loading.value = true
            _errorMessage.value = null

            try {

                val result = repo.firebaseAuthWithGoogle(idToken)

                result.onSuccess {

                    checkUserProfile(navController)

                }.onFailure {

                    _errorMessage.value =
                        it.message ?: "Google sign-in failed"
                }

            } catch (e: Exception) {

                _errorMessage.value =
                    e.message ?: "Unexpected error"

            } finally {

                _loading.value = false
            }
        }
    }


    // ---------------- CHECK PROFILE ----------------

    private suspend fun checkUserProfile(
        navController: NavController
    ) {

        try {

            val uid = auth.currentUser?.uid ?: return

            val exists =
                profileRepo.profileExists(uid)

            withContext(Dispatchers.Main) {

                if (exists) {

                    navController.navigate(Routes.MAIN) {

                        popUpTo(Routes.AUTH) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }

                } else {

                    navController.navigate(Routes.PROFILE) {

                        popUpTo(Routes.AUTH) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                }
            }

        } catch (e: Exception) {

            _errorMessage.value =
                e.message ?: "Profile check failed"
        }
    }
}
