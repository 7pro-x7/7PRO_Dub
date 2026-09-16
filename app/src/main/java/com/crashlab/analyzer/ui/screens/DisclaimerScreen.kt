package com.crashlab.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crashlab.analyzer.ui.components.SectionCard
import com.crashlab.analyzer.ui.theme.RiskRed

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    var adult by remember { mutableStateOf(false) }
    var understands by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "CrashLab",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "A statistics notebook for crash game history",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionCard(title = "Read this before you continue") {
            listOf(
                "This app does NOT predict future rounds. It cannot. Crash outcomes come " +
                    "from a random number generator and each round is independent of every " +
                    "round before it.",
                "This app does NOT guarantee profits, and no staking strategy can overcome " +
                    "the house edge built into these games.",
                "Everything shown here is descriptive analysis of results you enter yourself " +
                    "— a record of the past, never a forecast.",
                "Patterns, streaks and 'hot' or 'cold' windows appear naturally in random " +
                    "data. Seeing one does not mean anything is due.",
                "The long-run expected value of playing these games is negative. Treat any " +
                    "money you stake as money spent on entertainment, not invested."
            ).forEach {
                Text(
                    "•  $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        SectionCard(title = "Privacy") {
            Text(
                "All data stays on this device. There is no account, no server, no tracking, " +
                    "and no connection to any casino.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = adult, onCheckedChange = { adult = it })
            Text(
                "I am of legal gambling age in my jurisdiction",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = understands, onCheckedChange = { understands = it })
            Text(
                "I understand this app cannot predict results or guarantee profit",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onAccept,
            enabled = adult && understands,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Continue") }

        Text(
            "If gambling is causing you harm, support is available in the Safety tab.",
            style = MaterialTheme.typography.labelSmall,
            color = RiskRed,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(24.dp))
    }
}
