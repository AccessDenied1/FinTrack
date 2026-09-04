package com.sethv.fintrack.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Precision Ledger shape system.
 * - Tight (8dp) for chips and small controls — technical.
 * - Medium (14dp) for cards — paper-like, not bubbly.
 * - Large (20dp) for hero surfaces — editorial.
 * - Pill for capsules.
 * Never use 24-28dp everywhere — that's the slop giveaway.
 */
object FinTrackShape {
    val Tiny = RoundedCornerShape(6.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val XLarge = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(999.dp)
}
