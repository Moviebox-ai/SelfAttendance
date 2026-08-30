package com.aaryo.selfattendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.domain.SalaryCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

// ---------------- STATE ----------------

data class DashboardState(
    val profile: UserProfile = UserProfile(),
    val present: Int = 0,
    val half: Int = 0,
    val holiday: Int = 0,
    val absent: Int = 0,
    val overtime: Double = 0.0,
    val salary: Double = 0.0,
    val perDay: Double = 0.0,
    val perHour: Double = 0.0,
    val selectedMonth: YearMonth = YearMonth.now(),
    val attendanceRecords: List<Attendance> = emptyList()
)

// ---------------- VIEWMODEL ----------------

class DashboardViewModel(
    private val profileRepo: ProfileRepository = ProfileRepository(),
    private val attendanceRepo: AttendanceRepository = AttendanceRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val selectedMonth =
        MutableStateFlow(YearMonth.now())

    private val _state =
        MutableStateFlow(DashboardState())

    val state: StateFlow<DashboardState> =
        _state.asStateFlow()

    // Taki refresh() call hone par dashboard automatically update ho
    private val _profile = MutableStateFlow(UserProfile())

    init {
        observeDashboard()
    }

    fun setMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    // (MainActivity ya NavBackStackEntry ke saath)
    fun refresh() {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            val result = profileRepo.getProfile(uid)
            val profileRaw = result.getOrNull() ?: UserProfile()
            _profile.value = profileRaw.copy(
                name = profileRaw.name.trim().uppercase(Locale.getDefault())
            )
        }
    }

    // ---------------- MAIN OBSERVER ----------------

    private fun observeDashboard() {

        viewModelScope.launch {

            val user = authRepo.currentUser() ?: return@launch
            val uid = user.uid

            // Previously refresh() launched a separate coroutine that could
            // complete AFTER combine() emitted its first value, causing the
            // dashboard to briefly show ₹0 salary / empty name on every launch.
            runCatching {
                val result = profileRepo.getProfile(uid)
                val profileRaw = result.getOrNull() ?: UserProfile()
                _profile.value = profileRaw.copy(
                    name = profileRaw.name.trim().uppercase(Locale.getDefault())
                )
            }

            // Ab jab bhi refresh() call hoga, _profile update hoga
            // aur dashboard automatically recalculate karega
            combine(
                attendanceRepo.observeAttendance(uid),
                selectedMonth,
                _profile
            ) { logs, month, profile ->

                val monthlyLogs = logs.filter { log ->

                    val parsedDate =
                        runCatching {
                            LocalDate.parse(log.date)
                        }.getOrNull()

                    parsedDate != null &&
                            YearMonth.from(parsedDate) == month
                }

                val present =
                    monthlyLogs.count {
                        it.status == "PRESENT"
                    }

                val half =
                    monthlyLogs.count {
                        it.status == "HALF" ||
                        it.status == "HALF_DAY"
                    }

                val holiday =
                    monthlyLogs.count {
                        it.status == "HOLIDAY"
                    }

                val absent =
                    monthlyLogs.count {
                        it.status == "ABSENT"
                    }

                val overtime =
                    monthlyLogs.sumOf {
                        it.overtimeHours
                    }

                val salary =
                    runCatching {
                        SalaryCalculator.calculate(
                            profile,
                            monthlyLogs
                        )
                    }.getOrDefault(0.0)

                val perDay =
                    SalaryCalculator.perDaySalary(profile.monthlySalary, profile.workingDays)

                val perHour =
                    SalaryCalculator.perHourSalary(
                        profile.monthlySalary,
                        profile.standardHours,
                        profile.workingDays
                    )

                DashboardState(
                    profile = profile,
                    present = present,
                    half = half,
                    holiday = holiday,
                    absent = absent,
                    overtime = overtime,
                    salary = salary,
                    perDay = perDay,
                    perHour = perHour,
                    selectedMonth = month,
                    attendanceRecords = logs
                )

            }.collect {

                _state.value = it
            }
        }
    }
}
