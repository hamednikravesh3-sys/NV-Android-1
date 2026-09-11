package com.smartstorage.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF46A6FF),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFF9C74F),
    background = Color(0xFF061522),
    surface = Color(0xFF0D2233),
    surfaceVariant = Color(0xFF153149),
    error = Color(0xFFFF6B6B),
    onPrimary = Color.White,
    onBackground = Color(0xFFEAF5FF),
    onSurface = Color(0xFFEAF5FF)
)

@Composable
fun SmartStorageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, content = content)
}
