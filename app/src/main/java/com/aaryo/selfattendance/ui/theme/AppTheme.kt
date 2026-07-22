package com.aaryo.selfattendance.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
//  6 Modern Themes — each with full light + dark ColorScheme
//  + splash-specific dark bg + accent colors
// ═══════════════════════════════════════════════════════════════

enum class AppTheme(
    val displayName  : String,
    val emoji        : String,
    val description  : String,
    val prefsKey     : String,
    // Picker preview dots
    val primary      : Color,
    val primaryLight : Color,
    val bgColor      : Color,
    // Splash screen colors (themed dark bg + glows)
    val splashBg1    : Color,
    val splashBg2    : Color,
    val splashBg3    : Color,
    val splashAccent : Color,
    val splashAccent2: Color,
    val splashGlow   : Color,
    val splashTagline: Color,
    val splashTrack  : Color,
    val splashVersion: Color
) {

    DEEP_VIOLET(
        displayName   = "Deep Violet",  emoji = "💜",  description = "Bold & trendy",
        prefsKey      = "deep_violet",
        primary       = Color(0xFF7C3AED),
        primaryLight  = Color(0xFFEDE9FE),
        bgColor       = Color(0xFFF5F3FF),
        splashBg1     = Color(0xFF0F0A2E),
        splashBg2     = Color(0xFF1A1340),
        splashBg3     = Color(0xFF2D2469),
        splashAccent  = Color(0xFF7C3AED),
        splashAccent2 = Color(0xFF9F67FA),
        splashGlow    = Color(0xAA7C3AED),
        splashTagline = Color(0xFFD8B4FE),
        splashTrack   = Color(0xFF2D2460),
        splashVersion = Color(0xFF5050A0)
    ),

    ROSE_SLATE(
        displayName   = "Rose & Slate",  emoji = "🌹",  description = "Bold & energetic",
        prefsKey      = "rose_slate",
        primary       = Color(0xFFE11D48),
        primaryLight  = Color(0xFFFFE4E6),
        bgColor       = Color(0xFFFFF1F2),
        splashBg1     = Color(0xFF1C0A10),
        splashBg2     = Color(0xFF2C1018),
        splashBg3     = Color(0xFF3D1525),
        splashAccent  = Color(0xFFE11D48),
        splashAccent2 = Color(0xFFF43F5E),
        splashGlow    = Color(0xAAE11D48),
        splashTagline = Color(0xFFFDA4AF),
        splashTrack   = Color(0xFF3D1020),
        splashVersion = Color(0xFF8B3A50)
    ),

    OCEAN_TEAL(
        displayName   = "Ocean Teal",  emoji = "🌊",  description = "Fresh & professional",
        prefsKey      = "ocean_teal",
        primary       = Color(0xFF0891B2),
        primaryLight  = Color(0xFFCFFAFE),
        bgColor       = Color(0xFFF0FDFF),
        splashBg1     = Color(0xFF021520),
        splashBg2     = Color(0xFF062533),
        splashBg3     = Color(0xFF0C3348),
        splashAccent  = Color(0xFF0891B2),
        splashAccent2 = Color(0xFF06B6D4),
        splashGlow    = Color(0xAA0891B2),
        splashTagline = Color(0xFFA5F3FC),
        splashTrack   = Color(0xFF0C2A38),
        splashVersion = Color(0xFF3A7A8A)
    ),

    EMERALD_PRO(
        displayName   = "Emerald Pro",  emoji = "💚",  description = "Natural & calm",
        prefsKey      = "emerald_pro",
        primary       = Color(0xFF059669),
        primaryLight  = Color(0xFFD1FAE5),
        bgColor       = Color(0xFFF0FDF4),
        splashBg1     = Color(0xFF011C12),
        splashBg2     = Color(0xFF022C1E),
        splashBg3     = Color(0xFF04402A),
        splashAccent  = Color(0xFF059669),
        splashAccent2 = Color(0xFF10B981),
        splashGlow    = Color(0xAA059669),
        splashTagline = Color(0xFF6EE7B7),
        splashTrack   = Color(0xFF023A22),
        splashVersion = Color(0xFF2A7A50)
    ),

    WARM_AMBER(
        displayName   = "Warm Amber",  emoji = "🟡",  description = "Warm & unique",
        prefsKey      = "warm_amber",
        primary       = Color(0xFFD97706),
        primaryLight  = Color(0xFFFEF3C7),
        bgColor       = Color(0xFFFFFBEB),
        splashBg1     = Color(0xFF1A0800),
        splashBg2     = Color(0xFF2D1200),
        splashBg3     = Color(0xFF4A2000),
        splashAccent  = Color(0xFFFFB300),
        splashAccent2 = Color(0xFFF57F17),
        splashGlow    = Color(0xAAFFB300),
        splashTagline = Color(0xFFFFCC80),
        splashTrack   = Color(0xFF4A2000),
        splashVersion = Color(0xFF8D4A00)
    ),

    MIDNIGHT_BLUE(
        displayName   = "Midnight Blue",  emoji = "🌙",  description = "Corporate & trustworthy",
        prefsKey      = "midnight_blue",
        primary       = Color(0xFF2563EB),
        primaryLight  = Color(0xFFDBEAFE),
        bgColor       = Color(0xFFEFF6FF),
        splashBg1     = Color(0xFF020817),
        splashBg2     = Color(0xFF0D1B35),
        splashBg3     = Color(0xFF142247),
        splashAccent  = Color(0xFF2563EB),
        splashAccent2 = Color(0xFF3B82F6),
        splashGlow    = Color(0xAA2563EB),
        splashTagline = Color(0xFF93C5FD),
        splashTrack   = Color(0xFF142040),
        splashVersion = Color(0xFF3A5A90)
    );

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.prefsKey == key } ?: DEEP_VIOLET
    }
}

// ── Light schemes ─────────────────────────────────────────────────────

internal fun lightColorsFor(t: AppTheme) = when (t) {

    AppTheme.DEEP_VIOLET -> lightColorScheme(
        primary = Color(0xFF7C3AED), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF2E003E),
        secondary = Color(0xFF9F67FA), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3EEFF), onSecondaryContainer = Color(0xFF2B0052),
        tertiary = Color(0xFF00C853), onTertiary = Color.White,
        background = Color(0xFFF5F3FF), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E1B4B), onSurface = Color(0xFF1E1B4B),
        surfaceVariant = Color(0xFFEDE9FE), onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFDDD6FE),
        error = Color(0xFFE53935), onError = Color.White
    )

    AppTheme.ROSE_SLATE -> lightColorScheme(
        primary = Color(0xFFE11D48), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE4E6), onPrimaryContainer = Color(0xFF4C0519),
        secondary = Color(0xFFF43F5E), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFF1F2), onSecondaryContainer = Color(0xFF4C0519),
        tertiary = Color(0xFF00C853), onTertiary = Color.White,
        background = Color(0xFFFFF1F2), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF0F172A), onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFFFE4E6), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFFDA4AF),
        error = Color(0xFFB91C1C), onError = Color.White
    )

    AppTheme.OCEAN_TEAL -> lightColorScheme(
        primary = Color(0xFF0891B2), onPrimary = Color.White,
        primaryContainer = Color(0xFFCFFAFE), onPrimaryContainer = Color(0xFF0C4A6E),
        secondary = Color(0xFF06B6D4), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0F7FA), onSecondaryContainer = Color(0xFF0C4A6E),
        tertiary = Color(0xFF00C853), onTertiary = Color.White,
        background = Color(0xFFF0FDFF), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF0C4A6E), onSurface = Color(0xFF0C4A6E),
        surfaceVariant = Color(0xFFCFFAFE), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFA5F3FC),
        error = Color(0xFFE53935), onError = Color.White
    )

    AppTheme.EMERALD_PRO -> lightColorScheme(
        primary = Color(0xFF059669), onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
        secondary = Color(0xFF10B981), onSecondary = Color.White,
        secondaryContainer = Color(0xFFECFDF5), onSecondaryContainer = Color(0xFF064E3B),
        tertiary = Color(0xFFFFB300), onTertiary = Color.White,
        background = Color(0xFFF0FDF4), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF064E3B), onSurface = Color(0xFF064E3B),
        surfaceVariant = Color(0xFFD1FAE5), onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFA7F3D0),
        error = Color(0xFFE53935), onError = Color.White
    )

    AppTheme.WARM_AMBER -> lightColorScheme(
        primary = Color(0xFFD97706), onPrimary = Color(0xFF1A0800),
        primaryContainer = Color(0xFFFEF3C7), onPrimaryContainer = Color(0xFF451A03),
        secondary = Color(0xFFF59E0B), onSecondary = Color(0xFF1A0800),
        secondaryContainer = Color(0xFFFFFBEB), onSecondaryContainer = Color(0xFF451A03),
        tertiary = Color(0xFF00C853), onTertiary = Color.White,
        background = Color(0xFFFFFBEB), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF451A03), onSurface = Color(0xFF451A03),
        surfaceVariant = Color(0xFFFEF3C7), onSurfaceVariant = Color(0xFF92400E),
        outline = Color(0xFFFDE68A),
        error = Color(0xFFE53935), onError = Color.White
    )

    AppTheme.MIDNIGHT_BLUE -> lightColorScheme(
        primary = Color(0xFF2563EB), onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A5F),
        secondary = Color(0xFF3B82F6), onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFF6FF), onSecondaryContainer = Color(0xFF1E3A5F),
        tertiary = Color(0xFF00C853), onTertiary = Color.White,
        background = Color(0xFFEFF6FF), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E3A5F), onSurface = Color(0xFF1E3A5F),
        surfaceVariant = Color(0xFFDBEAFE), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFBFDBFE),
        error = Color(0xFFE53935), onError = Color.White
    )
}

// ── Dark schemes ──────────────────────────────────────────────────────

internal fun darkColorsFor(t: AppTheme) = when (t) {

    AppTheme.DEEP_VIOLET -> darkColorScheme(
        primary = Color(0xFFBB86FC), onPrimary = Color(0xFF21003E),
        primaryContainer = Color(0xFF490D89), onPrimaryContainer = Color(0xFFEFDCFF),
        secondary = Color(0xFFCDA9FF), onSecondary = Color(0xFF35007A),
        background = Color(0xFF0F0A2E), surface = Color(0xFF1A1340),
        onBackground = Color(0xFFEDE9FE), onSurface = Color(0xFFEDE9FE),
        surfaceVariant = Color(0xFF2D2460), onSurfaceVariant = Color(0xFFB0A8CC),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )

    AppTheme.ROSE_SLATE -> darkColorScheme(
        primary = Color(0xFFFB7185), onPrimary = Color(0xFF4C0519),
        primaryContainer = Color(0xFF9F1239), onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFFDA4AF), onSecondary = Color(0xFF4C0519),
        background = Color(0xFF1C0A10), surface = Color(0xFF2C1018),
        onBackground = Color(0xFFFFE4E6), onSurface = Color(0xFFFFE4E6),
        surfaceVariant = Color(0xFF3D1520), onSurfaceVariant = Color(0xFFCCA0A8),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )

    AppTheme.OCEAN_TEAL -> darkColorScheme(
        primary = Color(0xFF22D3EE), onPrimary = Color(0xFF0C4A6E),
        primaryContainer = Color(0xFF0E7490), onPrimaryContainer = Color(0xFFCFFAFE),
        secondary = Color(0xFF67E8F9), onSecondary = Color(0xFF0C4A6E),
        background = Color(0xFF021520), surface = Color(0xFF072A3A),
        onBackground = Color(0xFFCFFAFE), onSurface = Color(0xFFCFFAFE),
        surfaceVariant = Color(0xFF0C3348), onSurfaceVariant = Color(0xFF8BC8D8),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )

    AppTheme.EMERALD_PRO -> darkColorScheme(
        primary = Color(0xFF34D399), onPrimary = Color(0xFF064E3B),
        primaryContainer = Color(0xFF047857), onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF6EE7B7), onSecondary = Color(0xFF064E3B),
        background = Color(0xFF011C12), surface = Color(0xFF022C1E),
        onBackground = Color(0xFFD1FAE5), onSurface = Color(0xFFD1FAE5),
        surfaceVariant = Color(0xFF04402A), onSurfaceVariant = Color(0xFF80C8A8),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )

    AppTheme.WARM_AMBER -> darkColorScheme(
        primary = Color(0xFFFCD34D), onPrimary = Color(0xFF1A0800),
        primaryContainer = Color(0xFF92400E), onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFFFDE68A), onSecondary = Color(0xFF1A0800),
        background = Color(0xFF1A0E00), surface = Color(0xFF2A1800),
        onBackground = Color(0xFFFEF3C7), onSurface = Color(0xFFFEF3C7),
        surfaceVariant = Color(0xFF3A2200), onSurfaceVariant = Color(0xFFC8A060),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )

    AppTheme.MIDNIGHT_BLUE -> darkColorScheme(
        primary = Color(0xFF60A5FA), onPrimary = Color(0xFF1E3A5F),
        primaryContainer = Color(0xFF1D4ED8), onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF93C5FD), onSecondary = Color(0xFF1E3A5F),
        background = Color(0xFF020817), surface = Color(0xFF0D1B35),
        onBackground = Color(0xFFDBEAFE), onSurface = Color(0xFFDBEAFE),
        surfaceVariant = Color(0xFF142247), onSurfaceVariant = Color(0xFF8AAACE),
        error = Color(0xFFFF6E6E), onError = Color(0xFF690005)
    )
}
