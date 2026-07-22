package com.aaryo.selfattendance.notifications

import android.content.Context

object ReminderPreferences {

    private const val PREF = "attendance_pref"
    private const val TIME = "reminder_time"

    fun saveTime(context: Context, time: Long) {

        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        pref.edit().putLong(TIME, time).apply()
    }

    fun getTime(context: Context): Long {

        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        return pref.getLong(TIME, 0)
    }
}