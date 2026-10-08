package com.sctech.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sctech.obd.ui.theme.Sct
import java.util.Locale

/** A range on the dial that is drawn tinted and recolours the needle arc when reached. */
data class GaugeZone(val from: Float, val to: Float, val danger: Boolean = true)

data class GaugeScale(
    val min: Float,
    val max: Float,
    val decimals: Int,
    val zones: List<GaugeZone> = emptyList(),
    /** Labels at the arc ends, e.g. "0" and "8" for rpm x1000. */
    val minLabel: String = min.toInt().toString(),
    val maxLabel: String = max.toInt().toString(),
)

private val TURKISH = Locale.forLanguageTag("tr-TR")
private const val START_ANGLE = 150f
private const val SWEEP = 240f

/** Formats with Turkish separators: 7.325 / 13,8 */
fun formatGaugeValue(value: Double, decimals: Int): String =
    String.format(TURKISH, "%,.${decimals}f", value)

@Composable
fun ArcGauge(
    value: Double?,
    unit: String,
    scale: GaugeScale,
    modifier: Modifier = Modifier,
) {
    val c = Sct.colors
    val target = value?.toFloat()?.coerceIn(scale.min, scale.max) ?: scale.min
    val animated by animateFloatAsState(target, tween(durationMillis = 280), label = "gauge")
    val shownValue by animateFloatAsState(value?.toFloat() ?: 0f, tween(durationMillis = 280), label = "gaugeText")

    // Off-scale readings count as the zone at that end (e.g. 24 V -> overvoltage)
    val zone = value?.let { scale.zones.firstOrNull { z -> target >= z.from && target <= z.to } }
    val valueColor = when {
        value == null -> c.textTertiary
        zone == null -> c.accent
        zone.danger -> c.danger
        else -> c.warning
    }

    Box(modifier.fillMaxWidth().height(122.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 6.dp),
        ) {
            val stroke = 6.dp.toPx()
            // A 240° arc is 0.75 of its diameter tall
            val diameter = minOf(size.width, size.height / 0.75f) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)

            drawArcSegment(c.surface2, START_ANGLE, SWEEP, topLeft, arcSize, stroke)
            scale.zones.forEach { z ->
                val from = scale.fraction(z.from) * SWEEP
                val to = scale.fraction(z.to) * SWEEP
                drawArcSegment(
                    (if (z.danger) c.danger else c.warning).copy(alpha = 0.28f),
                    START_ANGLE + from, to - from, topLeft, arcSize, stroke,
                )
            }
            if (value != null) {
                val sweep = scale.fraction(animated) * SWEEP
                if (sweep > 0.5f) drawArcSegment(valueColor, START_ANGLE, sweep, topLeft, arcSize, stroke)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 30.dp)) {
            Text(
                if (value != null) formatGaugeValue(shownValue.toDouble(), scale.decimals) else "– –",
                color = if (value != null) c.textPrimary else c.textTertiary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                style = TabularNumbers,
                maxLines = 1,
            )
            Text(unit, color = c.textSecondary, fontSize = 12.sp)
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            Text(scale.minLabel, color = c.textTertiary, fontSize = 10.sp, style = TabularNumbers)
            Spacer(Modifier.weight(1f))
            Text(scale.maxLabel, color = c.textTertiary, fontSize = 10.sp, style = TabularNumbers)
        }
    }
}

private fun GaugeScale.fraction(v: Float): Float = ((v - min) / (max - min)).coerceIn(0f, 1f)

private fun DrawScope.drawArcSegment(
    color: Color,
    start: Float,
    sweep: Float,
    topLeft: Offset,
    size: Size,
    stroke: Float,
) = drawArc(
    color = color,
    startAngle = start,
    sweepAngle = sweep,
    useCenter = false,
    topLeft = topLeft,
    size = size,
    style = Stroke(width = stroke, cap = StrokeCap.Round),
)
