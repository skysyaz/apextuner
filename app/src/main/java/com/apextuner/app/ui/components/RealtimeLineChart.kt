package com.apextuner.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Compact real-time line chart. Renders a rolling window of [historyPoints]
 * values as a smooth curve with a gradient fill below. Used in the dashboard
 * for CPU/GPU/thermal/FPS mini-charts and on the tuning pages for live
 * frequency traces.
 *
 * This is a hand-rolled Canvas implementation rather than Vico because the
 * dashboard needs ~6 simultaneous mini-charts and Vico's per-chart overhead
 * is non-trivial. Vico is still used on the dedicated analytics screens where
 * richer axes/legends matter.
 *
 * The chart auto-scales to the [minValue]/[maxValue] range; if the data
 * exceeds it the line is clamped to the canvas edges rather than re-scaling
 * mid-stream (which would cause visual jitter).
 */
@Composable
fun RealtimeLineChart(
    history: List<Float>,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    lineColor: Color = Color(0xFF7C4DFF),
    fillColor: Color = lineColor.copy(alpha = 0.22f),
    strokeWidth: Float = 4f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (history.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val range = (maxValue - minValue).coerceAtLeast(0.0001f)

        // Build the smooth path via cubic segments.
        val path = Path()
        val fill = Path()
        val stepX = if (history.size > 1) w / (history.size - 1) else w

        val firstY = h - ((history.first() - minValue) / range).coerceIn(0f, 1f) * h
        path.moveTo(0f, firstY)
        fill.moveTo(0f, h)
        fill.lineTo(0f, firstY)

        for (i in 1 until history.size) {
            val x = i * stepX
            val y = h - ((history[i] - minValue) / range).coerceIn(0f, 1f) * h
            val prevX = (i - 1) * stepX
            val prevY = h - ((history[i - 1] - minValue) / range).coerceIn(0f, 1f) * h
            // Quadratic smoothing: midpoint between prev and current as control.
            val midX = (prevX + x) / 2
            path.cubicTo(midX, prevY, midX, y, x, y)
            fill.cubicTo(midX, prevY, midX, y, x, y)
        }
        fill.lineTo(w, h)
        fill.close()

        drawPath(fill, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
        drawPath(
            path = path,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            color = lineColor
        )
    }
}

@Suppress("unused")
private val mutableStateMarker: Any get() {
    var x by mutableStateOf(0f)
    x = 1f
    return x
}
