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
        Currency("BTN", "Nu.",  "Bhutanese Ngultrum",      "🇧🇹"),
        Currency("MVR", "Rf",   "Maldivian Rufiyaa",       "🇲🇻"),
        Currency("AFN", "؋",    "Afghan Afghani",          "🇦🇫"),
        // ── East Asia ───────────────────────────────────────────────
        Currency("CNY", "¥",    "Chinese Yuan",            "🇨🇳"),
        Currency("JPY", "¥",    "Japanese Yen",            "🇯🇵"),
        Currency("KRW", "₩",    "South Korean Won",        "🇰🇷"),
        Currency("HKD", "HK\$", "Hong Kong Dollar",        "🇭🇰"),
        Currency("TWD", "NT\$", "New Taiwan Dollar",       "🇹🇼"),
        Currency("MNT", "₮",    "Mongolian Tugrik",        "🇲🇳"),
        // ── Southeast Asia ──────────────────────────────────────────
        Currency("MYR", "RM",   "Malaysian Ringgit",       "🇲🇾"),
        Currency("IDR", "Rp",   "Indonesian Rupiah",       "🇮🇩"),
        Currency("SGD", "S\$",  "Singapore Dollar",        "🇸🇬"),
        Currency("THB", "฿",    "Thai Baht",               "🇹🇭"),
        Currency("PHP", "₱",    "Philippine Peso",         "🇵🇭"),
        Currency("VND", "₫",    "Vietnamese Dong",         "🇻🇳"),
        Currency("MMK", "K",    "Myanmar Kyat",            "🇲🇲"),
        Currency("KHR", "៛",    "Cambodian Riel",          "🇰🇭"),
        Currency("LAK", "₭",    "Lao Kip",                 "🇱🇦"),
        Currency("BND", "B\$",  "Brunei Dollar",           "🇧🇳"),
        // ── Middle East & West Asia ─────────────────────────────────
        Currency("AED", "د.إ",  "UAE Dirham",              "🇦🇪"),
        Currency("SAR", "﷼",   "Saudi Riyal",             "🇸🇦"),
        Currency("QAR", "ر.ق", "Qatari Riyal",            "🇶🇦"),
        Currency("KWD", "د.ك", "Kuwaiti Dinar",           "🇰🇼"),
        Currency("BHD", "BD",   "Bahraini Dinar",          "🇧🇭"),
        Currency("OMR", "﷼",   "Omani Rial",              "🇴🇲"),
        Currency("JOD", "JD",   "Jordanian Dinar",         "🇯🇴"),
        Currency("ILS", "₪",    "Israeli Shekel",          "🇮🇱"),
        Currency("IQD", "IQD",  "Iraqi Dinar",             "🇮🇶"),
        Currency("LBP", "L£",   "Lebanese Pound",          "🇱🇧"),
        Currency("IRR", "﷼",   "Iranian Rial",            "🇮🇷"),
        Currency("YER", "﷼",   "Yemeni Rial",             "🇾🇪"),
        // ── Central Asia & Caucasus ─────────────────────────────────
        Currency("KZT", "₸",    "Kazakhstani Tenge",       "🇰🇿"),
        Currency("UZS", "soʻm", "Uzbekistani Som",         "🇺🇿"),
        Currency("AZN", "₼",    "Azerbaijani Manat",       "🇦🇿"),
        Currency("GEL", "₾",    "Georgian Lari",           "🇬🇪"),
        Currency("AMD", "֏",    "Armenian Dram",           "🇦🇲"),
        // ── North America & Caribbean ───────────────────────────────
        Currency("USD", "\$",   "US Dollar",               "🇺🇸"),
        Currency("CAD", "CA\$", "Canadian Dollar",         "🇨🇦"),
        Currency("MXN", "\$",   "Mexican Peso",            "🇲🇽"),
        Currency("JMD", "J\$",  "Jamaican Dollar",         "🇯🇲"),
        Currency("DOP", "RD\$", "Dominican Peso",          "🇩🇴"),
        Currency("CRC", "₡",    "Costa Rican Colón",       "🇨🇷"),
        Currency("PAB", "B/.",  "Panamanian Balboa",       "🇵🇦"),
        Currency("GTQ", "Q",    "Guatemalan Quetzal",      "🇬🇹"),
        Currency("HNL", "L",    "Honduran Lempira",        "🇭🇳"),
        Currency("TTD", "TT\$", "Trinidad & Tobago Dollar","🇹🇹"),
        Currency("BSD", "B\$",  "Bahamian Dollar",         "🇧🇸"),
        Currency("BBD", "Bds\$","Barbadian Dollar",        "🇧🇧"),
        // ── Europe ──────────────────────────────────────────────────
        Currency("EUR", "€",    "Euro",                    "🇪🇺"),
        Currency("GBP", "£",    "British Pound",           "🇬🇧"),
        Currency("CHF", "Fr",   "Swiss Franc",             "🇨🇭"),
        Currency("SEK", "kr",   "Swedish Krona",           "🇸🇪"),
        Currency("NOK", "kr",   "Norwegian Krone",         "🇳🇴"),
        Currency("DKK", "kr",   "Danish Krone",            "🇩🇰"),
        Currency("PLN", "zł",   "Polish Zloty",            "🇵🇱"),
        Currency("CZK", "Kč",   "Czech Koruna",            "🇨🇿"),
        Currency("HUF", "Ft",   "Hungarian Forint",        "🇭🇺"),
        Currency("RON", "lei",  "Romanian Leu",            "🇷🇴"),
        Currency("BGN", "лв",   "Bulgarian Lev",           "🇧🇬"),
        Currency("RSD", "din",  "Serbian Dinar",           "🇷🇸"),
        Currency("TRY", "₺",    "Turkish Lira",            "🇹🇷"),
        Currency("RUB", "₽",    "Russian Ruble",           "🇷🇺"),
        Currency("UAH", "₴",    "Ukrainian Hryvnia",       "🇺🇦"),
        Currency("ISK", "kr",   "Icelandic Króna",         "🇮🇸"),
        // ── Africa ──────────────────────────────────────────────────
        Currency("ZAR", "R",    "South African Rand",      "🇿🇦"),
        Currency("NGN", "₦",    "Nigerian Naira",          "🇳🇬"),
        Currency("EGP", "E£",   "Egyptian Pound",          "🇪🇬"),
        Currency("KES", "KSh",  "Kenyan Shilling",         "🇰🇪"),
        Currency("GHS", "₵",    "Ghanaian Cedi",           "🇬🇭"),
        Currency("MAD", "DH",   "Moroccan Dirham",         "🇲🇦"),
        Currency("DZD", "DA",   "Algerian Dinar",          "🇩🇿"),
        Currency("TND", "DT",   "Tunisian Dinar",          "🇹🇳"),
        Currency("ETB", "Br",   "Ethiopian Birr",          "🇪🇹"),
        Currency("TZS", "TSh",  "Tanzanian Shilling",      "🇹🇿"),
        Currency("UGX", "USh",  "Ugandan Shilling",        "🇺🇬"),
        Currency("RWF", "FRw",  "Rwandan Franc",           "🇷🇼"),
        Currency("XOF", "CFA",  "West African CFA Franc",  "🇸🇳"),
        Currency("XAF", "FCFA", "Central African CFA Franc","🇨🇲"),
        Currency("AOA", "Kz",   "Angolan Kwanza",          "🇦🇴"),
        Currency("MZN", "MT",   "Mozambican Metical",      "🇲🇿"),
        Currency("ZMW", "K",    "Zambian Kwacha",          "🇿🇲"),
        Currency("BWP", "P",    "Botswana Pula",           "🇧🇼"),
        Currency("NAD", "N\$",  "Namibian Dollar",         "🇳🇦"),
        Currency("MUR", "Rs",   "Mauritian Rupee",         "🇲🇺"),
        // ── South America ───────────────────────────────────────────
        Currency("BRL", "R\$",  "Brazilian Real",          "🇧🇷"),
        Currency("ARS", "\$",   "Argentine Peso",          "🇦🇷"),
        Currency("CLP", "\$",   "Chilean Peso",            "🇨🇱"),
        Currency("COP", "\$",   "Colombian Peso",          "🇨🇴"),
        Currency("PEN", "S/.",  "Peruvian Sol",            "🇵🇪"),
        Currency("BOB", "Bs.",  "Bolivian Boliviano",      "🇧🇴"),
        Currency("PYG", "₲",    "Paraguayan Guaraní",      "🇵🇾"),
        Currency("UYU", "\$U",  "Uruguayan Peso",          "🇺🇾"),
        Currency("VES", "Bs.",  "Venezuelan Bolívar",      "🇻🇪"),
        // ── Oceania ─────────────────────────────────────────────────
        Currency("AUD", "A\$",  "Australian Dollar",       "🇦🇺"),
        Currency("NZD", "NZ\$", "New Zealand Dollar",      "🇳🇿"),
        Currency("FJD", "FJ\$", "Fijian Dollar",           "🇫🇯"),
        Currency("PGK", "K",    "Papua New Guinean Kina",  "🇵🇬"),
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
