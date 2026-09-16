package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.domain.StatsEngine
import com.crashlab.analyzer.ui.components.HistogramChart
import com.crashlab.analyzer.ui.components.KeyValueRow
import com.crashlab.analyzer.ui.components.RandomnessNotice
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.components.Sparkline
import com.crashlab.analyzer.ui.components.StatTile
import com.crashlab.analyzer.ui.components.color
import com.crashlab.analyzer.ui.components.emoji
import com.crashlab.analyzer.ui.vm.AnalyticsState
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: AnalyticsState,
    onWindowChange: (Int) -> Unit
) {
    val s = state.stats
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RandomnessNotice(
                "Every round is independent. These figures describe rounds that already " +
                    "happened — they cannot tell you what the next multiplier will be."
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(50, 100, 250, 500).forEach { w ->
                    FilterChip(
                        selected = state.settings.windowSize == w,
                        onClick = { onWindowChange(w) },
                        label = { Text("Last $w") }
                    )
                }
            }
        }

        // ---- Risk traffic light ----
        item {
            val band = state.risk.band
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(56.dp).background(band.color().copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(band.emoji(), style = MaterialTheme.typography.headlineMedium) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.risk.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = band.color(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Volatility score ${state.risk.score}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { state.risk.score / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = band.color(),
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    state.risk.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.risk.factors.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).background(f.band.color(), CircleShape))
                            Text(
                                f.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            f.value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "This is a volatility read-out of past rounds, not a betting signal. " +
                        "A red window does not make a big multiplier 'due'.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (s.count == 0) {
            item {
                SectionCard(title = "No rounds yet") {
                    Text(
                        "Go to the Log tab to enter results manually, paste a batch, or load " +
                            "a simulated reference set to explore the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@LazyColumn
        }

        // ---- Headline tiles ----
        item {
            SectionCard(title = "Window summary", subtitle = "${s.count} rounds analysed") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatTile("Median", "${StatsEngine.fmt(s.median)}x")
                    StatTile("Mean", "${StatsEngine.fmt(s.mean)}x")
                    StatTile("Max", "${StatsEngine.fmt(s.max)}x")
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatTile(
                        "Under 2.00x", StatsEngine.pct(s.bustRate),
                        hint = "theory ≈ 50%"
                    )
                    StatTile(
                        "Under 1.20x", StatsEngine.pct(s.instantBustRate),
                        hint = "theory ≈ 18%"
                    )
                    StatTile(
                        "10x or more", StatsEngine.pct(s.highRate),
                        hint = "theory ≈ 10%"
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Recent trend",
                subtitle = "Log scale, dashed line = 2.00x"
            ) {
                Sparkline(state.window.sortedBy { it.timestamp }.map { it.multiplier })
                Text(
                    "A rising or falling line is sampling noise, not momentum.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Distribution", subtitle = "Share of rounds per band") {
                HistogramChart(state.histogram)
            }
        }

        item {
            SectionCard(title = "Streaks & dispersion") {
                KeyValueRow("Current run under 2.00x", "${s.currentSubTwoStreak} rounds")
                KeyValueRow("Longest run under 2.00x", "${s.longestSubTwoStreak} rounds")
                KeyValueRow("Std. deviation", StatsEngine.fmt(s.stdDev))
                KeyValueRow("Geometric mean", "${StatsEngine.fmt(s.geometricMean)}x")
                KeyValueRow("5th percentile", "${StatsEngine.fmt(s.quantiles[5] ?: 0.0)}x")
                KeyValueRow("95th percentile", "${StatsEngine.fmt(s.quantiles[95] ?: 0.0)}x")
            }
        }

        item {
            SectionCard(
                title = "Randomness checks",
                subtitle = "Evidence that history carries no memory"
            ) {
                KeyValueRow(
                    "Lag-1 autocorrelation",
                    StatsEngine.fmt(state.autocorrelation)
                )
                Text(
                    "Values near 0.00 mean consecutive rounds are unrelated — exactly what a " +
                        "fair RNG produces. No pattern here can be exploited.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val (chi, df) = state.chiSquare
                if (df > 0) {
                    KeyValueRow("Chi-square vs. theory", "${StatsEngine.fmt(chi)} (df $df)")
                }
                KeyValueRow(
                    "Implied house edge (empirical)",
                    "${(s.impliedHouseEdge * 100).roundToInt()}%"
                )
                Text(
                    "The edge is why long-run expected value stays negative for the player, " +
                        "whatever staking plan is used.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AssistChip(
                onClick = {},
                label = { Text("Data is stored only on this device") },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
