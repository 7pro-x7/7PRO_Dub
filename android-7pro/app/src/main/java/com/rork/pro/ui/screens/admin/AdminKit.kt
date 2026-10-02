package com.rork.pro.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrAdminKit
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink

/*
 * The admin design kit.
 *
 * Every screen behind the owner console is built from these few pieces, so they read as one
 * product: the same tonal buttons (colour = meaning: teal approves, coral removes, amber is the
 * main action), the same chips with a real 40dp touch target instead of a tappable label, the
 * same collapsible "add new" form that no longer pushes the actual list below the fold, and the
 * same expandable item card that shows a one-line summary until you ask for the controls.
 */

enum class AdminTone { Primary, Success, Danger, Neutral, Info }

private fun AdminTone.colors(): Pair<Color, Color> = when (this) {
    AdminTone.Primary -> Ink.AmberSoft to Ink.Amber
    AdminTone.Success -> Ink.TealSoft to Ink.Teal
    AdminTone.Danger -> Ink.CoralSoft to Ink.Coral
    AdminTone.Info -> Ink.SkySoft to Ink.Sky
    AdminTone.Neutral -> Ink.SurfaceHigh to Ink.TextPrimary
}

/** Compact tonal button. Colour carries the meaning, so a row of actions scans at a glance. */
@Composable
fun AdminButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: AdminTone = AdminTone.Neutral,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val (bg, fg) = tone.colors()
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else Ink.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else Ink.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (enabled) fg else Ink.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of actions that share the width evenly. */
@Composable
fun AdminActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Single-select chips that wrap; each chip is a proper touch target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AdminChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Amber,
    counts: Map<T, Int> = emptyMap(),
) {
    // One scrollable line instead of a wrapping block: filters stay a single tidy row no matter
    // how many options a screen has, and the list below never jumps when a chip is added.
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            val count = counts[key]
            Row(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) tint.copy(alpha = 0.16f) else Ink.Surface)
                    .border(1.dp, if (on) tint.copy(alpha = 0.55f) else Ink.Hairline, RoundedCornerShape(50))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = if (on) tint else Ink.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (count != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        count.toString(),
                        color = if (on) tint else Ink.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Search box used at the top of every long list. */
@Composable
fun AdminSearchField(value: String, onChange: (String) -> Unit, placeholder: String = tr(StrAdminKit.search)) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = Ink.TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink.TextMuted) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, tr(Str.cancel), tint = Ink.TextMuted) } }
        } else {
            null
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Ink.Surface,
            unfocusedContainerColor = Ink.Surface,
            focusedBorderColor = Ink.Amber,
            unfocusedBorderColor = Ink.Hairline,
            cursorColor = Ink.Amber,
            focusedTextColor = Ink.TextPrimary,
            unfocusedTextColor = Ink.TextPrimary,
        ),
    )
}

/** Short explanatory note — one consistent look instead of loose grey paragraphs. */
@Composable
fun AdminNote(text: String, tone: AdminTone = AdminTone.Info) {
    val (bg, fg) = tone.colors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val warn = tone == AdminTone.Danger || tone == AdminTone.Primary
        Icon(
            if (warn) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
            null,
            tint = fg,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        // A warning reads in its own colour; an explanatory note stays quiet.
        Text(text, color = if (warn) fg else Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Section label with an optional count, used between groups inside a list. */
@Composable
fun AdminSectionLabel(text: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (count != null) {
            Text(
                count.toString(),
                color = Ink.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** A tinted rounded square holding an icon or a couple of letters. */
@Composable
fun AdminBadgeIcon(icon: ImageVector? = null, text: String? = null, tint: Color = Ink.Amber, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size((size / 2).dp))
        else if (text != null) Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp, maxLines = 1)
    }
}

/**
 * The "add new" form, collapsed by default so the list — what people actually open the screen
 * for — is visible immediately. Opens with one tap on the header.
 */
@Composable
fun AdminFormCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Default.Add,
    startExpanded: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(startExpanded) }
    val turn by animateFloatAsState(if (open) 45f else 0f, label = "plus")
    InkCard(
        borderColor = Ink.Amber.copy(alpha = if (open) 0.45f else 0.22f),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Ink.Amber),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Ink.OnAmber, modifier = Modifier.size(22.dp).rotate(if (icon == Icons.Default.Add) turn else 0f))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 16.dp)) {
                content { open = false }
            }
        }
    }
}

/**
 * One row of a management list: a compact summary always visible, the controls only when
 * expanded. Keeps a list of twenty teachers or orders scannable instead of twenty screens tall.
 */
@Composable
fun AdminItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    expandable: Boolean = true,
    startExpanded: Boolean = false,
    stateKey: Any? = null,
    showBody: Boolean = true,
    onClick: (() -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var open by remember(stateKey) { mutableStateOf(startExpanded) }
    val canExpand = expandable && body != null && showBody
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    InkCard(
        modifier = modifier.animateContentSize(),
        borderColor = accent?.copy(alpha = 0.4f),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        canExpand -> Modifier.clickable { open = !open }
                        onClick != null -> Modifier.clickable(onClick = onClick)
                        else -> Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
            if (canExpand) {
                Icon(Icons.Default.ExpandMore, null, tint = Ink.TextMuted, modifier = Modifier.size(22.dp).rotate(turn))
            }
        }
        if (body != null && showBody && (open || !canExpand)) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                if (canExpand) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
                    Spacer(Modifier.height(10.dp))
                }
                body()
            }
        }
    }
}

/** Trash icon that always asks first. */
@Composable
fun AdminDeleteIcon(title: String, body: String, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    IconButton(onClick = { ask = true }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.Delete, title, tint = Ink.Coral)
    }
    if (ask) {
        ConfirmDialog(
            title = title,
            body = body,
            confirmLabel = title,
            destructive = true,
            onDismiss = { ask = false },
            onConfirm = { ask = false; onConfirm() },
        )
    }
}

/** A small "label: value" chip used for dense metadata lines. */
@Composable
fun AdminMeta(text: String, tint: Color = Ink.TextSecondary) {
    Text(
        text,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Ink.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminMetaRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Initials for an avatar-less badge ("Ahmed Ali" → "AA"). */
fun initialsOf(name: String?): String =
    name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }

/** Localised role name. */
fun roleLabel(role: String?): String = when (role?.uppercase()) {
    "OWNER" -> tr(StrAdminKit.roleOwner)
    "ADMIN" -> tr(StrAdminKit.roleAdmin)
    "TEACHER" -> tr(StrAdminKit.roleTeacher)
    "STUDENT" -> tr(StrAdminKit.roleStudent)
    else -> role.orEmpty()
}

fun roleTint(role: String?): Color = when (role?.uppercase()) {
    "OWNER" -> Ink.Amber
    "ADMIN" -> Ink.Sky
    "TEACHER" -> Ink.Teal
    else -> Ink.Neutral
}

/** One figure in an [AdminSummaryStrip]. [tint] colours the value; null keeps it neutral. */
data class AdminStat(val value: String, val label: String, val tint: Color? = null)

/**
 * A row of two to four headline figures at the top of a management list — the "how many,
 * how much" an owner wants before scrolling. Two per row so values never wrap on a phone.
 */
@Composable
fun AdminSummaryStrip(stats: List<AdminStat>) {
    if (stats.isEmpty()) return
    InkCard(contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        stats.chunked(2).forEachIndexed { index, row ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).height(1.dp).background(Ink.Hairline))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { i, stat ->
                    if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(Ink.Hairline))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            stat.value,
                            color = stat.tint ?: Ink.TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stat.label, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) {
                    Box(Modifier.width(1.dp).height(34.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A square shortcut tile — icon over a short label, with an optional count badge. Used where a
 * screen offers a handful of destinations (the teacher console's per-teacher tools).
 */
@Composable
fun AdminShortcutTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Amber,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Surface)
            .border(1.dp, if (badge > 0) tint.copy(alpha = 0.45f) else Ink.Hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AdminBadgeIcon(icon, tint = tint, size = 46)
            if (badge > 0) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Ink.Coral)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/**
 * Segmented switch for the two or three *views* of one screen (orders / refunds, banners /
 * categories, queue / setup). Filters stay as chips; a view change looks like a view change.
 * A non-zero [counts] entry is shown as a small badge, so a waiting queue is visible from the
 * other tab too.
 */
@Composable
fun <T> AdminTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    counts: Map<T, Int> = emptyMap(),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.SurfaceHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            val count = counts[key] ?: 0
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ink.Surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Ink.Hairline, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = if (on) Ink.TextPrimary else Ink.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Ink.Coral)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * Empty / no-results state for admin lists: a compact card with an icon instead of a bare pair
 * of centred lines floating in 80dp of padding — so two empty queues on one screen don't read
 * as a blank page.
 */
@Composable
fun AdminEmpty(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
) {
    InkCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Ink.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Ink.TextMuted, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

/** The "nothing matches this search / filter" state, shared by every searchable list. */
@Composable
fun AdminNoResults() {
    AdminEmpty(tr(StrAdminKit.noResults), tr(StrAdminKit.noResultsBody), icon = Icons.Outlined.SearchOff)
}
