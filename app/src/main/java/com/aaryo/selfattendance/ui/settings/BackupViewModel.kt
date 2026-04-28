package com.aaryo.selfattendance.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.coin.CoinRepository
import com.aaryo.selfattendance.coin.PremiumFeature
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.BackupRepository
import com.aaryo.selfattendance.ui.premium.PremiumManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Extends AndroidViewModel so we can access Application context to
 * instantiate PreferencesManager (required by CoinRepository for
 * the cooldown + local daily-cache system).
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs          = PreferencesManager(application.applicationContext)
    private val backupRepo     = BackupRepository()
    private val attendanceRepo = AttendanceRepository()
    private val authRepo       = AuthRepository()
    private val premiumManager = PremiumManager(CoinRepository(preferencesManager = prefs))

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _restoreCompleted = MutableStateFlow(false)
    val restoreCompleted: StateFlow<Boolean> = _restoreCompleted.asStateFlow()

    // Premium lock state — observed by SettingsScreen to show lock UI
    private val _isBackupLocked = MutableStateFlow(false)
    val isBackupLocked: StateFlow<Boolean> = _isBackupLocked.asStateFlow()

    init {
        viewModelScope.launch {
            _isBackupLocked.value = !premiumManager.canUseFeature(PremiumFeature.BACKUP)
        }
    }

    fun backup() {
        viewModelScope.launch {
            // Premium gate
            if (!premiumManager.canUseFeature(PremiumFeature.BACKUP)) {
                _message.value = "🔒 Cloud Backup is a premium feature. Unlock it in the Wallet!"
                return@launch
            }

            _loading.value = true
            _message.value = null

            try {
                val uid = authRepo.currentUser()?.uid
                    ?: throw Exception("User not logged in")

                val attendanceList = attendanceRepo.getAllAttendance(uid)

                if (attendanceList.isEmpty()) {
                    _isError.value = false
                    _message.value = "No attendance data found to backup"
                    return@launch
                }

                backupRepo.backupAttendance(uid, attendanceList)

                _isError.value = false
                _message.value = "✓ Backup successful! ${attendanceList.size} records saved."

            } catch (e: Exception) {
                _isError.value = true
                _message.value = "Backup failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            // Premium gate
            if (!premiumManager.canUseFeature(PremiumFeature.BACKUP)) {
                _message.value = "🔒 Cloud Restore is a premium feature. Unlock it in the Wallet!"
                return@launch
            }

            _loading.value = true
            _message.value = null
            _restoreCompleted.value = false

            try {
                val uid = authRepo.currentUser()?.uid
                    ?: throw Exception("User not logged in")

                val restoredList = backupRepo.restoreAttendance(uid)

                if (restoredList.isEmpty()) {
                    _isError.value = false
                    _message.value = "No backup data found to restore"
                    return@launch
                }

                _isError.value = false
                _message.value = "✓ Restore successful! ${restoredList.size} records restored."
                _restoreCompleted.value = true

            } catch (e: Exception) {
                _isError.value = true
                _message.value = "Restore failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun resetAttendance() {
        viewModelScope.launch {
            _loading.value = true
            _message.value = null

            try {
                val uid = authRepo.currentUser()?.uid
                    ?: throw Exception("User not logged in")

                attendanceRepo.deleteAllAttendance(uid)

                _isError.value = false
                _message.value = "✓ Attendance data reset ho gaya."

            } catch (e: Exception) {
                _isError.value = true
                _message.value = "Reset failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }

    fun clearRestoreFlag() {
        _restoreCompleted.value = false
    }
}
