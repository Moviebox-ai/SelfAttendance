package com.aaryo.selfattendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aaryo.selfattendance.data.model.AttendanceRecord
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout

@Database(
    entities = [
        AttendanceRecord::class,
        Employee::class,
        StaffAttendance::class,
        StaffAdvance::class,
        StaffSalaryPayout::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao
    abstract fun staffDao(): StaffDao

    companion object {

        @Volatile
        private var INSTANCE: AttendanceDatabase? = null

        /**
         * Non-destructive migration from version 1 to version 2
         * Preserves existing personal attendance records completely.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create employees table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `employees` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `designation` TEXT NOT NULL,
                        `salaryType` TEXT NOT NULL,
                        `baseSalary` REAL NOT NULL,
                        `hourlyRate` REAL NOT NULL,
                        `overtimeRatePerHour` REAL NOT NULL,
                        `joiningDate` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_employees_phone` ON `employees` (`phone`)")

                // 2. Create staff_attendance table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `staff_attendance` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `employeeId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `checkInTime` TEXT,
                        `checkOutTime` TEXT,
                        `overtimeHours` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`employeeId`) REFERENCES `employees`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_attendance_employeeId_date` ON `staff_attendance` (`employeeId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_date` ON `staff_attendance` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_employeeId` ON `staff_attendance` (`employeeId`)")

                // 3. Create staff_advances table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `staff_advances` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `employeeId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `date` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `isDeducted` INTEGER NOT NULL,
                        `deductionMonthYear` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`employeeId`) REFERENCES `employees`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_advances_employeeId` ON `staff_advances` (`employeeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_advances_date` ON `staff_advances` (`date`)")

                // 4. Create staff_salary_payouts table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `staff_salary_payouts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `employeeId` INTEGER NOT NULL,
                        `monthYear` TEXT NOT NULL,
                        `totalPresentDays` REAL NOT NULL,
                        `totalHalfDays` REAL NOT NULL,
                        `totalAbsentDays` REAL NOT NULL,
                        `totalPaidLeaveDays` REAL NOT NULL,
                        `totalOvertimeHours` REAL NOT NULL,
                        `grossSalary` REAL NOT NULL,
                        `totalAdvancesDeducted` REAL NOT NULL,
                        `bonus` REAL NOT NULL,
                        `otherDeductions` REAL NOT NULL,
                        `netPayable` REAL NOT NULL,
                        `paidAmount` REAL NOT NULL,
                        `paymentStatus` TEXT NOT NULL,
                        `paymentDate` TEXT,
                        `paymentMode` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`employeeId`) REFERENCES `employees`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_salary_payouts_employeeId_monthYear` ON `staff_salary_payouts` (`employeeId`, `monthYear`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_salary_payouts_monthYear` ON `staff_salary_payouts` (`monthYear`)")
            }
        }

        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private const val DATABASE_NAME = "attendance_database"
    }
}
