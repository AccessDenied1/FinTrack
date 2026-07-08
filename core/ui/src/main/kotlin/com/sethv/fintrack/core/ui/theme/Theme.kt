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
    onPrimaryContainer = Color(0xFF002114),
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueContainer,
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = TealTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TealContainer,
    onTertiaryContainer = Color(0xFF002022),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFDEE5DE),
    onSurfaceVariant = Color(0xFF424942),
    outline = OutlineLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = DebitRedContainer,
    onErrorContainer = OnDebitRedContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF003822),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Color(0xFFB8E6CC),
    secondary = BlueSecondaryDark,
    onSecondary = Color(0xFF003258),
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Color(0xFFD0E4FF),
    tertiary = TealTertiaryDark,
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = Color(0xFFB2EBF2),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF424942),
    onSurfaceVariant = Color(0xFFC2C9C2),
    outline = OutlineDark,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Brand-specific colors that the M3 ColorScheme can't model directly.
 * Reads of [CreditGreen]/[DebitRed] should go through this CompositionLocal
 * so they follow the dark/light theme correctly.
 */
data class FinTrackColors(
    val credit: Color,
    val onCredit: Color,
    val debit: Color,
    val onDebit: Color,
)

val LocalFinTrackColors = staticCompositionLocalOf {
    FinTrackColors(
        credit = CreditGreen,
        onCredit = Color.White,
        debit = DebitRed,
        onDebit = Color.White,
    )
}

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Brand-first by default; set true to follow Material You on Android 12+. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // dynamicColor branch is intentionally disabled by default — brand identity
    // wins over Material You. Flip the flag if you want to A/B test.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val finTrackColors = if (darkTheme) {
        FinTrackColors(
            credit = CreditGreen,
            onCredit = Color.White,
            debit = DebitRed,
            onDebit = Color.White,
        )
    } else {
        FinTrackColors(
            credit = CreditGreen,
            onCredit = Color.White,
            debit = DebitRed,
            onDebit = Color.White,
        )
    }

    CompositionLocalProvider(LocalFinTrackColors provides finTrackColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinTrackTypography,
            content = content,
        )
    }
}