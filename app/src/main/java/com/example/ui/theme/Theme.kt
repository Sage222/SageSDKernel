package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KalamaDarkColorScheme = darkColorScheme(
    primary = KalamaCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF003D47),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = KalamaAmber,
    onSecondary = Color(0xFF2E1E00),
    secondaryContainer = Color(0xFF473000),
    onSecondaryContainer = Color(0xFFFFE09E),
    tertiary = KalamaEmerald,
    onTertiary = Color(0xFF002912),
    tertiaryContainer = Color(0xFF004521),
    onTertiaryContainer = Color(0xFFA8FFD0),
    error = KalamaCrimson,
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF5C0A0A),
    onErrorContainer = Color(0xFFFFD6D6),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianCardBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KalamaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
