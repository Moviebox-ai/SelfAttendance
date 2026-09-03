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
     * Generates a detailed, executive monthly salary slip PDF for an employee and launches share intent.
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
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842 pt)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 17f
                color = Color.rgb(15, 23, 42)
            }
            val subPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                color = Color.rgb(100, 116, 139)
            }
            val boldPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11f
                color = Color.rgb(15, 23, 42)
            }
            val normalPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10.5f
                color = Color.rgb(30, 41, 59)
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            // Header Background Card
            val headerBg = Paint().apply { color = Color.rgb(241, 245, 249) }
            canvas.drawRoundRect(25f, 25f, 570f, 105f, 8f, 8f, headerBg)

            // Business & Document Title
            canvas.drawText(businessName.ifBlank { "BUSINESS MANAGEMENT" }.uppercase(), 40f, 55f, titlePaint)
            val parsedYm = runCatching { YearMonth.parse(monthYear) }.getOrDefault(YearMonth.now())
            val monthTitle = "${parsedYm.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${parsedYm.year}"
            canvas.drawText("OFFICIAL SALARY SLIP / PAYSLIP — $monthTitle", 40f, 75f, boldPaint)
            canvas.drawText("Generated on: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}  |  Doc ID: SLIP-${employee.id}-${monthYear.replace("-", "")}", 40f, 92f, subPaint)

            // Employee & Shift Details Box
            var yPos = 132f
            canvas.drawRoundRect(25f, 115f, 570f, 215f, 6f, 6f, borderPaint)

            canvas.drawText("Employee Name:", 40f, yPos, subPaint)
            canvas.drawText(employee.name, 150f, yPos, boldPaint)
            canvas.drawText("Designation / Role:", 320f, yPos, subPaint)
            canvas.drawText(employee.designation.ifBlank { "Staff" }, 430f, yPos, normalPaint)

            yPos += 22f
            canvas.drawText("Assigned Shift:", 40f, yPos, subPaint)
            canvas.drawText("${employee.shiftName} (${employee.shiftStartTime} - ${employee.shiftEndTime})", 150f, yPos, normalPaint)
            canvas.drawText("Shift Hours:", 320f, yPos, subPaint)
            canvas.drawText("${employee.standardShiftHours} hrs/day", 430f, yPos, normalPaint)

            yPos += 22f
            canvas.drawText("Phone Number:", 40f, yPos, subPaint)
            canvas.drawText(employee.phone.ifBlank { "N/A" }, 150f, yPos, normalPaint)
            canvas.drawText("Wage Type:", 320f, yPos, subPaint)
            canvas.drawText(employee.salaryType.replace("_", " "), 430f, yPos, normalPaint)

            yPos += 22f
            canvas.drawText("Base Pay Rate:", 40f, yPos, subPaint)
            canvas.drawText("₹${employee.baseSalary.toInt()}/${if (employee.salaryType == Employee.SALARY_TYPE_MONTHLY) "Month" else if (employee.salaryType == Employee.SALARY_TYPE_DAILY) "Day" else "Hour"}", 150f, yPos, boldPaint)
            canvas.drawText("Payment Status:", 320f, yPos, subPaint)
            val statusColor = when (payout.paymentStatus) {
                StaffSalaryPayout.STATUS_PAID -> Color.rgb(22, 101, 52)
                StaffSalaryPayout.STATUS_PARTIAL -> Color.rgb(194, 65, 12)
                else -> Color.rgb(185, 28, 28)
            }
            val statusPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10.5f
                color = statusColor
            }
            canvas.drawText(payout.paymentStatus, 430f, yPos, statusPaint)

            // Section 1: Attendance Roster Summary
            yPos = 238f
            canvas.drawText("1. ATTENDANCE & SHIFT ROSTER SUMMARY", 40f, yPos, boldPaint)
            yPos += 10f
            canvas.drawRoundRect(25f, yPos, 570f, yPos + 55f, 6f, 6f, borderPaint)

            val attY1 = yPos + 22f
            canvas.drawText("Full Present: ${payout.totalPresentDays.toInt()} Days", 40f, attY1, normalPaint)
            canvas.drawText("Half Days: ${payout.totalHalfDays.toInt()} Days", 180f, attY1, normalPaint)
            canvas.drawText("Absent: ${payout.totalAbsentDays.toInt()} Days", 310f, attY1, normalPaint)
            canvas.drawText("Paid Leaves: ${payout.totalPaidLeaveDays.toInt()} Days", 430f, attY1, normalPaint)

            val attY2 = yPos + 42f
            canvas.drawText("Overtime Worked: ${payout.totalOvertimeHours} Hours", 40f, attY2, boldPaint)
            val effectiveOtRate = if (employee.overtimeRatePerHour > 0) employee.overtimeRatePerHour else if (employee.hourlyRate > 0) employee.hourlyRate * 1.5 else (employee.baseSalary / (30 * employee.standardShiftHours)) * 1.25
            canvas.drawText("OT Rate: ₹${effectiveOtRate.toInt()}/hr  |  Total OT Pay: ₹${payout.totalOvertimePay.toInt()}", 250f, attY2, normalPaint)

            // Section 2: Detailed Earnings vs Deductions Table
            yPos += 80f
            canvas.drawText("2. EARNINGS & DEDUCTIONS STATEMENT", 40f, yPos, boldPaint)
            yPos += 10f
            val tableTop = yPos
            val tableBottom = yPos + 210f
            canvas.drawRoundRect(25f, tableTop, 570f, tableBottom, 6f, 6f, borderPaint)

            // Table header background
            val tableHdrBg = Paint().apply { color = Color.rgb(248, 250, 252) }
            canvas.drawRect(26f, tableTop + 1f, 569f, tableTop + 24f, tableHdrBg)
            canvas.drawLine(25f, tableTop + 24f, 570f, tableTop + 24f, linePaint)
            canvas.drawLine(297f, tableTop, 297f, tableBottom, linePaint) // middle divider

            // Headers
            canvas.drawText("EARNINGS & ALLOWANCES", 40f, tableTop + 16f, boldPaint)
            canvas.drawText("AMOUNT (₹)", 215f, tableTop + 16f, boldPaint)
            canvas.drawText("DEDUCTIONS & ADVANCES", 310f, tableTop + 16f, boldPaint)
            canvas.drawText("AMOUNT (₹)", 485f, tableTop + 16f, boldPaint)

            var rowY = tableTop + 44f
            // Row 1: Base Earned vs Advance Deductions
            val baseEarnedOnly = (payout.grossSalary - payout.totalOvertimePay - payout.allowance).coerceAtLeast(0.0)
            canvas.drawText("Base Earned Salary", 40f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", baseEarnedOnly), 215f, rowY, normalPaint)
            canvas.drawText("Advance Khata Settled", 310f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", payout.totalAdvancesDeducted), 485f, rowY, normalPaint)

            rowY += 24f
            // Row 2: Overtime Pay vs PF / Statutory Deductions
            canvas.drawText("Overtime Earnings", 40f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", payout.totalOvertimePay), 215f, rowY, normalPaint)
            canvas.drawText("PF / Statutory Deduction", 310f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", employee.pfDeduction), 485f, rowY, normalPaint)

            rowY += 24f
            // Row 3: Fixed Allowances (Travel/Food) vs ESI / Tax
            canvas.drawText("Fixed Allowances (Food/Travel)", 40f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", payout.allowance), 215f, rowY, normalPaint)
            canvas.drawText("ESI / Tax / Penalty", 310f, rowY, normalPaint)
            val otherDedRest = (payout.otherDeductions - employee.pfDeduction).coerceAtLeast(0.0)
            canvas.drawText(String.format(Locale.US, "%.2f", otherDedRest), 485f, rowY, normalPaint)

            rowY += 24f
            // Row 4: Performance Bonus / Incentives
            canvas.drawText("Incentive / Bonus", 40f, rowY, normalPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", payout.bonus), 215f, rowY, normalPaint)
            canvas.drawText("Other Deductions", 310f, rowY, normalPaint)
            canvas.drawText("0.00", 485f, rowY, normalPaint)

            // Total Earnings & Total Deductions subtotal line
            rowY += 24f
            canvas.drawLine(25f, rowY - 10f, 570f, rowY - 10f, linePaint)
            val totalEarnings = payout.grossSalary + payout.bonus
            val totalDeductions = payout.totalAdvancesDeducted + payout.otherDeductions
            canvas.drawText("Total Gross Earnings", 40f, rowY + 4f, boldPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", totalEarnings)}", 215f, rowY + 4f, boldPaint)
            canvas.drawText("Total Deductions", 310f, rowY + 4f, boldPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", totalDeductions)}", 485f, rowY + 4f, boldPaint)

            // Net Payable Highlight Box
            yPos = tableBottom + 20f
            val netBoxBg = Paint().apply { color = Color.rgb(238, 242, 255) }
            canvas.drawRoundRect(25f, yPos, 570f, yPos + 60f, 8f, 8f, netBoxBg)
            val netBoxBorder = Paint().apply {
                color = Color.rgb(199, 210, 254)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(25f, yPos, 570f, yPos + 60f, 8f, 8f, netBoxBorder)

            val netTitlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 15f
                color = Color.rgb(30, 58, 138)
            }
            val netAmountPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
                color = Color.rgb(30, 58, 138)
            }
            canvas.drawText("NET TAKE-HOME SALARY:", 45f, yPos + 36f, netTitlePaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", payout.netPayable)}", 380f, yPos + 36f, netAmountPaint)

            // Disbursed Info
            yPos += 75f
            canvas.drawText("Payment Disbursed: ₹${String.format(Locale.US, "%.2f", payout.paidAmount)}   |   Mode: ${payout.paymentMode}   |   Date: ${payout.paymentDate ?: "Pending"}", 40f, yPos, boldPaint)
            if (payout.notes.isNotBlank()) {
                canvas.drawText("Note / Ref ID: ${payout.notes}", 40f, yPos + 16f, subPaint)
            }

            // Signatures & Stamp
            yPos = 740f
            canvas.drawLine(50f, yPos, 210f, yPos, linePaint)
            canvas.drawLine(380f, yPos, 540f, yPos, linePaint)
            canvas.drawText("Authorized Signatory (Employer)", 60f, yPos + 18f, subPaint)
            canvas.drawText("Employee Signature & Date", 395f, yPos + 18f, subPaint)

            pdfDocument.finishPage(page)

            // Save PDF
            val fileName = "SalarySlip_${employee.name.replace(" ", "_")}_$monthYear.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            // Open Share Intent
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Salary Slip - ${employee.name} ($monthTitle)")
                putExtra(Intent.EXTRA_TEXT, "Dear ${employee.name},\nPlease find attached your Salary Slip for $monthTitle from $businessName.\n\nNet Salary: ₹${payout.netPayable.toInt()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Salary Slip PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a monthly company-wide Master Payroll PDF Report for accountants & CA.
     */
    fun generateCompanyPayrollPdf(
        context: Context,
        businessName: String,
        monthYear: String,
        employees: List<Employee>,
        payouts: Map<Long, StaffSalaryPayout>
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create() // Landscape A4 (842 x 595 pt)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 16f
                color = Color.rgb(15, 23, 42)
            }
            val boldPaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 9.5f
                color = Color.rgb(15, 23, 42)
            }
            val normalPaint = Paint().apply {
                isAntiAlias = true
                textSize = 9f
                color = Color.rgb(30, 41, 59)
            }
            val subPaint = Paint().apply {
                isAntiAlias = true
                textSize = 8.5f
                color = Color.rgb(100, 116, 139)
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 0.8f
            }

            // Header Banner
            val headerBg = Paint().apply { color = Color.rgb(241, 245, 249) }
            canvas.drawRect(20f, 20f, 822f, 75f, headerBg)

            val parsedYm = runCatching { YearMonth.parse(monthYear) }.getOrDefault(YearMonth.now())
            val monthTitle = "${parsedYm.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${parsedYm.year}"
            canvas.drawText(businessName.ifBlank { "BUSINESS MANAGEMENT" }.uppercase() + " — MASTER PAYROLL REGISTER", 35f, 45f, titlePaint)
            canvas.drawText("Period: $monthTitle   |   Total Staff: ${employees.size}   |   Exported: ${LocalDate.now()}", 35f, 63f, subPaint)

            // Table Header
            val tableTop = 90f
            val colPositions = floatArrayOf(25f, 50f, 155f, 235f, 280f, 325f, 370f, 425f, 480f, 535f, 595f, 655f, 725f, 780f)
            val headers = arrayOf("ID", "Staff Name", "Role / Shift", "Base Rate", "P / HD / A", "OT Hrs", "OT Pay", "Allowances", "Bonus", "Advances", "Deductions", "Net Pay", "Paid", "Status")

            val tblHdrBg = Paint().apply { color = Color.rgb(226, 232, 240) }
            canvas.drawRect(20f, tableTop, 822f, tableTop + 20f, tblHdrBg)

            for (i in headers.indices) {
                canvas.drawText(headers[i], colPositions[i], tableTop + 14f, boldPaint)
            }

            var currentY = tableTop + 36f
            var totGross = 0.0
            var totOt = 0.0
            var totAllow = 0.0
            var totBonus = 0.0
            var totAdv = 0.0
            var totDed = 0.0
            var totNet = 0.0
            var totPaid = 0.0

            employees.take(20).forEach { emp ->
                val p = payouts[emp.id] ?: StaffSalaryPayout(employeeId = emp.id, monthYear = monthYear)
                totGross += p.grossSalary
                totOt += p.totalOvertimePay
                totAllow += p.allowance
                totBonus += p.bonus
                totAdv += p.totalAdvancesDeducted
                totDed += p.otherDeductions
                totNet += p.netPayable
                totPaid += p.paidAmount

                canvas.drawText(emp.id.toString(), colPositions[0], currentY, normalPaint)
                canvas.drawText(emp.name.take(16), colPositions[1], currentY, boldPaint)
                canvas.drawText(emp.designation.take(12), colPositions[2], currentY, normalPaint)
                canvas.drawText("₹${emp.baseSalary.toInt()}", colPositions[3], currentY, normalPaint)
                canvas.drawText("${p.totalPresentDays.toInt()}/${p.totalHalfDays.toInt()}/${p.totalAbsentDays.toInt()}", colPositions[4], currentY, normalPaint)
                canvas.drawText("${p.totalOvertimeHours}h", colPositions[5], currentY, normalPaint)
                canvas.drawText("₹${p.totalOvertimePay.toInt()}", colPositions[6], currentY, normalPaint)
                canvas.drawText("₹${p.allowance.toInt()}", colPositions[7], currentY, normalPaint)
                canvas.drawText("₹${p.bonus.toInt()}", colPositions[8], currentY, normalPaint)
                canvas.drawText("₹${p.totalAdvancesDeducted.toInt()}", colPositions[9], currentY, normalPaint)
                canvas.drawText("₹${p.otherDeductions.toInt()}", colPositions[10], currentY, normalPaint)
                canvas.drawText("₹${p.netPayable.toInt()}", colPositions[11], currentY, boldPaint)
                canvas.drawText("₹${p.paidAmount.toInt()}", colPositions[12], currentY, normalPaint)
                canvas.drawText(p.paymentStatus.take(4), colPositions[13], currentY, boldPaint)

                canvas.drawLine(20f, currentY + 5f, 822f, currentY + 5f, linePaint)
                currentY += 18f
            }

            // Totals Summary Bar
            val totalBg = Paint().apply { color = Color.rgb(238, 242, 255) }
            canvas.drawRect(20f, currentY + 2f, 822f, currentY + 26f, totalBg)
            canvas.drawText("TOTALS", colPositions[1], currentY + 18f, boldPaint)
            canvas.drawText("₹${totOt.toInt()}", colPositions[6], currentY + 18f, boldPaint)
            canvas.drawText("₹${totAllow.toInt()}", colPositions[7], currentY + 18f, boldPaint)
            canvas.drawText("₹${totBonus.toInt()}", colPositions[8], currentY + 18f, boldPaint)
            canvas.drawText("₹${totAdv.toInt()}", colPositions[9], currentY + 18f, boldPaint)
            canvas.drawText("₹${totDed.toInt()}", colPositions[10], currentY + 18f, boldPaint)
            canvas.drawText("₹${totNet.toInt()}", colPositions[11], currentY + 18f, boldPaint)
            canvas.drawText("₹${totPaid.toInt()}", colPositions[12], currentY + 18f, boldPaint)

            // Signatures
            val sigY = 560f
            canvas.drawLine(40f, sigY, 200f, sigY, linePaint)
            canvas.drawLine(640f, sigY, 800f, sigY, linePaint)
            canvas.drawText("Prepared By (HR / Accounts)", 50f, sigY + 14f, subPaint)
            canvas.drawText("Approved By (Managing Director)", 645f, sigY + 14f, subPaint)

            pdfDocument.finishPage(page)

            val fileName = "Master_Payroll_${monthYear}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Master Payroll Register - $monthTitle ($businessName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Master Payroll PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating Master Payroll PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a comprehensive monthly attendance & payroll CSV/Excel spreadsheet.
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
            csvBuilder.append("Business:,\"${businessName.ifBlank { "Business Management" }}\"\n")
            csvBuilder.append("Payroll Month:,\"${monthYear}\"\n")
            csvBuilder.append("Total Employees:,\"${employees.size}\"\n")
            csvBuilder.append("Generated on:,\"${LocalDate.now()}\"\n\n")

            // Headers
            csvBuilder.append("Employee ID,Employee Name,Phone,Designation,Shift,Shift Timings,Wage Type,Base Salary (₹),Present Days,Half Days,Absent Days,Paid Leaves,Overtime (Hrs),Overtime Pay (₹),Fixed Allowance (₹),Performance Bonus (₹),Advance Deductions (₹),Statutory/Other Deductions (₹),Gross Earnings (₹),Net Payable (₹),Paid Amount (₹),Payment Mode,Payment Status\n")

            var totalGross = 0.0
            var totalOtHours = 0.0
            var totalOtPay = 0.0
            var totalAllowance = 0.0
            var totalBonus = 0.0
            var totalAdvances = 0.0
            var totalDeductions = 0.0
            var totalNet = 0.0
            var totalPaid = 0.0

            employees.forEach { emp ->
                val p = payouts[emp.id]
                val gross = p?.grossSalary ?: 0.0
                val otHrs = p?.totalOvertimeHours ?: 0.0
                val otPay = p?.totalOvertimePay ?: 0.0
                val allow = p?.allowance ?: emp.fixedAllowance
                val bonus = p?.bonus ?: 0.0
                val adv = p?.totalAdvancesDeducted ?: 0.0
                val ded = p?.otherDeductions ?: (emp.pfDeduction + emp.esiDeduction)
                val net = p?.netPayable ?: 0.0
                val paid = p?.paidAmount ?: 0.0

                totalGross += gross
                totalOtHours += otHrs
                totalOtPay += otPay
                totalAllowance += allow
                totalBonus += bonus
                totalAdvances += adv
                totalDeductions += ded
                totalNet += net
                totalPaid += paid

                csvBuilder.append("${emp.id},")
                csvBuilder.append("\"${emp.name}\",")
                csvBuilder.append("\"${emp.phone}\",")
                csvBuilder.append("\"${emp.designation}\",")
                csvBuilder.append("\"${emp.shiftName}\",")
                csvBuilder.append("\"${emp.shiftStartTime} - ${emp.shiftEndTime}\",")
                csvBuilder.append("\"${emp.salaryType}\",")
                csvBuilder.append("${emp.baseSalary},")
                csvBuilder.append("${p?.totalPresentDays ?: 0.0},")
                csvBuilder.append("${p?.totalHalfDays ?: 0.0},")
                csvBuilder.append("${p?.totalAbsentDays ?: 0.0},")
                csvBuilder.append("${p?.totalPaidLeaveDays ?: 0.0},")
                csvBuilder.append("${otHrs},")
                csvBuilder.append("${otPay},")
                csvBuilder.append("${allow},")
                csvBuilder.append("${bonus},")
                csvBuilder.append("${adv},")
                csvBuilder.append("${ded},")
                csvBuilder.append("${gross},")
                csvBuilder.append("${net},")
                csvBuilder.append("${paid},")
                csvBuilder.append("\"${p?.paymentMode ?: "CASH"}\",")
                csvBuilder.append("\"${p?.paymentStatus ?: StaffSalaryPayout.STATUS_PENDING}\"\n")
            }

            // Totals Row
            csvBuilder.append("\nTOTALS,,,,,,,,,,,,${totalOtHours},${totalOtPay},${totalAllowance},${totalBonus},${totalAdvances},${totalDeductions},${totalGross},${totalNet},${totalPaid},,\n")

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
                putExtra(Intent.EXTRA_SUBJECT, "Staff Payroll Excel/CSV Report - $monthYear ($businessName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Payroll Excel/CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting CSV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
