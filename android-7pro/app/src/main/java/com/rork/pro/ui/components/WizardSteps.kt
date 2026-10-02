package com.rork.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.pro.ui.theme.Ink

/**
 * Numbered progress header for a multi-step flow — one row of circles joined by a line, each
 * carrying a short label underneath. A finished step turns into a teal check, the current one
 * is filled amber, the rest sit dim on [Ink.SurfaceHigh]. This is the one place that defines
 * "which step am I on" for every wizard in the app (question builder today; exercise creation,
 * anything else that reads as a sequence tomorrow), so every one of
 * them looks and behaves the same way instead of each screen inventing its own progress bar.
 *
 * Compose mirrors this Row itself under RTL, so the reading order (step 1 first) is correct in
 * both languages without any extra handling here.
 */
@Composable
fun WizardSteps(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            val dotColor = when {
                done -> Ink.Teal
                active -> Ink.Amber
                else -> Ink.SurfaceHigh
            }
            val labelColor = when {
                active -> Ink.TextPrimary
                done -> Ink.TextSecondary
                else -> Ink.TextMuted
            }
            val lineColor = if (done) Ink.Teal.copy(alpha = 0.6f) else Ink.Hairline

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index == 0) {
                        Spacer(Modifier.width(0.dp).weight(1f))
                    } else {
                        Box(Modifier.weight(1f).height(2.dp).background(lineColor))
                    }
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(dotColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Ink.OnAmber,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                (index + 1).toString(),
                                color = if (active) Ink.OnAmber else Ink.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (index == steps.lastIndex) {
                        Spacer(Modifier.width(0.dp).weight(1f))
                    } else {
                        Box(
                            Modifier.weight(1f).height(2.dp)
                                .background(if (done) Ink.Teal.copy(alpha = 0.6f) else Ink.Hairline),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
