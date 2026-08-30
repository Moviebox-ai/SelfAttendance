package com.aaryo.selfattendance.ui.salarycalculator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SalaryCalculatorViewModel : ViewModel() {

    private val _state = MutableStateFlow(SalaryCalculatorState())
    val state: StateFlow<SalaryCalculatorState> = _state.asStateFlow()

    // ── Input handlers ───────────────────────────────────────────────────────

    fun onMonthlySalaryChange(value: String) {
        _state.update { it.copy(monthlySalaryInput = value, salaryError = null, isCalculated = false) }
    }

    fun onPresentDaysChange(value: String) {
        _state.update { it.copy(presentDaysInput = value, presentDaysError = null, isCalculated = false) }
    }

    fun onHalfDaysChange(value: String) {
        _state.update { it.copy(halfDaysInput = value, halfDaysError = null, isCalculated = false) }
    }

    fun onAbsentDaysChange(value: String) {
        _state.update { it.copy(absentDaysInput = value, absentDaysError = null, isCalculated = false) }
    }

    // ── Calculate ────────────────────────────────────────────────────────────

    fun calculate() {
        val current = _state.value

        val salary      = validateSalary(current.monthlySalaryInput)           ?: return
        val presentDays = validateDays(current.presentDaysInput, "presentDays") ?: return
        val halfDays    = validateDays(current.halfDaysInput,    "halfDays")    ?: return
        val absentDays  = validateDays(current.absentDaysInput,  "absentDays")  ?: return

        val totalDays = presentDays + halfDays + absentDays
        if (totalDays > 30) {
            _state.update { it.copy(absentDaysError = "Total days (Present + Half + Absent) cannot exceed 30") }
            return
        }

        // ── Fixed 30-Day Deduction Formula ───────────────────────────────────
        //
        // Per Day Salary  = Monthly Salary ÷ 30
        //                   e.g. ₹18,000 ÷ 30 = ₹600/day
        //
        // Per Hour Salary = Per Day Salary ÷ 8  (8 working hours/day)
        //                   e.g. ₹600 ÷ 8 = ₹75/hour
        //
        // Half Day Salary = Per Day Salary ÷ 2
        //                   e.g. ₹600 ÷ 2 = ₹300
        //
        // Total Deduction = (Absent Days × Per Day) + (Half Days × Half Day)
        //
        // Final Salary    = Monthly Salary − Total Deduction
        //
        // Example: Monthly = ₹17,500
        //   Per Day  = ₹583.33 | Per Hour = ₹72.92 | Half Day = ₹291.67
        //   Absent=2, Half=1 → Deduction = (2×583.33)+(1×291.67) = ₹1,458.33
        //   Final = ₹17,500 − ₹1,458.33 = ₹16,041.67

        val perDay      = salary / 30.0
        val perHour     = perDay / 8.0
        val halfDay     = perDay / 2.0
        val deduction   = (absentDays * perDay) + (halfDays * halfDay)
        // the monthly salary (e.g. many absent days on a low salary). A negative net
        // salary is mathematically possible but produces a nonsensical negative display.
        // Clamped to 0.0 — the employee's take-home is never less than zero.
        val finalSalary = (salary - deduction).coerceAtLeast(0.0)

        _state.update {
            it.copy(
                perDaySalary     = perDay,
                perHourSalary    = perHour,
                halfDaySalary    = halfDay,
                totalDeduction   = deduction,
                finalSalary      = finalSalary,
                isCalculated     = true,
                salaryError      = null,
                presentDaysError = null,
                halfDaysError    = null,
                absentDaysError  = null
            )
        }
    }

    fun reset() { _state.value = SalaryCalculatorState() }

    // ── Validation helpers ───────────────────────────────────────────────────

    private fun validateSalary(input: String): Double? {
        if (input.isBlank()) {
            _state.update { it.copy(salaryError = "Monthly salary is required") }
            return null
        }
        val value = input.toDoubleOrNull()
        if (value == null || value <= 0) {
            _state.update { it.copy(salaryError = "Enter a valid salary amount") }
            return null
        }
        if (value > 10_000_000) {
            _state.update { it.copy(salaryError = "Salary seems too large") }
            return null
        }
        return value
    }

    private fun validateDays(input: String, field: String): Int? {
        if (input.isBlank()) {
            setDayError(field, "Required")
            return null
        }
        val value = input.toIntOrNull()
        if (value == null || value < 0) {
            setDayError(field, "Enter 0 or more")
            return null
        }
        if (value > 30) {
            setDayError(field, "Max 30 days")
            return null
        }
        return value
    }

    private fun setDayError(field: String, message: String) {
        _state.update {
            when (field) {
                "presentDays" -> it.copy(presentDaysError = message)
                "halfDays"    -> it.copy(halfDaysError    = message)
                "absentDays"  -> it.copy(absentDaysError  = message)
                else          -> it
            }
        }
    }
}
