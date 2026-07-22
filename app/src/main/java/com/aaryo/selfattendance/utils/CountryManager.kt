package com.aaryo.selfattendance.utils

// ═══════════════════════════════════════════════════════════════
//  CountryManager — First-Run Country Picker
//
//  Replaces the old raw language picker. The user picks their
//  COUNTRY once; app language + currency are both auto-selected
//  from that choice (no separate language step needed).
//
//  Special case: India → "hi-Latn" (Hinglish — Hindi meaning,
//  written in Latin/English script), matching how most Indian
//  users actually read and type day to day.
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
        Country("IN", "India",         "🇮🇳", "hi-Latn", "INR"),
        Country("BD", "Bangladesh",    "🇧🇩", "bn",      "BDT"),
        Country("PK", "Pakistan",      "🇵🇰", "en",      "PKR"),
        Country("LK", "Sri Lanka",     "🇱🇰", "en",      "LKR"),
        Country("NP", "Nepal",         "🇳🇵", "hi-Latn", "NPR"),
        // ── East Asia ───────────────────────────────────────────────
        Country("CN", "China",         "🇨🇳", "zh",      "CNY"),
        Country("JP", "Japan",         "🇯🇵", "ja",      "JPY"),
        Country("KR", "South Korea",   "🇰🇷", "ko",      "KRW"),
        // ── Southeast Asia ──────────────────────────────────────────
        Country("ID", "Indonesia",     "🇮🇩", "id",      "IDR"),
        Country("MY", "Malaysia",      "🇲🇾", "en",      "MYR"),
        Country("SG", "Singapore",     "🇸🇬", "en",      "SGD"),
        Country("PH", "Philippines",   "🇵🇭", "en",      "PHP"),
        // ── Middle East ─────────────────────────────────────────────
        Country("AE", "UAE",           "🇦🇪", "ar",      "AED"),
        Country("SA", "Saudi Arabia",  "🇸🇦", "ar",      "SAR"),
        Country("EG", "Egypt",         "🇪🇬", "ar",      "EGP"),
        // ── North America ───────────────────────────────────────────
        Country("US", "United States", "🇺🇸", "en",      "USD"),
        Country("CA", "Canada",        "🇨🇦", "en",      "CAD"),
        Country("MX", "Mexico",        "🇲🇽", "es",      "MXN"),
        // ── Europe ──────────────────────────────────────────────────
        Country("GB", "United Kingdom","🇬🇧", "en",      "GBP"),
        Country("FR", "France",        "🇫🇷", "fr",      "EUR"),
        Country("DE", "Germany",       "🇩🇪", "de",      "EUR"),
        Country("ES", "Spain",         "🇪🇸", "es",      "EUR"),
        Country("PT", "Portugal",      "🇵🇹", "pt",      "EUR"),
        Country("TR", "Turkey",        "🇹🇷", "tr",      "TRY"),
        // ── Africa ──────────────────────────────────────────────────
        Country("ZA", "South Africa",  "🇿🇦", "en",      "ZAR"),
        Country("NG", "Nigeria",       "🇳🇬", "en",      "NGN"),
        Country("KE", "Kenya",         "🇰🇪", "en",      "KES"),
        // ── South America ───────────────────────────────────────────
        Country("BR", "Brazil",        "🇧🇷", "pt",      "BRL"),
        Country("AR", "Argentina",     "🇦🇷", "es",      "ARS"),
        // ── Oceania ─────────────────────────────────────────────────
        Country("AU", "Australia",     "🇦🇺", "en",      "AUD"),
        Country("NZ", "New Zealand",   "🇳🇿", "en",      "NZD"),
        // ── Fallback ────────────────────────────────────────────────
        Country("OT", "Other / International", "🌐", "en", "USD"),
    )

    fun getCountry(code: String): Country =
        SUPPORTED_COUNTRIES.find { it.code == code } ?: SUPPORTED_COUNTRIES.first()
}
