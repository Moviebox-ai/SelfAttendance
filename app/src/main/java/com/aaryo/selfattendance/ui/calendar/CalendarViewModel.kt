package com.aaryo.selfattendance.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(
    private val attendanceRepo: AttendanceRepository = AttendanceRepository(),
    private val profileRepo: ProfileRepository = ProfileRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    // Attendance map (date -> attendance)

    private val _attendanceMap =
        MutableStateFlow<Map<String, Attendance>>(emptyMap())

    val attendanceMap: StateFlow<Map<String, Attendance>> =
        _attendanceMap.asStateFlow()


    // Selected month

    private val _selectedMonth =
        MutableStateFlow(YearMonth.now())

    val selectedMonth: StateFlow<YearMonth> =
        _selectedMonth.asStateFlow()


    // Profile cache

    private var profile: UserProfile? = null


    // Selected date

    var selectedDate: String? = null
        private set


    init {
        loadProfileAndAttendance()
    }

    // ------------------------------------------------
    // Load profile + attendance stream
    // ------------------------------------------------

    private fun loadProfileAndAttendance() {

        viewModelScope.launch {

            val uid = authRepo.currentUser()?.uid
                ?: return@launch

            val result = profileRepo.getProfile(uid)

            profile = result.getOrNull()

            attendanceRepo.observeAttendance(uid)
                .collect { list ->

                    _attendanceMap.value =
                        list.associateBy { it.date }
                }
        }
    }

    // ------------------------------------------------
    // Month control
    // ------------------------------------------------

    fun setMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun nextMonth() {
        _selectedMonth.value =
            _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value =
            _selectedMonth.value.minusMonths(1)
    }

    // ------------------------------------------------
    // Date selection
    // ------------------------------------------------

    fun selectDate(date: String) {

        val selected = LocalDate.parse(date)
        val today = LocalDate.now()

        if (selected.isAfter(today)) return

        selectedDate = date
    }

    // ------------------------------------------------
    // Save attendance
    // ------------------------------------------------

    fun saveAttendance(
        status: String,
        overtime: Double,
        onTodayMarked: (() -> Unit)? = null
    ) {

        viewModelScope.launch {

            val uid = authRepo.currentUser()?.uid
                ?: return@launch

            val date = selectedDate ?: return@launch

            val standardHours =
                profile?.standardHours ?: 8.0

            val workedHours = when (status) {

                "PRESENT" -> standardHours

                "HALF", "HALF_DAY" -> standardHours / 2

                "ABSENT" -> 0.0

                else -> 0.0
            }

            val overtimeHours =
                if (status == "PRESENT")
                    overtime.coerceAtLeast(0.0)
                else
                    0.0

            attendanceRepo.saveOrUpdateAttendance(
                uid,
                Attendance(
                    date = date,
                    status = status,
                    workedHours = workedHours,
                    overtimeHours = overtimeHours
                )
            )

            // Notify caller if today's attendance was marked (for SharedPrefs update)
            val todayStr = LocalDate.now().toString()
            if (date == todayStr) {
                onTodayMarked?.invoke()
            }
        }
    }

    // ------------------------------------------------
    // Delete attendance
    // ------------------------------------------------

    fun deleteAttendance(date: String) {

        viewModelScope.launch {

            val uid = authRepo.currentUser()?.uid
                ?: return@launch

            attendanceRepo.deleteAttendance(
                uid,
                date
            )
        }
    }
}
