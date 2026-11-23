package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BladeDarkColorScheme = darkColorScheme(
    primary = BladeRed,
    onPrimary = TextWhite,
    primaryContainer = BladeRedDark,
    onPrimaryContainer = TextWhite,
    secondary = BladeRedAccent,
    onSecondary = TextWhite,
    secondaryContainer = Color(0xFF2B1417),
    onSecondaryContainer = BladeRedAccent,
    tertiary = BladeGold,
    onTertiary = BladeObsidian,
    background = BladeObsidian,
    onBackground = TextWhite,
    surface = BladeSurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = BladeContainerDark,
    onSurfaceVariant = TextMutedDark,
    outline = BladeBorderDark,
    error = BladeRedAccent,
    onError = TextWhite
)

private val BladeLightColorScheme = lightColorScheme(
    primary = BladeRed,
    onPrimary = TextWhite,
    primaryContainer = Color(0xFFFFE3E5),
    onPrimaryContainer = BladeRedDark,
    secondary = BladeRedDark,
    onSecondary = TextWhite,
    secondaryContainer = BladeContainerLight,
    onSecondaryContainer = BladeRedDark,
    tertiary = BladeGold,
    onTertiary = TextInk,
    background = BladePearlLight,
    onBackground = TextInk,
    surface = BladeSurfaceLight,
    onSurface = TextInk,
    surfaceVariant = BladeContainerLight,
    onSurfaceVariant = TextMutedLight,
    outline = BladeBorderLight,
    error = BladeRedDark,
    onError = TextWhite
)

val BladeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BladeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BladeDarkColorScheme else BladeLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BladeTypography,
        shapes = BladeShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    BladeTheme(darkTheme = darkTheme, content = content)
}
