package com.aaryo.selfattendance.utils

// ═══════════════════════════════════════════════════════════════
//  CountryManager — Global Country & Localization Configuration
//
//  Provides comprehensive country data for all global regions
//  with matching ISO codes, flags, native default languages,
//  and local currencies.
// ═══════════════════════════════════════════════════════════════

object CountryManager {

    data class Country(
        val code: String,      // ISO 3166-1 alpha-2
        val name: String,
        val flag: String,
        val language: String,  // matches a key in LocaleManager.SUPPORTED_LANGUAGES
        val currency: String   // matches a code in CurrencyManager.SUPPORTED_CURRENCIES
    )

    val SUPPORTED_COUNTRIES: List<Country> = listOf(
        // ── South Asia ──────────────────────────────────────────────
        Country("IN", "India",                    "🇮🇳", "en",      "INR"),
        Country("BD", "Bangladesh",               "🇧🇩", "bn",      "BDT"),
        Country("PK", "Pakistan",                 "🇵🇰", "en",      "PKR"),
        Country("LK", "Sri Lanka",                "🇱🇰", "en",      "LKR"),
        Country("NP", "Nepal",                    "🇳🇵", "en",      "NPR"),
        Country("BT", "Bhutan",                   "🇧🇹", "en",      "BTN"),
        Country("MV", "Maldives",                 "🇲🇻", "en",      "MVR"),
        Country("AF", "Afghanistan",              "🇦🇫", "en",      "AFN"),

        // ── East & Southeast Asia ───────────────────────────────────
        Country("CN", "China",                    "🇨🇳", "zh",      "CNY"),
        Country("JP", "Japan",                    "🇯🇵", "ja",      "JPY"),
        Country("KR", "South Korea",              "🇰🇷", "ko",      "KRW"),
        Country("HK", "Hong Kong",                "🇭🇰", "zh",      "HKD"),
        Country("TW", "Taiwan",                   "🇹🇼", "zh",      "TWD"),
        Country("ID", "Indonesia",                "🇮🇩", "id",      "IDR"),
        Country("MY", "Malaysia",                 "🇲🇾", "en",      "MYR"),
        Country("SG", "Singapore",                "🇸🇬", "en",      "SGD"),
        Country("PH", "Philippines",              "🇵🇭", "en",      "PHP"),
        Country("TH", "Thailand",                 "🇹🇭", "en",      "THB"),
        Country("VN", "Vietnam",                  "🇻🇳", "en",      "VND"),
        Country("MM", "Myanmar",                  "🇲🇲", "en",      "MMK"),
        Country("KH", "Cambodia",                 "🇰🇭", "en",      "KHR"),
        Country("LA", "Laos",                     "🇱🇦", "en",      "LAK"),
        Country("BN", "Brunei",                   "🇧🇳", "en",      "BND"),
        Country("MN", "Mongolia",                 "🇲🇳", "en",      "MNT"),

        // ── Middle East & Central/West Asia ─────────────────────────
        Country("AE", "United Arab Emirates",     "🇦🇪", "ar",      "AED"),
        Country("SA", "Saudi Arabia",             "🇸🇦", "ar",      "SAR"),
        Country("QA", "Qatar",                    "🇶🇦", "ar",      "QAR"),
        Country("KW", "Kuwait",                   "🇰🇼", "ar",      "KWD"),
        Country("OM", "Oman",                     "🇴🇲", "ar",      "OMR"),
        Country("BH", "Bahrain",                  "🇧🇭", "ar",      "BHD"),
        Country("EG", "Egypt",                    "🇪🇬", "ar",      "EGP"),
        Country("JO", "Jordan",                   "🇯🇴", "ar",      "JOD"),
        Country("LB", "Lebanon",                  "🇱🇧", "ar",      "LBP"),
        Country("IQ", "Iraq",                     "🇮🇶", "ar",      "IQD"),
        Country("IR", "Iran",                     "🇮🇷", "en",      "IRR"),
        Country("YE", "Yemen",                    "🇾🇪", "ar",      "YER"),
        Country("IL", "Israel",                   "🇮🇱", "en",      "ILS"),
        Country("TR", "Turkey",                   "🇹🇷", "tr",      "TRY"),
        Country("KZ", "Kazakhstan",               "🇰🇿", "en",      "KZT"),
        Country("UZ", "Uzbekistan",               "🇺🇿", "en",      "UZS"),
        Country("AZ", "Azerbaijan",               "🇦🇿", "en",      "AZN"),
        Country("GE", "Georgia",                  "🇬🇪", "en",      "GEL"),
        Country("AM", "Armenia",                  "🇦🇲", "en",      "AMD"),

        // ── Europe ──────────────────────────────────────────────────
        Country("GB", "United Kingdom",           "🇬🇧", "en",      "GBP"),
        Country("DE", "Germany",                  "🇩🇪", "de",      "EUR"),
        Country("FR", "France",                   "🇫🇷", "fr",      "EUR"),
        Country("ES", "Spain",                    "🇪🇸", "es",      "EUR"),
        Country("IT", "Italy",                    "🇮🇹", "en",      "EUR"),
        Country("PT", "Portugal",                 "🇵🇹", "pt",      "EUR"),
        Country("NL", "Netherlands",              "🇳🇱", "en",      "EUR"),
        Country("BE", "Belgium",                  "🇧🇪", "fr",      "EUR"),
        Country("CH", "Switzerland",              "🇨🇭", "de",      "CHF"),
        Country("AT", "Austria",                  "🇦🇹", "de",      "EUR"),
        Country("SE", "Sweden",                   "🇸🇪", "en",      "SEK"),
        Country("NO", "Norway",                   "🇳🇴", "en",      "NOK"),
        Country("DK", "Denmark",                  "🇩🇰", "en",      "DKK"),
        Country("FI", "Finland",                  "🇫🇮", "en",      "EUR"),
        Country("IE", "Ireland",                  "🇮🇪", "en",      "EUR"),
        Country("PL", "Poland",                   "🇵🇱", "en",      "PLN"),
        Country("CZ", "Czech Republic",           "🇨🇿", "en",      "CZK"),
        Country("HU", "Hungary",                  "🇭🇺", "en",      "HUF"),
        Country("RO", "Romania",                  "🇷🇴", "en",      "RON"),
        Country("GR", "Greece",                   "🇬🇷", "en",      "EUR"),
        Country("UA", "Ukraine",                  "🇺🇦", "en",      "UAH"),
        Country("RU", "Russia",                   "🇷🇺", "en",      "RUB"),
        Country("BG", "Bulgaria",                 "🇧🇬", "en",      "BGN"),
        Country("RS", "Serbia",                   "🇷🇸", "en",      "RSD"),
        Country("HR", "Croatia",                  "🇭🇷", "en",      "EUR"),
        Country("SK", "Slovakia",                 "🇸🇰", "en",      "EUR"),
        Country("SI", "Slovenia",                 "🇸🇮", "en",      "EUR"),
        Country("IS", "Iceland",                  "🇮🇸", "en",      "ISK"),
        Country("LU", "Luxembourg",               "🇱🇺", "fr",      "EUR"),
        Country("CY", "Cyprus",                   "🇨🇾", "en",      "EUR"),
        Country("MT", "Malta",                    "🇲🇹", "en",      "EUR"),

        // ── North America & Caribbean ───────────────────────────────
        Country("US", "United States",            "🇺🇸", "en",      "USD"),
        Country("CA", "Canada",                   "🇨🇦", "en",      "CAD"),
        Country("MX", "Mexico",                   "🇲🇽", "es",      "MXN"),
        Country("JM", "Jamaica",                  "🇯🇲", "en",      "JMD"),
        Country("DO", "Dominican Republic",       "🇩🇴", "es",      "DOP"),
        Country("CR", "Costa Rica",               "🇨🇷", "es",      "CRC"),
        Country("PA", "Panama",                   "🇵🇦", "es",      "PAB"),
        Country("GT", "Guatemala",                "🇬🇹", "es",      "GTQ"),
        Country("HN", "Honduras",                 "🇭🇳", "es",      "HNL"),
        Country("SV", "El Salvador",              "🇸🇻", "es",      "USD"),
        Country("NI", "Nicaragua",                "🇳🇮", "es",      "USD"),
        Country("TT", "Trinidad and Tobago",      "🇹🇹", "en",      "TTD"),
        Country("BS", "Bahamas",                  "🇧🇸", "en",      "BSD"),
        Country("BB", "Barbados",                 "🇧🇧", "en",      "BBD"),
        Country("CU", "Cuba",                     "🇨🇺", "es",      "USD"),
        Country("PR", "Puerto Rico",              "🇵🇷", "es",      "USD"),

        // ── South America ───────────────────────────────────────────
        Country("BR", "Brazil",                   "🇧🇷", "pt",      "BRL"),
        Country("AR", "Argentina",                "🇦🇷", "es",      "ARS"),
        Country("CL", "Chile",                    "🇨🇱", "es",      "CLP"),
        Country("CO", "Colombia",                 "🇨🇴", "es",      "COP"),
        Country("PE", "Peru",                     "🇵🇪", "es",      "PEN"),
        Country("EC", "Ecuador",                  "🇪🇨", "es",      "USD"),
        Country("BO", "Bolivia",                  "🇧🇴", "es",      "BOB"),
        Country("PY", "Paraguay",                 "🇵🇾", "es",      "PYG"),
        Country("UY", "Uruguay",                  "🇺🇾", "es",      "UYU"),
        Country("VE", "Venezuela",                "🇻🇪", "es",      "VES"),

        // ── Africa ──────────────────────────────────────────────────
        Country("ZA", "South Africa",             "🇿🇦", "en",      "ZAR"),
        Country("NG", "Nigeria",                  "🇳🇬", "en",      "NGN"),
        Country("KE", "Kenya",                    "🇰🇪", "en",      "KES"),
        Country("GH", "Ghana",                    "🇬🇭", "en",      "GHS"),
        Country("MA", "Morocco",                  "🇲🇦", "ar",      "MAD"),
        Country("DZ", "Algeria",                  "🇩🇿", "ar",      "DZD"),
        Country("TN", "Tunisia",                  "🇹🇳", "ar",      "TND"),
        Country("ET", "Ethiopia",                 "🇪🇹", "en",      "ETB"),
        Country("TZ", "Tanzania",                 "🇹🇿", "en",      "TZS"),
        Country("UG", "Uganda",                   "🇺🇬", "en",      "UGX"),
        Country("RW", "Rwanda",                   "🇷🇼", "en",      "RWF"),
        Country("SN", "Senegal",                  "🇸🇳", "fr",      "XOF"),
        Country("CI", "Ivory Coast",              "🇨🇮", "fr",      "XOF"),
        Country("CM", "Cameroon",                 "🇨🇲", "fr",      "XAF"),
        Country("AO", "Angola",                   "🇦🇴", "pt",      "AOA"),
        Country("MZ", "Mozambique",               "🇲🇿", "pt",      "MZN"),
        Country("ZM", "Zambia",                   "🇿🇲", "en",      "ZMW"),
        Country("ZW", "Zimbabwe",                 "🇿🇼", "en",      "USD"),
        Country("BW", "Botswana",                 "🇧🇼", "en",      "BWP"),
        Country("NA", "Namibia",                  "🇳🇦", "en",      "NAD"),
        Country("MU", "Mauritius",                "🇲🇺", "en",      "MUR"),

        // ── Oceania ─────────────────────────────────────────────────
        Country("AU", "Australia",                "🇦🇺", "en",      "AUD"),
        Country("NZ", "New Zealand",              "🇳🇿", "en",      "NZD"),
        Country("FJ", "Fiji",                     "🇫🇯", "en",      "FJD"),
        Country("PG", "Papua New Guinea",         "🇵🇬", "en",      "PGK"),

        // ── International Fallback ──────────────────────────────────
        Country("OT", "Other / International",    "🌐", "en",      "USD"),
    )

    fun getCountry(code: String): Country =
        SUPPORTED_COUNTRIES.find { it.code.equals(code, ignoreCase = true) } ?: SUPPORTED_COUNTRIES.first()

    fun getDefaultCountryForLanguage(languageCode: String): String = when (languageCode) {
        "hi", "hi-Latn", "gu", "mr", "ta", "te", "kn", "ml", "pa" -> "IN"
        "bn" -> "BD"
        "ar" -> "SA"
        "fr" -> "FR"
        "de" -> "DE"
        "es" -> "ES"
        "pt" -> "BR"
        "id" -> "ID"
        "tr" -> "TR"
        "ko" -> "KR"
        "ja" -> "JP"
        "zh" -> "CN"
        "en" -> "US"
        else -> "IN"
    }
}
