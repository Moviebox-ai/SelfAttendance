package com.aaryo.selfattendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val absent: Int = 0,
    val overtime: Double = 0.0,
    val salary: Double = 0.0,
    val perDay: Double = 0.0,
    val perHour: Double = 0.0,
    val selectedMonth: YearMonth = YearMonth.now()
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

    init {
        observeDashboard()
    }

    fun setMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    // ---------------- MAIN OBSERVER ----------------

    private fun observeDashboard() {

        viewModelScope.launch {

            val user = authRepo.currentUser() ?: return@launch
            val uid = user.uid

            val profileResult = profileRepo.getProfile(uid)

            val profileRaw =
                profileResult.getOrNull() ?: UserProfile()

            // NAME CAPITAL FIX
            val profile =
                profileRaw.copy(
                    name = profileRaw.name
                        .trim()
                        .uppercase(Locale.getDefault())
                )

            combine(
                attendanceRepo.observeAttendance(uid),
                selectedMonth
            ) { logs, month ->

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
                    SalaryCalculator.perDaySalary(profile.monthlySalary)

                val perHour =
                    SalaryCalculator.perHourSalary(
                        profile.monthlySalary,
                        profile.standardHours
                    )

                DashboardState(
                    profile = profile,
                    present = present,
                    half = half,
                    absent = absent,
                    overtime = overtime,
                    salary = salary,
                    perDay = perDay,
                    perHour = perHour,
                    selectedMonth = month
                )

            }.collect {

                _state.value = it
            }
        }
    }
}