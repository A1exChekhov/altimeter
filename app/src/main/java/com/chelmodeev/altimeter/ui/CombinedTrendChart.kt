package com.chelmodeev.altimeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class TrendLine(
    val color: Color,
    val points: List<Pair<Long, Double>>,
)

/**
 * Общий график динамики. У каждой метрики своя шкала, поэтому на одном поле
 * сравнивается направление изменений, а абсолютные значения остаются в легенде.
 */
@Composable
fun CombinedTrendChart(
    lines: List<TrendLine>,
    windowMs: Long,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - windowMs
        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 10.dp.toPx()
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        repeat(4) { index ->
            val y = chartTop + chartHeight * index / 3f
            drawLine(
                color = Color.White.copy(alpha = if (index == 3) 0.10f else 0.055f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        lines.forEach { line ->
            val visible = line.points
                .asSequence()
                .filter { it.first in startTime..endTime }
                .sortedBy { it.first }
                .toList()
            if (visible.isEmpty()) return@forEach

            val minValue = visible.minOf { it.second }
            val maxValue = visible.maxOf { it.second }
            val valueRange = (maxValue - minValue).takeIf { it > 1e-9 }
            fun point(time: Long, value: Double): Offset {
                val x = ((time - startTime).toDouble() / windowMs.toDouble())
                    .coerceIn(0.0, 1.0).toFloat() * size.width
                val normalized = valueRange?.let { (value - minValue) / it } ?: 0.5
                val y = chartBottom - normalized.toFloat() * chartHeight
                return Offset(x, y)
            }

            if (visible.size == 1) {
                drawCircle(
                    color = line.color,
                    radius = 3.dp.toPx(),
                    center = point(visible[0].first, visible[0].second),
                )
            } else {
                val path = Path()
                visible.forEachIndexed { index, sample ->
                    val p = point(sample.first, sample.second)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = line.color,
                    style = Stroke(width = 2.25.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
