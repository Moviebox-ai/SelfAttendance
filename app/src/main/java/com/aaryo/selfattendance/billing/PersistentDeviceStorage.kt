package com.aaryo.selfattendance.billing

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * PersistentDeviceStorage
 *
 * Provides a resilient, device-level trial anchor that survives app uninstalls and reinstalls.
 * When an app is uninstalled on Android, app-private directories are cleared, but shared/public
 * storage directories (such as Documents or Downloads) retain their contents.
 *
 * This layer works together with Android Auto Backup (Google Drive) and Firestore
 * to guarantee that the 7-day business free trial start date is strictly preserved
 * even if the user uninstalls and reinstalls the app days later.
 */
object PersistentDeviceStorage {

    private const val TAG = "PersistentDeviceStorage"
    private const val FILE_NAME = ".sys_attendance_bt_anchor.dat"
    private const val SALT = "SelfAttendance_BMode_AntiAbuse_Salt_2026"

    /**
     * Reads the earliest trial anchor timestamp recorded on this physical device.
     * Returns null if no previous trial anchor exists.
     */
    fun readAnchorTime(context: Context): Long? {
        val candidates = mutableListOf<Long>()

        // 1. Check Public Documents Directory
        readFromDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))?.let {
            candidates.add(it)
        }

        // 2. Check Public Downloads Directory
        readFromDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))?.let {
            candidates.add(it)
        }

        // 3. Check App External Media / Persistent Files
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    readFromDirectory(dir)?.let { candidates.add(it) }
                    // Also check parent directories if accessible
                    dir.parentFile?.let { readFromDirectory(it)?.let { t -> candidates.add(t) } }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "External dirs check skipped: ${e.message}")
        }

        return candidates.minOrNull()
    }

    /**
     * Persists the given trial start time to the device filesystem.
     * Tries multiple persistent locations with fallback error handling.
     */
    fun saveAnchorTime(context: Context, startTimeMillis: Long) {
        if (startTimeMillis <= 0L) return

        val payload = createPayload(startTimeMillis)

        // 1. Save to Public Documents Directory
        writeToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), payload)

        // 2. Save to Public Downloads Directory
        writeToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), payload)

        // 3. Save to App External Dirs
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    writeToDirectory(dir, payload)
                    dir.parentFile?.let { writeToDirectory(it, payload) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "External dirs write skipped: ${e.message}")
        }
    }

    private fun readFromDirectory(dir: File?): Long? {
        return try {
            if (dir == null || !dir.exists()) return null
            val file = File(dir, FILE_NAME)
            if (!file.exists() || !file.canRead()) return null

            val text = file.readText().trim()
            verifyAndExtractTime(text)
        } catch (e: Exception) {
            Log.d(TAG, "Could not read anchor from $dir: ${e.message}")
            null
        }
    }

    private fun writeToDirectory(dir: File?, payload: String) {
        try {
            if (dir == null) return
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, FILE_NAME)
            file.writeText(payload)
            Log.d(TAG, "Trial anchor saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.d(TAG, "Could not write anchor to $dir: ${e.message}")
        }
    }

    private fun createPayload(startTimeMillis: Long): String {
        val checksum = sha256("$startTimeMillis:$SALT")
        return "$startTimeMillis:$checksum"
    }

    private fun verifyAndExtractTime(payload: String): Long? {
        val parts = payload.split(":")
        if (parts.size != 2) return null
        val time = parts[0].toLongOrNull() ?: return null
        val expectedChecksum = sha256("$time:$SALT")
        return if (parts[1] == expectedChecksum && time > 1577836800000L) { // after 2020
            time
        } else {
            null
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
