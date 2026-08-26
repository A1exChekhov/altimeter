package com.chelmodeev.altimeter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class TrendLine(
    val key: String,
    val label: String,
    val unit: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
    val decimals: Int = 0,
)

data class TrendScale(
    val line: TrendLine,
    val min: Double,
    val max: Double,
    val plotMin: Double,
    val plotMax: Double,
)

fun trendScales(
    lines: List<TrendLine>,
    startTimeMs: Long,
    endTimeMs: Long,
): List<TrendScale> {
    val cleanLines = lines.mapNotNull { line ->
        val points = line.points.asSequence()
            .filter { (time, value) -> time > 0L && value.isFinite() }
            .sortedBy { it.first }
            .toList()
        if (points.isEmpty()) null else line.copy(points = points)
    }
    val safeStart = minOf(startTimeMs, endTimeMs - 1L)
    val safeEnd = maxOf(endTimeMs, safeStart + 1L)
    return cleanLines.mapNotNull { line ->
        val values = line.points.asSequence()
            .filter { it.first in safeStart..safeEnd }
            .map { it.second }
            .toList()
        if (values.isEmpty()) null else {
            val rawMin = values.min()
            val rawMax = values.max()
            val basePadding = when (line.key) {
                "heart" -> 5.0
                "oxygen" -> 1.0
                "steps" -> 1.0
                else -> 2.0
            }
            val padding = ((rawMax - rawMin) * 0.08).coerceAtLeast(basePadding)
            TrendScale(
                line = line,
                min = rawMin,
                max = rawMax,
                plotMin = rawMin - padding,
                plotMax = rawMax + padding,
            )
        }
    }
}

/**
 * График с общей реальной осью времени и отдельной шкалой Y для каждой линии.
 * Первая точка неполного окна закреплена слева; затем график заполняется вправо.
 */
@Composable
fun CombinedTrendChart(
    lines: List<TrendLine>,
    startTimeMs: Long,
    endTimeMs: Long,
    gridColor: Color,
    axisColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    var cursorX by remember { mutableStateOf<Float?>(null) }
    val localTimeZoneId = TimeZone.getDefault().id
    val timeFormatter = remember(localTimeZoneId, Locale.getDefault()) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(localTimeZoneId)
        }
    }
    val scales = trendScales(lines, startTimeMs, endTimeMs)

    Canvas(
        modifier = modifier.pointerInput(lines, startTimeMs, endTimeMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                cursorX = down.position.x
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull()
                    if (change != null) cursorX = change.position.x
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        if (scales.isEmpty()) return@Canvas
        if (!size.width.isFinite() || !size.height.isFinite() ||
            size.width <= 8f || size.height <= 32f
        ) return@Canvas

        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 29.dp.toPx()
        val plotWidth = (right - left).coerceAtLeast(1f)
        val plotHeight = (bottom - top).coerceAtLeast(1f)
        val startTime = minOf(startTimeMs, endTimeMs - 1L)
        val endTime = maxOf(endTimeMs, startTime + 1L)
        val span = (endTime - startTime).coerceAtLeast(1L)

        repeat(5) { index ->
            val y = top + plotHeight * index / 4f
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(
            color = axisColor.copy(alpha = 0.55f),
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            strokeWidth = 1.dp.toPx(),
        )

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.dp.toPx()
            color = axisColor.toArgb()
        }
        repeat(4) { index ->
            val fraction = index / 3f
            val x = left + plotWidth * fraction
            val timestamp = startTime + (span * fraction).toLong()
            val label = timeFormatter.format(Date(timestamp))
            val width = axisPaint.measureText(label)
            val drawX = when (index) {
                0 -> left
                3 -> right - width
                else -> x - width / 2f
            }
            drawContext.canvas.nativeCanvas.drawText(label, drawX, size.height - 7.dp.toPx(), axisPaint)
        }

        fun xFor(time: Long): Float = left +
            ((time - startTime).toDouble() / span.toDouble()).coerceIn(0.0, 1.0).toFloat() * plotWidth

        scales.forEach { scale ->
            val visible = scale.line.points.asSequence()
                .filter { it.first in startTime..endTime }
                .sortedBy { it.first }
                .toList()
            if (visible.isEmpty()) return@forEach
            val valueSpan = (scale.plotMax - scale.plotMin).coerceAtLeast(1e-9)
            fun yFor(value: Double): Float = bottom -
                ((value - scale.plotMin) / valueSpan).coerceIn(0.0, 1.0).toFloat() * plotHeight

            if (visible.size == 1) {
                drawCircle(
                    color = scale.line.color,
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(visible[0].first), yFor(visible[0].second)),
                )
            } else {
                val path = Path()
                visible.forEachIndexed { index, sample ->
                    val point = Offset(xFor(sample.first), yFor(sample.second))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path = path,
                    color = scale.line.color,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        repeat(5) { index ->
            val fraction = index / 4.0
            val y = top + plotHeight * index / 4f
            val baseline = when (index) {
                0 -> y + 9.dp.toPx()
                4 -> y - 3.dp.toPx()
                else -> y - 3.dp.toPx()
            }
            var textX = left + 4.dp.toPx()
            scales.forEach { scale ->
                val value = scale.plotMax - (scale.plotMax - scale.plotMin) * fraction
                val label = formatTrendValue(value, scale.line.decimals)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.dp.toPx()
                    color = scale.line.color.copy(alpha = 0.92f).toArgb()
                }
                drawContext.canvas.nativeCanvas.drawText(label, textX, baseline, paint)
                textX += paint.measureText(label) + 7.dp.toPx()
            }
        }

        cursorX?.takeIf { it.isFinite() }?.coerceIn(left, right)?.let { x ->
            val cursorTime = startTime + (((x - left) / plotWidth) * span).toLong()
            val cursorValues = scales.mapNotNull { scale ->
                val nearest = scale.line.points.asSequence()
                    .filter { it.first in startTime..endTime }
                    .minByOrNull { abs(it.first - cursorTime) }
                    ?: return@mapNotNull null
                val valueSpan = (scale.plotMax - scale.plotMin).coerceAtLeast(1e-9)
                val y = bottom - ((nearest.second - scale.plotMin) / valueSpan)
                    .coerceIn(0.0, 1.0).toFloat() * plotHeight
                drawCircle(Color.White, 4.dp.toPx(), Offset(xFor(nearest.first), y))
                drawCircle(scale.line.color, 2.6.dp.toPx(), Offset(xFor(nearest.first), y))
                scale to nearest
            }

            val valuePaints = cursorValues.map { (scale, _) ->
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 12.dp.toPx()
                    color = scale.line.color.toArgb()
                    isFakeBoldText = true
                }
            }
            val valueLabels = cursorValues.map { (scale, nearest) ->
                formatTrendValue(nearest.second, scale.line.decimals) + scale.line.unit
            }
            val valueGap = 10.dp.toPx()
            val valueContentWidth = valueLabels.indices.sumOf { index ->
                valuePaints[index].measureText(valueLabels[index]).toDouble()
            }.toFloat() + valueGap * (valueLabels.size - 1).coerceAtLeast(0)
            val valueBubbleWidth = (valueContentWidth + 14.dp.toPx()).coerceAtMost(plotWidth)
            val valueBubbleHeight = 28.dp.toPx()
            val valueBubbleLeft = (x - valueBubbleWidth / 2f)
                .coerceIn(left, right - valueBubbleWidth)
            drawRoundRect(
                color = backgroundColor.copy(alpha = 0.98f),
                topLeft = Offset(valueBubbleLeft, top),
                size = Size(valueBubbleWidth, valueBubbleHeight),
                cornerRadius = CornerRadius(7.dp.toPx()),
            )
            drawRoundRect(
                color = axisColor.copy(alpha = 0.46f),
                topLeft = Offset(valueBubbleLeft, top),
                size = Size(valueBubbleWidth, valueBubbleHeight),
                cornerRadius = CornerRadius(7.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            var valueTextX = valueBubbleLeft + 7.dp.toPx()
            valueLabels.indices.forEach { index ->
                drawContext.canvas.nativeCanvas.drawText(
                    valueLabels[index],
                    valueTextX,
                    top + 19.dp.toPx(),
                    valuePaints[index],
                )
                valueTextX += valuePaints[index].measureText(valueLabels[index]) + valueGap
            }

            drawLine(
                color = axisColor.copy(alpha = 0.82f),
                start = Offset(x, top + valueBubbleHeight + 2.dp.toPx()),
                end = Offset(x, bottom),
                strokeWidth = 1.2.dp.toPx(),
            )

            val timeLabel = timeFormatter.format(Date(cursorTime))
            val timeWidth = axisPaint.measureText(timeLabel)
            val timeBubbleWidth = timeWidth + 12.dp.toPx()
            val timeBubbleHeight = 19.dp.toPx()
            val timeBubbleLeft = (x - timeBubbleWidth / 2f)
                .coerceIn(left, right - timeBubbleWidth)
            val timeBubbleTop = bottom + 3.dp.toPx()
            drawRoundRect(
                color = backgroundColor.copy(alpha = 0.98f),
                topLeft = Offset(timeBubbleLeft, timeBubbleTop),
                size = Size(timeBubbleWidth, timeBubbleHeight),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = axisColor.copy(alpha = 0.4f),
                topLeft = Offset(timeBubbleLeft, timeBubbleTop),
                size = Size(timeBubbleWidth, timeBubbleHeight),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.drawText(
                timeLabel,
                timeBubbleLeft + 6.dp.toPx(),
                timeBubbleTop + 13.dp.toPx(),
                axisPaint,
            )
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

fun formatTrendValue(value: Double, decimals: Int): String =
    if (!value.isFinite()) "—"
    else if (decimals <= 0) value.toInt().toString()
    else String.format(Locale.getDefault(), "%.${decimals}f", value)
