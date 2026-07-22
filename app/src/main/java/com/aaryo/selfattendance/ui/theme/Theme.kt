package com.aaryo.selfattendance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val AppTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    small  = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large  = RoundedCornerShape(20.dp)
)

@Composable
fun SelfAttendanceTheme(
    appTheme  : AppTheme = AppTheme.DEEP_VIOLET,
    darkTheme : Boolean  = isSystemInDarkTheme(),
    content   : @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorsFor(appTheme) else lightColorsFor(appTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
