package com.sethv.fintrack.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Seven daily-spending bars with weekday initials underneath.
 *
 * - Bars scale against the week's peak; flat days render a small stub so the
 *   weekly rhythm stays readable.
 * - The LAST bar (today) is highlighted in [highlightColor].
 */
@Composable
fun WeeklyBarsChart(
    values: List<Double>,
    dayLabels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            if (values.isEmpty()) return@Canvas
            val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val slotWidth = size.width / values.size
            val barWidth = slotWidth * 0.52f
            val minHeight = 4.dp.toPx()
            val drawableHeight = size.height

            values.forEachIndexed { index, value ->
                val fraction = (value / peak).coerceIn(0.0, 1.0)
                val barHeight = (fraction * (drawableHeight - minHeight)).toFloat() + minHeight
                val left = slotWidth * index + (slotWidth - barWidth) / 2f
                val top = drawableHeight - barHeight
                drawRoundRect(
                    color = if (index == values.lastIndex) highlightColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            dayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == dayLabels.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == dayLabels.lastIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        labelColor
                    },
                )
            }
        }
    }
}
