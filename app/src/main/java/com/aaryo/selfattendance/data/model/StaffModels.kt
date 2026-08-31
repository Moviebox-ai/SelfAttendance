package com.aaryo.selfattendance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Employee / Staff Entity
 */
@Entity(
    tableName = "employees",
    indices = [Index(value = ["phone"])]
)
data class Employee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val designation: String = "Staff",
    val salaryType: String = SALARY_TYPE_MONTHLY, // MONTHLY, DAILY_WAGE, HOURLY
    val baseSalary: Double = 0.0,
    val hourlyRate: Double = 0.0,
    val overtimeRatePerHour: Double = 0.0,
    val shiftName: String = "Day Shift (9AM - 6PM)",
    val shiftStartTime: String = "09:00",
    val shiftEndTime: String = "18:00",
    val standardShiftHours: Double = 8.0,
    val fixedAllowance: Double = 0.0,
    val pfDeduction: Double = 0.0,
    val esiDeduction: Double = 0.0,
    val joiningDate: String = "",
    val isActive: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SALARY_TYPE_MONTHLY = "MONTHLY"
        const val SALARY_TYPE_DAILY = "DAILY_WAGE"
        const val SALARY_TYPE_HOURLY = "HOURLY"
    }
}

/**
 * Daily Staff Attendance Record
 */
@Entity(
    tableName = "staff_attendance",
    indices = [
        Index(value = ["employeeId", "date"], unique = true),
        Index(value = ["date"]),
        Index(value = ["employeeId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Employee::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StaffAttendance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val employeeId: Long,
    val date: String, // YYYY-MM-DD
    val status: String, // PRESENT, ABSENT, HALF_DAY, OVERTIME, PAID_LEAVE
    val shiftName: String = "General",
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val overtimeHours: Double = 0.0,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PRESENT = "PRESENT"
        const val STATUS_ABSENT = "ABSENT"
        const val STATUS_HALF_DAY = "HALF_DAY"
        const val STATUS_OVERTIME = "OVERTIME"
        const val STATUS_PAID_LEAVE = "PAID_LEAVE"
    }
}

/**
 * Staff Advance / Loan Ledger
 */
@Entity(
    tableName = "staff_advances",
    indices = [
        Index(value = ["employeeId"]),
        Index(value = ["date"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Employee::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StaffAdvance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val employeeId: Long,
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val reason: String = "Advance",
    val isDeducted: Boolean = false,
    val deductionMonthYear: String? = null, // YYYY-MM when settled
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Monthly Staff Salary Payout
 */
@Entity(
    tableName = "staff_salary_payouts",
    indices = [
        Index(value = ["employeeId", "monthYear"], unique = true),
        Index(value = ["monthYear"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Employee::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StaffSalaryPayout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val employeeId: Long,
    val monthYear: String, // YYYY-MM
    val totalPresentDays: Double = 0.0,
    val totalHalfDays: Double = 0.0,
    val totalAbsentDays: Double = 0.0,
    val totalPaidLeaveDays: Double = 0.0,
    val totalOvertimeHours: Double = 0.0,
    val totalOvertimePay: Double = 0.0,
    val allowance: Double = 0.0,
    val grossSalary: Double = 0.0,
    val totalAdvancesDeducted: Double = 0.0,
    val bonus: Double = 0.0,
    val otherDeductions: Double = 0.0,
    val netPayable: Double = 0.0,
    val paidAmount: Double = 0.0,
    val paymentStatus: String = STATUS_PENDING, // PAID, PARTIAL, PENDING
    val paymentDate: String? = null,
    val paymentMode: String = "CASH", // CASH, UPI, BANK_TRANSFER
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PARTIAL = "PARTIAL"
        const val STATUS_PAID = "PAID"
    }
}
