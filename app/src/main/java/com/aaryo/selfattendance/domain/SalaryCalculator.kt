package com.aaryo.selfattendance.domain

import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile

object SalaryCalculator {

    // Fixed 30-day month for ALL salary calculations
    // Per Day = Monthly Salary ÷ 30  (e.g. ₹18,000 ÷ 30 = ₹600/day)
    const val CALENDAR_DAYS = 30

    fun calculate(
        profile       : UserProfile,
        attendanceList: List<Attendance>,
        deductions    : Double = 0.0,
        bonus         : Double = 0.0
    ): Double {

        if (profile.monthlySalary == 0.0) return 0.0

        val presentDays   = attendanceList.count { it.status == "PRESENT" }
        val halfDays      = attendanceList.count { it.status == "HALF" || it.status == "HALF_DAY" }
        val totalOvertime = attendanceList.sumOf { it.overtimeHours }

        val perDay        = profile.monthlySalary / CALENDAR_DAYS.toDouble()
        val halfDaySalary = perDay / 2.0
        val overtimePay   = totalOvertime * profile.overtimeRate

        val earned =
            (presentDays * perDay) +
            (halfDays    * halfDaySalary)

        return earned + overtimePay + bonus - deductions
    }

    /**
     * Per day salary = Monthly ÷ 30 (always fixed 30-day basis — Indian HR standard).
     *
     * BUG FIX #9: [workingDays] parameter was never used in the calculation,
     * causing silent confusion: callers passed profile.workingDays but the
     * result was always monthlySalary / 30 regardless. Parameter is kept for
     * API compatibility but explicitly marked @Suppress so the intent is clear.
     * ProfileValidation allows up to 31 working days, but salary division
     * always uses the fixed 30-day HR standard — this is intentional design.
     */
    @Suppress("UNUSED_PARAMETER")
    fun perDaySalary(monthlySalary: Double, workingDays: Int = 0): Double =
        if (monthlySalary <= 0.0) 0.0
        else monthlySalary / CALENDAR_DAYS.toDouble()

    /**
     * Per hour salary (fixed 30-day basis).
     * See [perDaySalary] for note on [workingDays].
     */
    @Suppress("UNUSED_PARAMETER")
    fun perHourSalary(
        monthlySalary : Double,
        standardHours : Double,
        workingDays   : Int = 0
    ): Double {
        val hours = if (standardHours > 0.0) standardHours else 8.0
        return perDaySalary(monthlySalary) / hours
    }

    /**
     * Attendance earned percent (fixed 30-day basis).
     * BUG FIX: Result is now capped at 100.0 — previously could exceed 100%
     * if user had more than 30 attendance records in a month.
     * See [perDaySalary] for note on [workingDays].
     */
    @Suppress("UNUSED_PARAMETER")
    fun earnedPercent(attendanceList: List<Attendance>, workingDays: Int = 0): Double {
        val presentDays   = attendanceList.count { it.status == "PRESENT" }
        val halfDays      = attendanceList.count { it.status == "HALF" || it.status == "HALF_DAY" }
        val effectiveDays = presentDays + (halfDays * 0.5)
        // BUG FIX: minOf(..., 100.0) — prevents > 100% when records exceed 30 days
        return minOf((effectiveDays / CALENDAR_DAYS.toDouble()) * 100, 100.0)
    }
}
