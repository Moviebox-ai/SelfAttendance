package com.aaryo.selfattendance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String,

    val checkInTime: String,

    val checkOutTime: String? = null,

    val status: String
)