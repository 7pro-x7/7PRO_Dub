package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.domain.Platform
import com.crashlab.analyzer.domain.Round
import com.crashlab.analyzer.ui.components.RandomnessNotice
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.theme.RiskAmber
import com.crashlab.analyzer.ui.theme.RiskGreen
import com.crashlab.analyzer.ui.theme.RiskRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    rounds: List<Round>,
    onAdd: (String, String) -> Unit,
    onImport: (String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSeedDemo: () -> Unit,
    onClearAll: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(Platform.ONEXBET) }
    var showBulk by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RandomnessNotice(
                "Enter results you observed yourself. This app does not connect to any " +
                    "casino account and never places bets."
            )
        }

        item {
            SectionCard(title = "Source platform") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Platform.entries.forEach { p ->
                        FilterChip(
                            selected = platform == p,
                            onClick = { platform = p },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Record a round") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Multiplier, e.g. 2.47") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Button(
                        onClick = {
                            onAdd(input, platform.key)
                            input = ""
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Add") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showBulk = !showBulk }) {
                        Text(if (showBulk) "Hide bulk paste" else "Bulk paste / CSV")
                    }
                }
                if (showBulk) {
                    OutlinedTextField(
                        value = bulk,
                        onValueChange = { bulk = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        label = { Text("Paste values: 1.23x, 4.50, 2.00 …") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            onImport(bulk, platform.key)
                            bulk = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Import batch") }
                }
            }
        }

        item {
            SectionCard(
                title = "Explore with reference data",
                subtitle = "Generates a provably-fair style sample so you can try the tools"
            ) {
                OutlinedButton(
                    onClick = onSeedDemo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Load 500 simulated rounds") }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History (${rounds.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (rounds.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = !confirmClear }) {
                        Text(
                            if (confirmClear) "Tap again to confirm" else "Clear all",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (confirmClear) {
                Button(
                    onClick = { onClearAll(); confirmClear = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Delete everything") }
            }
        }

        items(rounds, key = { it.id }) { r ->
            val color = when {
                r.multiplier < 1.5 -> RiskRed
                r.multiplier < 3.0 -> RiskAmber
                else -> RiskGreen
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${String.format("%.2f", r.multiplier)}x",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        "${Platform.from(r.platform).label} · ${fmt.format(Date(r.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(r.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete round",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
