package com.chelmodeev.altimeter.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.ChartPoint
import com.chelmodeev.altimeter.util.Fmt

@Composable
fun AltitudeChart(
    points: List<ChartPoint>,
    unit: AltUnit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73FFFFFF
            textSize = with(density) { 10.sp.toPx() }
        }
    }

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val t0 = points.first().timeMs
        val t1 = points.last().timeMs
        if (t1 <= t0) return@Canvas

        var minA = points.minOf { it.altitude }
        var maxA = points.maxOf { it.altitude }
        if (maxA - minA < 12.0) {
            val mid = (maxA + minA) / 2
            minA = mid - 6.0
            maxA = mid + 6.0
        }
        val pad = (maxA - minA) * 0.10
        minA -= pad
        maxA += pad

        val w = size.width
        val h = size.height
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0).toFloat() * w
        fun y(a: Double) = h - ((a - minA) / (maxA - minA)).toFloat() * h

        // сетка + подписи
        val gridValues = listOf(minA + pad, (minA + maxA) / 2, maxA - pad)
        for (gv in gridValues) {
            val gy = y(gv)
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                Fmt.altitudeValue(gv, unit),
                6.dp.toPx(),
                gy - 4.dp.toPx(),
                labelPaint,
            )
        }

        val line = Path()
        points.forEachIndexed { i, p ->
            val px = x(p.timeMs)
            val py = y(p.altitude)
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(x(points.first().timeMs), h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(
            path = line,
            color = accent,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // точка текущего значения
        val last = points.last()
        val cx = x(last.timeMs)
        val cy = y(last.altitude)
        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(cx, cy))
    }
}
