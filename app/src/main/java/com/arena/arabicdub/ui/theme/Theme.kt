package com.arena.arabicdub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF002233),
    primaryContainer = Color(0xFF1A3A52),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF0B2E13),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1F232C),
    onSurfaceVariant = Color(0xFFB8BEC9),
    outline = Color(0xFF3A3F4B),
    error = Color(0xFFE57373),
)

@Composable
fun ArabicDubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
