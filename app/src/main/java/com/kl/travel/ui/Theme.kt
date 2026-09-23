package com.kl.travel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F3864), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF), onPrimaryContainer = Color(0xFF0B1F44),
    secondary = Color(0xFF0277BD), tertiary = Color(0xFF00897B),
    background = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF4),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9DBDFF), onPrimary = Color(0xFF0B1F44),
    primaryContainer = Color(0xFF243B6B), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF4FC3F7), tertiary = Color(0xFF4DB6AC),
    background = Color(0xFF10141B), surface = Color(0xFF171C25),
    surfaceVariant = Color(0xFF232A36),
)

@Composable
fun KLTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
