package com.aaryo.selfattendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
//  FIX 1: Interfaces for testability — real implementations inject
//  karo production mein, mock inject karo tests mein.
// ─────────────────────────────────────────────────────────────────

interface IProfileRepository {
    suspend fun getProfile(uid: String): Result<UserProfile?>
    suspend fun saveProfile(uid: String, profile: UserProfile): Result<Unit>
}

interface IAuthRepository {
    fun currentUser(): com.google.firebase.auth.FirebaseUser?
}

// ── Default adapters (wrap existing concrete classes) ────────────

class DefaultProfileRepository(
    private val delegate: ProfileRepository = ProfileRepository()
) : IProfileRepository {
    override suspend fun getProfile(uid: String) = delegate.getProfile(uid)
    override suspend fun saveProfile(uid: String, profile: UserProfile) =
        delegate.saveProfile(uid, profile)
}

class DefaultAuthRepository(
    private val delegate: AuthRepository = AuthRepository()
) : IAuthRepository {
    override fun currentUser() = delegate.currentUser()
}

// ─────────────────────────────────────────────────────────────────
//  FIX 1: Factory — ViewModelProvider ke through inject karo.
//  Usage in screen:
//    val viewModel: ProfileViewModel = viewModel(
//        factory = ProfileViewModelFactory()
//    )
// ─────────────────────────────────────────────────────────────────

class ProfileViewModelFactory(
    private val repo    : IProfileRepository = DefaultProfileRepository(),
    private val authRepo: IAuthRepository    = DefaultAuthRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileViewModel(repo, authRepo) as T
}

// ─────────────────────────────────────────────────────────────────
//  ProfileViewModel
// ─────────────────────────────────────────────────────────────────

class ProfileViewModel(
    private val repo    : IProfileRepository,
    private val authRepo: IAuthRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _profileExists = MutableStateFlow(false)
    val profileExists: StateFlow<Boolean> = _profileExists.asStateFlow()

    // ─────────────────────────────────────────────────────────
    //  FIX 5: SharedFlow for one-shot navigation event.
    //  StateFlow same value re-emit nahi karta — navigation
    //  silently skip ho sakti thi. SharedFlow har emit par
    //  collect karta hai regardless of previous value.
    //  resetNavigation() ki ab zaroorat nahi.
    // ─────────────────────────────────────────────────────────
    private val _profileSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val profileSaved: SharedFlow<Unit> = _profileSaved.asSharedFlow()


    // ── LOAD PROFILE ──────────────────────────────────────────

    fun loadProfile() {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null

            try {
                val user = authRepo.currentUser()
                if (user == null) {
                    _error.value = "User not logged in"
                    return@launch
                }

                // FIX 3: fold() — onSuccess/onFailure ke andar
                // throw hua exception silently swallow hota tha.
                // fold() mein exception outer catch tak pahunchta hai.
                repo.getProfile(user.uid).fold(
                    onSuccess = { profile ->
                        _profile.value      = profile
                        _profileExists.value = profile != null
                    },
                    onFailure = { throwable ->
                        _error.value = throwable.message ?: "Failed to load profile"
                    }
                )

            } catch (e: Exception) {
                _error.value = e.message ?: "Unexpected error while loading"

            } finally {
                // FIX 2: isActive check — scope cancel hone par
                // (e.g. screen se bahar jaane par) stale false
                // write nahi hoga cancelled coroutine mein.
                if (isActive) _loading.value = false
            }
        }
    }


    // ── SAVE PROFILE (new setup) ──────────────────────────────

    fun saveProfile(profile: UserProfile) {
        saveOrUpdate(profile)
    }


    // ── UPDATE PROFILE (edit screen) ─────────────────────────
    //
    // FIX 6: Dono same saveOrUpdate() call karte hain.
    // Yeh methods distinguish karte hain caller intent ko —
    // SetupProfileScreen → saveProfile()
    // EditProfileScreen  → updateProfile()
    // Agar future mein alag logic chahiye (e.g. audit log,
    // "created_at" vs "updated_at") toh split ready hai.

    fun updateProfile(profile: UserProfile) {
        saveOrUpdate(profile)
    }


    // ── COMMON SAVE/UPDATE ────────────────────────────────────

    private fun saveOrUpdate(profile: UserProfile) {
        viewModelScope.launch {
            _loading.value = true
            _error.value   = null

            try {
                val user = authRepo.currentUser()
                if (user == null) {
                    _error.value = "User not logged in"
                    return@launch
                }

                // FIX 3: fold() — same reason as loadProfile()
                repo.saveProfile(user.uid, profile).fold(
                    onSuccess = {
                        _profile.value       = profile
                        _profileExists.value = true
                        // FIX 5: emit karo, value set nahi karo
                        _profileSaved.tryEmit(Unit)
                    },
                    onFailure = { throwable ->
                        _error.value = throwable.message ?: "Failed to save profile"
                    }
                )

            } catch (e: Exception) {
                _error.value = e.message ?: "Unexpected error while saving"

            } finally {
                // FIX 2: Cancel-safe loading reset
                if (isActive) _loading.value = false
            }
        }
    }


    // ── RESET NAVIGATION ─────────────────────────────────────
    //
    // FIX 5: SharedFlow use karne ke baad yeh function
    // zaruri nahi raha. Backward compatibility ke liye rakha
    // hai — screens update hone ke baad hata sakte ho.

    @Deprecated(
        message  = "profileSaved is now a SharedFlow — resetNavigation() ki zaroorat nahi.",
        level    = DeprecationLevel.WARNING
    )
    fun resetNavigation() {
        // no-op — SharedFlow automatically consumed hota hai
    }
}