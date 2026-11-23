package com.tabletpilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

// Brand colors — purple + teal
private val PilotPurple = Color(0xFF7C4DFF)
private val PilotPurpleContainer = Color(0xFF4A148C)
private val PilotTeal = Color(0xFF03DAC5)
private val PilotTealContainer = Color(0xFF00796B)
private val BackgroundDark = Color(0xFF0F0F17)
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceVariantDark = Color(0xFF252540)

private val DarkColorScheme = darkColorScheme(
    primary = PilotPurple,
    onPrimary = Color.White,
    primaryContainer = PilotPurpleContainer,
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = PilotTeal,
    onSecondary = Color.Black,
    secondaryContainer = PilotTealContainer,
    onSecondaryContainer = Color(0xFFB2EBF2),
    background = BackgroundDark,
    onBackground = Color(0xFFE8E8F0),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB0B0CC),
    error = Color(0xFFCF6679),
    outline = Color(0xFF5A5A7A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7F6),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF018786),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF00201F),
    background = Color(0xFFF8F8FF),
    surface = Color.White
)

@Composable
fun TabletPilotTheme(
    darkTheme: Boolean = true, // Default dark for AMOLED tablet screens
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
