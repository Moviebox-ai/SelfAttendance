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
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfExporter {

    // ── A4 page (595 × 842 pt) ───────────────────────────────────────────────
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 32f

    // ── Global colour palette ─────────────────────────────────────────────────
    private val CLR_GREEN       = Color.parseColor("#00C853")
    private val CLR_GREEN_DARK  = Color.parseColor("#00963D")
    private val CLR_GREEN_LIGHT = Color.parseColor("#E8F5E9")
    private val CLR_RED         = Color.parseColor("#E53935")
    private val CLR_RED_LIGHT   = Color.parseColor("#FFEBEE")
    private val CLR_GOLD        = Color.parseColor("#FFB300")
    private val CLR_GOLD_LIGHT  = Color.parseColor("#FFF8E1")
    private val CLR_BLUE        = Color.parseColor("#1565C0")
    private val CLR_DIVIDER     = Color.parseColor("#BDBDBD")
    private val CLR_TEXT_DARK   = Color.parseColor("#1A1A2E")
    private val CLR_TEXT_MED    = Color.parseColor("#424242")
    private val CLR_TEXT_LIGHT  = Color.parseColor("#757575")
    private val CLR_WHITE       = Color.WHITE
    private val CLR_ROW_ALT     = Color.parseColor("#F9F9F9")

    // ── Month header accent colours (cycles for each month) ──────────────────
    // Each pair: (header bg, header text/light bg)
    private val MONTH_ACCENTS = listOf(
        Pair(Color.parseColor("#1565C0"), Color.parseColor("#E3F2FD")), // Blue
        Pair(Color.parseColor("#6A1B9A"), Color.parseColor("#F3E5F5")), // Purple
        Pair(Color.parseColor("#00838F"), Color.parseColor("#E0F7FA")), // Teal
        Pair(Color.parseColor("#E65100"), Color.parseColor("#FFF3E0")), // Deep Orange
        Pair(Color.parseColor("#2E7D32"), Color.parseColor("#E8F5E9")), // Dark Green
        Pair(Color.parseColor("#C62828"), Color.parseColor("#FFEBEE")), // Dark Red
        Pair(Color.parseColor("#4527A0"), Color.parseColor("#EDE7F6")), // Deep Purple
        Pair(Color.parseColor("#00695C"), Color.parseColor("#E0F2F1")), // Dark Teal
        Pair(Color.parseColor("#F57F17"), Color.parseColor("#FFFDE7")), // Amber
        Pair(Color.parseColor("#1A237E"), Color.parseColor("#E8EAF6")), // Indigo
        Pair(Color.parseColor("#880E4F"), Color.parseColor("#FCE4EC")), // Pink
        Pair(Color.parseColor("#37474F"), Color.parseColor("#ECEFF1")), // Blue Grey
    )

    // ── Column X positions ────────────────────────────────────────────────────
    private const val COL_DATE   = MARGIN + 8f
    private const val COL_STATUS = MARGIN + 148f
    private const val COL_WORKED = MARGIN + 288f
    private const val COL_OT     = MARGIN + 406f
    private const val ROW_H      = 24f

    /**
     * Export attendance records grouped by month, each month with a distinct
     * coloured section header + mini attendance summary.
     * Returns the Uri of the saved PDF.
     */
    fun export(context: Context, list: List<Attendance>): Uri {
        val sorted = list.sortedBy { it.date }

        // ── Group by month ────────────────────────────────────────────────────
        // Try to parse "yyyy-MM-dd"; fall back to treating first 7 chars as key
        val grouped: Map<String, List<Attendance>> = sorted
            .groupBy { att ->
                runCatching {
                    val d = LocalDate.parse(att.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    d.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                }.getOrElse {
                    if (att.date.length >= 7) att.date.substring(0, 7) else att.date
                }
            }
            .toSortedMap()                       // chronological order

        // ── Overall totals for page-1 summary ────────────────────────────────
        val totalPresent = sorted.count { it.status.uppercase() == "PRESENT" }
        val totalHalf    = sorted.count { it.status.uppercase() in setOf("HALF", "HALF_DAY") }
        val totalAbsent  = sorted.count { it.status.uppercase() == "ABSENT" }
        val totalOT      = sorted.sumOf { it.overtimeHours }

        val pdfDoc  = PdfDocument()
        var pageNum = 1
        var page    = startPage(pdfDoc, pageNum)
        var canvas  = page.canvas
        val paint   = Paint(Paint.ANTI_ALIAS_FLAG)

        // Page 1: global header + overall summary
        var y = drawPageHeader(canvas, paint, pageNum)
        y = drawOverallSummary(canvas, paint, y, totalPresent, totalHalf, totalAbsent, totalOT, grouped.size)

        var monthIndex = 0

        for ((monthKey, records) in grouped) {
            val accent = MONTH_ACCENTS[monthIndex % MONTH_ACCENTS.size]
            monthIndex++

            // Month section header needs ~56pt, ensure it fits
            val headerNeed = 56f + 26f  // month header + table col header
            if (y + headerNeed > PAGE_H - 50f) {
                drawFooter(canvas, paint, pageNum)
                pdfDoc.finishPage(page)
                pageNum++
                page   = startPage(pdfDoc, pageNum)
                canvas = page.canvas
                y = drawPageHeader(canvas, paint, pageNum, continuation = true)
            }

            // Draw month section header
            y = drawMonthHeader(canvas, paint, y, monthKey, records, accent)

            // Column labels under the month header
            y = drawTableColLabels(canvas, paint, y, accent.first)

            // Rows for this month
            records.forEachIndexed { idx, att ->
                if (y + ROW_H > PAGE_H - 50f) {
                    drawFooter(canvas, paint, pageNum)
                    pdfDoc.finishPage(page)
                    pageNum++
                    page   = startPage(pdfDoc, pageNum)
                    canvas = page.canvas
                    y = drawPageHeader(canvas, paint, pageNum, continuation = true)
                    // Repeat month label on new page
                    y = drawMonthContinuationBadge(canvas, paint, y, monthKey, accent)
                    y = drawTableColLabels(canvas, paint, y, accent.first)
                }
                drawRow(canvas, paint, att, idx, y, accent.second)
                y += ROW_H
            }

            y += 10f  // gap between months
        }

        drawFooter(canvas, paint, pageNum)
        pdfDoc.finishPage(page)

        return saveAndGetUri(context, pdfDoc)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page header
    // ─────────────────────────────────────────────────────────────────────────

    private fun startPage(pdf: PdfDocument, num: Int): PdfDocument.Page =
        pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, num).create())

    private fun drawPageHeader(
        c: Canvas, p: Paint, pageNum: Int, continuation: Boolean = false
    ): Float {
        p.color = CLR_GREEN
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 76f, p)

        // Dark accent triangle (top-right)
        p.color = CLR_GREEN_DARK
        val path = android.graphics.Path().apply {
            moveTo(PAGE_W - 90f, 0f)
            lineTo(PAGE_W.toFloat(), 0f)
            lineTo(PAGE_W.toFloat(), 76f)
            lineTo(PAGE_W - 190f, 76f)
            close()
        }
        c.drawPath(path, p)

        p.color = CLR_WHITE
        p.textSize = 20f
        p.isFakeBoldText = true
        c.drawText("Self Attendance", MARGIN, 30f, p)

        p.textSize = 11f
        p.isFakeBoldText = false
        p.color = Color.parseColor("#CCFFFFFF")
        c.drawText(
            if (continuation) "Attendance Report (continued)" else "Monthly Attendance Report",
            MARGIN, 50f, p
        )

        val dateStr = "Generated: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
        p.textSize = 9f
        c.drawText(dateStr, PAGE_W - MARGIN - p.measureText(dateStr), 32f, p)
        val pgStr = "Page $pageNum"
        c.drawText(pgStr, PAGE_W - MARGIN - p.measureText(pgStr), 52f, p)

        return 86f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Overall summary card (page 1 only)
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawOverallSummary(
        c: Canvas, p: Paint, startY: Float,
        present: Int, half: Int, absent: Int, ot: Double, months: Int
    ): Float {
        val y = startY

        p.color = CLR_GREEN_LIGHT
        p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 68f), 10f, 10f, p)

        p.color = CLR_GREEN
        c.drawRoundRect(RectF(MARGIN, y, MARGIN + 4f, y + 68f), 4f, 4f, p)

        p.color = CLR_GREEN_DARK
        p.textSize = 10.5f
        p.isFakeBoldText = true
        c.drawText("OVERALL SUMMARY  —  $months month(s)", MARGIN + 14f, y + 16f, p)

        val stats = listOf(
            Triple("Present",  "$present days",  CLR_GREEN),
            Triple("Half Day", "$half days",     CLR_GOLD),
            Triple("Absent",   "$absent days",   CLR_RED),
            Triple("Overtime", String.format(Locale.ENGLISH, "%.1fh", ot), CLR_BLUE),
        )
        val colW = (PAGE_W - MARGIN * 2 - 16f) / 4f
        stats.forEachIndexed { i, (label, value, color) ->
            val x = MARGIN + 14f + i * (colW + 2f)
            p.color = CLR_TEXT_LIGHT; p.textSize = 8.5f; p.isFakeBoldText = false
            c.drawText(label, x, y + 36f, p)
            p.color = color; p.textSize = 15f; p.isFakeBoldText = true
            c.drawText(value, x, y + 56f, p)
        }
        return y + 78f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Month section header
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawMonthHeader(
        c: Canvas, p: Paint, startY: Float,
        monthKey: String,
        records: List<Attendance>,
        accent: Pair<Int, Int>
    ): Float {
        val y = startY
        val (accentDark, accentLight) = accent

        // Parse display name: "2025-01" → "January 2025"
        val displayName = runCatching {
            val ym = YearMonth.parse(monthKey, DateTimeFormatter.ofPattern("yyyy-MM"))
            ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { monthKey }

        val present = records.count { it.status.uppercase() == "PRESENT" }
        val half    = records.count { it.status.uppercase() in setOf("HALF", "HALF_DAY") }
        val absent  = records.count { it.status.uppercase() == "ABSENT" }
        val ot      = records.sumOf { it.overtimeHours }
        val total   = records.size

        // Full-width section band
        p.color = accentDark
        p.style = Paint.Style.FILL
        c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 28f, p)

        // Month name
        p.color = CLR_WHITE
        p.textSize = 13f
        p.isFakeBoldText = true
        c.drawText("📅  $displayName", MARGIN + 10f, y + 19f, p)

        // Total days badge (right)
        val badge = "$total days"
        p.textSize = 9.5f
        p.isFakeBoldText = false
        p.color = Color.parseColor("#CCFFFFFF")
        c.drawText(badge, PAGE_W - MARGIN - p.measureText(badge) - 6f, y + 19f, p)

        // Mini stats row below the band
        p.color = accentLight
        c.drawRect(MARGIN, y + 28f, PAGE_W - MARGIN, y + 54f, p)

        val miniStats = listOf(
            Pair("✓ Present: $present", CLR_GREEN),
            Pair("½ Half: $half",       CLR_GOLD),
            Pair("✗ Absent: $absent",   CLR_RED),
            Pair("⊕ OT: ${String.format(Locale.ENGLISH, "%.1f", ot)}h", CLR_BLUE),
        )
        val segW = (PAGE_W - MARGIN * 2) / 4f
        miniStats.forEachIndexed { i, (label, color) ->
            val x = MARGIN + i * segW + 8f
            p.color = color; p.textSize = 9f; p.isFakeBoldText = true
            c.drawText(label, x, y + 44f, p)
        }

        return y + 56f
    }

    /** Small "continued" badge when a month spills onto a new page */
    private fun drawMonthContinuationBadge(
        c: Canvas, p: Paint, startY: Float,
        monthKey: String, accent: Pair<Int, Int>
    ): Float {
        val displayName = runCatching {
            val ym = YearMonth.parse(monthKey, DateTimeFormatter.ofPattern("yyyy-MM"))
            ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        }.getOrElse { monthKey }

        p.color = accent.first
        p.style = Paint.Style.FILL
        c.drawRect(MARGIN, startY, PAGE_W - MARGIN, startY + 22f, p)
        p.color = CLR_WHITE; p.textSize = 10f; p.isFakeBoldText = true
        c.drawText("$displayName  (continued)", MARGIN + 10f, startY + 15f, p)
        return startY + 24f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Column label row
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawTableColLabels(c: Canvas, p: Paint, startY: Float, accentColor: Int): Float {
        p.color = Color.argb(30, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        p.style = Paint.Style.FILL
        c.drawRect(MARGIN, startY, PAGE_W - MARGIN, startY + 20f, p)

        p.color = accentColor; p.textSize = 9f; p.isFakeBoldText = true
        c.drawText("DATE",       COL_DATE,   startY + 14f, p)
        c.drawText("STATUS",     COL_STATUS, startY + 14f, p)
        c.drawText("WORKED HRS", COL_WORKED, startY + 14f, p)
        c.drawText("OVERTIME",   COL_OT,     startY + 14f, p)
        return startY + 22f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single attendance row
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawRow(
        c: Canvas, p: Paint, att: Attendance, index: Int, y: Float, monthLightBg: Int
    ) {
        val status = att.status.uppercase()

        // Row background
        val bgColor = when (status) {
            "PRESENT"              -> if (index % 2 == 0) CLR_GREEN_LIGHT else Color.parseColor("#DCEDC8")
            "ABSENT"               -> if (index % 2 == 0) CLR_RED_LIGHT   else Color.parseColor("#FFCDD2")
            "HALF", "HALF_DAY"     -> if (index % 2 == 0) CLR_GOLD_LIGHT  else Color.parseColor("#FFE082")
            else                   -> if (index % 2 == 0) monthLightBg     else CLR_ROW_ALT
        }
        p.color = bgColor; p.style = Paint.Style.FILL
        c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + ROW_H, p)

        // Left colour stripe
        val stripeColor = when (status) {
            "PRESENT"          -> CLR_GREEN
            "ABSENT"           -> CLR_RED
            "HALF", "HALF_DAY" -> CLR_GOLD
            else               -> CLR_DIVIDER
        }
        p.color = stripeColor
        c.drawRect(MARGIN, y, MARGIN + 4f, y + ROW_H, p)

        // Date
        p.color = CLR_TEXT_DARK; p.textSize = 10f; p.isFakeBoldText = false; p.style = Paint.Style.FILL
        c.drawText(att.date, COL_DATE, y + 16f, p)

        // Status pill badge
        val statusLabel = when (status) {
            "PRESENT"          -> "Present"
            "ABSENT"           -> "Absent"
            "HALF", "HALF_DAY" -> "Half Day"
            else               -> att.status
        }
        val badgePad = 5f
        p.textSize = 9f; p.isFakeBoldText = true
        val badgeW = p.measureText(statusLabel) + badgePad * 2
        p.color = stripeColor
        c.drawRoundRect(RectF(COL_STATUS - 1f, y + 5f, COL_STATUS - 1f + badgeW, y + ROW_H - 4f), 4f, 4f, p)
        p.color = CLR_WHITE
        c.drawText(statusLabel, COL_STATUS + badgePad - 1f, y + 15.5f, p)

        // Worked hours
        p.color = CLR_TEXT_MED; p.textSize = 10f; p.isFakeBoldText = false; p.style = Paint.Style.FILL
        c.drawText("${att.workedHours}h", COL_WORKED, y + 16f, p)

        // Overtime
        if (att.overtimeHours > 0) {
            p.color = CLR_GOLD; p.isFakeBoldText = true
            c.drawText("+${att.overtimeHours}h", COL_OT, y + 16f, p)
        } else {
            p.color = CLR_TEXT_LIGHT; p.isFakeBoldText = false
            c.drawText("—", COL_OT, y + 16f, p)
        }

        // Thin bottom divider
        p.color = CLR_DIVIDER; p.strokeWidth = 0.4f; p.style = Paint.Style.STROKE
        c.drawLine(MARGIN + 4f, y + ROW_H, PAGE_W - MARGIN, y + ROW_H, p)
        p.style = Paint.Style.FILL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Footer
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawFooter(c: Canvas, p: Paint, pageNum: Int) {
        p.color = CLR_GREEN_LIGHT; p.style = Paint.Style.FILL
        c.drawRect(0f, PAGE_H - 28f, PAGE_W.toFloat(), PAGE_H.toFloat(), p)
        p.color = CLR_GREEN
        c.drawRect(0f, PAGE_H - 28f, PAGE_W.toFloat(), PAGE_H - 27f, p)
        p.color = CLR_TEXT_LIGHT; p.textSize = 8f; p.isFakeBoldText = false
        c.drawText(
            "Self Attendance App  •  Auto-generated  •  ${LocalDate.now()}",
            MARGIN, PAGE_H - 8f, p
        )
        val pg = "Page $pageNum"
        c.drawText(pg, PAGE_W - MARGIN - p.measureText(pg), PAGE_H - 8f, p)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save & return URI
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveAndGetUri(context: Context, pdfDoc: PdfDocument): Uri {
        // and the previous PDF is not silently overwritten.
        val dateStamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "attendance_report_$dateStamp.pdf"

        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/SelfAttendance")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), cv
                ) ?: throw Exception("Failed to create MediaStore entry")
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
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
        } finally {
            pdfDoc.close()
        }
    }
}
