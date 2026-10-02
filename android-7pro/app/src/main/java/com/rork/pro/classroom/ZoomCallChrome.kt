package com.rork.pro.classroom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import java.util.Locale

/**
 * Zoom-style chrome for the in-call screen: a floating top capsule (camera flip, class title with
 * a live status dot and a timer, a red End/Leave pill), a floating bottom row of circular
 * labelled buttons, a bottom sheet for everything else ("More" / "Share"), and small dark pills
 * that float over the video.
 *
 * These colours are fixed on purpose and do NOT follow the app's light/dark theme ([com.rork.pro.ui.theme.Ink]):
 * a video call is a dark surface in every appearance, exactly like Zoom, so the video is never
 * framed by cream/white bars in light mode.
 */
internal object Zm {
    val Bg = Color(0xFF000000)
    val Bar = Color(0xFF1C1C1E)
    val Divider = Color(0xFF38383A)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF8A8A8E)

    // Brand teal/copper in place of the generic Zoom green/yellow this chrome started from — the
    // exact hues the rest of the app uses for "positive/live" (Ink.Teal) and "attention" (Ink.Amber).
    // Every banner, pill and tool-button state in the ~2000-line call screen reads these by name,
    // so retinting the two constants re-skins the whole in-call experience as 7PRO's own, not a
    // borrowed Zoom look — without touching that file.
    val Green = Color(0xFF3FAF8E)
    val Red = Color(0xFFE5484D)
    val Blue = Color(0xFF377ADD)
    val Yellow = Color(0xFFE0954F)
    val Pill = Color(0xEB1C1C1E)
    val Scrim = Color(0x99000000)

    /** Soft, semi-transparent fill for floating circular chrome (capsule top bar, tool buttons). */
    val Glass = Color(0x1FFFFFFF)
}

/** mm:ss, or h:mm:ss once past an hour. Always Latin digits so the timer never flips script mid-call. */
internal fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ENGLISH, "%02d:%02d", m, sec)
}

/**
 * A small dot that breathes between dim and full brightness on a loop — used for the top bar's
 * live/status indicator so the chrome reads as a living call in progress rather than a parked
 * screen, whatever [color] the current status happens to be (live teal, reconnecting amber,
 * offline red).
 */
@Composable
private fun BreathingDot(color: Color, size: androidx.compose.ui.unit.Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "callStatusDot")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "callStatusDotAlpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/**
 * Top bar, redesigned as three floating pieces over transparent space (no solid bar) instead of
 * a single dense 56dp strip: a lone circular "flip camera" button on the start side, a rounded
 * capsule in the middle carrying the title + a live status dot + the elapsed time stacked tight,
 * and a rounded red capsule on the end side. Copy-invite moved out of this row entirely (it now
 * lives in the "More" sheet) so the busiest three-things-in-one-line layout goes back to two.
 * [status] normally shows the elapsed time and switches to an amber/red word while
 * reconnecting/syncing/offline; [statusColor] drives both the dot and the small text under the
 * title so the two always agree.
 */
@Composable
internal fun ZoomTopBar(
    title: String,
    /** A connection problem to show instead of the timer; null while the call is healthy. */
    status: String?,
    statusColor: Color,
    /** Wall-clock start of this device's call — the elapsed timer counts from here. */
    elapsedSinceMs: Long,
    endLabel: String,
    onFlipCamera: () -> Unit,
    onEnd: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        // Soft downward scrim behind the whole row so the glass buttons and capsule keep
        // readable contrast even over a light or busy video background, instead of relying on
        // the buttons' own translucency alone. Strengthened to 0xB3 (~70% black) at the top edge
        // (was 0x99/~60%): the 9sp status/label text under the icons is small enough that AA's
        // 4.5:1 small-text contrast ratio isn't safely cleared at the lighter value against a
        // worst-case near-white video frame — 70% gives it real headroom instead of a bare pass.
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xB3000000), Color.Transparent),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 44dp, not 34dp: the old size sat below the standard 44dp/48dp minimum touch target
            // recommendation for a lone icon button (every other tappable circle in this chrome —
            // the bottom bar's tool buttons — is already 46dp; this one had been left smaller).
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Zm.Glass)
                    .clickable(onClick = onFlipCamera),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.FlipCameraAndroid, tr(StrClassroom.flipCamera), tint = Zm.Text, modifier = Modifier.size(18.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Zm.Glass)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BreathingDot(color = statusColor)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        title,
                        color = Zm.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The status line itself carries statusColor again (not just the dot above it),
                // so "reconnecting / offline / syncing" reads as clearly as it did in the
                // original bar — the dot alone was not enough on its own.
                // The elapsed timer was documented but never actually shown (a static "Active"
                // label sat there instead). It ticks here, inside the top bar only, so the whole
                // call screen does not recompose every second.
                val elapsed by produceState(initialValue = 0L, elapsedSinceMs) {
                    while (true) {
                        value = (System.currentTimeMillis() - elapsedSinceMs) / 1000
                        delay(1000)
                    }
                }
                Text(
                    status ?: formatElapsed(elapsed),
                    color = statusColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .defaultMinSize(minHeight = 34.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Zm.Red)
                    .clickable(onClick = onEnd)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(endLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/**
 * The bottom toolbar, redesigned as floating circular buttons over transparent space instead of
 * a solid gray strip with a hairline divider. An upward scrim sits behind the row for the same
 * contrast reason as the top bar's (0xB3/~70% black at the bottom edge, tapering to transparent) —
 * glass buttons alone don't guarantee a readable icon or label over a light or busy video frame.
 */
@Composable
internal fun ZoomBottomBar(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000)),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Zm.Bar.copy(alpha = 0.82f))
                .border(1.dp, Zm.Green.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

/**
 * One toolbar button: a circular 50dp button holding a 20dp icon, with a 9sp label underneath.
 * A neutral button stays on plain glass; a button carrying a state color (muted mic, locked
 * camera, raised hand) gets a soft tinted fill and a matching ring instead of just tinting the
 * icon — the state reads at a glance instead of needing to notice one small icon color change.
 * The circle itself is the primary touch target (50dp already clears the 44dp minimum on its
 * own), with the label adding a little extra height rather than being squeezed inside it.
 */
@Composable
internal fun RowScope.ZoomToolButton(
    icon: ImageVector,
    label: String,
    tint: Color = Zm.Text,
    badge: Int? = null,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val active = tint != Zm.Text
    // A "filled" button is the loud state: a solid rounded square in the tint color (e.g. the green
    // stop-sharing button), so the teacher can never miss that something is being shared.
    val shape = if (filled) RoundedCornerShape(14.dp) else CircleShape
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.92f else 1f,
        tween(110),
        label = "toolButtonPress",
    )
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .size(50.dp)
                    .scale(scale)
                    .clip(shape)
                    .background(if (filled) tint else if (active) tint.copy(alpha = 0.16f) else Zm.Glass)
                    .then(
                        if (active && !filled) Modifier.border(1.5.dp, tint.copy(alpha = 0.6f), CircleShape) else Modifier,
                    )
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = if (filled) Zm.Bg else tint, modifier = Modifier.size(20.dp))
            }
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Zm.Yellow)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.toString(), color = Zm.Bg, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) tint else Zm.TextDim,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

internal class ZoomSheetItem(
    val icon: ImageVector,
    val label: String,
    val badge: Int? = null,
    val tint: Color = Zm.Text,
    val onClick: () -> Unit,
)

/**
 * The bottom sheet used for "More" and "Share": a scrim over the video area (the bars stay
 * visible and usable) and a list of full-width rows, each 52dp tall. Tapping the scrim dismisses it.
 */
@Composable
internal fun ZoomSheet(items: List<ZoomSheetItem>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val noRipple = remember { MutableInteractionSource() }
    val noRipple2 = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .background(Zm.Scrim)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Zm.Bar)
                .clickable(interactionSource = noRipple2, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Zm.Divider))
            }
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(item.label, color = item.tint, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (item.badge != null) {
                        Box(
                            Modifier
                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .clip(CircleShape)
                                .background(Zm.Blue)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.badge.toString(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** A small dark label that floats over the video, e.g. "An image was shared". */
@Composable
internal fun ZoomPill(icon: ImageVector, text: String, modifier: Modifier = Modifier, iconTint: Color = Zm.Green) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Zm.Pill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Zm.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A 44dp round dark button that floats over the video (close, etc.). */
@Composable
internal fun ZoomOverlayButton(icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Zm.Pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Zm.Text, modifier = Modifier.size(20.dp))
    }
}
