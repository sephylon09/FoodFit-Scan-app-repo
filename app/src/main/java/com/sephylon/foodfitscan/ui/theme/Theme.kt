package com.sephylon.foodfitscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = GreenGrey40,
    onSecondary = Color.White,
    secondaryContainer = GreenGrey90,
    onSecondaryContainer = Color(0xFF10241A),
    tertiary = Amber30,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    background = Color.White,
    onBackground = Neutral10,
    surface = Color.White,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral98,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Color(0xFFEDF0EC),
    surfaceContainerHighest = Neutral90,
    outline = Neutral50,
    outlineVariant = Neutral90,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = GreenGrey80,
    onSecondary = Color(0xFF203629),
    secondaryContainer = Color(0xFF36493E),
    onSecondaryContainer = GreenGrey90,
    tertiary = Color(0xFFE8C26C),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Amber90,
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4E0),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E4E0),
    surfaceVariant = Color(0xFF404844),
    onSurfaceVariant = Color(0xFFC0C9C2),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF191D1B),
    surfaceContainer = Color(0xFF1D211F),
    surfaceContainerHigh = Color(0xFF272B29),
    surfaceContainerHighest = Color(0xFF323634),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404844),
    error = Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Error90,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FoodFitScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
