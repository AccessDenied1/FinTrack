package com.sethv.fintrack.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
)

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinTrackTypography,
        content = content,
    )
}
