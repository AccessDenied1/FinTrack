package com.sethv.fintrack.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Precision Ledger spacing — 4pt grid, editorial rhythm. */
object FinTrackSpacing {
    val None: Dp = 0.dp
    val Xxs: Dp = 2.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val SmPlus: Dp = 12.dp
    val Md: Dp = 16.dp
    val MdPlus: Dp = 20.dp
    val Lg: Dp = 24.dp
    val Xl: Dp = 32.dp
    val Xxl: Dp = 48.dp
    val Huge: Dp = 64.dp

    /** Bottom padding to keep last list item above the bottom nav bar. */
    val ListBottomFab: Dp = 88.dp

    /** Hairline border width (0.5dp renders as 1px on most densities). */
    val Hairline: Dp = 0.5.dp
}
