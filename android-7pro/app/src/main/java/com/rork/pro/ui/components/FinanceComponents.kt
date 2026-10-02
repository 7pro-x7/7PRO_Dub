package com.rork.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.pro.ui.theme.Ink

/**
 * Shared presentation pieces for the money-facing admin surfaces (teacher console,
 * subscription earnings, teacher detail).
 *
 * These exist because those screens were assembling the same shapes inline, each slightly
 * differently: a headline number here was `displaySmall` and there `headlineSmall`, a
 * breakdown was a single run-on sentence in one place and a column in another, and section
 * headings carried their step number inside the translated string ("1. Choose a teacher"),
 * which both hardcodes ordering into copy and forces translators to keep numbering in sync.
 * Centralising them makes the hierarchy consistent: one hero number per screen, secondary
 * figures visibly subordinate to it, and structure carried by layout rather than punctuation.
 */

/**
 * The single most important number on a screen, with optional supporting figures beneath it.
 *
 * A subtle vertical wash instead of a flat fill gives the card presence without another border
 * competing with the cards below it — on a dark canvas a plain surface at this size reads as an
 * empty panel rather than the headline.
 */
@Composable
fun HeroMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Amber,
    breakdown: List<Pair<String, String>> = emptyList(),
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f)),
                ),
            )
            .padding(20.dp),
    ) {
        Text(
            label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (breakdown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            // Each contributing figure gets its own labelled column instead of being folded
            // into one sentence — the split between teacher and platform share is something
            // an owner scans repeatedly, so it should be readable at a glance, not parsed.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                breakdown.forEach { (itemLabel, itemValue) ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            itemLabel,
                            color = Ink.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            itemValue,
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A numbered section heading for a screen that is genuinely a sequence (pick a teacher, then
 * act on them). The number lives in the component, not in the translated string, so copy stays
 * clean and reordering a flow doesn't mean re-translating every heading.
 *
 * [done] marks a step the user has completed, which is what makes this more than decoration:
 * on this console the second step is meaningless until the first is satisfied, and the
 * colour shift is the cheapest way to show that state.
 */
@Composable
fun StepHeader(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    done: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) Ink.Teal.copy(alpha = 0.18f) else Ink.AmberSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                step.toString(),
                color = if (done) Ink.Teal else Ink.Amber,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A compact labelled figure for use inside a card — amount, next renewal, status and so on.
 * Previously these were rendered as "Label: value" strings, which wraps badly in Arabic and
 * gives the label the same visual weight as the number it describes.
 */
@Composable
fun FieldStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink.TextPrimary,
) {
    Column(modifier) {
        Text(
            label,
            color = Ink.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Empty state with an icon, used where a list can legitimately be empty (no pending approvals,
 * no subscriptions yet). Distinct from [ErrorBlock]: this says "nothing to do here", not
 * "something failed", and that difference matters on an approvals queue where an empty screen
 * is the normal, good outcome.
 */
@Composable
fun QuietEmpty(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ink.TextMuted, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** Card padding tuned for dense finance rows — slightly tighter than the default InkCard. */
val FinanceCardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/**
 * One revenue stream in [EarningsMatrixCard]. [monthly] is null while the figure is still
 * loading or could not be fetched, which renders as a dash rather than a misleading zero.
 */
data class EarningsStream(
    val label: String,
    val monthly: Double?,
    val accent: Color = Ink.Amber,
)

/**
 * Every earnings figure a dashboard needs, in one card the height of a single old one.
 *
 * This replaces the four stacked headline cards (monthly courses, monthly subscriptions, and a
 * weekly pair below them) that pushed everything else on the owner console below the fold. Each
 * of those cards also repeated a teacher/platform split, so the screen carried twelve numbers
 * to say what these four say. Here a stream is a row and a period is a column, which is the
 * shape the data actually has — and it means adding a third stream later costs one row, not a
 * whole card.
 *
 * The weekly column is derived, never fetched: a 30-day window is four weeks, so weekly is
 * simply the monthly figure divided by four. That keeps the two columns guaranteed consistent
 * with each other and costs no extra query.
 */
@Composable
fun EarningsMatrixCard(
    title: String,
    monthlyLabel: String,
    weeklyLabel: String,
    streams: List<EarningsStream>,
    modifier: Modifier = Modifier,
    currency: String = "EGP",
    /**
     * Optional caption for the period the figures cover. Off by default: the column headings
     * already say monthly and weekly, and a date-range caption beside them is the kind of
     * detail that makes a compact card read busy again.
     */
    periodLabel: String? = null,
) {
    InkCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (periodLabel != null) {
                Spacer(Modifier.weight(1f))
                Text(periodLabel, color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }

        streams.forEachIndexed { index, stream ->
            Spacer(Modifier.height(10.dp))
            if (index > 0) {
                androidx.compose.material3.HorizontalDivider(color = Ink.Hairline)
                Spacer(Modifier.height(10.dp))
            }

            Text(
                stream.label,
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EarningsTile(
                    periodLabel = monthlyLabel,
                    amount = stream.monthly,
                    currency = currency,
                    accent = stream.accent,
                    hero = true,
                    modifier = Modifier.weight(1f),
                )
                EarningsTile(
                    periodLabel = weeklyLabel,
                    amount = stream.monthly?.let { it / 4 },
                    currency = currency,
                    accent = stream.accent,
                    hero = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One period's figure for one stream, in its own tinted tile rather than a table cell — this is
 * the shape a dashboard's headline numbers actually take, and it gives the monthly figure room
 * to be set large and bold without a plain-table row fighting it for space. Sized compactly so
 * two streams' worth of tiles still fit inside one card without pushing the page down.
 */
@Composable
private fun EarningsTile(
    periodLabel: String,
    amount: Double?,
    currency: String,
    accent: Color,
    hero: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = if (hero) 0.12f else 0.07f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            periodLabel,
            color = Ink.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            amount?.let { formatMoney(it, currency) } ?: "—",
            color = accent,
            style = if (hero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
