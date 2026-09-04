package com.sethv.fintrack.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sethv.fintrack.core.ui.util.Format

/**
 * Ledger amount — mono tabular, spring-driven.
 */
@Composable
fun AnimatedCurrency(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    fontWeight: FontWeight = FontWeight.Black,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animatedValue by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "currency-counter",
    )
    Text(
        text = Format.currency(if (kotlin.math.abs(animatedValue) < 0.005f) 0.0 else animatedValue.toDouble()),
        style = style.copy(fontFamily = FontFamily.Monospace, letterSpacing = (-0.5).sp),
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}
