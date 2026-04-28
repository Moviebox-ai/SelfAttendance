package com.aaryo.selfattendance.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.aaryo.selfattendance.data.model.Attendance
import java.io.File
import java.io.FileWriter
import java.io.OutputStream

object CsvExporter {

    fun export(
        context: Context,
        list: List<Attendance>
    ) {

        try {

            val csvContent = buildString {
                append("Date,Status,Worked Hours,Overtime Hours\n")
                list.forEach {
                    append("${it.date},${it.status},${it.workedHours},${it.overtimeHours}\n")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                // Android 10+ - use MediaStore (no storage permission needed)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "attendance_report.csv")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/SelfAttendance")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
                )

                uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                        stream.write(csvContent.toByteArray())
                        stream.flush()
                    }
                    Toast.makeText(
                        context,
                        "CSV saved to Documents/SelfAttendance/",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: throw Exception("Failed to create file")

            } else {

                // Android 9 and below
                val folder = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    "SelfAttendance"
                )

                if (!folder.exists()) folder.mkdirs()

                val file = File(folder, "attendance_report.csv")
                file.writeText(csvContent)

                Toast.makeText(
                    context,
                    "CSV saved: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {

            Toast.makeText(
                context,
                "CSV export failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
