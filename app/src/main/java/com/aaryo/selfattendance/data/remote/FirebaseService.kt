package com.aaryo.selfattendance.data.remote

import android.util.Log
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Low-level Firebase Firestore operations.
 * Repositories use this service internally.
 */
object FirebaseService {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "FirebaseService"

    // ----------------------------------------------------------------
    // USER PROFILE
    // ----------------------------------------------------------------

    suspend fun saveUserProfile(uid: String, profile: UserProfile) {
        try {
            db.collection("users")
                .document(uid)
                .set(profile)
                .await()
            Log.d(TAG, "Profile saved: $uid")
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile failed", e)
            throw e
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val doc = db.collection("users")
                .document(uid)
                .get()
                .await()
            doc.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile failed", e)
            null
        }
    }

    // ----------------------------------------------------------------
    // ATTENDANCE
    // ----------------------------------------------------------------

    suspend fun saveAttendance(uid: String, attendance: Attendance) {
        try {
            val data = hashMapOf(
                "date"          to attendance.date,
                "status"        to attendance.status,
                "workedHours"   to attendance.workedHours,
                "overtimeHours" to attendance.overtimeHours,
                "updatedAt"     to FieldValue.serverTimestamp()
            )
            db.collection("attendance")
                .document(uid)
                .collection("days")
                .document(attendance.date)
                .set(data)
                .await()
            Log.d(TAG, "Attendance saved: ${attendance.date}")
        } catch (e: Exception) {
            Log.e(TAG, "saveAttendance failed", e)
            throw e
        }
    }

    suspend fun getAttendanceList(uid: String): List<Attendance> {
        return try {
            val snapshot = db.collection("attendance")
                .document(uid)
                .collection("days")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Attendance::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAttendanceList failed", e)
            emptyList()
        }
    }

    suspend fun deleteAttendance(uid: String, date: String) {
        try {
            db.collection("attendance")
                .document(uid)
                .collection("days")
                .document(date)
                .delete()
                .await()
            Log.d(TAG, "Attendance deleted: $date")
        } catch (e: Exception) {
            Log.e(TAG, "deleteAttendance failed", e)
            throw e
        }
    }

    // ----------------------------------------------------------------
    // PROFILE EXISTS CHECK
    // ----------------------------------------------------------------

    suspend fun profileExists(uid: String): Boolean {
        return try {
            val doc = db.collection("users")
                .document(uid)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "profileExists check failed", e)
            false
        }
    }
}
