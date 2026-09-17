package com.wifishare.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Paper = Color(0xFFEFECE4)
private val Ink = Color(0xFF171614)
private val Pine = Color(0xFF21554F)
private val Night = Color(0xFF10110F)
private val NightSurface = Color(0xFF181916)
private val Foam = Color(0xFF9EC4BE)

@Composable
fun WifiShareTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Foam,
            onPrimary = Night,
            background = Night,
            onBackground = Color(0xFFF3F0E8),
            surface = NightSurface,
            onSurface = Color(0xFFF3F0E8),
            secondary = Foam,
            onSecondary = Night,
            error = Color(0xFFE08B82),
        )
    } else {
        lightColorScheme(
            primary = Pine,
            onPrimary = Paper,
            background = Paper,
            onBackground = Ink,
            surface = Color(0xFFF7F4EE),
            onSurface = Ink,
            secondary = Ink,
            onSecondary = Paper,
            error = Color(0xFF9B3A32),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
