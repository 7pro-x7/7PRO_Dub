package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.domain.StrategyType
import com.crashlab.analyzer.ui.components.EquityChart
import com.crashlab.analyzer.ui.components.KeyValueRow
import com.crashlab.analyzer.ui.components.RandomnessNotice
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.theme.RiskGreen
import com.crashlab.analyzer.ui.theme.RiskRed
import com.crashlab.analyzer.ui.vm.SimState
import kotlin.math.roundToInt

private fun StrategyType.label() = when (this) {
    StrategyType.FLAT -> "Flat"
    StrategyType.MARTINGALE -> "Martingale"
    StrategyType.DALEMBERT -> "D'Alembert"
    StrategyType.FIBONACCI -> "Fibonacci"
    StrategyType.PERCENT_BANKROLL -> "% bankroll"
}

private fun StrategyType.blurb() = when (this) {
    StrategyType.FLAT -> "Same stake every round. The least destructive, still negative EV."
    StrategyType.MARTINGALE -> "Doubles after every loss. Recovers small losses until one " +
        "streak exceeds your bankroll or the table limit — then it takes everything."
    StrategyType.DALEMBERT -> "Adds one unit after a loss, removes one after a win. Slower " +
        "escalation than Martingale, same negative expectation."
    StrategyType.FIBONACCI -> "Follows the Fibonacci sequence on losses. Grows more slowly " +
        "than doubling but still compounds into large stakes."
    StrategyType.PERCENT_BANKROLL -> "Stakes a fixed share of the current bankroll. Cannot " +
        "hit zero outright, but grinds down with the house edge."
}

@Composable
fun SimulatorScreen(
    sim: SimState,
    dataCount: Int,
    onConfig: ((com.crashlab.analyzer.domain.StrategyConfig) -> com.crashlab.analyzer.domain.StrategyConfig) -> Unit,
    onRun: () -> Unit
) {
    val cfg = sim.config
    var bankrollText by remember { mutableStateOf(cfg.startingBankroll.roundToInt().toString()) }
    var stakeText by remember { mutableStateOf(cfg.baseStake.toString()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RandomnessNotice(
                "This is a backtest on rounds that already happened. A profitable result " +
                    "here is luck in one sample — it does not transfer to future play. " +
                    "No staking plan can overcome the house edge.",
                tone = RiskRed
            )
        }

        item {
            SectionCard(title = "Staking plan") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrategyType.entries.forEach { t ->
                        FilterChip(
                            selected = cfg.type == t,
                            onClick = { onConfig { c -> c.copy(type = t) } },
                            label = { Text(t.label()) }
                        )
                    }
                }
                Text(
                    cfg.type.blurb(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Parameters") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = bankrollText,
                        onValueChange = {
                            bankrollText = it
                            it.toDoubleOrNull()?.let { v ->
                                onConfig { c -> c.copy(startingBankroll = v) }
                            }
                        },
                        label = { Text("Bankroll") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = stakeText,
                        onValueChange = {
                            stakeText = it
                            it.toDoubleOrNull()?.let { v ->
                                onConfig { c -> c.copy(baseStake = v) }
                            }
                        },
                        label = { Text("Base stake") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                Column {
                    Text(
                        "Cash-out target: ${String.format("%.2f", cfg.cashOutAt)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = cfg.cashOutAt.toFloat(),
                        onValueChange = { onConfig { c -> c.copy(cashOutAt = it.toDouble()) } },
                        valueRange = 1.1f..20f
                    )
                }
                Column {
                    Text(
                        "Stop loss: ${cfg.stopLossPct.roundToInt()}% of bankroll",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = cfg.stopLossPct.toFloat(),
                        onValueChange = { onConfig { c -> c.copy(stopLossPct = it.toDouble()) } },
                        valueRange = 10f..100f
                    )
                }
                Column {
                    Text(
                        "Take profit: +${cfg.takeProfitPct.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = cfg.takeProfitPct.toFloat(),
                        onValueChange = { onConfig { c -> c.copy(takeProfitPct = it.toDouble()) } },
                        valueRange = 10f..300f
                    )
                }

                Button(
                    onClick = onRun,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !sim.running && dataCount >= 20
                ) {
                    if (sim.running) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (dataCount < 20) "Need 20+ rounds" else "Run backtest")
                }
            }
        }

        sim.result?.let { r ->
            item {
                SectionCard(
                    title = "Single replay",
                    subtitle = "Your ${r.roundsPlayed} recorded rounds, in order"
                ) {
                    EquityChart(r.equity, r.config.startingBankroll)
                    KeyValueRow(
                        "Net result",
                        "${if (r.netProfit >= 0) "+" else ""}${String.format("%.2f", r.netProfit)}",
                        if (r.netProfit >= 0) RiskGreen else RiskRed
                    )
                    KeyValueRow("ROI", "${String.format("%.1f", r.roiPct)}%")
                    KeyValueRow("Rounds played", r.roundsPlayed.toString())
                    KeyValueRow("Win rate", "${(r.winRate * 100).roundToInt()}%")
                    KeyValueRow("Max drawdown", "${String.format("%.1f", r.maxDrawdownPct)}%")
                    KeyValueRow("Longest losing streak", r.longestLosingStreak.toString())
                    KeyValueRow("Total staked", String.format("%.2f", r.totalStaked))
                    when {
                        r.ruin -> Text(
                            "Bankroll wiped out before the data ran out.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RiskRed
                        )
                        r.hitStopLoss -> Text(
                            "Stop loss triggered — the session ended early.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        r.hitTakeProfit -> Text(
                            "Take profit hit in this one sample. Re-run with different " +
                                "settings to see how fragile that outcome is.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RiskGreen
                        )
                    }
                }
            }
        }

        sim.monteCarlo?.let { mc ->
            item {
                SectionCard(
                    title = "Spread across ${mc.runs} resamples",
                    subtitle = "Bootstrapped from your own history — the honest view"
                ) {
                    KeyValueRow("Median final bankroll", String.format("%.2f", mc.medianFinal))
                    KeyValueRow("Mean final bankroll", String.format("%.2f", mc.meanFinal))
                    KeyValueRow(
                        "5th–95th percentile",
                        "${String.format("%.0f", mc.p05)} – ${String.format("%.0f", mc.p95)}"
                    )
                    KeyValueRow(
                        "Runs ending in ruin",
                        "${(mc.ruinRate * 100).roundToInt()}%",
                        RiskRed
                    )
                    KeyValueRow(
                        "Runs ending in profit",
                        "${(mc.profitRate * 100).roundToInt()}%"
                    )
                    KeyValueRow("Worst drawdown seen", "${String.format("%.1f", mc.worstDrawdownPct)}%")
                    Text(
                        "Even when more than half of runs finish in profit, the average " +
                            "outcome stays negative: the rare catastrophic runs are far " +
                            "larger than the frequent small wins. That asymmetry is the " +
                            "house edge doing its work.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
