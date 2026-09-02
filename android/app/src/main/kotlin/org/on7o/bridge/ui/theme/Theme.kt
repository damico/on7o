package org.on7o.bridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    background = Color(0xFF0B0B0C),
    surface = Color(0xFF141416),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F9D63),
)

@Composable
fun OnStickBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
