package com.aaryo.selfattendance.data.local

import androidx.room.*
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {

    // ───────────────── Employee CRUD ─────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteEmployeeById(id: Long)

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployeeById(id: Long): Employee?

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    fun observeEmployeeById(id: Long): Flow<Employee?>

    @Query("SELECT * FROM employees WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees ORDER BY isActive DESC, name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT COUNT(*) FROM employees WHERE isActive = 1")
    fun getActiveEmployeeCount(): Flow<Int>

    // ───────────────── Staff Attendance CRUD ─────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendance(attendance: StaffAttendance): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendanceList(list: List<StaffAttendance>)

    @Delete
    suspend fun deleteAttendance(attendance: StaffAttendance)

    @Query("SELECT * FROM staff_attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<StaffAttendance>>

    @Query("SELECT * FROM staff_attendance WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getAttendanceForEmployeeOnDate(employeeId: Long, date: String): StaffAttendance?

    @Query("SELECT * FROM staff_attendance WHERE employeeId = :employeeId AND date LIKE :monthPrefix || '%' ORDER BY date ASC")
    fun getAttendanceForEmployeeMonth(employeeId: Long, monthPrefix: String): Flow<List<StaffAttendance>>

    @Query("SELECT * FROM staff_attendance WHERE date LIKE :monthPrefix || '%'")
    fun getAllAttendanceForMonth(monthPrefix: String): Flow<List<StaffAttendance>>

    @Query("DELETE FROM staff_attendance WHERE employeeId = :employeeId AND date = :date")
    suspend fun deleteAttendanceRecord(employeeId: Long, date: String)

    // ───────────────── Staff Advances CRUD ─────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdvance(advance: StaffAdvance): Long

    @Update
    suspend fun updateAdvance(advance: StaffAdvance)

    @Delete
    suspend fun deleteAdvance(advance: StaffAdvance)

    @Query("SELECT * FROM staff_advances WHERE employeeId = :employeeId ORDER BY date DESC, id DESC")
    fun getAdvancesForEmployee(employeeId: Long): Flow<List<StaffAdvance>>

    @Query("SELECT * FROM staff_advances WHERE employeeId = :employeeId AND isDeducted = 0 ORDER BY date ASC")
    suspend fun getPendingAdvancesForEmployee(employeeId: Long): List<StaffAdvance>

    @Query("SELECT * FROM staff_advances ORDER BY date DESC, id DESC")
    fun getAllAdvances(): Flow<List<StaffAdvance>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM staff_advances WHERE isDeducted = 0")
    fun getTotalPendingAdvances(): Flow<Double>

    @Query("UPDATE staff_advances SET isDeducted = :isDeducted, deductionMonthYear = :monthYear WHERE id = :advanceId")
    suspend fun updateAdvanceDeductionStatus(advanceId: Long, isDeducted: Boolean, monthYear: String?)

    // ───────────────── Salary Payout CRUD ─────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePayout(payout: StaffSalaryPayout): Long

    @Update
    suspend fun updatePayout(payout: StaffSalaryPayout)

    @Query("SELECT * FROM staff_salary_payouts WHERE employeeId = :employeeId AND monthYear = :monthYear LIMIT 1")
    suspend fun getPayoutForEmployeeMonth(employeeId: Long, monthYear: String): StaffSalaryPayout?

    @Query("SELECT * FROM staff_salary_payouts WHERE employeeId = :employeeId AND monthYear = :monthYear LIMIT 1")
    fun observePayoutForEmployeeMonth(employeeId: Long, monthYear: String): Flow<StaffSalaryPayout?>

    @Query("SELECT * FROM staff_salary_payouts WHERE monthYear = :monthYear")
    fun getPayoutsForMonth(monthYear: String): Flow<List<StaffSalaryPayout>>

    @Query("DELETE FROM staff_salary_payouts WHERE employeeId = :employeeId AND monthYear = :monthYear")
    suspend fun deletePayout(employeeId: Long, monthYear: String)
}
