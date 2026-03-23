package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.Attendance
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BackupRepository {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "BackupRepository"
        // Firestore batch limit 500 hai, safe margin ke liye 400
        private const val BATCH_LIMIT = 400
    }

    // ------------------------------------------------
    // BACKUP — attendance → backup collection
    // ------------------------------------------------

    suspend fun backupAttendance(
        uid: String,
        attendanceList: List<Attendance>
    ) {

        if (attendanceList.isEmpty()) return

        try {

            // 400 records ke chunks mein batch karo
            attendanceList.chunked(BATCH_LIMIT).forEach { chunk ->

                val batch = db.batch()

                chunk.forEach { attendance ->

                    val doc = db
                        .collection("backup")
                        .document(uid)
                        .collection("days")
                        .document(attendance.date)

                    batch.set(doc, attendance)
                }

                batch.commit().await()
            }

            Log.d(TAG, "Backup done: ${attendanceList.size} records")

        } catch (e: Exception) {

            Log.e(TAG, "Backup failed", e)
            throw e
        }
    }

    // ------------------------------------------------
    // RESTORE — backup → attendance collection
    // ------------------------------------------------

    suspend fun restoreAttendance(uid: String): List<Attendance> {

        return try {

            // Step 1: Backup collection se fetch karo
            val snapshot = db
                .collection("backup")
                .document(uid)
                .collection("days")
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No backup data found")
                return emptyList()
            }

            val backupList = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Attendance::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${doc.id}", e)
                    null
                }
            }

            if (backupList.isEmpty()) return emptyList()

            // Step 2: Existing attendance pehle clear karo
            val existingSnapshot = db
                .collection("attendance")
                .document(uid)
                .collection("days")
                .get()
                .await()

            if (!existingSnapshot.isEmpty) {
                existingSnapshot.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                    val deleteBatch = db.batch()
                    chunk.forEach { deleteBatch.delete(it.reference) }
                    deleteBatch.commit().await()
                }
            }

            // Step 3: Backup data ko attendance collection mein write karo
            backupList.chunked(BATCH_LIMIT).forEach { chunk ->

                val batch = db.batch()

                chunk.forEach { attendance ->

                    val doc = db
                        .collection("attendance")
                        .document(uid)
                        .collection("days")
                        .document(attendance.date)

                    batch.set(doc, attendance)
                }

                batch.commit().await()
            }

            Log.d(TAG, "Restore done: ${backupList.size} records")

            backupList

        } catch (e: Exception) {

            Log.e(TAG, "Restore failed", e)
            throw e
        }
    }

    // ------------------------------------------------
    // BACKUP LAST DATE — backup kab hua check karo
    // ------------------------------------------------

    suspend fun getLastBackupDate(uid: String): String? {

        return try {

            val snapshot = db
                .collection("backup")
                .document(uid)
                .collection("days")
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()
                ?.toObject(Attendance::class.java)
                ?.date

        } catch (e: Exception) {
            null
        }
    }
}
