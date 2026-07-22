package com.aaryo.selfattendance.utils

// ═══════════════════════════════════════════════════════════════
//  CurrencyManager — Global Salary Currency Support
//
//  Supports 40+ currencies. The selected currency is persisted
//  in PreferencesManager.selectedCurrency (ISO 4217 code).
//  Use CurrencyManager.getSymbol(code) anywhere a currency
//  symbol is needed, e.g. formatMoney(currencySymbol = ...).
// ═══════════════════════════════════════════════════════════════

object CurrencyManager {

    data class Currency(
        val code: String,
        val symbol: String,
        val name: String,
        val flag: String = ""
    )

    val SUPPORTED_CURRENCIES: List<Currency> = listOf(
        // ── South Asia ──────────────────────────────────────────────
        Currency("INR", "₹",    "Indian Rupee",           "🇮🇳"),
        Currency("BDT", "৳",    "Bangladeshi Taka",        "🇧🇩"),
        Currency("PKR", "Rs",   "Pakistani Rupee",         "🇵🇰"),
        Currency("LKR", "Rs",   "Sri Lankan Rupee",        "🇱🇰"),
        Currency("NPR", "रू",   "Nepalese Rupee",          "🇳🇵"),
        // ── East Asia ───────────────────────────────────────────────
        Currency("CNY", "¥",    "Chinese Yuan",            "🇨🇳"),
        Currency("JPY", "¥",    "Japanese Yen",            "🇯🇵"),
        Currency("KRW", "₩",    "South Korean Won",        "🇰🇷"),
        Currency("HKD", "HK\$", "Hong Kong Dollar",        "🇭🇰"),
        Currency("TWD", "NT\$", "New Taiwan Dollar",       "🇹🇼"),
        // ── Southeast Asia ──────────────────────────────────────────
        Currency("MYR", "RM",   "Malaysian Ringgit",       "🇲🇾"),
        Currency("IDR", "Rp",   "Indonesian Rupiah",       "🇮🇩"),
        Currency("SGD", "S\$",  "Singapore Dollar",        "🇸🇬"),
        Currency("THB", "฿",    "Thai Baht",               "🇹🇭"),
        Currency("PHP", "₱",    "Philippine Peso",         "🇵🇭"),
        Currency("VND", "₫",    "Vietnamese Dong",         "🇻🇳"),
        Currency("MMK", "K",    "Myanmar Kyat",            "🇲🇲"),
        // ── Middle East ─────────────────────────────────────────────
        Currency("AED", "د.إ",  "UAE Dirham",              "🇦🇪"),
        Currency("SAR", "﷼",   "Saudi Riyal",             "🇸🇦"),
        Currency("QAR", "ر.ق", "Qatari Riyal",            "🇶🇦"),
        Currency("KWD", "د.ك", "Kuwaiti Dinar",           "🇰🇼"),
        Currency("BHD", "BD",   "Bahraini Dinar",          "🇧🇭"),
        Currency("OMR", "﷼",   "Omani Rial",              "🇴🇲"),
        // ── North America ───────────────────────────────────────────
        Currency("USD", "\$",   "US Dollar",               "🇺🇸"),
        Currency("CAD", "CA\$", "Canadian Dollar",         "🇨🇦"),
        Currency("MXN", "\$",   "Mexican Peso",            "🇲🇽"),
        // ── Europe ──────────────────────────────────────────────────
        Currency("EUR", "€",    "Euro",                    "🇪🇺"),
        Currency("GBP", "£",    "British Pound",           "🇬🇧"),
        Currency("CHF", "Fr",   "Swiss Franc",             "🇨🇭"),
        Currency("SEK", "kr",   "Swedish Krona",           "🇸🇪"),
        Currency("NOK", "kr",   "Norwegian Krone",         "🇳🇴"),
        Currency("DKK", "kr",   "Danish Krone",            "🇩🇰"),
        Currency("PLN", "zł",   "Polish Zloty",            "🇵🇱"),
        Currency("TRY", "₺",    "Turkish Lira",            "🇹🇷"),
        Currency("RUB", "₽",    "Russian Ruble",           "🇷🇺"),
        // ── Africa ──────────────────────────────────────────────────
        Currency("ZAR", "R",    "South African Rand",      "🇿🇦"),
        Currency("NGN", "₦",    "Nigerian Naira",          "🇳🇬"),
        Currency("EGP", "E£",   "Egyptian Pound",          "🇪🇬"),
        Currency("KES", "KSh",  "Kenyan Shilling",         "🇰🇪"),
        Currency("GHS", "₵",    "Ghanaian Cedi",           "🇬🇭"),
        // ── South America ───────────────────────────────────────────
        Currency("BRL", "R\$",  "Brazilian Real",          "🇧🇷"),
        Currency("ARS", "\$",   "Argentine Peso",          "🇦🇷"),
        Currency("CLP", "\$",   "Chilean Peso",            "🇨🇱"),
        Currency("COP", "\$",   "Colombian Peso",          "🇨🇴"),
        // ── Oceania ─────────────────────────────────────────────────
        Currency("AUD", "A\$",  "Australian Dollar",       "🇦🇺"),
        Currency("NZD", "NZ\$", "New Zealand Dollar",      "🇳🇿"),
    )

    /** Returns the symbol for the given ISO 4217 code, defaults to ₹ */
    fun getSymbol(code: String): String =
        SUPPORTED_CURRENCIES.find { it.code == code }?.symbol ?: "₹"

    /** Returns the full Currency object, defaults to INR */
    fun getCurrency(code: String): Currency =
        SUPPORTED_CURRENCIES.find { it.code == code }
            ?: SUPPORTED_CURRENCIES.first()

    /** Display string: "🇮🇳 ₹ Indian Rupee" */
    fun getDisplayName(code: String): String =
        getCurrency(code).let { "${it.flag} ${it.symbol}  ${it.name}" }

    /**
     * Returns the most appropriate default currency ISO code for the given
     * BCP-47 language code. Used when the user picks a language for the first
     * time so that the currency is auto-matched without manual selection.
     */
    fun getDefaultCurrencyForLanguage(languageCode: String): String = when (languageCode) {
        "hi", "gu", "mr", "ta", "te", "kn", "ml", "pa" -> "INR"
        "bn"  -> "BDT"
        "ar"  -> "SAR"
        "fr"  -> "EUR"
        "de"  -> "EUR"
        "es"  -> "EUR"
        "pt"  -> "BRL"
        "id"  -> "IDR"
        "tr"  -> "TRY"
        "ko"  -> "KRW"
        "ja"  -> "JPY"
        "zh"  -> "CNY"
        "en"  -> "USD"
        else  -> "INR"
    }
}
