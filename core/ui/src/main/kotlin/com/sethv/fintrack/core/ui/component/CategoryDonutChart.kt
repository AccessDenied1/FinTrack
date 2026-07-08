package com.sethv.fintrack.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sethv.fintrack.core.ui.theme.colorForCategoryIndex
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

data class DonutSlice(
    val label: String,
    val value: Float,
    val colorIndex: Int,
)

/**
 * Custom Canvas donut chart. No external chart library.
 * Center label shows the total; legend renders colored swatches next to labels.
 */
@Composable
fun CategoryDonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerSubLabel: String,
    modifier: Modifier = Modifier,
    strokeWidthDp: Int = 28,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val stroke = strokeWidthDp.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                var startAngle = -90f
                if (slices.isEmpty()) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                } else {
                    slices.forEach { slice ->
                        val sweep = (slice.value / total) * 360f
                        drawArc(
                            color = colorForCategoryIndex(slice.colorIndex),
                            startAngle = startAngle,
                            sweepAngle = sweep - 1f, // small gap
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = centerLabel, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = centerSubLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            slices.forEach { slice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 8.dp),
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = colorForCategoryIndex(slice.colorIndex))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${"%.0f".format(slice.value)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}