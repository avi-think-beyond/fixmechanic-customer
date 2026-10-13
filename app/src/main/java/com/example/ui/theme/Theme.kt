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
    onBackground = Neutral900,
    surface = PureWhite,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,
    outline = Neutral300,
    outlineVariant = Neutral200,
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
