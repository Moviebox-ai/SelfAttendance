package com.aaryo.selfattendance.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.domain.SalaryCalculator
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

object SalarySlipGenerator {

    // ── A4 size in points (595 x 842) ────────────────────────────────────────
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f

    // ── Colors ────────────────────────────────────────────────────────────────
    private val COLOR_PRIMARY   = Color.parseColor("#4F46E5")
    private val COLOR_LIGHT     = Color.parseColor("#EEF2FF")
    private val COLOR_TEXT      = Color.parseColor("#1F2937")
    private val COLOR_MUTED     = Color.parseColor("#6B7280")
    private val COLOR_GREEN     = Color.parseColor("#16A34A")
    private val COLOR_RED       = Color.parseColor("#DC2626")
    private val COLOR_AMBER     = Color.parseColor("#D97706")
    private val COLOR_DIVIDER   = Color.parseColor("#E5E7EB")
    private val COLOR_WHITE     = Color.WHITE
    private val COLOR_ROW_ALT   = Color.parseColor("#F9FAFB")

    // ✅ FIX 1: Background thread — UI freeze / ANR nahi hoga
    private val executor    = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ✅ FIX 2: onDone callback parameter add kiya
    fun generate(
        context: Context,
        profile: UserProfile,
        attendanceList: List<Attendance>,
        month: YearMonth,
        bonus: Double = 0.0,
        deductions: Double = 0.0,
        onDone: (() -> Unit)? = null
    ) {
        executor.execute {
            try {
                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_W.toInt(), PAGE_H.toInt(), 1
                ).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                drawSlip(canvas, profile, attendanceList, month, bonus, deductions)

                pdf.finishPage(page)

                val monthLabel = month.format(DateTimeFormatter.ofPattern("MMM_yyyy"))
                val fileName = "salary_slip_${monthLabel}.pdf"

                val uri = savePdf(context, pdf, fileName)
                pdf.close()

                mainHandler.post {
                    if (uri != null) {
                        Toast.makeText(
                            context,
                            "Salary slip saved: Documents/SelfAttendance/$fileName",
                            Toast.LENGTH_LONG
                        ).show()
                        shareOrOpen(context, uri)
                    } else {
                        Toast.makeText(
                            context,
                            "PDF save failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDone?.invoke()
                }

            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        context,
                        "Salary slip failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone?.invoke()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw everything on canvas
    // ─────────────────────────────────────────────────────────────────────────
    private fun drawSlip(
        c: Canvas,
        profile: UserProfile,
        list: List<Attendance>,
        month: YearMonth,
        bonus: Double,
        deductions: Double
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val monthName = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

        // ── Header background ─────────────────────────────────────────────────
        p.color = COLOR_PRIMARY
        c.drawRect(0f, 0f, PAGE_W, 110f, p)

        // App name
        p.color = COLOR_WHITE
        p.textSize = 22f
        p.isFakeBoldText = true
        c.drawText("Self Attendance Pro", 40f, 42f, p)

        // Subtitle
        p.textSize = 13f
        p.isFakeBoldText = false
        p.color = Color.parseColor("#C7D2FE")
        c.drawText("Monthly Salary Slip", 40f, 64f, p)

        // Month badge (right side)
        p.color = Color.parseColor("#4338CA")
        c.drawRoundRect(RectF(PAGE_W - 170f, 22f, PAGE_W - 30f, 52f), 8f, 8f, p)
        p.color = COLOR_WHITE
        p.textSize = 13f
        p.isFakeBoldText = true
        c.drawText(monthName, PAGE_W - 163f, 41f, p)

        // Generated on
        p.isFakeBoldText = false
        p.color = Color.parseColor("#C7D2FE")
        p.textSize = 11f
        val today = java.time.LocalDate.now()
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        c.drawText("Generated: $today", PAGE_W - 163f, 60f, p)

        // ── Employee info card ────────────────────────────────────────────────
        p.color = COLOR_LIGHT
        c.drawRoundRect(RectF(30f, 120f, PAGE_W - 30f, 200f), 10f, 10f, p)

        p.color = COLOR_PRIMARY
        p.textSize = 11f
        p.isFakeBoldText = true
        c.drawText("EMPLOYEE DETAILS", 50f, 143f, p)

        p.color = COLOR_TEXT
        p.textSize = 18f
        p.isFakeBoldText = true
        c.drawText(profile.name.ifBlank { "Employee" }, 50f, 168f, p)

        p.color = COLOR_MUTED
        p.textSize = 12f
        p.isFakeBoldText = false
        c.drawText(
            "Monthly CTC: ${formatMoney(profile.monthlySalary)}   |   " +
            "Working Days: ${profile.workingDays}   |   " +
            "Std Hours: ${profile.standardHours}h/day",
            50f, 190f, p
        )

        // ── Attendance summary section ────────────────────────────────────────
        var y = 225f
        sectionHeader(c, p, "Attendance Summary", y)
        y += 35f

        val present = list.count { it.status == "PRESENT" }
        val half    = list.count { it.status == "HALF" || it.status == "HALF_DAY" }
        val absent  = list.count { it.status == "ABSENT" }
        val overtime = list.sumOf { it.overtimeHours }
        val totalDays = present + half + absent

        // 4 stat boxes
        val boxW = (PAGE_W - 80f) / 4f
        val boxes = listOf(
            Triple("Present", "$present days", COLOR_GREEN),
            Triple("Half Day", "$half days", COLOR_AMBER),
            Triple("Absent", "$absent days", COLOR_RED),
            Triple("Overtime", "${String.format("%.1f", overtime)}h", COLOR_PRIMARY)
        )
        boxes.forEachIndexed { i, (label, value, color) ->
            val bx = 40f + i * (boxW + 8f)
            p.color = Color.parseColor("#F3F4F6")
            c.drawRoundRect(RectF(bx, y, bx + boxW, y + 58f), 8f, 8f, p)

            p.color = color
            c.drawRoundRect(RectF(bx, y, bx + 4f, y + 58f), 4f, 4f, p)

            p.color = color
            p.textSize = 18f
            p.isFakeBoldText = true
            c.drawText(value, bx + 14f, y + 28f, p)

            p.color = COLOR_MUTED
            p.textSize = 11f
            p.isFakeBoldText = false
            c.drawText(label, bx + 14f, y + 46f, p)
        }

        // ── Earnings table ────────────────────────────────────────────────────
        y += 80f
        sectionHeader(c, p, "Earnings Breakdown", y)
        y += 35f

        val perDay = SalaryCalculator.perDaySalary(profile.monthlySalary)
        val perHour = SalaryCalculator.perHourSalary(
            profile.monthlySalary, profile.standardHours
        )
        val basicPay    = present * perDay
        val halfPay     = half * (perDay / 2)
        val overtimePay = overtime * profile.overtimeRate

        tableHeader(c, p, y)
        y += 28f

        val rows = listOf(
            listOf("Basic Pay (Present)", "$present × ${formatMoney(perDay)}", formatMoney(basicPay)),
            listOf("Half Day Pay", "$half × ${formatMoney(perDay / 2)}", formatMoney(halfPay)),
            listOf("Overtime Pay", "${String.format("%.1f", overtime)}h × ${formatMoney(profile.overtimeRate)}", formatMoney(overtimePay)),
            listOf("Bonus", "–", formatMoney(bonus))
        )

        rows.forEachIndexed { i, row ->
            if (i % 2 == 1) {
                p.color = COLOR_ROW_ALT
                c.drawRect(40f, y - 16f, PAGE_W - 40f, y + 14f, p)
            }
            tableRow(c, p, row[0], row[1], row[2], y, isEarning = true)
            y += 30f
        }

        divider(c, p, y); y += 16f

        val gross = basicPay + halfPay + overtimePay + bonus
        p.color = Color.parseColor("#ECFDF5")
        c.drawRoundRect(RectF(40f, y - 16f, PAGE_W - 40f, y + 16f), 4f, 4f, p)
        p.color = COLOR_GREEN
        p.textSize = 13f
        p.isFakeBoldText = true
        c.drawText("Gross Earnings", 50f, y + 4f, p)
        c.drawText(formatMoney(gross), PAGE_W - 50f - textWidth(p, formatMoney(gross)), y + 4f, p)
        y += 36f

        // ── Deductions table ──────────────────────────────────────────────────
        sectionHeader(c, p, "Deductions", y); y += 35f

        tableHeader(c, p, y); y += 28f

        val absentDed = absent * perDay
        val dedRows = listOf(
            listOf("Absent Deduction", "$absent × ${formatMoney(perDay)}", formatMoney(absentDed)),
            listOf("Other Deductions", "–", formatMoney(deductions))
        )

        dedRows.forEachIndexed { i, row ->
            if (i % 2 == 1) {
                p.color = COLOR_ROW_ALT
                c.drawRect(40f, y - 16f, PAGE_W - 40f, y + 14f, p)
            }
            tableRow(c, p, row[0], row[1], row[2], y, isEarning = false)
            y += 30f
        }

        divider(c, p, y); y += 16f

        val totalDed = absentDed + deductions
        p.color = Color.parseColor("#FEF2F2")
        c.drawRoundRect(RectF(40f, y - 16f, PAGE_W - 40f, y + 16f), 4f, 4f, p)
        p.color = COLOR_RED
        p.textSize = 13f
        p.isFakeBoldText = true
        c.drawText("Total Deductions", 50f, y + 4f, p)
        c.drawText(formatMoney(totalDed), PAGE_W - 50f - textWidth(p, formatMoney(totalDed)), y + 4f, p)
        y += 40f

        // ── Net salary box ────────────────────────────────────────────────────
        val net = gross - totalDed
        p.color = COLOR_PRIMARY
        c.drawRoundRect(RectF(30f, y, PAGE_W - 30f, y + 70f), 12f, 12f, p)

        p.color = Color.parseColor("#C7D2FE")
        p.textSize = 12f
        p.isFakeBoldText = false
        c.drawText("Net Salary Payable", 50f, y + 26f, p)

        p.color = COLOR_WHITE
        p.textSize = 26f
        p.isFakeBoldText = true
        c.drawText(formatMoney(net), 50f, y + 56f, p)

        val earnedPct = SalaryCalculator.earnedPercent(list)
        p.color = Color.parseColor("#C7D2FE")
        p.textSize = 11f
        p.isFakeBoldText = false
        c.drawText("Earned: ${String.format("%.1f", earnedPct)}%", PAGE_W - 160f, y + 26f, p)

        p.color = Color.parseColor("#A5B4FC")
        p.textSize = 11f
        c.drawText("$totalDays days worked / ${SalaryCalculator.CALENDAR_DAYS} days", PAGE_W - 160f, y + 46f, p)

        y += 88f

        // ── Attendance detail table ───────────────────────────────────────────
        if (list.isNotEmpty() && y < PAGE_H - 120f) {
            sectionHeader(c, p, "Attendance Detail", y); y += 30f

            p.color = COLOR_PRIMARY
            p.textSize = 10f
            p.isFakeBoldText = true
            c.drawText("Date", 50f, y, p)
            c.drawText("Status", 170f, y, p)
            c.drawText("Worked Hrs", 290f, y, p)
            c.drawText("Overtime", 410f, y, p)
            y += 6f
            divider(c, p, y); y += 14f

            p.textSize = 10f
            p.isFakeBoldText = false

            list.sortedBy { it.date }.forEach { att ->
                if (y > PAGE_H - 60f) return@forEach

                val statusColor = when (att.status) {
                    "PRESENT" -> COLOR_GREEN
                    "HALF", "HALF_DAY" -> COLOR_AMBER
                    "ABSENT" -> COLOR_RED
                    else -> COLOR_MUTED
                }

                p.color = COLOR_TEXT
                c.drawText(att.date, 50f, y, p)

                p.color = statusColor
                c.drawText(att.status, 170f, y, p)

                p.color = COLOR_TEXT
                c.drawText("${att.workedHours}h", 290f, y, p)

                p.color = if (att.overtimeHours > 0) COLOR_PRIMARY else COLOR_MUTED
                c.drawText(
                    if (att.overtimeHours > 0) "+${att.overtimeHours}h" else "–",
                    410f, y, p
                )
                y += 18f
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        p.color = COLOR_DIVIDER
        c.drawRect(0f, PAGE_H - 40f, PAGE_W, PAGE_H - 39f, p)

        p.color = COLOR_MUTED
        p.textSize = 10f
        p.isFakeBoldText = false
        c.drawText(
            "Self Attendance Pro  •  This is a system-generated salary slip",
            40f, PAGE_H - 18f, p
        )
        c.drawText(
            "Generated on $today",
            PAGE_W - 180f, PAGE_H - 18f, p
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper draw functions
    // ─────────────────────────────────────────────────────────────────────────

    private fun sectionHeader(c: Canvas, p: Paint, title: String, y: Float) {
        p.color = COLOR_PRIMARY
        c.drawRect(40f, y, 44f, y + 20f, p)
        p.color = COLOR_TEXT
        p.textSize = 13f
        p.isFakeBoldText = true
        c.drawText(title, 52f, y + 15f, p)
    }

    private fun tableHeader(c: Canvas, p: Paint, y: Float) {
        p.color = Color.parseColor("#F3F4F6")
        c.drawRect(40f, y - 16f, PAGE_W - 40f, y + 12f, p)
        p.color = COLOR_MUTED
        p.textSize = 11f
        p.isFakeBoldText = true
        c.drawText("Description", 50f, y, p)
        c.drawText("Calculation", 250f, y, p)
        c.drawText("Amount", PAGE_W - 120f, y, p)
    }

    private fun tableRow(
        c: Canvas, p: Paint,
        desc: String, calc: String, amount: String,
        y: Float, isEarning: Boolean
    ) {
        p.color = COLOR_TEXT
        p.textSize = 11f
        p.isFakeBoldText = false
        c.drawText(desc, 50f, y, p)

        p.color = COLOR_MUTED
        c.drawText(calc, 250f, y, p)

        p.color = if (isEarning) COLOR_GREEN else COLOR_RED
        p.isFakeBoldText = true
        c.drawText(amount, PAGE_W - 120f, y, p)
    }

    private fun divider(c: Canvas, p: Paint, y: Float) {
        p.color = COLOR_DIVIDER
        c.drawRect(40f, y, PAGE_W - 40f, y + 1f, p)
    }

    private fun textWidth(p: Paint, text: String): Float = p.measureText(text)

    private fun formatMoney(amount: Double): String {
        if (amount.isNaN() || amount.isInfinite()) return "₹0.00"
        val abs = Math.abs(amount)
        val intPart = abs.toLong()
        val decPart = Math.round((abs - intPart) * 100)
        val prefix = if (amount < 0) "-₹" else "₹"
        return "$prefix${formatIndian(intPart)}.${decPart.toString().padStart(2, '0')}"
    }

    private fun formatIndian(n: Long): String {
        if (n <= 999) return n.toString()
        val s = n.toString()
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ",")
            sb.insert(0, rest.substring(start, i))
            i = start
        }
        return "$sb,$last3"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save PDF to storage
    // ─────────────────────────────────────────────────────────────────────────
    private fun savePdf(
        context: Context,
        pdf: PdfDocument,
        fileName: String
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/SelfAttendance"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), cv
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        pdf.writeTo(stream)
                    }
                }
                uri

            } else {

                val folder = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    "SelfAttendance"
                )
                if (!folder.exists()) folder.mkdirs()
                val file = File(folder, fileName)
                pdf.writeTo(FileOutputStream(file))

                // ✅ FIX 3: FileProvider use karo — Uri.fromFile() Android 7+ par crash karta hai
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Share / Open after save
    // ─────────────────────────────────────────────────────────────────────────
    private fun shareOrOpen(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // No PDF viewer — file saved successfully, that's fine
        }
    }
}
