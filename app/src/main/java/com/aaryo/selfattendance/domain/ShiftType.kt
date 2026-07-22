package com.aaryo.selfattendance.domain

/**
 * ShiftType — defines the three standard work shifts.
 * Stored as a string key in PreferencesManager.
 */
enum class ShiftType(
    val key: String,
    val displayName: String,
    val emoji: String,
    val startHour: Int,
    val endHour: Int
) {
    MORNING("morning", "Morning Shift", "🌅", 6, 14),
    EVENING("evening", "Evening Shift", "🌆", 14, 22),
    NIGHT("night",   "Night Shift",   "🌙", 22, 6);

    companion object {
        fun fromKey(key: String): ShiftType =
            values().firstOrNull { it.key == key } ?: MORNING
    }
}
