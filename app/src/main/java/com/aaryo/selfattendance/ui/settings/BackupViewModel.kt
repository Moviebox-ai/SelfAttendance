package com.aaryo.selfattendance.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs          = PreferencesManager(application.applicationContext)
    private val backupRepo     = BackupRepository()
    private val attendanceRepo = AttendanceRepository()
    private val authRepo       = AuthRepository()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _restoreCompleted = MutableStateFlow(false)
    val restoreCompleted: StateFlow<Boolean> = _restoreCompleted.asStateFlow()

    // meaning the backup rate-limiting was completely dead code. Users could spam backup
    // repeatedly with no cooldown. Now enforced: 30-minute cooldown after a successful
    // backup or restore so accidental double-taps and rapid-fire requests are blocked.
    private val _isBackupLocked = MutableStateFlow(false)
    val isBackupLocked: StateFlow<Boolean> = _isBackupLocked.asStateFlow()

    /** Epoch ms of the last successful backup or restore. */
    private var lastBackupOrRestoreAt: Long = 0L
    private val BACKUP_COOLDOWN_MS = 30 * 60 * 1_000L  // 30 minutes

    /** Returns how many minutes remain in the cooldown, or 0 if the cooldown has elapsed. */
    private fun cooldownMinutesRemaining(): Long {
        val elapsed = System.currentTimeMillis() - lastBackupOrRestoreAt
        val remaining = BACKUP_COOLDOWN_MS - elapsed
        return if (remaining > 0) TimeUnit.MILLISECONDS.toMinutes(remaining) + 1 else 0
    }

    fun backup() {
        // Guard: enforce cooldown so the user cannot spam cloud writes
        if (_isBackupLocked.value) {
            val mins = cooldownMinutesRemaining()
            _isError.value = false
            _message.value = if (mins > 0)
                "Please wait $mins more minute(s) before backing up again."
            else
                "Backup cooldown active. Please wait a moment."
            return
        }

        viewModelScope.launch {
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

                // Start cooldown after a successful backup
                lastBackupOrRestoreAt = System.currentTimeMillis()
                _isBackupLocked.value = true

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
        // Guard: enforce cooldown so the user cannot spam cloud reads/writes
        if (_isBackupLocked.value) {
            val mins = cooldownMinutesRemaining()
            _isError.value = false
            _message.value = if (mins > 0)
                "Please wait $mins more minute(s) before restoring again."
            else
                "Restore cooldown active. Please wait a moment."
            return
        }

        viewModelScope.launch {
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

                // Start cooldown after a successful restore
                lastBackupOrRestoreAt = System.currentTimeMillis()
                _isBackupLocked.value = true

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

    /**
     * Unlock backup/restore after the cooldown window has elapsed.
     * Call this from the UI whenever the cooldown timer expires
     * (e.g. re-entering the screen after 30 minutes).
     */
    fun checkAndClearCooldown() {
        if (_isBackupLocked.value && cooldownMinutesRemaining() == 0L) {
            _isBackupLocked.value = false
        }
    }
}
