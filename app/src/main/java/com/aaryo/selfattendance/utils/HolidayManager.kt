package com.aaryo.selfattendance.utils

import java.time.LocalDate
import java.time.YearMonth

data class HolidayInfo(
    val date: String,          // yyyy-MM-dd
    val nameEn: String,
    val nameHi: String,
    val isNational: Boolean = true
)

object HolidayManager {

    private val holidays = listOf(
        // ── 2025 Holidays ──
        HolidayInfo("2025-01-01", "New Year's Day", "नव वर्ष"),
        HolidayInfo("2025-01-14", "Makar Sankranti / Pongal", "मकर संक्रांति / पोंगल"),
        HolidayInfo("2025-01-26", "Republic Day", "गणतंत्र दिवस"),
        HolidayInfo("2025-02-26", "Maha Shivratri", "महाशिवरात्रि"),
        HolidayInfo("2025-03-14", "Holi", "होली"),
        HolidayInfo("2025-03-31", "Eid-ul-Fitr", "ईद-उल-फ़ितर"),
        HolidayInfo("2025-04-10", "Mahavir Jayanti", "महावीर जयंती"),
        HolidayInfo("2025-04-14", "Dr. Ambedkar Jayanti", "डॉ. अम्बेडकर जयंती"),
        HolidayInfo("2025-04-18", "Good Friday", "गुड फ्राइडे"),
        HolidayInfo("2025-05-01", "Labour Day / May Day", "मजदूर दिवस / मई दिवस"),
        HolidayInfo("2025-05-12", "Buddha Purnima", "बुद्ध पूर्णिमा"),
        HolidayInfo("2025-06-07", "Eid-ul-Adha (Bakrid)", "बकरीद"),
        HolidayInfo("2025-07-06", "Muharram", "मोहर्रम"),
        HolidayInfo("2025-08-15", "Independence Day", "स्वतंत्रता दिवस"),
        HolidayInfo("2025-08-16", "Janmashtami", "श्री कृष्ण जन्माष्टमी"),
        HolidayInfo("2025-08-27", "Ganesh Chaturthi", "गणेश चतुर्थी"),
        HolidayInfo("2025-10-02", "Mahatma Gandhi Jayanti", "गांधी जयंती"),
        HolidayInfo("2025-10-02", "Dussehra (Vijayadashami)", "दशहरा (विजयदशमी)"),
        HolidayInfo("2025-10-20", "Diwali (Deepavali)", "दीपावली"),
        HolidayInfo("2025-10-22", "Govardhan Puja / Bhai Dooj", "गोवर्धन पूजा / भाई दूज"),
        HolidayInfo("2025-10-28", "Chhath Puja", "छठ पूजा"),
        HolidayInfo("2025-11-05", "Guru Nanak Jayanti", "गुरु नानक जयंती"),
        HolidayInfo("2025-12-25", "Christmas Day", "क्रिसमस"),

        // ── 2026 Holidays ──
        HolidayInfo("2026-01-01", "New Year's Day", "नव वर्ष"),
        HolidayInfo("2026-01-14", "Makar Sankranti / Pongal", "मकर संक्रांति / पोंगल"),
        HolidayInfo("2026-01-26", "Republic Day", "गणतंत्र दिवस"),
        HolidayInfo("2026-02-15", "Maha Shivratri", "महाशिवरात्रि"),
        HolidayInfo("2026-03-04", "Holi", "होली"),
        HolidayInfo("2026-03-21", "Eid-ul-Fitr", "ईद-उल-फ़ितर"),
        HolidayInfo("2026-03-31", "Mahavir Jayanti", "महावीर जयंती"),
        HolidayInfo("2026-04-03", "Good Friday", "गुड फ्राइडे"),
        HolidayInfo("2026-04-14", "Dr. Ambedkar Jayanti", "डॉ. अम्बेडकर जयंती"),
        HolidayInfo("2026-05-01", "Labour Day / May Day", "मजदूर दिवस / मई दिवस"),
        HolidayInfo("2026-05-01", "Buddha Purnima", "बुद्ध पूर्णिमा"),
        HolidayInfo("2026-05-27", "Eid-ul-Adha (Bakrid)", "बकरीद"),
        HolidayInfo("2026-06-25", "Muharram", "मोहर्रम"),
        HolidayInfo("2026-08-15", "Independence Day", "स्वतंत्रता दिवस"),
        HolidayInfo("2026-08-28", "Raksha Bandhan", "रक्षा बंधन"),
        HolidayInfo("2026-09-04", "Janmashtami", "श्री कृष्ण जन्माष्टमी"),
        HolidayInfo("2026-09-14", "Ganesh Chaturthi", "गणेश चतुर्थी"),
        HolidayInfo("2026-10-02", "Mahatma Gandhi Jayanti", "गांधी जयंती"),
        HolidayInfo("2026-10-20", "Dussehra (Vijayadashami)", "दशहरा (विजयदशमी)"),
        HolidayInfo("2026-11-08", "Diwali (Deepavali)", "दीपावली"),
        HolidayInfo("2026-11-09", "Govardhan Puja", "गोवर्धन पूजा"),
        HolidayInfo("2026-11-10", "Bhai Dooj", "भाई दूज"),
        HolidayInfo("2026-11-15", "Chhath Puja", "छठ पूजा"),
        HolidayInfo("2026-11-24", "Guru Nanak Jayanti", "गुरु नानक जयंती"),
        HolidayInfo("2026-12-25", "Christmas Day", "क्रिसमस"),

        // ── 2027 Holidays ──
        HolidayInfo("2027-01-01", "New Year's Day", "नव वर्ष"),
        HolidayInfo("2027-01-14", "Makar Sankranti / Pongal", "मकर संक्रांति / पोंगल"),
        HolidayInfo("2027-01-26", "Republic Day", "गणतंत्र दिवस"),
        HolidayInfo("2027-03-07", "Maha Shivratri", "महाशिवरात्रि"),
        HolidayInfo("2027-03-23", "Holi", "होली"),
        HolidayInfo("2027-04-10", "Eid-ul-Fitr", "ईद-उल-फ़ितर"),
        HolidayInfo("2027-04-14", "Dr. Ambedkar Jayanti", "डॉ. अम्बेडकर जयंती"),
        HolidayInfo("2027-04-23", "Good Friday", "गुड फ्राइडे"),
        HolidayInfo("2027-05-01", "Labour Day / May Day", "मजदूर दिवस / मई दिवस"),
        HolidayInfo("2027-08-15", "Independence Day", "स्वतंत्रता दिवस"),
        HolidayInfo("2027-10-02", "Mahatma Gandhi Jayanti", "गांधी जयंती"),
        HolidayInfo("2027-10-09", "Dussehra (Vijayadashami)", "दशहरा (विजयदशमी)"),
        HolidayInfo("2027-10-29", "Diwali (Deepavali)", "दीपावली"),
        HolidayInfo("2027-12-25", "Christmas Day", "क्रिसमस")
    )

    private val holidayMap: Map<String, HolidayInfo> = holidays.associateBy { it.date }

    fun getHoliday(date: String): HolidayInfo? = holidayMap[date]

    fun isHoliday(date: String): Boolean = holidayMap.containsKey(date)

    fun getHolidaysForMonth(yearMonth: YearMonth): List<HolidayInfo> {
        val prefix = yearMonth.toString() // "yyyy-MM"
        return holidays.filter { it.date.startsWith(prefix) }
    }

    fun getHolidaysForMonth(year: Int, month: Int): List<HolidayInfo> {
        val monthStr = if (month < 10) "0$month" else "$month"
        val prefix = "$year-$monthStr"
        return holidays.filter { it.date.startsWith(prefix) }
    }

    fun getHolidaysForYear(year: Int): List<HolidayInfo> {
        val prefix = "$year-"
        return holidays.filter { it.date.startsWith(prefix) }
    }
}
