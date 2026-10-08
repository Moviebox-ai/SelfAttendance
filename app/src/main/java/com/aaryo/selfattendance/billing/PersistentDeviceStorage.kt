package com.aaryo.selfattendance.billing

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * PersistentDeviceStorage
 *
 * Provides a resilient, device-level trial anchor that survives app uninstalls and reinstalls.
 *
 * Uses multiple layers of persistence:
 * 1. Android MediaStore (survives uninstall on Android 10+ without storage permissions)
 * 2. Public Documents Directory (.sys_attendance_bt_anchor.dat)
 * 3. Public Downloads Directory (.sys_attendance_bt_anchor.dat)
 * 4. App External Media / Shared Directories
 *
 * All stored timestamps are cryptographically signed with a SHA-256 HMAC-style checksum
 * to prevent user tampering.
 */
object PersistentDeviceStorage {

    private const val TAG = "PersistentDeviceStorage"
    private const val FILE_NAME = ".sys_attendance_bt_anchor.dat"
    private const val SALT = "SelfAttendance_BMode_AntiAbuse_Salt_2026_V2"

    /**
     * Reads the earliest trial anchor timestamp recorded on this physical device.
     * Returns null if no previous trial anchor exists.
     */
    fun readAnchorTime(context: Context): Long? {
        val candidates = mutableListOf<Long>()

        // 1. Check MediaStore (Survives uninstall across Android 10, 11, 12, 13, 14, 15)
        readFromMediaStore(context)?.let { candidates.add(it) }

        // 2. Check Public Documents Directory
        try {
            readFromDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))?.let {
                candidates.add(it)
            }
        } catch (_: Exception) {}

        // 3. Check Public Downloads Directory
        try {
            readFromDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))?.let {
                candidates.add(it)
            }
        } catch (_: Exception) {}

        // 4. Check App External Media / Persistent Files
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    readFromDirectory(dir)?.let { candidates.add(it) }
                    dir.parentFile?.let { readFromDirectory(it)?.let { t -> candidates.add(t) } }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "External dirs check skipped: ${e.message}")
        }

        val earliest = candidates.filter { it > 1577836800000L }.minOrNull()
        if (earliest != null) {
            Log.d(TAG, "Found persistent device trial anchor: $earliest")
        }
        return earliest
    }

    /**
     * Persists the given trial start time to the device filesystem.
     * Tries multiple persistent locations with fallback error handling.
     */
    fun saveAnchorTime(context: Context, startTimeMillis: Long) {
        if (startTimeMillis <= 1577836800000L) return

        val payload = createPayload(startTimeMillis)

        // 1. Save to MediaStore (Survives uninstall)
        writeToMediaStore(context, payload)

        // 2. Save to Public Documents Directory
        try {
            writeToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), payload)
        } catch (_: Exception) {}

        // 3. Save to Public Downloads Directory
        try {
            writeToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), payload)
        } catch (_: Exception) {}

        // 4. Save to App External Dirs
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

    private fun readFromMediaStore(context: Context): Long? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(FILE_NAME)
            val queryUri = MediaStore.Files.getContentUri("external")

            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val fileUri = ContentUris.withAppendedId(queryUri, id)
                    context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { reader ->
                        val text = reader.readText().trim()
                        verifyAndExtractTime(text)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "MediaStore read error: ${e.message}")
            null
        }
    }

    private fun writeToMediaStore(context: Context, payload: String) {
        try {
            // Check if already exists
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(FILE_NAME)
            val queryUri = MediaStore.Files.getContentUri("external")

            var existingId: Long? = null
            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    existingId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                }
            }

            if (existingId != null) {
                // Already stored in MediaStore, don't overwrite with newer date
                Log.d(TAG, "MediaStore anchor already exists (id=$existingId)")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
                val uri = context.contentResolver.insert(queryUri, cv)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray())
                    }
                    Log.d(TAG, "Saved trial anchor to MediaStore Documents")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "MediaStore write error: ${e.message}")
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
            if (!file.exists()) {
                file.writeText(payload)
                Log.d(TAG, "Trial anchor saved to ${file.absolutePath}")
            }
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
