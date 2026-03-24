package com.aaryo.selfattendance.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.aaryo.selfattendance.data.model.Attendance
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun export(
        context: Context,
        list: List<Attendance>
    ) {

        try {

            val pdfDocument = PdfDocument()

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            val paint = Paint()

            var y = 60

            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText("Self Attendance Report", 40f, y.toFloat(), paint)

            y += 40

            paint.textSize = 16f
            paint.isFakeBoldText = false

            canvas.drawText("Date", 40f, y.toFloat(), paint)
            canvas.drawText("Status", 160f, y.toFloat(), paint)
            canvas.drawText("Worked Hrs", 280f, y.toFloat(), paint)
            canvas.drawText("Overtime", 420f, y.toFloat(), paint)

            y += 30

            list.forEach {

                canvas.drawText(it.date, 40f, y.toFloat(), paint)
                canvas.drawText(it.status, 160f, y.toFloat(), paint)
                canvas.drawText("${it.workedHours}h", 280f, y.toFloat(), paint)
                canvas.drawText("${it.overtimeHours}h", 420f, y.toFloat(), paint)

                y += 25
            }

            pdfDocument.finishPage(page)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val contentValues = ContentValues().apply {

                    put(MediaStore.MediaColumns.DISPLAY_NAME, "attendance_report.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/SelfAttendance"
                    )
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
                )

                uri?.let {

                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        pdfDocument.writeTo(stream)
                    }

                    Toast.makeText(
                        context,
                        "PDF saved to Documents/SelfAttendance",
                        Toast.LENGTH_LONG
                    ).show()

                } ?: throw Exception("Failed to create file")

            } else {

                val folder = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    "SelfAttendance"
                )

                if (!folder.exists()) folder.mkdirs()

                val file = File(folder, "attendance_report.pdf")

                FileOutputStream(file).use { stream ->
                    pdfDocument.writeTo(stream)
                }

                Toast.makeText(
                    context,
                    "PDF saved: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }

            pdfDocument.close()

        } catch (e: Exception) {

            Toast.makeText(
                context,
                "PDF export failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}