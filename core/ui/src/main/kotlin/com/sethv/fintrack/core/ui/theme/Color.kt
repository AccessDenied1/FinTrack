package com.sethv.fintrack.core.ui.theme

import androidx.compose.ui.graphics.Color

// ── Precision Ledger palette ─────────────────────────────────────────────
// Editorial, paper-and-ink foundation. Emerald stays as the primary signal
// (money in), slate for structure, warm brass for highlights. All containers
// are desaturated paper tones — no neon, no purple slop.

val GreenPrimary = Color(0xFF0D7A4C)
val GreenPrimaryDark = Color(0xFF6DD3A0)
val GreenContainer = Color(0xFFE7F3EC)
val GreenContainerDark = Color(0xFF123523)

val BlueSecondary = Color(0xFF3A5B75)
val BlueSecondaryDark = Color(0xFF8AB4D8)
val BlueContainer = Color(0xFFE8EEF4)
val BlueContainerDark = Color(0xFF1B2E42)

val TealTertiary = Color(0xFF8A6E3A)
val TealTertiaryDark = Color(0xFFD4B78F)
val TealContainer = Color(0xFFF3EBDA)
val TealContainerDark = Color(0xFF2A2216)

// Surfaces — warm paper vs deep ink
val BackgroundLight = Color(0xFFF9F7F2)
val SurfaceLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF1A1E1C)
val OnSurfaceLight = Color(0xFF1A1E1C)
val OutlineLight = Color(0xFFC2C8BF)
val OutlineVariantLight = Color(0xFFE5E3DF)
val ErrorLight = Color(0xFFA12B2F)

val BackgroundDark = Color(0xFF0C1411)
val SurfaceDark = Color(0xFF171E1C)
val OnBackgroundDark = Color(0xFFE8E7E1)
val OnSurfaceDark = Color(0xFFE8E7E1)
val OutlineDark = Color(0xFF404943)
val OutlineVariantDark = Color(0xFF2A3230)
val ErrorDark = Color(0xFFE28A8E)

// Semantic transaction colors — muted, ledger-like, not traffic-light
val CreditGreen = Color(0xFF0E7A4C)
val CreditGreenContainer = Color(0xFFE7F3EC)
val OnCreditGreenContainer = Color(0xFF0A2E1D)

val DebitRed = Color(0xFFA12B2F)
val DebitRedContainer = Color(0xFFF4DFDF)
val OnDebitRedContainer = Color(0xFF3A0F10)

// Hairline and elevation — for paper/ink separation without heavy shadow
val HairlineLight = Color(0xFFE5E3DF)
val HairlineDark = Color(0xFF2A3230)

// Categorical palette — editorial, muted, distinct in both themes
// No neon; tones echo paper inks: emerald, slate, brass, brick, indigo, moss
val CategoryPalette: List<Color> = listOf(
    Color(0xFF0E7A4C), // emerald
    Color(0xFF3A5B75), // slate
    Color(0xFF8A6E3A), // brass
    Color(0xFFA12B2F), // brick
    Color(0xFF2E4A6B), // ink blue
    Color(0xFF6B4A2E), // umber
    Color(0xFF4A6356), // sage
    Color(0xFF6B3A4A), // plum
    Color(0xFF3A6B5A), // moss
    Color(0xFF7A5A2E), // ochre
    Color(0xFF4A5A6B), // stone blue
    Color(0xFF5A3A6B), // dusk
)

fun colorForCategoryIndex(index: Int): Color =
    CategoryPalette[index.mod(CategoryPalette.size)]

// Bank inks for the card carousel — distinct muted inks per bank so each
// page reads as a different paper, with a neutral ink for unknown banks.
val BankInkHdfc = Color(0xFF1D3557)
val BankInkIcici = Color(0xFF3D405B)
val BankInkSbi = Color(0xFF6D597A)
val BankInkAxis = Color(0xFF815582)
val BankInkDefault = Color(0xFF4A4642)

fun bankColor(bankHint: String): Color {
    val bank = bankHint.trim().uppercase()
    return when {
        bank.contains("HDFC") -> BankInkHdfc
        bank.contains("ICICI") -> BankInkIcici
        bank.contains("SBI") -> BankInkSbi
        bank.contains("AXIS") -> BankInkAxis
        else -> BankInkDefault
    }
}
