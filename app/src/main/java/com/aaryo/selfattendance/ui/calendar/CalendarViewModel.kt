package com.aaryo.selfattendance.ui.calendar

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.review.InAppReviewManager
import com.aaryo.selfattendance.review.ReviewMilestone
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

    // ── Save error (null = no error) ──────────────────────────────────────
    // Now we surface it to the UI so the user knows attendance was NOT saved.
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    // ── Profile cache ─────────────────────────────────────────────────────
    private var profile: UserProfile? = null

    // ── Selected date ─────────────────────────────────────────────────────
    var selectedDate: String? = null
        private set

    init {
        loadProfileAndAttendance()
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private fun loadProfileAndAttendance() {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            // getProfile() would crash this coroutine silently, leaving
            // attendanceRepo.observeAttendance().collect() unreachable —
            // the calendar would show empty with no error feedback.
            try {
                val result = profileRepo.getProfile(uid)
                profile = result.getOrNull()
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Profile load failed", e)
                // Continue — attendance can still load even if profile fails
            }
            try {
                attendanceRepo.observeAttendance(uid).collect { list ->
                    _attendanceMap.value = list.associateBy { it.date }
                }
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Attendance observe failed", e)
            }
        }
    }

    // ── Month control ─────────────────────────────────────────────────────

    fun setMonth(month: YearMonth)   { _selectedMonth.value = month }
    fun nextMonth()                   { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth()               { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    // ── Date selection ────────────────────────────────────────────────────

    fun selectDate(date: String) {
        // date string — e.g. if CalendarScreen ever passes an unexpected format.
        // Wrap in try/catch so a bad value is silently ignored instead of crashing.
        val selected = try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            android.util.Log.e("CalendarViewModel", "selectDate: invalid date '$date'", e)
            return
        }
        val today = LocalDate.now()
        if (selected.isAfter(today)) return
        selectedDate = date
    }

    // ── Save attendance ───────────────────────────────────────────────────

    /**
     * Save or update attendance for the selected date.
     *
     * failure. We catch it here, set saveError so the UI can show a Snackbar,
     * and do NOT call onTodayMarked() — preventing the widget/prefs from
     * showing a false "marked today" state when Firestore actually rejected
     * the write.
     *
     * @param activity         Required to show the interstitial. May be null on
     *                         edge cases (no-op for ad, save still proceeds).
     * @param onTodayMarked    Callback for shared-prefs / widget refresh.
     *                         Only called on confirmed successful save.
     */
    fun saveAttendance(
        status: String,
        overtime: Double,
        dateOverride: String? = null,
        activity: Activity? = null,
        onTodayMarked: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val uid  = authRepo.currentUser()?.uid ?: return@launch
            val date = dateOverride ?: selectedDate ?: return@launch

            val standardHours = profile?.standardHours ?: 8.0

            val workedHours = when (status) {
                "PRESENT", "HOLIDAY"        -> standardHours
                "HALF", "HALF_DAY"          -> standardHours / 2
                "ABSENT"                    -> 0.0
                else                        -> 0.0
            }

            val overtimeHours = if (status == "PRESENT") overtime.coerceAtLeast(0.0) else 0.0

            // status string is always saved. DashboardViewModel checks both, but
            // using a consistent value avoids subtle count bugs if the check ever
            // changes. CalendarViewModel is the write path, so normalizing here
            // guarantees clean data in Firestore.
            val normalizedStatus = if (status == "HALF_DAY") "HALF" else status

            try {
                attendanceRepo.saveOrUpdateAttendance(
                    uid,
                    Attendance(
                        date          = date,
                        status        = normalizedStatus,
                        workedHours   = workedHours,
                        overtimeHours = overtimeHours
                    )
                )

                // Only notify caller + show ad on CONFIRMED success
                val todayStr = LocalDate.now().toString()
                if (date == todayStr) onTodayMarked?.invoke()

                // Show interstitial AFTER every confirmed save.
                // AdsController.showInterstitialAfterSave() is frequency-gated (every 3 saves)
                // and respects the global 60-second cooldown — fully AdMob-policy compliant.
                if (activity != null) {
                    AdsController.showInterstitialAfterSave(activity)
                    // Smart In-App Review check (gated to engagement milestones + 14-day cooldown)
                    InAppReviewManager.getInstance(activity).triggerSmartReviewIfAppropriate(
                        activity,
                        ReviewMilestone.ATTENDANCE_MARKED
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Attendance save failed", e)
                _saveError.value = "Attendance save failed. Please check your connection and try again."
            }
        }
    }

    /** Clear the error after the UI has displayed it (e.g. after Snackbar dismissal). */
    fun clearSaveError() {
        _saveError.value = null
    }

    // ── Delete attendance ─────────────────────────────────────────────────

    fun deleteAttendance(date: String) {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            try {
                attendanceRepo.deleteAttendance(uid, date)
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Attendance delete failed", e)
                // Reuse the existing saveError flow so the UI shows a Snackbar
                // instead of silently pretending the delete succeeded.
                _saveError.value = "Delete failed. Please check your connection and try again."
            }
        }
    }
}
