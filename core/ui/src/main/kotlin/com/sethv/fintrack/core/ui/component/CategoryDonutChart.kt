package com.sethv.fintrack.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sethv.fintrack.core.ui.theme.FinTrackShape
import com.sethv.fintrack.core.ui.theme.colorForCategoryIndex

data class DonutSlice(
    val label: String,
    val value: Float,
    val colorIndex: Int,
)

@Composable
fun CategoryDonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerSubLabel: String,
    modifier: Modifier = Modifier,
    strokeWidthDp: Int = 18,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)

    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f))
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val hairline = MaterialTheme.colorScheme.outlineVariant
        Box(modifier = Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(148.dp)) {
                val stroke = strokeWidthDp.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                var startAngle = -90f
                if (slices.isEmpty()) {
                    drawArc(
                        color = hairline,
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
                    )
                } else {
                    slices.forEach { slice ->
                        val sweep = (slice.value / total) * 360f * sweepProgress.value
                        drawArc(
                            color = colorForCategoryIndex(slice.colorIndex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                            useCenter = false, topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = centerSubLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            slices.forEach { slice ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colorForCategoryIndex(slice.colorIndex), FinTrackShape.Pill),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${"%.0f".format(slice.value)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}
