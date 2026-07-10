package com.sethv.fintrack.core.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors
val GreenPrimary = Color(0xFF0F9D58) // Vibrant Google Green
val GreenPrimaryDark = Color(0xFF1CE885) // Neon mint for dark mode
val GreenContainer = Color(0xFFE6F4EA)
val GreenContainerDark = Color(0xFF0A3D24)

val BlueSecondary = Color(0xFF1A73E8) // Crisp Google Blue
val BlueSecondaryDark = Color(0xFF669DF6)
val BlueContainer = Color(0xFFE8F0FE)
val BlueContainerDark = Color(0xFF132F53)

val TealTertiary = Color(0xFF009688)
val TealTertiaryDark = Color(0xFF4DB6AC)
val TealContainer = Color(0xFFE0F2F1)
val TealContainerDark = Color(0xFF00332F)

// Surfaces
val BackgroundLight = Color(0xFFF5F9F7)
val SurfaceLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF1A1C1A)
val OnSurfaceLight = Color(0xFF1A1C1A)
val OutlineLight = Color(0xFF727972)
val ErrorLight = Color(0xFFBA1A1A)

val BackgroundDark = Color(0xFF0F1412)
val SurfaceDark = Color(0xFF1A211E)
val OnBackgroundDark = Color(0xFFE0E3E0)
val OnSurfaceDark = Color(0xFFE0E3E0)
val OutlineDark = Color(0xFF8B938B)
val ErrorDark = Color(0xFFFFB4AB)

// Semantic transaction colors
val CreditGreen = Color(0xFF2E7D32)
val CreditGreenContainer = Color(0xFFC8E6C9)
val OnCreditGreenContainer = Color(0xFF1B5E20)

val DebitRed = Color(0xFFC62828)
val DebitRedContainer = Color(0xFFFFCDD2)
val OnDebitRedContainer = Color(0xFFB71C1C)

// Categorical palette for category breakdown chart (donut / legend).
// 12 hues, distinct in both light and dark themes.
val CategoryPalette: List<Color> = listOf(
    Color(0xFF1B7A4E), // green
    Color(0xFF1565C0), // blue
    Color(0xFF00838F), // teal
    Color(0xFFE65100), // orange
    Color(0xFF6A1B9A), // purple
    Color(0xFFC62828), // red
    Color(0xFFAD1457), // pink
    Color(0xFF4527A0), // deep purple
    Color(0xFF2E7D32), // leaf
    Color(0xFFEF6C00), // amber
    Color(0xFF00695C), // dark teal
    Color(0xFF283593), // indigo
)

fun colorForCategoryIndex(index: Int): Color =
    CategoryPalette[index.mod(CategoryPalette.size)]