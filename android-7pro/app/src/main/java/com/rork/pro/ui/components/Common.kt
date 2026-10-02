package com.rork.pro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rork.pro.data.AppError
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.levelLabel
import com.rork.pro.ui.i18n.statusLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink.TextPrimary)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(actionLabel, color = Ink.Amber, style = MaterialTheme.typography.titleSmall)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ink.TextSecondary) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Ink.Surface,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val sky = com.rork.pro.ui.theme.AppPalette.sky
    val shape = RoundedCornerShape(if (sky) 22.dp else Dimens.cardRadius)
    // A card at the default surface colour is painted with the faint top-lit sheen rather than
    // a flat fill, and always carries a hairline. On the black canvas a flat-filled card has no
    // edge for the eye to catch and reads as a hole in the page; the sheen and the hairline are
    // what make it sit *on* the page. A caller that passes its own colour gets exactly that
    // colour — a status tint or an accent fill is meant to be flat.
    // Learner screens (sky palette): in light mode a default card is white on a soft blue
    // shadow with no outline, exactly like Home; in dark mode it keeps the navy hairline.
    val isDefaultSurface = color == Ink.Surface
    val skyLightCard = sky && isDefaultSurface && !com.rork.pro.ui.theme.AppAppearance.dark
    val base = modifier
        .fillMaxWidth()
        .then(
            if (skyLightCard) {
                Modifier.shadow(
                    8.dp,
                    shape,
                    ambientColor = com.rork.pro.ui.theme.HomeSky.Shadow,
                    spotColor = com.rork.pro.ui.theme.HomeSky.Shadow,
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .then(
            if (isDefaultSurface) {
                Modifier.background(com.rork.pro.ui.theme.surfaceSheenBrush())
            } else {
                Modifier.background(color)
            },
        )
        .then(
            when {
                borderColor != null -> Modifier.border(1.dp, borderColor, shape)
                skyLightCard -> Modifier
                isDefaultSurface -> Modifier.border(1.dp, Ink.Hairline, shape)
                else -> Modifier
            },
        )
    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        content = {
            Column(Modifier.padding(contentPadding), content = content)
        },
    )
}

/** Amber pill used for levels, prices and status. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Ink.AmberSoft,
    foreground: Color = Ink.Amber,
    bold: Boolean = true,
) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = foreground,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
        maxLines = 1,
    )
}

@Composable
fun LevelBadge(level: String?, modifier: Modifier = Modifier) {
    if (level.isNullOrBlank()) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .border(1.dp, Ink.Amber.copy(alpha = 0.55f), RoundedCornerShape(Dimens.chipRadius))
            .background(Ink.AmberSoft)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.dp, Ink.Amber, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(level.take(2), color = Ink.Amber, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Text(levelName(level), color = Ink.Amber, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

fun levelName(level: String): String = levelLabel(level).ifBlank { level }

@Composable
fun RatingRow(rating: Double, count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) {
        Text(tr(Str.new), modifier = modifier, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            String.format(com.rork.pro.ui.i18n.AppLanguage.locale(), "%.1f", rating),
            color = Ink.Amber,
            style = MaterialTheme.typography.titleSmall,
        )
        Icon(Icons.Default.Star, null, tint = Ink.Amber, modifier = Modifier.size(14.dp))
        Text("($count)", color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Primary amber action with a subtle press-scale. */
@Composable
fun PrimaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    /**
     * Overrides the 7PRO amber. Used by the payment sheet so the confirm button carries the
     * chosen wallet's own colour; every other caller keeps the default and is unaffected.
     */
    containerColor: Color = Ink.Amber,
    contentColor: Color = Ink.OnAmber,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(110), label = "press")
    // Learner screens: the brand button is the design's orange gradient pill. Callers that pass
    // their own colour (wallet colours, teal, green) keep a flat fill in the same pill shape.
    val sky = com.rork.pro.ui.theme.AppPalette.sky
    val shape = RoundedCornerShape(if (sky) 999.dp else 16.dp)
    val gradient = sky && containerColor == Ink.Amber && enabled && !loading
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (sky) 52.dp else 54.dp)
            .scale(scale)
            .then(
                if (gradient) {
                    Modifier
                        .shadow(6.dp, shape, ambientColor = Ink.Amber, spotColor = Ink.Amber)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(com.rork.pro.ui.theme.HomeSky.BrandDeep, com.rork.pro.ui.theme.HomeSky.Brand),
                            ),
                            shape,
                        )
                } else {
                    Modifier
                },
            ),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (gradient) Color.Transparent else containerColor,
            contentColor = contentColor,
            disabledContainerColor = Ink.SurfaceHigh,
            disabledContentColor = Ink.TextMuted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Ink.TextPrimary,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(if (com.rork.pro.ui.theme.AppPalette.sky) 999.dp else 16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (tint == Ink.TextPrimary) Ink.Hairline else tint.copy(alpha = 0.45f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Asks before an action that cannot be undone.
 *
 * Destructive choices are tinted coral and never made the default, so the safe way out is
 * always the easiest one to hit.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Ink.Coral else Ink.Amber)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(tr(Str.cancel), color = Ink.TextSecondary)
            }
        },
    )
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier, label: String = tr(Str.loading)) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = Ink.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ErrorBlock(error: AppError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (error.code == "OFFLINE") Icons.Default.CloudOff else Icons.Default.ErrorOutline,
            null,
            tint = if (error.code == "OFFLINE") Ink.Sky else Ink.Coral,
            modifier = Modifier.size(34.dp),
        )
        Text(
            error.message,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (onRetry != null && error.retryable) {
            SecondaryAction(tr(Str.retry), onClick = onRetry)
        }
    }
}

@Composable
fun EmptyBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            SecondaryAction(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackLabel: String = "",
) {
    Box(modifier.background(Ink.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else if (fallbackLabel.isNotBlank()) {
            Text(
                fallbackLabel.take(2).uppercase(),
                color = Ink.Amber.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
fun Avatar(url: String?, name: String?, size: androidx.compose.ui.unit.Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Ink.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Text(
                // A literal "?" reads as a broken avatar. Falling back to the brand initial
                // means an unnamed teacher — i.e. a 7PRO-run course — still shows something
                // that makes sense on its own.
                (name?.trim()?.takeIf { it.isNotEmpty() } ?: "7PRO").take(1).uppercase(),
                color = Ink.Amber,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** Animated horizontal skill/progress bar. */
@Composable
fun SkillBar(
    label: String,
    percent: Double,
    modifier: Modifier = Modifier,
    color: Color = Ink.Amber,
) {
    val animated by animateFloatAsState(
        targetValue = (percent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(720),
        label = "skill",
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(104.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.SurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${percent.toInt()}%",
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/**
 * Circular dial used for the placement level result.
 *
 * [accent] lets the caller tint the dial with the colour of the band it is showing, so the
 * result reads the same in the light and dark palettes.
 */
@Composable
fun LevelDial(
    level: String,
    percent: Double,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Amber,
    caption: String? = null,
) {
    val sweep by animateFloatAsState(
        targetValue = (percent / 100.0).toFloat().coerceIn(0.04f, 1f),
        animationSpec = tween(1100),
        label = "dial",
    )
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            drawArc(
                color = Ink.SurfaceHigh,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accent.copy(alpha = 0.35f), accent, accent.copy(alpha = 0.75f), accent.copy(alpha = 0.35f)),
                ),
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(level, color = accent, fontSize = 60.sp, fontWeight = FontWeight.Black)
            if (!caption.isNullOrBlank()) {
                Text(caption, color = Ink.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Ink.TextPrimary,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ink.TextPrimary) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.titleSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status.uppercase()) {
        "ACTIVE", "PUBLISHED", "APPROVED", "PAID", "AVAILABLE", "AVAILABLE_BALANCE" -> Ink.TealSoft to Ink.Teal
        "PENDING", "PENDING_REVIEW", "DUE_SOON", "EXPIRING", "OFFERED", "WAITING" -> Ink.AmberSoft to Ink.Amber
        "REJECTED", "FAILED", "SUSPENDED", "OVERDUE", "EXPIRED", "CANCELLED", "CHARGEBACK", "REFUNDED" -> Ink.CoralSoft to Ink.Coral
        "DRAFT", "ARCHIVED" -> Ink.NeutralSoft to Ink.TextSecondary
        else -> Ink.SkySoft to Ink.Sky
    }
    Pill(statusLabel(status), modifier, background = bg, foreground = fg)
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Ink.Hairline),
    )
}

@Composable
fun animatedAccent(active: Boolean): Color {
    val color by animateColorAsState(if (active) Ink.Amber else Ink.TextMuted, label = "accent")
    return color
}

/** A course's price for lists: the amount, with "/ month" when it is sold by subscription only. */
fun coursePriceLabel(course: com.rork.pro.data.Course): String {
    val money = formatMoney(course.basePrice, course.baseCurrency)
    return if (course.isMonthlyOnly) com.rork.pro.ui.i18n.trf(com.rork.pro.ui.i18n.StrCourses.perMonth, money) else money
}

fun formatMoney(amount: Double, currency: String): String {
    val locale = com.rork.pro.ui.i18n.AppLanguage.locale()
    val rounded = if (amount % 1.0 == 0.0) {
        String.format(locale, "%,.0f", amount)
    } else {
        String.format(locale, "%,.2f", amount)
    }
    return "$rounded $currency"
}

fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val instant = if (iso.length <= 10) {
            java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        } else {
            java.time.Instant.parse(iso.replace(" ", "T").let { if (it.endsWith("Z")) it else it + "Z" })
        }
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", AppLanguage.locale())
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { iso.take(10) }
}

/**
 * Date **and clock time** of a server timestamp, in the device's time zone — for records where
 * the exact moment matters (a transfer, an approval). Unlike [formatDate] this reads the
 * `+00:00` offset PostgREST emits, so the time shown is the real local time of the operation.
 */
fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val s = iso.trim().replace(' ', 'T').let { v ->
            // Postgres text output can carry a bare "+00" offset; java.time wants "+00:00".
            if (v.contains(':') && Regex("[+-]\\d{2}$").containsMatchIn(v)) "$v:00" else v
        }
        val instant = runCatching { java.time.OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { java.time.Instant.parse(s) }
            .recoverCatching { java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC) }
            .getOrThrow()
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", AppLanguage.locale())
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { iso.take(16).replace('T', ' ') }
}

/**
 * Wraps a screen's content in pull-to-refresh.
 *
 * The refresh keeps whatever is already on screen and only swaps in newer data when it arrives,
 * so a swipe never blanks the page the way a full reload does.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Ink.Surface,
                color = Ink.Amber,
            )
        },
    ) { content() }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    color: Color = Ink.Teal,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = Ink.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 7PRO's signature corner flourish: a soft gold-to-teal diagonal accent behind the top bar.
 * Meant to sit as the first child of a `Box` wrapping a screen's whole content — every
 * top-level tab screen (Home, Courses, Profile...) uses the exact same shape and colors here,
 * so the brand reads identically the moment any of them opens.
 */
@Composable
fun HeaderSwoosh() {
    // Intentionally empty: the flat redesign has no corner glow. Kept as a function so every
    // screen that still calls it compiles unchanged.
}

/**
 * Text style for titles that come from course data, which freely mixes Arabic, English and emoji
 * ("المستوى الأول | English + عربي 🇬🇧"). Left to guess, the paragraph direction follows the first
 * strong character, so an English title flips to the wrong edge and separators ("|", "–", "+")
 * land on the wrong side of the words around them. In an RTL language the whole title is laid
 * out right-to-left; in an LTR language it keeps content-based detection.
 */
@Composable
fun titleDirectionStyle(base: androidx.compose.ui.text.TextStyle): androidx.compose.ui.text.TextStyle =
    base.copy(
        textDirection = if (com.rork.pro.ui.i18n.AppLanguage.current.isRtl) {
            androidx.compose.ui.text.style.TextDirection.Rtl
        } else {
            androidx.compose.ui.text.style.TextDirection.Content
        },
    )
