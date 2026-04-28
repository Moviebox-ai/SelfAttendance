package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.Attendance
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AttendanceRepository {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "AttendanceRepository"
    }

    // ------------------------------------------------
    // REALTIME ATTENDANCE STREAM
    // ------------------------------------------------

    fun observeAttendance(uid: String): Flow<List<Attendance>> = callbackFlow {

        val listener = db.collection("attendance")
            .document(uid)
            .collection("days")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {

                    Log.e(TAG, "Attendance listener error", error)

                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->

                    try {
                        doc.toObject(Attendance::class.java)
                    } catch (e: Exception) {

                        Log.e(TAG, "Parse error", e)
                        null
                    }

                } ?: emptyList()

                trySend(list)
            }

        awaitClose {

            try {
                listener.remove()
            } catch (e: Exception) {
                Log.e(TAG, "Listener close error", e)
            }
        }
    }

    // ------------------------------------------------
    // SAVE / UPDATE ATTENDANCE
    // ------------------------------------------------

    suspend fun saveOrUpdateAttendance(
        uid: String,
        attendance: Attendance
    ) {

        try {

            val docRef = db.collection("attendance")
                .document(uid)
                .collection("days")
                .document(attendance.date)

            val data = hashMapOf(

                "date" to attendance.date,
                "status" to attendance.status,
                "workedHours" to attendance.workedHours,
                "overtimeHours" to attendance.overtimeHours,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.runTransaction { transaction ->

                val snapshot = transaction.get(docRef)

                if (snapshot.exists()) {

                    transaction.update(docRef, data)

                } else {

                    transaction.set(docRef, data)
                }

            }.await()

        } catch (e: Exception) {

            Log.e(TAG, "Save attendance failed", e)
        }
    }

    // ------------------------------------------------
    // DELETE ATTENDANCE
    // ------------------------------------------------

    suspend fun deleteAttendance(
        uid: String,
        date: String
    ) {

        try {

            db.collection("attendance")
                .document(uid)
                .collection("days")
                .document(date)
                .delete()
                .await()

        } catch (e: Exception) {

            Log.e(TAG, "Delete attendance failed", e)
        }
    }

    // ------------------------------------------------
    // DELETE ALL ATTENDANCE (Reset)
    // ------------------------------------------------

    suspend fun deleteAllAttendance(uid: String) {

        try {

            val snapshot = db.collection("attendance")
                .document(uid)
                .collection("days")
                .get()
                .await()

            val batch = db.batch()

            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()

        } catch (e: Exception) {

            Log.e(TAG, "Delete all attendance failed", e)
            throw e
        }
    }

    // ------------------------------------------------
    // BACKUP RESTORE
    // ------------------------------------------------

    suspend fun getAllAttendance(uid: String): List<Attendance> {

        return try {

            val snapshot = db.collection("attendance")
                .document(uid)
                .collection("days")
                .get()
                .await()

            snapshot.documents.mapNotNull {

                try {
                    it.toObject(Attendance::class.java)
                } catch (e: Exception) {
                    null
                }
            }

        } catch (e: Exception) {

            Log.e(TAG, "Fetch attendance failed", e)

            emptyList()
        }
    }
}