package com.jepongdevxyz.devxyzcode

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DevxyzDark = darkColorScheme(
    primary = Color(0xFF22D3EE),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111C33),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0)
)

private val DevxyzLight = lightColorScheme(
    primary = Color(0xFF0891B2),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun DevxyzCodeTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    // v3.4: "system" follows the phone, "dark"/"light" force one mode.
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DevxyzDark else DevxyzLight
    MaterialTheme(colorScheme = scheme, content = content)
}
