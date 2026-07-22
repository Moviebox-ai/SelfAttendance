package com.aaryo.selfattendance.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.domain.SalaryCalculator
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SalarySlipExporter — generates a professional PDF salary slip for a given month.
 *
 * Layout (A4 595×842 pt):
 *  1. Green header bar  — app name, "SALARY SLIP", month, generation date
 *  2. Employee info card — name, employee ID
 *  3. Attendance summary — Present / Half / Absent / OT Hours table
 *  4. Salary breakdown   — Monthly CTC → per-day → basic earned → OT pay → net
 *  5. Signature area
 *  6. Footer
 */
object SalarySlipExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842

    private const val MARGIN = 36f

    // Color palette (kept consistent with app theme)
    private val CLR_GREEN        = Color.parseColor("#00C853")
    private val CLR_GREEN_DARK   = Color.parseColor("#00963D")
    private val CLR_LIGHT_BG     = Color.parseColor("#F1FDF5")
    private val CLR_TABLE_HDR    = Color.parseColor("#E8F5E9")
    private val CLR_DIVIDER      = Color.parseColor("#BDBDBD")
    private val CLR_TEXT_DARK    = Color.parseColor("#1A1A2E")
    private val CLR_TEXT_MED     = Color.parseColor("#424242")
    private val CLR_TEXT_LIGHT   = Color.parseColor("#757575")
    private val CLR_WHITE        = Color.WHITE
    private val CLR_GOLD         = Color.parseColor("#FFB300")
    private val CLR_RED          = Color.parseColor("#E53935")

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Export salary slip as PDF.
     * Returns the Uri of the saved file so the caller can launch a share/view intent.
     * Throws on failure — caller is responsible for showing the error.
     */
    fun export(
        context       : Context,
        profile       : UserProfile,
        attendanceList: List<Attendance>,
        yearMonth     : YearMonth
    ): Uri {
        val pdfDoc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            val page     = pdfDoc.startPage(pageInfo)
            draw(page.canvas, profile, attendanceList, yearMonth)
            pdfDoc.finishPage(page)

            val monthTag = yearMonth.format(DateTimeFormatter.ofPattern("MMM_yyyy", Locale.ENGLISH))
            val fileName = "salary_slip_${monthTag}.pdf"

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/SelfAttendance")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), cv
                ) ?: throw Exception("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use { pdfDoc.writeTo(it) }
                uri
            } else {
                val folder = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "SelfAttendance"
                )
                if (!folder.exists()) folder.mkdirs()
                val file = File(folder, fileName)
                FileOutputStream(file).use { pdfDoc.writeTo(it) }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            }

        } finally {
            pdfDoc.close()
        }
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    private fun draw(
        canvas        : Canvas,
        profile       : UserProfile,
        attendanceList: List<Attendance>,
        yearMonth     : YearMonth
    ) {
        val p   = Paint(Paint.ANTI_ALIAS_FLAG)
        var y   = 0f
        val cw  = PAGE_W - MARGIN * 2       // content width

        // ── 1. Header bar ─────────────────────────────────────────────────────
        p.color = CLR_GREEN
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 88f, p)

        // Diagonal accent strip (decorative)
        p.color = CLR_GREEN_DARK
        val path = android.graphics.Path()
        path.moveTo(PAGE_W - 120f, 0f)
        path.lineTo(PAGE_W.toFloat(), 0f)
        path.lineTo(PAGE_W.toFloat(), 88f)
        path.lineTo(PAGE_W - 220f, 88f)
        path.close()
        canvas.drawPath(path, p)

        // "SALARY SLIP" title
        p.color = CLR_WHITE
        p.textSize = 24f
        p.isFakeBoldText = true
        canvas.drawText("SALARY SLIP", MARGIN, 40f, p)

        // Month + year
        val monthLabel = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        p.textSize = 13f
        p.isFakeBoldText = false
        canvas.drawText(monthLabel, MARGIN, 64f, p)

        // App name (top-right)
        p.textSize = 11f
        p.color = Color.parseColor("#CCFFFFFF")
        val appName = "Self Attendance"
        canvas.drawText(appName, PAGE_W - MARGIN - p.measureText(appName), 36f, p)
        val genStr = "Generated: ${LocalDate.now()}"
        canvas.drawText(genStr, PAGE_W - MARGIN - p.measureText(genStr), 56f, p)

        y = 100f

        // ── 2. Employee info card ─────────────────────────────────────────────
        p.color = CLR_LIGHT_BG
        p.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 64f), 10f, 10f, p)

        // Left border accent
        p.color = CLR_GREEN
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 4f, y + 64f), 4f, 4f, p)

        p.color = CLR_TEXT_LIGHT
        p.textSize = 10f
        canvas.drawText("EMPLOYEE", MARGIN + 14f, y + 18f, p)

        p.color = CLR_TEXT_DARK
        p.textSize = 17f
        p.isFakeBoldText = true
        canvas.drawText(profile.name.ifBlank { "—" }, MARGIN + 14f, y + 46f, p)

        // Right side — employee ID
        p.isFakeBoldText = false
        p.textSize = 10f
        p.color = CLR_TEXT_LIGHT
        val eidLabel = "EMPLOYEE ID"
        canvas.drawText(eidLabel, PAGE_W - MARGIN - p.measureText(eidLabel), y + 18f, p)

        val eidVal = if (profile.uid.length > 10) "…${profile.uid.takeLast(10)}" else profile.uid.ifBlank { "—" }
        p.textSize = 12f
        p.color = CLR_TEXT_MED
        canvas.drawText(eidVal, PAGE_W - MARGIN - p.measureText(eidVal), y + 36f, p)

        y += 80f

        // ── 3. Attendance Summary ─────────────────────────────────────────────
        p.isFakeBoldText = true
        p.textSize = 12f
        p.color = CLR_TEXT_DARK
        canvas.drawText("ATTENDANCE SUMMARY", MARGIN, y, p)

        y += 6f
        hLine(canvas, p, CLR_GREEN, y)
        y += 14f

        val presentDays   = attendanceList.count { it.status == "PRESENT" }
        val halfDays      = attendanceList.count { it.status == "HALF" || it.status == "HALF_DAY" }
        val absentDays    = attendanceList.count { it.status == "ABSENT" }
        val totalOT       = attendanceList.sumOf { it.overtimeHours }
        val effectiveDays = presentDays + halfDays * 0.5
        val daysInMonth   = yearMonth.lengthOfMonth()

        // 4-column stat boxes
        val boxW   = cw / 4f - 4f
        val boxH   = 56f
        val cols   = listOf(
            Triple("PRESENT",  "$presentDays days", CLR_GREEN),
            Triple("HALF DAY", "$halfDays days",    CLR_GOLD),
            Triple("ABSENT",   "$absentDays days",  CLR_RED),
            Triple("OVERTIME", "${String.format(Locale.ENGLISH, "%.1f", totalOT)} h", CLR_TEXT_MED)
        )
        cols.forEachIndexed { i, (label, value, accentColor) ->
            val bx = MARGIN + i * (boxW + 5.3f)

            p.color = CLR_LIGHT_BG
            canvas.drawRoundRect(RectF(bx, y, bx + boxW, y + boxH), 8f, 8f, p)

            // top accent stripe
            p.color = accentColor
            canvas.drawRoundRect(RectF(bx, y, bx + boxW, y + 4f), 4f, 4f, p)

            p.color = CLR_TEXT_LIGHT
            p.textSize = 9f
            p.isFakeBoldText = false
            canvas.drawText(label, bx + 8f, y + 18f, p)

            p.color = CLR_TEXT_DARK
            p.textSize = 15f
            p.isFakeBoldText = true
            canvas.drawText(value, bx + 8f, y + 42f, p)
        }
        y += boxH + 10f

        // Effective days note
        p.color = CLR_TEXT_LIGHT
        p.textSize = 9.5f
        p.isFakeBoldText = false
        canvas.drawText(
            "Working days in month: $daysInMonth   |   Effective days worked: ${"%.1f".format(effectiveDays)}   |   Days logged: ${presentDays + halfDays + absentDays}",
            MARGIN, y, p
        )
        y += 20f

        // ── 4. Salary Breakdown ───────────────────────────────────────────────
        p.isFakeBoldText = true
        p.textSize = 12f
        p.color = CLR_TEXT_DARK
        canvas.drawText("SALARY BREAKDOWN", MARGIN, y, p)

        y += 6f
        hLine(canvas, p, CLR_GREEN, y)
        y += 14f

        val perDay     = SalaryCalculator.perDaySalary(profile.monthlySalary)
        val basicEarned = presentDays * perDay + halfDays * (perDay / 2.0)
        val otPay      = totalOT * profile.overtimeRate
        val netPayable = SalaryCalculator.calculate(profile, attendanceList)

        val rowH = 28f

        salaryRow(canvas, p, y, "Monthly Salary (CTC)",
            "₹ ${fmt(profile.monthlySalary)}", shade = false)
        y += rowH
        salaryRow(canvas, p, y, "Per Day Rate  (÷ 30 days)",
            "₹ ${fmt(perDay)}", shade = true)
        y += rowH
        salaryRow(canvas, p, y, "Basic Earned  (${presentDays} Present + ${halfDays} Half)",
            "₹ ${fmt(basicEarned)}", shade = false)
        y += rowH

        if (otPay > 0.0) {
            salaryRow(canvas, p, y,
                "Overtime Pay  (${String.format(Locale.ENGLISH, "%.1f", totalOT)}h × ₹${fmt(profile.overtimeRate)}/h)",
                "+ ₹ ${fmt(otPay)}", shade = true, valueColor = CLR_GOLD)
            y += rowH
        }

        // Thin divider
        p.color = CLR_DIVIDER
        p.strokeWidth = 0.8f
        p.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN, y + 4f, PAGE_W - MARGIN, y + 4f, p)
        p.style = Paint.Style.FILL
        y += 14f

        // NET PAYABLE — filled green row
        p.color = CLR_GREEN
        canvas.drawRoundRect(RectF(MARGIN, y - 8f, PAGE_W - MARGIN, y + 24f), 8f, 8f, p)

        p.color = CLR_WHITE
        p.textSize = 12f
        p.isFakeBoldText = true
        canvas.drawText("NET PAYABLE", MARGIN + 12f, y + 12f, p)

        val netStr = "₹ ${fmt(netPayable)}"
        canvas.drawText(netStr, PAGE_W - MARGIN - p.measureText(netStr) - 12f, y + 12f, p)

        y += 40f

        // ── 5. Signature Area ─────────────────────────────────────────────────
        p.color = CLR_DIVIDER
        p.strokeWidth = 0.8f
        p.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN, y, MARGIN + 140f, y, p)
        canvas.drawLine(PAGE_W - MARGIN - 140f, y, PAGE_W - MARGIN, y, p)
        p.style = Paint.Style.FILL

        p.color = CLR_TEXT_LIGHT
        p.textSize = 9f
        p.isFakeBoldText = false
        canvas.drawText("Employee Signature", MARGIN, y + 14f, p)
        val sigRightLabel = "Authorised Signatory"
        canvas.drawText(sigRightLabel, PAGE_W - MARGIN - p.measureText(sigRightLabel), y + 14f, p)

        y += 38f

        // ── 6. Footer ─────────────────────────────────────────────────────────
        p.color = CLR_LIGHT_BG
        canvas.drawRect(0f, PAGE_H - 32f, PAGE_W.toFloat(), PAGE_H.toFloat(), p)

        p.color = CLR_GREEN
        canvas.drawRect(0f, PAGE_H - 32f, PAGE_W.toFloat(), PAGE_H - 31f, p)

        p.color = CLR_TEXT_LIGHT
        p.textSize = 8.5f
        val footer = "This is a computer-generated document  •  Self Attendance App  •  ${LocalDate.now()}"
        canvas.drawText(footer, MARGIN, PAGE_H - 10f, p)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun hLine(canvas: Canvas, p: Paint, color: Int, y: Float) {
        p.color = color
        p.strokeWidth = 1.8f
        p.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, p)
        p.style = Paint.Style.FILL
    }

    private fun salaryRow(
        canvas     : Canvas,
        p          : Paint,
        y          : Float,
        label      : String,
        value      : String,
        shade      : Boolean,
        valueColor : Int = CLR_TEXT_DARK
    ) {
        if (shade) {
            p.color = CLR_LIGHT_BG
            p.style = Paint.Style.FILL
            canvas.drawRect(MARGIN, y - 18f, PAGE_W - MARGIN, y + 8f, p)
        }
        p.color = CLR_TEXT_MED
        p.textSize = 11f
        p.isFakeBoldText = false
        p.style = Paint.Style.FILL
        canvas.drawText(label, MARGIN + 10f, y, p)

        p.color = valueColor
        p.isFakeBoldText = true
        val vw = p.measureText(value)
        canvas.drawText(value, PAGE_W - MARGIN - vw - 10f, y, p)
    }

    private fun fmt(amount: Double): String =
        if (amount == amount.toLong().toDouble())
            String.format(Locale.ENGLISH, "%,d", amount.toLong())
        else
            String.format(Locale.ENGLISH, "%,.2f", amount)
}
