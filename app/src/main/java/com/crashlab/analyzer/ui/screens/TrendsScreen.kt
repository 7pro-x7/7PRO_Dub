package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.domain.StatsEngine
import com.crashlab.analyzer.ui.components.KeyValueRow
import com.crashlab.analyzer.ui.components.RandomnessNotice
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.components.Sparkline
import com.crashlab.analyzer.ui.components.SurvivalBars
import com.crashlab.analyzer.ui.vm.AnalyticsState

@Composable
fun TrendsScreen(state: AnalyticsState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RandomnessNotice(
                "Comparing your sample with the textbook crash curve shows how ordinary " +
                    "streaks are. Gaps between the two bars are sampling noise, not an edge."
            )
        }

        if (state.stats.count < 5) {
            item {
                SectionCard(title = "Not enough data") {
                    Text(
                        "Record at least 5 rounds to see trend analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(
                title = "Cash-out target reach rate",
                subtitle = "Your history vs. the 0.99/x theoretical curve"
            ) {
                SurvivalBars(
                    labels = state.survival.map { StatsEngine.fmt(it.target) + "x" },
                    empirical = state.survival.map { it.empiricalProbability },
                    theoretical = state.survival.map { it.theoreticalProbability }
                )
                Text(
                    "Reading: if you always cashed out at 2.00x across these rounds, you " +
                        "would have succeeded in the share shown — already-settled outcomes, " +
                        "not a forecast.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Sampling uncertainty",
                subtitle = "95% confidence interval on each rate"
            ) {
                state.survival.filter { it.target <= 10.0 }.forEach { p ->
                    val lo = ((p.empiricalProbability - p.marginOfError) * 100).coerceAtLeast(0.0)
                    val hi = ((p.empiricalProbability + p.marginOfError) * 100).coerceAtMost(100.0)
                    KeyValueRow(
                        "≥ ${StatsEngine.fmt(p.target)}x",
                        "${String.format("%.1f", lo)}% – ${String.format("%.1f", hi)}%"
                    )
                }
                Text(
                    "Wide intervals mean your sample is small. Small samples produce " +
                        "convincing-looking patterns that vanish with more data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(
                title = "Rolling 20-round average",
                subtitle = "Smoothed mean multiplier over time"
            ) {
                Sparkline(
                    values = state.rollingMean.ifEmpty { listOf(1.0, 1.0) },
                    referenceLine = state.stats.mean
                )
                Text(
                    "The dashed line is the window mean. Excursions above and below it are " +
                        "what random data looks like.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Percentile breakdown") {
                listOf(5, 25, 50, 75, 95, 99).forEach { q ->
                    KeyValueRow(
                        "P$q",
                        "${StatsEngine.fmt(state.stats.quantiles[q] ?: 0.0)}x"
                    )
                }
            }
        }

        item {
            SectionCard(title = "What this screen cannot do") {
                Text(
                    "It cannot identify a hot table, a due multiplier, a pattern in the seed, " +
                        "or a safe time to bet. Crash outcomes are drawn independently from the " +
                        "same distribution every round, and the house edge applies to all of " +
                        "them equally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
