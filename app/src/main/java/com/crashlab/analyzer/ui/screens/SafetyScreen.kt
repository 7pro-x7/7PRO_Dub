package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.crashlab.analyzer.ui.components.RandomnessNotice
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.theme.RiskRed

private data class SelfCheck(val question: String)

private val SELF_CHECKS = listOf(
    SelfCheck("Have you bet more than you planned to in the last month?"),
    SelfCheck("Have you chased losses, trying to win back money?"),
    SelfCheck("Have you borrowed money or sold anything to gamble?"),
    SelfCheck("Has gambling caused you stress, anxiety, or sleep problems?"),
    SelfCheck("Have people close to you expressed concern about your gambling?"),
    SelfCheck("Have you tried to cut back and found you could not?")
)

@Composable
fun SafetyScreen(
    realityCheck: Boolean,
    sessionLimit: Int,
    onRealityCheck: (Boolean) -> Unit,
    onSessionLimit: (Int) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RandomnessNotice(
                "Gambling is not a way to make money. Over time the house edge means the " +
                    "expected outcome for every player is a loss.",
                tone = RiskRed
            )
        }

        item {
            SectionCard(title = "How crash games actually work") {
                Text(
                    "Each round's crash point is drawn by a random number generator, usually " +
                        "from a server seed committed before the round begins. The classic " +
                        "formula gives P(crash ≥ x) ≈ 0.99 / x, which means:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Roughly half of all rounds end below 2.00x.",
                        "About 1 in 100 rounds reaches 100x.",
                        "Every round is independent — a run of ten low results does not make " +
                            "a high one more likely.",
                        "The ~1% edge applies to every stake, at every cash-out target."
                    ).forEach {
                        Text(
                            "•  $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Myths this app will not support") {
                listOf(
                    "\"The next round is due for a big multiplier\" — false, rounds have no memory.",
                    "\"A pattern in the last 50 results predicts the next one\" — false.",
                    "\"Martingale guarantees recovery\" — false, it guarantees eventual ruin.",
                    "\"A bot or signal group can beat crash\" — false, and usually a scam.",
                    "\"Cashing out early is risk-free\" — the edge still applies."
                ).forEach {
                    Text(
                        "✕  $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Session controls") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.fillMaxWidth(0.75f)) {
                        Text("Reality check reminders", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Shows an elapsed-time banner while you use the app",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = realityCheck, onCheckedChange = onRealityCheck)
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Text("Reminder interval: $sessionLimit minutes", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        OutlinedButton(
                            onClick = { onSessionLimit(m) },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("$m min") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Quick self-check") {
                Text(
                    "If you answer yes to any of these, consider taking a break and talking " +
                        "to a support service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SELF_CHECKS.forEach {
                    Text(
                        "?  ${it.question}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            SectionCard(title = "Get support") {
                Text(
                    "Free, confidential help is available. Services vary by country — these " +
                        "are widely used starting points:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val links = listOf(
                    "BeGambleAware (UK)" to "https://www.begambleaware.org",
                    "Gambling Therapy (global, multilingual)" to "https://www.gamblingtherapy.org",
                    "Gamblers Anonymous" to "https://www.gamblersanonymous.org",
                    "GamCare helpline" to "https://www.gamcare.org.uk"
                )
                links.forEach { (label, url) ->
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(label) }
                }
                Text(
                    "Most casinos also offer deposit limits, cool-off periods, and " +
                        "self-exclusion in their account settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "About this app") {
                Text(
                    "CrashLab is an offline statistics notebook. It stores only the round " +
                        "results you enter, on your device. It has no account integration, " +
                        "places no bets, sends no data anywhere, and makes no prediction " +
                        "about any future round.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "18+ / 21+ depending on your jurisdiction. Gambling may be restricted " +
                        "or illegal where you live — check your local law.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
