package com.aaryo.selfattendance.data.local

import androidx.room.*
import com.aaryo.selfattendance.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    /**
     * Insert attendance
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord)

    /**
     * Update attendance record
     */
    @Update
    suspend fun updateAttendance(record: AttendanceRecord)

    /**
     * Delete attendance record
     */
    @Delete
    suspend fun deleteAttendance(record: AttendanceRecord)

    /**
     * Get all attendance records (observe)
     */
    @Query(
        """
        SELECT * FROM attendance_records
        ORDER BY date DESC
        """
    )
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    /**
     * Get attendance for a specific date
     */
    @Query(
        """
        SELECT * FROM attendance_records
        WHERE date = :date
        LIMIT 1
        """
    )
    suspend fun getAttendanceByDate(date: String): AttendanceRecord?

    /**
     * Observe attendance for a specific date
     */
    @Query(
        """
        SELECT * FROM attendance_records
        WHERE date = :date
        LIMIT 1
        """
    )
    fun observeAttendanceByDate(date: String): Flow<AttendanceRecord?>

    /**
     * Delete attendance by date
     */
    @Query(
        """
        DELETE FROM attendance_records
        WHERE date = :date
        """
    )
    suspend fun deleteAttendanceByDate(date: String)

    /**
     * Clear all attendance records
     */
    @Query("DELETE FROM attendance_records")
    suspend fun clearAllAttendance()
}