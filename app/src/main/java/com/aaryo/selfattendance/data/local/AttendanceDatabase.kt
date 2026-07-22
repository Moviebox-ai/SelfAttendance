package com.aaryo.selfattendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aaryo.selfattendance.data.model.AttendanceRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [AttendanceRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao

    companion object {

        @Volatile
        private var INSTANCE: AttendanceDatabase? = null

        fun getDatabase(context: Context): AttendanceDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private const val DATABASE_NAME = "attendance_database"
    }

    /**
     * Optional database callback
     * Used for pre-populating data or logging
     */
    private class DatabaseCallback : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            // Example background initialization if needed
            CoroutineScope(Dispatchers.IO).launch {
                // Pre-populate database if required
            }
        }
    }
}