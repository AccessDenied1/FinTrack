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
import androidx.compose.ui.unit.sp

@Composable
fun WeeklyBarsChart(
    values: List<Double>,
    dayLabels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.outlineVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier) {
        val hairline = MaterialTheme.colorScheme.outlineVariant
        Canvas(modifier = Modifier.fillMaxWidth().height(88.dp)) {
            if (values.isEmpty()) return@Canvas
            val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val slotWidth = size.width / values.size
            val barWidth = slotWidth * 0.38f
            val minHeight = 3.dp.toPx()
            val h = size.height
            // hairline baseline — adaptive to light/dark
            drawLine(
                color = hairline,
                start = Offset(0f, h - 0.5f),
                end = Offset(size.width, h - 0.5f),
                strokeWidth = 0.5.dp.toPx(),
            )
            values.forEachIndexed { index, value ->
                val fraction = (value / peak).coerceIn(0.0, 1.0)
                val barHeight = (fraction * (h - 8.dp.toPx() - minHeight)).toFloat() + minHeight
                val left = slotWidth * index + (slotWidth - barWidth) / 2f
                val top = h - barHeight - 4.dp.toPx()
                val isToday = index == values.lastIndex
                drawRoundRect(
                    color = if (isToday) highlightColor else barColor.copy(alpha = 0.55f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            dayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.4.sp,
                    fontWeight = if (index == dayLabels.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == dayLabels.lastIndex) MaterialTheme.colorScheme.primary else labelColor,
                )
            }
        }
    }
}
