package com.aaryo.selfattendance.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------------- PREMIUM LIGHT COLORS ----------------

private val LightColors = lightColorScheme(

    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,

    secondary = Color(0xFF4F8CFF),
    onSecondary = Color.White,

    background = Color(0xFFF5F7FB),
    surface = Color.White,

    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),

    error = Color(0xFFEF4444),
    onError = Color.White
)

// ---------------- PREMIUM DARK COLORS ----------------

private val DarkColors = darkColorScheme(

    primary = Color(0xFF7C83FF),
    onPrimary = Color.Black,

    secondary = Color(0xFF5FA8FF),
    onSecondary = Color.Black,

    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),

    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),

    error = Color(0xFFF87171),
    onError = Color.Black
)

// ---------------- TYPOGRAPHY ----------------

private val AppTypography = Typography(

    headlineLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    ),

    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),

    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    ),

    bodyLarge = TextStyle(
        fontSize = 16.sp
    ),

    bodyMedium = TextStyle(
        fontSize = 14.sp
    ),

    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
)

// ---------------- SHAPES ----------------

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

// ---------------- THEME ----------------

@Composable
fun SelfAttendanceTheme(

    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,

    content: @Composable () -> Unit
) {

    val context = LocalContext.current

    val colorScheme = when {

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {

            if (darkTheme)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }

    val view = LocalView.current

    if (!view.isInEditMode) {

        SideEffect {

            val window = (view.context as Activity).window

            window.statusBarColor = colorScheme.background.toArgb()

            WindowCompat
                .getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}