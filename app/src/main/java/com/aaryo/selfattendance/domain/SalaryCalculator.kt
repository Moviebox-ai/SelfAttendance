package com.aaryo.selfattendance.domain

import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile

object SalaryCalculator {

    // FIX: Changed private → internal so EditProfileScreen and
    // SetupProfileScreen can reference SalaryCalculator.CALENDAR_DAYS
    // without a "Cannot access 'CALENDAR_DAYS': it is private" error.
    internal const val CALENDAR_DAYS = 30

    fun calculate(
        profile       : UserProfile,
        attendanceList: List<Attendance>,
        deductions    : Double = 0.0,
        bonus         : Double = 0.0
    ): Double {

        val presentDays  = attendanceList.count { it.status == "PRESENT" }
        val halfDays     = attendanceList.count { it.status == "HALF" }
        val totalOvertime = attendanceList.sumOf { it.overtimeHours }

        if (profile.monthlySalary == 0.0) return 0.0

        val perDaySalary = profile.monthlySalary / CALENDAR_DAYS
        val halfDaySalary = perDaySalary / 2
        val overtimePay  = totalOvertime * profile.overtimeRate

        val baseSalary =
            (presentDays * perDaySalary) +
            (halfDays    * halfDaySalary)

        return baseSalary + overtimePay + bonus - deductions
    }

    /**
     * Per day salary using 30-days formula:
     *   perDay = monthlySalary / 30
     */
    fun perDaySalary(monthlySalary: Double): Double =
        if (monthlySalary <= 0.0) 0.0
        else monthlySalary / CALENDAR_DAYS

    /**
     * Per hour salary using 30-days formula:
     *   perHour = monthlySalary / 30 / standardHours
     * Falls back to 8 hours if standardHours is not set.
     */
    fun perHourSalary(monthlySalary: Double, standardHours: Double): Double {
        val hours = if (standardHours > 0.0) standardHours else 8.0
        return perDaySalary(monthlySalary) / hours
    }

    fun earnedPercent(attendanceList: List<Attendance>): Double {
        val presentDays   = attendanceList.count { it.status == "PRESENT" }
        val halfDays      = attendanceList.count { it.status == "HALF" }
        val effectiveDays = presentDays + (halfDays * 0.5)
        return (effectiveDays / CALENDAR_DAYS) * 100
    }
}