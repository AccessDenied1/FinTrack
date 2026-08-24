package com.sethv.fintrack.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.sethv.fintrack.core.ui.util.Format

/**
 * Money text that counts up/down smoothly whenever the target amount changes,
 * instead of hard-snapping. First composition animates from zero, which gives
 * dashboards a lively "ticking total" feel.
 */
@Composable
fun AnimatedCurrency(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animatedValue by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "currency-counter",
    )
    Text(
        // Guard against float rounding showing "-0" for tiny negatives.
        text = Format.currency(if (kotlin.math.abs(animatedValue) < 0.005f) 0.0 else animatedValue.toDouble()),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}
