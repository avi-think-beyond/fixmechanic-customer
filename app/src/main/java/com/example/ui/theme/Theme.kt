package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FixMechanicDarkColorScheme = darkColorScheme(
    primary = EmergencyOrangeLight,
    onPrimary = NavyDark,
    primaryContainer = EmergencyOrange,
    onPrimaryContainer = PureWhite,
    secondary = WarningAmber,
    onSecondary = NavyDark,
    background = NavyDark,
    onBackground = Neutral50,
    surface = NavySurface,
    onSurface = Neutral50,
    surfaceVariant = NavyLight,
    onSurfaceVariant = Neutral200,
    outline = Neutral500,
    error = EmergencyRed,
    onError = PureWhite
)

private val FixMechanicLightColorScheme = lightColorScheme(
    primary = NavyDark,
    onPrimary = PureWhite,
    primaryContainer = NavySurface,
    onPrimaryContainer = PureWhite,
    secondary = EmergencyOrange,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFEBE5),
    onSecondaryContainer = Color(0xFFBF360C),
    tertiary = WarningAmber,
    background = Neutral50,
    onBackground = Color(0xFF0F172A),
    surface = PureWhite,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Neutral100,
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = EmergencyRed,
    onError = PureWhite
)

@Composable
fun FixMechanicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FixMechanicDarkColorScheme else FixMechanicLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = FixMechanicTheme(darkTheme = darkTheme, content = content)
