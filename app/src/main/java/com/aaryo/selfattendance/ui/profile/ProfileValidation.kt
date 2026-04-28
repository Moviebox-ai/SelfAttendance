package com.aaryo.selfattendance.ui.profile

// ─────────────────────────────────────────────────────────────────
//  Shared validation constants & function used by both
//  SetupProfileScreen and EditProfileScreen.
//  FIX: Moved out of EditProfileScreen.kt (where they were private)
//  so SetupProfileScreen.kt can access them without compile errors.
//
//  NOTE: Per Day & Per Hour calculations use the 30-days formula:
//    perDay  = monthlySalary / 30
//    perHour = perDay / standardHours
//  This is the HR/Payroll standard used across India for LWP,
//  joining date, and relieving date calculations.
// ─────────────────────────────────────────────────────────────────

internal const val OVERTIME_MULTIPLIER = 1.5

internal const val MIN_SALARY       = 1_000.0
internal const val MAX_SALARY       = 10_000_000.0
internal const val MIN_WORKING_DAYS = 1
internal const val MAX_WORKING_DAYS = 31
internal const val MIN_HOURS        = 1.0
internal const val MAX_HOURS        = 24.0

internal fun validateInputs(
    name       : String,
    salary     : String,
    workingDays: String,
    hours      : String,
    overtime   : String
): String? {
    if (name.isBlank()) return "Name cannot be empty"

    val s = salary.toDoubleOrNull()
        ?: return "Enter a valid salary"
    if (s < MIN_SALARY || s > MAX_SALARY)
        return "Salary must be between ₹${MIN_SALARY.toLong()} and ₹${MAX_SALARY.toLong()}"

    val d = workingDays.toIntOrNull()
        ?: return "Enter valid working days"
    if (d < MIN_WORKING_DAYS || d > MAX_WORKING_DAYS)
        return "Working days must be between $MIN_WORKING_DAYS and $MAX_WORKING_DAYS"

    val h = hours.toDoubleOrNull()
        ?: return "Enter valid hours per day"
    if (h < MIN_HOURS || h > MAX_HOURS)
        return "Hours per day must be between $MIN_HOURS and $MAX_HOURS"

    val o = overtime.toDoubleOrNull()
        ?: return "Enter a valid overtime rate"
    if (o < 0) return "Overtime rate cannot be negative"

    return null
}