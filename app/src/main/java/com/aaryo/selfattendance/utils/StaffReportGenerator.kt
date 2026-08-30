package com.aaryo.selfattendance.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object StaffReportGenerator {

    /**
     * Generates a monthly salary slip PDF for an employee and launches share intent.
     */
    fun generateSalarySlipPdf(
        context: Context,
        businessName: String,
        employee: Employee,
        payout: StaffSalaryPayout,
        monthYear: String,
        attendanceList: List<StaffAttendance>,
        advances: List<StaffAdvance>
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint().apply { isAntiAlias = true }
            val titlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
                color = Color.rgb(24, 43, 73)
            }
            val subPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                color = Color.DKGRAY
            }
            val boldPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 12f
                color = Color.BLACK
            }
            val normalPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                color = Color.BLACK
            }

            // Header Background Card
            val headerBg = Paint().apply {
                color = Color.rgb(238, 242, 250)
            }
            canvas.drawRect(30f, 30f, 565f, 110f, headerBg)

            // Business Info
            canvas.drawText(businessName.ifBlank { "BUSINESS MANAGEMENT" }, 45f, 60f, titlePaint)
            val parsedYm = runCatching { YearMonth.parse(monthYear) }.getOrDefault(YearMonth.now())
            val monthTitle = "${parsedYm.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${parsedYm.year}"
            canvas.drawText("SALARY SLIP / PAYSLIP — $monthTitle", 45f, 82f, boldPaint)
            canvas.drawText("Generated on: ${LocalDate.now()}", 45f, 98f, subPaint)

            // Employee Details Box
            var yPos = 135f
            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(30f, 120f, 565f, 215f, borderPaint)

            canvas.drawText("Employee Name:", 45f, yPos, subPaint)
            canvas.drawText(employee.name, 160f, yPos, boldPaint)

            canvas.drawText("Designation / Role:", 320f, yPos, subPaint)
            canvas.drawText(employee.designation.ifBlank { "Staff" }, 440f, yPos, normalPaint)

            yPos += 24f
            canvas.drawText("Phone Number:", 45f, yPos, subPaint)
            canvas.drawText(employee.phone.ifBlank { "N/A" }, 160f, yPos, normalPaint)

            canvas.drawText("Wage Type:", 320f, yPos, subPaint)
            canvas.drawText(employee.salaryType, 440f, yPos, normalPaint)

            yPos += 24f
            canvas.drawText("Base Salary / Rate:", 45f, yPos, subPaint)
            canvas.drawText("₹${employee.baseSalary.toInt()}", 160f, yPos, boldPaint)

            canvas.drawText("Payment Status:", 320f, yPos, subPaint)
            val statusColor = if (payout.paymentStatus == StaffSalaryPayout.STATUS_PAID) Color.rgb(46, 125, 50) else Color.rgb(230, 81, 0)
            val statusPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11f
                color = statusColor
            }
            canvas.drawText(payout.paymentStatus, 440f, yPos, statusPaint)

            // Attendance Breakdown Summary
            yPos = 245f
            canvas.drawText("1. ATTENDANCE SUMMARY", 45f, yPos, boldPaint)
            yPos += 15f
            canvas.drawRect(30f, yPos, 565f, yPos + 60f, borderPaint)

            val attY = yPos + 22f
            canvas.drawText("Present Days: ${payout.totalPresentDays}", 45f, attY, normalPaint)
            canvas.drawText("Half Days: ${payout.totalHalfDays}", 180f, attY, normalPaint)
            canvas.drawText("Absent Days: ${payout.totalAbsentDays}", 310f, attY, normalPaint)
            canvas.drawText("Paid Leaves: ${payout.totalPaidLeaveDays}", 440f, attY, normalPaint)

            val attY2 = yPos + 44f
            canvas.drawText("Overtime Hours: ${payout.totalOvertimeHours} hrs", 45f, attY2, normalPaint)

            // Salary Calculation Breakdown
            yPos += 95f
            canvas.drawText("2. SALARY & DEDUCTION DETAILS", 45f, yPos, boldPaint)
            yPos += 15f
            canvas.drawRect(30f, yPos, 565f, yPos + 180f, borderPaint)

            var salY = yPos + 26f
            canvas.drawText("Gross Calculated Earnings:", 45f, salY, normalPaint)
            canvas.drawText("₹${String.format("%.2f", payout.grossSalary)}", 440f, salY, boldPaint)

            salY += 22f
            canvas.drawText("Bonus / Incentives (+):", 45f, salY, normalPaint)
            canvas.drawText("₹${String.format("%.2f", payout.bonus)}", 440f, salY, normalPaint)

            salY += 22f
            canvas.drawText("Advances / Loans Deducted (-):", 45f, salY, normalPaint)
            canvas.drawText("₹${String.format("%.2f", payout.totalAdvancesDeducted)}", 440f, salY, normalPaint)

            salY += 22f
            canvas.drawText("Other Deductions / Penalties (-):", 45f, salY, normalPaint)
            canvas.drawText("₹${String.format("%.2f", payout.otherDeductions)}", 440f, salY, normalPaint)

            salY += 10f
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            canvas.drawLine(40f, salY, 555f, salY, linePaint)

            salY += 24f
            val netTitlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 14f
                color = Color.rgb(24, 43, 73)
            }
            canvas.drawText("NET PAYABLE SALARY:", 45f, salY, netTitlePaint)
            canvas.drawText("₹${String.format("%.2f", payout.netPayable)}", 440f, salY, netTitlePaint)

            salY += 24f
            canvas.drawText("Amount Paid: ₹${String.format("%.2f", payout.paidAmount)}   |   Mode: ${payout.paymentMode}", 45f, salY, subPaint)

            // Footer Signature Area
            yPos = 730f
            canvas.drawLine(60f, yPos, 220f, yPos, linePaint)
            canvas.drawLine(370f, yPos, 530f, yPos, linePaint)
            canvas.drawText("Employer Signature", 80f, yPos + 18f, subPaint)
            canvas.drawText("Employee Signature", 395f, yPos + 18f, subPaint)

            pdfDocument.finishPage(page)

            // Write File to Cache/Documents
            val fileName = "SalarySlip_${employee.name.replace(" ", "_")}_$monthYear.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            // Open / Share PDF via Intent
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Salary Slip - ${employee.name} ($monthTitle)")
                putExtra(Intent.EXTRA_TEXT, "Hello ${employee.name}, here is your salary slip for $monthTitle from $businessName.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Salary Slip").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a monthly attendance & payroll CSV sheet to export to Excel / WhatsApp.
     */
    fun exportStaffMonthlyReportCsv(
        context: Context,
        businessName: String,
        monthYear: String,
        employees: List<Employee>,
        payouts: Map<Long, StaffSalaryPayout>
    ) {
        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("sep=,\n")
            csvBuilder.append("Business:,\"${businessName}\"\n")
            csvBuilder.append("Month:,\"${monthYear}\"\n")
            csvBuilder.append("Generated on:,\"${LocalDate.now()}\"\n\n")

            // Headers
            csvBuilder.append("ID,Employee Name,Phone,Designation,Wage Type,Base Rate,Present Days,Half Days,Absent Days,Overtime (Hrs),Gross (Rs),Advance (Rs),Bonus (Rs),Other Deductions (Rs),Net Payable (Rs),Paid (Rs),Payment Status\n")

            employees.forEach { emp ->
                val p = payouts[emp.id]
                csvBuilder.append("${emp.id},")
                csvBuilder.append("\"${emp.name}\",")
                csvBuilder.append("\"${emp.phone}\",")
                csvBuilder.append("\"${emp.designation}\",")
                csvBuilder.append("\"${emp.salaryType}\",")
                csvBuilder.append("${emp.baseSalary},")
                csvBuilder.append("${p?.totalPresentDays ?: 0.0},")
                csvBuilder.append("${p?.totalHalfDays ?: 0.0},")
                csvBuilder.append("${p?.totalAbsentDays ?: 0.0},")
                csvBuilder.append("${p?.totalOvertimeHours ?: 0.0},")
                csvBuilder.append("${p?.grossSalary ?: 0.0},")
                csvBuilder.append("${p?.totalAdvancesDeducted ?: 0.0},")
                csvBuilder.append("${p?.bonus ?: 0.0},")
                csvBuilder.append("${p?.otherDeductions ?: 0.0},")
                csvBuilder.append("${p?.netPayable ?: 0.0},")
                csvBuilder.append("${p?.paidAmount ?: 0.0},")
                csvBuilder.append("\"${p?.paymentStatus ?: StaffSalaryPayout.STATUS_PENDING}\"\n")
            }

            val fileName = "Payroll_Report_${monthYear}.csv"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            file.writeText(csvBuilder.toString())

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Staff Payroll Report - $monthYear ($businessName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Payroll CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting CSV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
