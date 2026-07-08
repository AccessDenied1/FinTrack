package com.sethv.fintrack.core.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors
val GreenPrimary = Color(0xFF1B7A4E)
val GreenPrimaryDark = Color(0xFF4CAF7A)
val GreenContainer = Color(0xFFB8E6CC)
val GreenContainerDark = Color(0xFF1E4D35)

val BlueSecondary = Color(0xFF1565C0)
val BlueSecondaryDark = Color(0xFF64B5F6)
val BlueContainer = Color(0xFFD0E4FF)
val BlueContainerDark = Color(0xFF1A3A5C)

val TealTertiary = Color(0xFF00838F)
val TealTertiaryDark = Color(0xFF4DD0E1)
val TealContainer = Color(0xFFB2EBF2)
val TealContainerDark = Color(0xFF004D57)

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