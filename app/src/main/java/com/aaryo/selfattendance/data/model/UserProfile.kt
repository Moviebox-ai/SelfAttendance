package com.aaryo.selfattendance.data.model

data class UserProfile(

    val uid: String = "",

    // Unique AX ID (format: "AX-XXXXXXX", e.g. "AX-4567789") generated for user accounts.
    // Lets support/admins find this user's data on the server without
    // needing their long Firebase UID. See UniqueIdGenerator.
    val uniqueId: String = "",

    val name: String = "",

    val monthlySalary: Double = 0.0,

    val workingDays: Int = 0,

    val standardHours: Double = 0.0,

    val overtimeRate: Double = 0.0
)