package com.aaryo.selfattendance.data.repository

import android.content.Context
import com.aaryo.selfattendance.data.local.AttendanceDatabase
import com.aaryo.selfattendance.data.local.StaffDao
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class StaffRepository(context: Context) {

    private val staffDao: StaffDao = AttendanceDatabase.getDatabase(context).staffDao()

    // ───────────────── Staff Management ─────────────────

    fun getAllActiveEmployees(): Flow<List<Employee>> = staffDao.getAllActiveEmployees()

    fun getAllEmployees(): Flow<List<Employee>> = staffDao.getAllEmployees()

    fun observeEmployeeById(id: Long): Flow<Employee?> = staffDao.observeEmployeeById(id)

    suspend fun getEmployeeById(id: Long): Employee? = withContext(Dispatchers.IO) {
        staffDao.getEmployeeById(id)
    }

    suspend fun saveEmployee(employee: Employee): Long = withContext(Dispatchers.IO) {
        if (employee.id == 0L) {
            staffDao.insertEmployee(employee)
        } else {
            staffDao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(id: Long) = withContext(Dispatchers.IO) {
        staffDao.deleteEmployeeById(id)
    }

    // ───────────────── Staff Attendance ─────────────────

    fun getAttendanceForDate(date: String): Flow<List<StaffAttendance>> =
        staffDao.getAttendanceForDate(date)

    fun getAttendanceForEmployeeMonth(employeeId: Long, monthPrefix: String): Flow<List<StaffAttendance>> =
        staffDao.getAttendanceForEmployeeMonth(employeeId, monthPrefix)

    fun getAllAttendanceForMonth(monthPrefix: String): Flow<List<StaffAttendance>> =
        staffDao.getAllAttendanceForMonth(monthPrefix)

    suspend fun markAttendance(
        employeeId: Long,
        date: String,
        status: String,
        shiftName: String = "Day Shift",
        checkInTime: String? = null,
        checkOutTime: String? = null,
        overtimeHours: Double = 0.0,
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val record = StaffAttendance(
            employeeId = employeeId,
            date = date,
            status = status,
            shiftName = shiftName,
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            overtimeHours = overtimeHours,
            notes = notes,
            updatedAt = System.currentTimeMillis()
        )
        staffDao.insertOrUpdateAttendance(record)
    }

    suspend fun markBulkAttendance(
        employeeIds: List<Long>,
        date: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        val employees = staffDao.getAllEmployees().firstOrNull() ?: emptyList()
        val empMap = employees.associateBy { it.id }

        val records = employeeIds.map { empId ->
            val emp = empMap[empId]
            StaffAttendance(
                employeeId = empId,
                date = date,
                status = status,
                shiftName = emp?.shiftName ?: "Day Shift",
                checkInTime = emp?.shiftStartTime,
                checkOutTime = emp?.shiftEndTime,
                updatedAt = System.currentTimeMillis()
            )
        }
        staffDao.insertOrUpdateAttendanceList(records)
    }

    suspend fun deleteAttendance(employeeId: Long, date: String) = withContext(Dispatchers.IO) {
        staffDao.deleteAttendanceRecord(employeeId, date)
    }

    // ───────────────── Advance Khata ─────────────────

    fun getAllAdvances(): Flow<List<StaffAdvance>> = staffDao.getAllAdvances()

    fun getAdvancesForEmployee(employeeId: Long): Flow<List<StaffAdvance>> =
        staffDao.getAdvancesForEmployee(employeeId)

    fun getTotalPendingAdvances(): Flow<Double> = staffDao.getTotalPendingAdvances()

    suspend fun addAdvance(
        employeeId: Long,
        amount: Double,
        date: String,
        reason: String
    ): Long = withContext(Dispatchers.IO) {
        val advance = StaffAdvance(
            employeeId = employeeId,
            amount = amount,
            date = date,
            reason = reason,
            isDeducted = false,
            createdAt = System.currentTimeMillis()
        )
        staffDao.insertAdvance(advance)
    }

    suspend fun toggleAdvanceDeduction(advanceId: Long, isDeducted: Boolean, monthYear: String?) =
        withContext(Dispatchers.IO) {
            staffDao.updateAdvanceDeductionStatus(advanceId, isDeducted, monthYear)
        }

    suspend fun deleteAdvance(advance: StaffAdvance) = withContext(Dispatchers.IO) {
        staffDao.deleteAdvance(advance)
    }

    // ───────────────── Salary Calculation & Payout ─────────────────

    fun getPayoutsForMonth(monthYear: String): Flow<List<StaffSalaryPayout>> =
        staffDao.getPayoutsForMonth(monthYear)

    fun observePayoutForEmployeeMonth(employeeId: Long, monthYear: String): Flow<StaffSalaryPayout?> =
        staffDao.observePayoutForEmployeeMonth(employeeId, monthYear)

    /**
     * Calculates payroll breakdown for a given employee and month.
     * Takes into account:
     * - Salary type: Monthly / Daily / Hourly
     * - Standard shift hours, present days, half days (0.5), overtime hours & OT rate
     * - Fixed & one-off allowances (food/travel), bonuses, statutory deductions (PF/ESI), outstanding advances
     */
    suspend fun calculateEmployeeMonthlySalary(
        employeeId: Long,
        monthYear: String // YYYY-MM
    ): StaffSalaryPayout = withContext(Dispatchers.IO) {
        val employee = staffDao.getEmployeeById(employeeId)
            ?: throw IllegalArgumentException("Employee not found")

        val attendanceList = staffDao.getAttendanceForEmployeeMonth(employeeId, monthYear).firstOrNull() ?: emptyList()
        val advances = staffDao.getPendingAdvancesForEmployee(employeeId)

        var presentDays = 0.0
        var halfDays = 0.0
        var absentDays = 0.0
        var paidLeaveDays = 0.0
        var totalOvertimeHours = 0.0

        attendanceList.forEach { record ->
            when (record.status) {
                StaffAttendance.STATUS_PRESENT -> presentDays += 1.0
                StaffAttendance.STATUS_HALF_DAY -> halfDays += 1.0
                StaffAttendance.STATUS_ABSENT -> absentDays += 1.0
                StaffAttendance.STATUS_PAID_LEAVE -> paidLeaveDays += 1.0
                StaffAttendance.STATUS_OVERTIME -> {
                    presentDays += 1.0
                    totalOvertimeHours += record.overtimeHours
                }
            }
            if (record.overtimeHours > 0 && record.status != StaffAttendance.STATUS_OVERTIME) {
                totalOvertimeHours += record.overtimeHours
            }
        }

        val ym = runCatching { YearMonth.parse(monthYear) }.getOrDefault(YearMonth.now())
        val daysInMonth = ym.lengthOfMonth().toDouble().coerceAtLeast(1.0)
        val shiftHours = if (employee.standardShiftHours > 0) employee.standardShiftHours else 8.0

        // Base earnings
        val baseEarned: Double = when (employee.salaryType) {
            Employee.SALARY_TYPE_MONTHLY -> {
                val dailyRate = employee.baseSalary / daysInMonth
                val workedDaysEquivalent = presentDays + (halfDays * 0.5) + paidLeaveDays
                workedDaysEquivalent * dailyRate
            }
            Employee.SALARY_TYPE_DAILY -> {
                val workedDaysEquivalent = presentDays + (halfDays * 0.5) + paidLeaveDays
                workedDaysEquivalent * employee.baseSalary
            }
            Employee.SALARY_TYPE_HOURLY -> {
                val regularEarned = (presentDays * shiftHours + halfDays * (shiftHours / 2.0)) * employee.hourlyRate
                regularEarned
            }
            else -> employee.baseSalary
        }

        // Overtime Calculation
        val effectiveOtRate = when {
            employee.overtimeRatePerHour > 0 -> employee.overtimeRatePerHour
            employee.salaryType == Employee.SALARY_TYPE_HOURLY && employee.hourlyRate > 0 -> employee.hourlyRate * 1.5
            employee.salaryType == Employee.SALARY_TYPE_DAILY && employee.baseSalary > 0 -> (employee.baseSalary / shiftHours) * 1.25
            employee.salaryType == Employee.SALARY_TYPE_MONTHLY && employee.baseSalary > 0 -> (employee.baseSalary / (daysInMonth * shiftHours)) * 1.25
            else -> 0.0
        }
        val totalOvertimePay = totalOvertimeHours * effectiveOtRate

        val existingPayout = staffDao.getPayoutForEmployeeMonth(employeeId, monthYear)

        // Allowances: default to fixed allowance on employee profile, unless customized in payout
        val allowance = existingPayout?.allowance ?: employee.fixedAllowance

        // Gross salary = Base Earned + OT Pay + Allowances
        val grossSalary = baseEarned + totalOvertimePay + allowance

        // Bonuses
        val bonus = existingPayout?.bonus ?: 0.0

        // Deductions: advances + PF/ESI default + other deductions
        val totalAdvances = advances.sumOf { it.amount }
        val defaultStatutoryDeductions = employee.pfDeduction + employee.esiDeduction
        val otherDeductions = existingPayout?.otherDeductions ?: defaultStatutoryDeductions

        val paidAmount = existingPayout?.paidAmount ?: 0.0
        val netPayable = (grossSalary + bonus - totalAdvances - otherDeductions).coerceAtLeast(0.0)

        val paymentStatus = when {
            paidAmount >= netPayable && netPayable > 0 -> StaffSalaryPayout.STATUS_PAID
            paidAmount > 0 -> StaffSalaryPayout.STATUS_PARTIAL
            else -> StaffSalaryPayout.STATUS_PENDING
        }

        StaffSalaryPayout(
            id = existingPayout?.id ?: 0L,
            employeeId = employeeId,
            monthYear = monthYear,
            totalPresentDays = presentDays,
            totalHalfDays = halfDays,
            totalAbsentDays = absentDays,
            totalPaidLeaveDays = paidLeaveDays,
            totalOvertimeHours = totalOvertimeHours,
            totalOvertimePay = Math.round(totalOvertimePay * 100.0) / 100.0,
            allowance = Math.round(allowance * 100.0) / 100.0,
            grossSalary = Math.round(grossSalary * 100.0) / 100.0,
            totalAdvancesDeducted = totalAdvances,
            bonus = Math.round(bonus * 100.0) / 100.0,
            otherDeductions = Math.round(otherDeductions * 100.0) / 100.0,
            netPayable = Math.round(netPayable * 100.0) / 100.0,
            paidAmount = paidAmount,
            paymentStatus = paymentStatus,
            paymentDate = existingPayout?.paymentDate,
            paymentMode = existingPayout?.paymentMode ?: "CASH",
            notes = existingPayout?.notes ?: "",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveSalaryPayout(payout: StaffSalaryPayout): Long = withContext(Dispatchers.IO) {
        val id = staffDao.insertOrUpdatePayout(payout)
        if (payout.paymentStatus == StaffSalaryPayout.STATUS_PAID && payout.totalAdvancesDeducted > 0) {
            // Mark pending advances as deducted for this month
            val pending = staffDao.getPendingAdvancesForEmployee(payout.employeeId)
            pending.forEach { adv ->
                staffDao.updateAdvanceDeductionStatus(adv.id, true, payout.monthYear)
            }
        }
        id
    }
}
