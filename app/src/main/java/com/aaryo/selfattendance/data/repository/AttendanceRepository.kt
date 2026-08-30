package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.Attendance
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AttendanceRepository {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "AttendanceRepository"
        // Firestore batch limit 500 hai — safe margin ke liye 400
        private const val BATCH_LIMIT = 400
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

    /**
     * Save or update attendance for the given date.
     *
     * could not distinguish success from failure. A silent failure meant the UI
     * showed "saved" while Firestore actually rejected the write (no internet,
     * permission error, etc.) — a silent data-loss bug in an attendance app.
     *
     * Now re-throws after logging so CalendarViewModel can catch it, show an
     * error to the user, and avoid calling onTodayMarked() on failure.
     */
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

            val backupRef = db.collection("backup")
                .document(uid)
                .collection("days")
                .document(attendance.date)

            // Direct set with merge supports both offline local persistence and cloud sync
            docRef.set(data, SetOptions.merge()).await()

            // Backup copy update (non-blocking for primary attendance flow)
            runCatching {
                backupRef.set(data, SetOptions.merge()).await()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Save attendance failed", e)
            throw e
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

            runCatching {
                db.collection("backup")
                    .document(uid)
                    .collection("days")
                    .document(date)
                    .delete()
                    .await()
            }

        } catch (e: Exception) {

            Log.e(TAG, "Delete attendance failed", e)
            // so callers (CalendarViewModel/CalendarScreen) had no way to know the
            // delete failed — the UI would show "deleted" while Firestore still
            // had the record. Rethrow so the caller can surface the failure.
            throw e
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

            // documents delete karne ki koshish hoti thi — 500+ records pe crash.
            // Ab BATCH_LIMIT (400) ke chunks mein delete karo (same as BackupRepository).
            snapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }

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
