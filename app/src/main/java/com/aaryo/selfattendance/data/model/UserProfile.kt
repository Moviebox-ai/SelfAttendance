package com.aaryo.selfattendance.data.model

data class UserProfile(

    val uid: String = "",

    val name: String = "",

    val monthlySalary: Double = 0.0,

    val workingDays: Int = 0,

    val standardHours: Double = 0.0,

    val overtimeRate: Double = 0.0
)