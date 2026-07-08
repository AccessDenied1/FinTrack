package com.sethv.fintrack.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for spacing. Stop scattering raw dp values. */
object FinTrackSpacing {
    val None: Dp = 0.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    /** Compact card padding (12dp). */
    val SmPlus: Dp = 12.dp
    val Md: Dp = 16.dp
    val Lg: Dp = 24.dp
    val Xl: Dp = 32.dp
    val Xxl: Dp = 48.dp

    /** Bottom padding to keep last list item above the bottom nav bar. */
    val ListBottomFab: Dp = 72.dp
}