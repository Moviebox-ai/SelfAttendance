package com.aaryo.selfattendance.ui.calendar

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.ads.InterstitialAdManager
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
    private val profileRepo: ProfileRepository       = ProfileRepository(),
    private val authRepo: AuthRepository             = AuthRepository()
) : ViewModel() {

    // ── Attendance map (date -> attendance) ───────────────────────────────
    private val _attendanceMap = MutableStateFlow<Map<String, Attendance>>(emptyMap())
    val attendanceMap: StateFlow<Map<String, Attendance>> = _attendanceMap.asStateFlow()

    // ── Selected month ────────────────────────────────────────────────────
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    // ── Profile cache ─────────────────────────────────────────────────────
    private var profile: UserProfile? = null

    // ── Selected date ─────────────────────────────────────────────────────
    var selectedDate: String? = null
        private set

    // ── Interstitial ad manager (lazy — only created when context is set) ─
    private var interstitialAdManager: InterstitialAdManager? = null

    init {
        loadProfileAndAttendance()
    }

    /**
     * Call once from the Composable (e.g. LaunchedEffect) to provide a Context
     * so the InterstitialAdManager can preload. Using applicationContext ensures
     * no Activity leak.
     */
    fun initAdManager(context: Context) {
        if (interstitialAdManager == null) {
            interstitialAdManager = InterstitialAdManager(context.applicationContext)
            interstitialAdManager?.preload()
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private fun loadProfileAndAttendance() {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            val result = profileRepo.getProfile(uid)
            profile = result.getOrNull()
            attendanceRepo.observeAttendance(uid).collect { list ->
                _attendanceMap.value = list.associateBy { it.date }
            }
        }
    }

    // ── Month control ─────────────────────────────────────────────────────

    fun setMonth(month: YearMonth)   { _selectedMonth.value = month }
    fun nextMonth()                   { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth()               { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    // ── Date selection ────────────────────────────────────────────────────

    fun selectDate(date: String) {
        val selected = LocalDate.parse(date)
        val today    = LocalDate.now()
        if (selected.isAfter(today)) return
        selectedDate = date
    }

    // ── Save attendance ───────────────────────────────────────────────────

    /**
     * Save or update attendance for the selected date.
     *
     * After a successful save that includes overtime, an interstitial ad
     * is shown (subject to frequency + cooldown policy). The save is NEVER
     * blocked or delayed by ad logic.
     *
     * @param activity         Required to show the interstitial. May be null on
     *                         edge cases (no-op for ad, save still proceeds).
     * @param onTodayMarked    Callback for shared-prefs / widget refresh.
     */
    fun saveAttendance(
        status: String,
        overtime: Double,
        activity: Activity? = null,
        onTodayMarked: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val uid  = authRepo.currentUser()?.uid ?: return@launch
            val date = selectedDate ?: return@launch

            val standardHours = profile?.standardHours ?: 8.0

            val workedHours = when (status) {
                "PRESENT"                   -> standardHours
                "HALF", "HALF_DAY"          -> standardHours / 2
                "ABSENT"                    -> 0.0
                else                        -> 0.0
            }

            val overtimeHours = if (status == "PRESENT") overtime.coerceAtLeast(0.0) else 0.0

            // BUG FIX: Normalize "HALF_DAY" → "HALF" to ensure a single canonical
            // status string is always saved. DashboardViewModel checks both, but
            // using a consistent value avoids subtle count bugs if the check ever
            // changes. CalendarViewModel is the write path, so normalizing here
            // guarantees clean data in Firestore.
            val normalizedStatus = if (status == "HALF_DAY") "HALF" else status

            // ✅ Save always succeeds regardless of ad state
            attendanceRepo.saveOrUpdateAttendance(
                uid,
                Attendance(
                    date          = date,
                    status        = normalizedStatus,
                    workedHours   = workedHours,
                    overtimeHours = overtimeHours
                )
            )

            // Notify caller if today was marked
            val todayStr = LocalDate.now().toString()
            if (date == todayStr) onTodayMarked?.invoke()

            // ✅ Show interstitial AFTER save, only if overtime was entered
            if (overtimeHours > 0.0 && activity != null) {
                interstitialAdManager?.showAfterOvertimeSave(activity, viewModelScope)
            }
        }
    }

    // ── Delete attendance ─────────────────────────────────────────────────

    fun deleteAttendance(date: String) {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            attendanceRepo.deleteAttendance(uid, date)
        }
    }
}
