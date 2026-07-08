package com.sethv.fintrack.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Lightweight Canvas sparkline. Renders a stroked polyline connecting the
 * data points. Empty list draws nothing.
 */
@Composable
fun SparkLine(
    values: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    fillUnderline: Boolean = true,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(0.0001)
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = h - ((v - min) / range).toFloat() * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        if (fillUnderline) {
            val area = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = area,
                color = lineColor.copy(alpha = 0.12f),
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, pathEffect = PathEffect.cornerPathEffect(8f)),
        )

        // End-point dot
        val lastX = stepX * (values.size - 1)
        val lastY = h - ((values.last() - min) / range).toFloat() * h
        drawCircle(color = lineColor, radius = 4f, center = Offset(lastX, lastY))
    }
}