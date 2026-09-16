package com.crashlab.analyzer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.domain.Bucket
import com.crashlab.analyzer.domain.EquityPoint
import kotlin.math.max
import kotlin.math.min

/** Lightweight sparkline for a series of multipliers (log-scaled Y). */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    referenceLine: Double? = 2.0
) {
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.size < 2) return@Canvas
        val logs = values.map { kotlin.math.ln(max(it, 1.0)) }
        val maxV = logs.max().coerceAtLeast(0.1)
        val w = size.width
        val h = size.height
        val dx = w / (values.size - 1)

        referenceLine?.let { ref ->
            val y = h - (kotlin.math.ln(ref) / maxV * h).toFloat().coerceIn(0f, h)
            drawLine(
                color = grid,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(w, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }

        val path = Path()
        logs.forEachIndexed { i, v ->
            val x = i * dx
            val y = h - (v / maxV * h).toFloat().coerceIn(0f, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5f))
    }
}

/** Horizontal distribution bars. */
@Composable
fun HistogramChart(buckets: List<Bucket>, barColor: Color = MaterialTheme.colorScheme.primary) {
    val maxShare = (buckets.maxOfOrNull { it.share } ?: 1.0).coerceAtLeast(0.01)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.26f)
                )
                Canvas(Modifier.fillMaxWidth(0.62f).height(14.dp)) {
                    val r = size.height / 2
                    drawRoundRect(
                        color = track,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                    val frac = (b.share / maxShare).toFloat().coerceIn(0f, 1f)
                    if (frac > 0f) {
                        drawRoundRect(
                            color = barColor,
                            size = androidx.compose.ui.geometry.Size(size.width * frac, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                        )
                    }
                }
                Text(
                    "  ${(b.share * 100).let { String.format("%.1f", it) }}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

/** Equity curve from a strategy backtest. */
@Composable
fun EquityChart(
    points: List<EquityPoint>,
    startingBankroll: Double,
    modifier: Modifier = Modifier
) {
    val up = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        if (points.size < 2) return@Canvas
        val values = points.map { it.bankroll }
        val lo = min(values.min(), startingBankroll) * 0.98
        val hi = max(values.max(), startingBankroll) * 1.02
        val range = (hi - lo).coerceAtLeast(1e-6)
        val w = size.width
        val h = size.height
        val dx = w / (points.size - 1)

        fun yOf(v: Double) = (h - ((v - lo) / range * h)).toFloat().coerceIn(0f, h)

        val baseY = yOf(startingBankroll)
        drawLine(
            color = baseline,
            start = androidx.compose.ui.geometry.Offset(0f, baseY),
            end = androidx.compose.ui.geometry.Offset(w, baseY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * dx
            val y = yOf(p.bankroll)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, up, style = Stroke(width = 3f))
    }
}

/** Empirical vs theoretical survival comparison. */
@Composable
fun SurvivalBars(
    labels: List<String>,
    empirical: List<Double>,
    theoretical: List<Double>,
    empiricalColor: Color = MaterialTheme.colorScheme.primary,
    theoreticalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.indices.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "≥ ${labels[i]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${String.format("%.1f", empirical[i] * 100)}% vs ${
                            String.format("%.1f", theoretical[i] * 100)
                        }% theory",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = size.height / 2
                    drawRoundRect(
                        color = theoreticalColor.copy(alpha = 0.28f),
                        size = androidx.compose.ui.geometry.Size(
                            (size.width * theoretical[i]).toFloat().coerceIn(0f, size.width),
                            size.height
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                    drawRoundRect(
                        color = empiricalColor,
                        size = androidx.compose.ui.geometry.Size(
                            (size.width * empirical[i]).toFloat().coerceIn(0f, size.width),
                            size.height * 0.55f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                }
            }
        }
    }
}
