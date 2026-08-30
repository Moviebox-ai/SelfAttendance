package com.aaryo.selfattendance.ui.salarycalculator

data class SalaryCalculatorState(
    // ── Inputs ──────────────────────────────────────────────────────────────
    val monthlySalaryInput: String = "",
    val presentDaysInput  : String = "",
    val halfDaysInput     : String = "",
    val absentDaysInput   : String = "",

    // ── Validation errors ───────────────────────────────────────────────────
    val salaryError      : String? = null,
    val presentDaysError : String? = null,
    val halfDaysError    : String? = null,
    val absentDaysError  : String? = null,

    // ── Calculated results (null = not yet calculated) ──────────────────────
    val perDaySalary    : Double? = null,   // Monthly ÷ 30
    val perHourSalary   : Double? = null,   // Per Day ÷ 8
    val halfDaySalary   : Double? = null,   // Per Day ÷ 2
    val totalDeduction  : Double? = null,   // (Absent × PerDay) + (Half × HalfDay)
    val finalSalary     : Double? = null,   // Monthly − Total Deduction
    val isCalculated    : Boolean = false
)
