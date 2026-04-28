package com.aaryo.selfattendance.admin

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

// ═══════════════════════════════════════════════════════════════
//  AdminViewModel — MVVM layer for admin panel
// ═══════════════════════════════════════════════════════════════

data class AdminUiState(
    // Auth state
    val isAuthenticated   : Boolean                    = false,
    val pinError          : String?                    = null,
    val lockoutSeconds    : Int                        = 0,

    // Dashboard data
    val isLoading         : Boolean                    = false,
    val totalUsers        : Int                        = 0,
    val totalCoins        : Int                        = 0,
    val userList          : List<AdminUserSummary>     = emptyList(),

    // Selected user detail
    val selectedUser      : AdminUserSummary?          = null,
    val userTransactions  : List<AdminTransaction>     = emptyList(),
    val loadingUser       : Boolean                    = false,

    // ── Wallet Settings (global) ──────────────────────────────
    val walletSettings       : WalletSettings          = WalletSettings(),
    val loadingWalletSettings: Boolean                 = false,
    val walletSettingsSaved  : Boolean                 = false,

    // Action feedback
    val actionMessage     : String?                    = null,
    val actionSuccess     : Boolean                    = true
)

class AdminViewModel(
    private val adminRepo  : AdminRepository,
    private val pinManager : AdminPinManager
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private var lockoutJob: Job? = null

    init {
        // If already locked out, start countdown
        val remaining = pinManager.getLockoutRemainingMs()
        if (remaining > 0) startLockoutCountdown(remaining)
    }

    // ── PIN Authentication ────────────────────────────────────────

    fun isFirstTimeSetup() = pinManager.isFirstTimeSetup()

    fun saveNewPin(pin: String) {
        pinManager.setPin(pin)
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            when (val result = pinManager.verify(pin)) {
                is AdminPinManager.VerifyResult.Success -> {
                    AdminAuditLogger.log(AdminAction.ADMIN_LOGIN)
                    _state.update { it.copy(isAuthenticated = true, pinError = null) }
                    loadDashboard()
                    loadWalletSettings()
                }
                is AdminPinManager.VerifyResult.FirstTimeSetup -> {
                    // No PIN set yet — UI will show setup flow; nothing to do here
                    _state.update { it.copy(pinError = null) }
                }
                is AdminPinManager.VerifyResult.Failed -> {
                    AdminAuditLogger.log(AdminAction.PIN_FAILED, details = "Attempts left: ${result.attemptsLeft}")
                    _state.update {
                        it.copy(pinError = "Wrong PIN. ${result.attemptsLeft} attempt(s) left.")
                    }
                }
                is AdminPinManager.VerifyResult.Locked -> {
                    startLockoutCountdown(result.remainingMs)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            AdminAuditLogger.log(AdminAction.ADMIN_LOGOUT)
            _state.update { AdminUiState() }
        }
    }

    // ── Dashboard ─────────────────────────────────────────────────

    fun loadDashboard() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val statsResult = adminRepo.getGlobalStats()
            val usersResult = adminRepo.getAllUsers()

            _state.update {
                it.copy(
                    isLoading  = false,
                    totalUsers = statsResult.getOrNull()?.first  ?: 0,
                    totalCoins = statsResult.getOrNull()?.second ?: 0,
                    userList   = usersResult.getOrNull() ?: emptyList()
                )
            }
        }
    }

    // ── User Detail ───────────────────────────────────────────────

    fun selectUser(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loadingUser = true, selectedUser = null) }
            AdminAuditLogger.log(AdminAction.VIEW_USER, userId)

            val wallet = adminRepo.getWallet(userId)
            val txs    = adminRepo.getTransactions(userId)

            _state.update {
                it.copy(
                    loadingUser      = false,
                    selectedUser     = wallet.getOrNull(),
                    userTransactions = txs.getOrNull() ?: emptyList()
                )
            }
        }
    }

    fun clearSelectedUser() = _state.update { it.copy(selectedUser = null, userTransactions = emptyList()) }

    // ── Coin Actions ──────────────────────────────────────────────

    fun addCoins(userId: String, amount: Int, reason: String) {
        viewModelScope.launch {
            val result = adminRepo.addCoins(userId, amount, reason)
            result.onSuccess { newBalance ->
                _state.update { s ->
                    s.copy(
                        actionMessage = "+$amount coins added. New balance: $newBalance",
                        actionSuccess = true,
                        selectedUser  = s.selectedUser?.copy(balance = newBalance)
                    )
                }
                refreshTransactions(userId)
            }
            result.onFailure { e ->
                _state.update { it.copy(actionMessage = "Failed: ${e.message}", actionSuccess = false) }
            }
        }
    }

    fun deductCoins(userId: String, amount: Int, reason: String) {
        viewModelScope.launch {
            val result = adminRepo.deductCoins(userId, amount, reason)
            result.onSuccess { newBalance ->
                _state.update { s ->
                    s.copy(
                        actionMessage = "-$amount coins deducted. New balance: $newBalance",
                        actionSuccess = true,
                        selectedUser  = s.selectedUser?.copy(balance = newBalance)
                    )
                }
                refreshTransactions(userId)
            }
            result.onFailure { e ->
                _state.update { it.copy(actionMessage = "Failed: ${e.message}", actionSuccess = false) }
            }
        }
    }

    fun resetWallet(userId: String, reason: String) {
        viewModelScope.launch {
            val result = adminRepo.resetWallet(userId, reason)
            result.onSuccess {
                _state.update { s ->
                    s.copy(
                        actionMessage = "Wallet reset to 0",
                        actionSuccess = true,
                        selectedUser  = s.selectedUser?.copy(balance = 0)
                    )
                }
                refreshTransactions(userId)
            }
            result.onFailure { e ->
                _state.update { it.copy(actionMessage = "Failed: ${e.message}", actionSuccess = false) }
            }
        }
    }

    fun clearActionMessage() = _state.update { it.copy(actionMessage = null) }

    // ── Wallet Settings ───────────────────────────────────────────

    fun loadWalletSettings() {
        viewModelScope.launch {
            _state.update { it.copy(loadingWalletSettings = true) }
            val result = adminRepo.getWalletSettings()
            _state.update {
                it.copy(
                    loadingWalletSettings = false,
                    walletSettings        = result.getOrNull() ?: WalletSettings()
                )
            }
        }
    }

    fun saveWalletSettings(settings: WalletSettings) {
        viewModelScope.launch {
            _state.update { it.copy(loadingWalletSettings = true, walletSettingsSaved = false) }
            val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
            val result   = adminRepo.saveWalletSettings(settings, adminUid)
            result.onSuccess {
                _state.update {
                    it.copy(
                        loadingWalletSettings = false,
                        walletSettings        = settings,
                        walletSettingsSaved   = true,
                        actionMessage         = "✅ Wallet settings saved successfully"
                    )
                }
            }
            result.onFailure { e ->
                _state.update {
                    it.copy(
                        loadingWalletSettings = false,
                        actionMessage         = "❌ Save failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearWalletSettingsSaved() =
        _state.update { it.copy(walletSettingsSaved = false) }

    // ── Private helpers ───────────────────────────────────────────

    private suspend fun refreshTransactions(userId: String) {
        val txs = adminRepo.getTransactions(userId)
        _state.update { it.copy(userTransactions = txs.getOrNull() ?: it.userTransactions) }
    }

    private fun startLockoutCountdown(remainingMs: Long) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var remaining = (remainingMs / 1000).toInt()
            _state.update { it.copy(lockoutSeconds = remaining, pinError = null) }
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.update { it.copy(lockoutSeconds = remaining) }
            }
            _state.update { it.copy(lockoutSeconds = 0) }
        }
    }

    // ── Factory ───────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(
                adminRepo  = AdminRepository(),
                pinManager = AdminPinManager(context)
            ) as T
    }
}
