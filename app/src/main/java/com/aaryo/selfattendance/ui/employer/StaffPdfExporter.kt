package com.aaryo.selfattendance.ui.employer

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object StaffPdfExporter {

    fun generateAndShareSalarySlip(
        context: Context,
        businessName: String,
        employee: Employee,
        payout: StaffSalaryPayout
    ) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Header Background
            paint.color = Color.rgb(25, 118, 210) // Primary Blue
            canvas.drawRect(0f, 0f, 595f, 90f, paint)

            // Header Text
            paint.color = Color.WHITE
            paint.textSize = 22f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(businessName.ifBlank { "Company / Business" }, 40f, 45f, paint)

            paint.textSize = 14f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("SALARY SLIP - ${payout.monthYear}", 40f, 70f, paint)

            var y = 130f

            // Employee Details Box
            paint.color = Color.rgb(240, 243, 246)
            canvas.drawRoundRect(40f, y, 555f, y + 100f, 8f, 8f, paint)

            paint.color = Color.BLACK
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Employee Details", 55f, y + 25f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 12f
            paint.color = Color.rgb(60, 60, 60)
            canvas.drawText("Name: ${employee.name}", 55f, y + 50f, paint)
            canvas.drawText("Designation: ${employee.designation}", 55f, y + 70f, paint)
            canvas.drawText("Phone: ${employee.phone.ifBlank { "N/A" }}", 55f, y + 90f, paint)

            canvas.drawText("Salary Type: ${employee.salaryType}", 330f, y + 50f, paint)
            canvas.drawText("Base Rate: ₹${employee.baseSalary.toInt()}", 330f, y + 70f, paint)
            canvas.drawText("Payment Date: ${payout.paymentDate ?: LocalDate.now().toString()}", 330f, y + 90f, paint)

            y += 125f

            // Attendance Summary Table
            paint.color = Color.rgb(245, 245, 245)
            canvas.drawRoundRect(40f, y, 555f, y + 65f, 6f, 6f, paint)

            paint.color = Color.BLACK
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Present Days", 55f, y + 25f, paint)
            canvas.drawText("Half Days", 180f, y + 25f, paint)
            canvas.drawText("Absent Days", 305f, y + 25f, paint)
            canvas.drawText("Overtime", 430f, y + 25f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 14f
            canvas.drawText("${payout.totalPresentDays.toInt()} Days", 55f, y + 50f, paint)
            canvas.drawText("${payout.totalHalfDays.toInt()} Days", 180f, y + 50f, paint)
            canvas.drawText("${payout.totalAbsentDays.toInt()} Days", 305f, y + 50f, paint)
            canvas.drawText("${payout.totalOvertimeHours} Hrs", 430f, y + 50f, paint)

            y += 90f

            // Earnings & Deductions Breakdown
            paint.color = Color.BLACK
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Earnings & Deductions", 40f, y, paint)

            y += 20f

            fun drawRow(label: String, value: String, isBold: Boolean = false, isHighlight: Boolean = false) {
                paint.color = if (isHighlight) Color.rgb(230, 245, 230) else Color.rgb(250, 250, 250)
                canvas.drawRect(40f, y, 555f, y + 28f, paint)

                paint.color = Color.BLACK
                paint.textSize = if (isBold) 13f else 12f
                paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                canvas.drawText(label, 55f, y + 19f, paint)

                val textWidth = paint.measureText(value)
                canvas.drawText(value, 540f - textWidth, y + 19f, paint)

                y += 30f
            }

            drawRow("Gross Earned Salary", "₹${payout.grossSalary.toInt()}", isBold = false)
            if (payout.bonus > 0) {
                drawRow("Bonus / Incentives", "+₹${payout.bonus.toInt()}", isBold = false)
            }
            if (payout.totalAdvancesDeducted > 0) {
                drawRow("Advance / Loan Deductions", "-₹${payout.totalAdvancesDeducted.toInt()}", isBold = false)
            }
            if (payout.otherDeductions > 0) {
                drawRow("Other Deductions", "-₹${payout.otherDeductions.toInt()}", isBold = false)
            }

            y += 10f
            drawRow("NET PAYABLE AMOUNT", "₹${payout.netPayable.toInt()}", isBold = true, isHighlight = true)
            drawRow("Amount Paid (${payout.paymentStatus})", "₹${payout.paidAmount.toInt()}", isBold = true)

            y += 60f

            // Signatures
            paint.color = Color.GRAY
            paint.strokeWidth = 1f
            canvas.drawLine(55f, y + 40f, 200f, y + 40f, paint)
            canvas.drawLine(380f, y + 40f, 530f, y + 40f, paint)

            paint.color = Color.rgb(80, 80, 80)
            paint.textSize = 11f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Employee Signature", 65f, y + 55f, paint)
            canvas.drawText("Employer Signature", 400f, y + 55f, paint)

            // Footer
            paint.textSize = 10f
            paint.color = Color.GRAY
            canvas.drawText("Generated via Self Attendance App", 210f, 810f, paint)

            doc.finishPage(page)

            val dir = File(context.cacheDir, "salary_slips")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "SalarySlip_${employee.name.replace(" ", "_")}_${payout.monthYear}.pdf"
            val file = File(dir, fileName)
            val outputStream = FileOutputStream(file)
            doc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Salary Slip - ${employee.name} (${payout.monthYear})")
                putExtra(Intent.EXTRA_TEXT, "Hello ${employee.name},\nHere is your salary slip for ${payout.monthYear}.\nNet Payable: ₹${payout.netPayable.toInt()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Salary Slip"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
