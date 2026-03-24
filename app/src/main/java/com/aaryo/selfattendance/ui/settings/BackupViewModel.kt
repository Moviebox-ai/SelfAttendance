package com.aaryo.selfattendance.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel : ViewModel() {

    private val backupRepo = BackupRepository()
    private val attendanceRepo = AttendanceRepository()
    private val authRepo = AuthRepository()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    // Restore hone ke baad UI ko signal karo ki data reload kare
    private val _restoreCompleted = MutableStateFlow(false)
    val restoreCompleted: StateFlow<Boolean> = _restoreCompleted.asStateFlow()

    // ------------------------------------------------
    // BACKUP — Firestore attendance → backup collection
    // ------------------------------------------------

    fun backup() {

        viewModelScope.launch {

            _loading.value = true
            _message.value = null

            try {

                val uid = authRepo.currentUser()?.uid
                    ?: throw Exception("User not logged in")

                // Live Firestore se latest attendance fetch karo
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

    // ------------------------------------------------
    // RESTORE — backup collection → attendance collection
    // ------------------------------------------------

    fun restore() {

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

                _isError.value = false
                _message.value = "✓ Restore successful! ${restoredList.size} records restored."

                // UI ko signal karo ki refresh kare
                _restoreCompleted.value = true

            } catch (e: Exception) {

                _isError.value = true
                _message.value = "Restore failed: ${e.message}"

            } finally {

                _loading.value = false
            }
        }
    }

    // ------------------------------------------------
    // RESET — sare attendance records delete karo
    // ------------------------------------------------

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

    // ------------------------------------------------
    // CLEAR
    // ------------------------------------------------

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }

    fun clearRestoreFlag() {
        _restoreCompleted.value = false
    }
}
