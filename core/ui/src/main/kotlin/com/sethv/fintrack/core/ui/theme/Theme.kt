package com.sethv.fintrack.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = Color(0xFF0A2E1D),
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueContainer,
    onSecondaryContainer = Color(0xFF1B2E42),
    tertiary = TealTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = Color(0xFF2A2216),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE9E7E3),
    onSurfaceVariant = Color(0xFF4A4642),
    surfaceContainer = Color(0xFFF3F1ED),
    surfaceContainerHigh = Color(0xFFEDEBE7),
    surfaceContainerHighest = Color(0xFFE5E3DF),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = DebitRedContainer,
    onErrorContainer = OnDebitRedContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF00391F),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Color(0xFFA8E6C5),
    secondary = BlueSecondaryDark,
    onSecondary = Color(0xFF003054),
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Color(0xFFD0E4FF),
    tertiary = TealTertiaryDark,
    onTertiary = Color(0xFF3A2E14),
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = Color(0xFFF0DDB8),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF2A3230),
    onSurfaceVariant = Color(0xFFC2C8BF),
    surfaceContainer = Color(0xFF1E2422),
    surfaceContainerHigh = Color(0xFF282E2C),
    surfaceContainerHighest = Color(0xFF333937),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = Color(0xFF4A0D0F),
    errorContainer = Color(0xFF5A1A1E),
    onErrorContainer = Color(0xFFF4DFDF),
)

/**
 * Brand-specific ledger colors. Always read via LocalFinTrackColors
 * so dark/light adapt correctly.
 */
data class FinTrackColors(
    val credit: Color,
    val onCredit: Color,
    val debit: Color,
    val onDebit: Color,
    val hairline: Color,
    val onHairline: Color,
)

val LocalFinTrackColors = staticCompositionLocalOf {
    FinTrackColors(
        credit = CreditGreen,
        onCredit = Color.White,
        debit = DebitRed,
        onDebit = Color.White,
        hairline = HairlineLight,
        onHairline = HairlineDark,
    )
}

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val finTrackColors = FinTrackColors(
        credit = CreditGreen,
        onCredit = Color.White,
        debit = DebitRed,
        onDebit = Color.White,
        hairline = if (darkTheme) HairlineDark else HairlineLight,
        onHairline = if (darkTheme) HairlineLight else HairlineDark,
    )

    CompositionLocalProvider(LocalFinTrackColors provides finTrackColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinTrackTypography,
            content = content,
        )
    }
}
