package com.aaryo.selfattendance.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Attendance(

    // Date format: yyyy-MM-dd
    val date: String = "",

    // Status values: PRESENT, HALF, ABSENT
    val status: String = "NONE",

    // Total working hours
    val workedHours: Double = 0.0,

    // Extra overtime hours
    val overtimeHours: Double = 0.0,

    // Firestore auto timestamp
    @ServerTimestamp
    val updatedAt: Date? = null
) {

    // Helper functions (optional but useful)

    fun isPresent(): Boolean {
        return status == "PRESENT"
    }

    fun isHalfDay(): Boolean {
        return status == "HALF"
    }

    fun isAbsent(): Boolean {
        return status == "ABSENT"
    }

    fun hasOvertime(): Boolean {
        return overtimeHours > 0
    }
}