package com.crashlab.analyzer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Accent = Color(0xFF4F7DF9)
val AccentSoft = Color(0xFF7FA3FF)
val SurfaceDark = Color(0xFF12141A)
val SurfaceDarkElevated = Color(0xFF1B1E26)
val CardDark = Color(0xFF1F232D)

val RiskGreen = Color(0xFF2ECC71)
val RiskAmber = Color(0xFFF5B041)
val RiskRed = Color(0xFFE74C3C)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF243250),
    onPrimaryContainer = AccentSoft,
    secondary = Color(0xFF8B93A7),
    background = SurfaceDark,
    onBackground = Color(0xFFE8EAF0),
    surface = SurfaceDarkElevated,
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = CardDark,
    onSurfaceVariant = Color(0xFFA6ADBD),
    outline = Color(0xFF39404F),
    error = RiskRed
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF1B2F63),
    secondary = Color(0xFF5B6478),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF5B6478),
    outline = Color(0xFFD3D8E3),
    error = RiskRed
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

@Composable
fun CrashLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
