package com.photoframe.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5AA9E6),
    onPrimary = Color(0xFF06121F),
    secondary = Color(0xFFFFD166),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF161C24),
    onSurface = Color(0xFFECEFF3),
)

@Composable
fun PhotoFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
