package com.rork.pro.ui.screens.classroom

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.surfaceSheenBrush
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The virtual classroom's "no sessions yet" screen.
 *
 * An empty list is the first thing a new teacher or student sees here, so instead of a line of
 * grey text it shows the thing they are about to get: a live class. The hero is a miniature of
 * the real in-call screen (host tile, student tiles, the same control row, the same green LIVE
 * badge) that assembles itself once — the students' empty seats fill in one by one, then a hand
 * goes up — and afterwards only idles: broadcast rings drift out of the window, the host tile's
 * speaking outline breathes, and a notification bell bobs.
 *
 * The bell is the honest part of the picture for both roles: creating a session sends every
 * invited student a CLASSROOM_INVITE notification (see classroom_create_session), so a teacher
 * sees their invitations going out and a student sees how their first class will reach them.
 *
 * Everything is drawn with Compose primitives and the app's own Ink tokens, so it follows the
 * light and dark appearance, both languages and RTL with nothing to translate inside the
 * picture. Motion is skipped entirely when the system's animation scale is off.
 *
 * [canManage] is true for teachers and staff, who get the create button; students only get the
 * explanation, because they have nothing to press.
 */
@Composable
fun ClassroomEmptyState(
    canManage: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionEnabled()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Grows to the viewport so the block is vertically centred on tall phones, yet
                // still scrolls (instead of clipping) on small screens or with a large font size.
                .heightIn(min = viewport)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LiveClassScene(
                animate = motion,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // Purely decorative — the title and body below carry the meaning.
                    .clearAndSetSemantics { },
            )

            Spacer(Modifier.height(24.dp))

            Text(
                tr(if (canManage) StrClassroom.emptyTeacherTitle else StrClassroom.emptyStudentTitle),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr(if (canManage) StrClassroom.emptyTeacherBody else StrClassroom.emptyStudentBody),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )

            if (canManage) {
                Spacer(Modifier.height(26.dp))
                CreateFirstSessionButton(
                    label = tr(StrClassroom.createFirstSession),
                    onClick = onCreate,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(if (canManage) 26.dp else 30.dp))

            // What the class gives you, one glance each. Only tools the call screen really has
            // for that role: the host shares and draws, a student raises a hand.
            Row(Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
                if (canManage) {
                    FeatureItem(Icons.Default.Draw, tr(StrClassroom.whiteboard), Modifier.weight(1f))
                    FeatureItem(Icons.Default.ScreenShare, tr(StrClassroom.shareScreen), Modifier.weight(1f))
                    FeatureItem(Icons.Default.Chat, tr(StrClassroom.chat), Modifier.weight(1f))
                } else {
                    FeatureItem(Icons.Default.PanTool, tr(StrClassroom.raiseHand), Modifier.weight(1f))
                    FeatureItem(Icons.Default.Chat, tr(StrClassroom.chat), Modifier.weight(1f))
                    FeatureItem(Icons.Default.Draw, tr(StrClassroom.whiteboard), Modifier.weight(1f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Hero scene

/** Host + three student seats, the broadcast rings behind them and the bell stuck on the corner. */
@Composable
private fun LiveClassScene(animate: Boolean, modifier: Modifier = Modifier) {
    // One shared clock (0..1, ~3.6 s) drives every idle movement. It is handed down as a State
    // and only ever read inside draw / graphicsLayer blocks, so a running animation redraws the
    // scene without recomposing it.
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "classScene").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
            label = "classScenePhase",
        )
    } else {
        remember { mutableStateOf(0.35f) }
    }

    // The one orchestrated moment: seats fill left to right, then a hand goes up. With motion
    // off they start (and stay) filled.
    val joins = remember { List(3) { Animatable(if (animate) 0f else 1f) } }
    val hand = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            delay(350)
            joins.forEachIndexed { index, join ->
                launch {
                    delay(index * 380L)
                    // Low damping on purpose: the tile overshoots a little and settles, which
                    // reads as somebody arriving rather than as a fade.
                    join.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
                }
            }
            delay(3 * 380L + 250L)
            hand.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawBroadcast(phase.value) }

        ClassWindow(
            phase = phase,
            joinProgress = { index -> joins[index].value },
            handProgress = { hand.value },
            modifier = Modifier.fillMaxWidth(0.78f),
        )

        BellBadge(
            phase = phase,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 2.dp),
        )
    }
}

/** A soft copper glow behind the window plus three thin rings that swell out of it and fade. */
private fun DrawScope.drawBroadcast(phase: Float) {
    val s = size.minDimension
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Ink.Amber.copy(alpha = 0.20f), Color.Transparent),
            center = center,
            radius = s * 0.56f,
        ),
        radius = s * 0.56f,
        center = center,
    )
    repeat(3) { i ->
        val p = (phase + i / 3f) % 1f
        drawCircle(
            color = Ink.Amber.copy(alpha = (1f - p) * 0.45f),
            // Starts at the window's own edge (hidden behind it) and grows outward.
            radius = s * (0.36f + 0.16f * p),
            center = center,
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
}

@Composable
private fun ClassWindow(
    phase: State<Float>,
    joinProgress: (Int) -> Float,
    handProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Ink.Amber.copy(alpha = 0.30f),
                spotColor = Ink.Amber.copy(alpha = 0.55f),
            )
            .background(surfaceSheenBrush(), shape)
            .border(1.dp, Ink.HairlineStrong, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HostTile(phase)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudentTile(Ink.Teal, { joinProgress(0) }, Modifier.weight(1f))
            StudentTile(Ink.Sky, { joinProgress(1) }, Modifier.weight(1f), handProgress)
            StudentTile(Ink.Coral, { joinProgress(2) }, Modifier.weight(1f))
        }
        ControlRow()
    }
}

/** The teacher's tile: copper wash, silhouette, the app's LIVE badge and a breathing speaker outline. */
@Composable
private fun HostTile(phase: State<Float>) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(shape)
            .background(Ink.SurfaceHigh),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(Ink.Amber.copy(alpha = 0.30f), Ink.Amber.copy(alpha = 0.08f))))
            drawPerson(Ink.Amber)

            // The "active speaker" outline every video-call app draws around whoever is talking.
            val pulse = 0.5f + 0.5f * sin(phase.value * 2f * PI.toFloat())
            val inset = 1.dp.toPx()
            drawRoundRect(
                color = Ink.CtaGreen.copy(alpha = 0.30f + 0.55f * pulse),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(16.dp.toPx() - inset),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { LivePill() }
    }
}

/**
 * One student seat. Before [progress] reaches 1 it is a dashed, empty outline; as it does the
 * outline gives way to a filled tile that pops in. [handProgress], when given, raises a small
 * hand badge on the tile's corner.
 */
@Composable
private fun StudentTile(
    color: Color,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    handProgress: (() -> Float)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.height(62.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val seatAlpha = (1f - progress()).coerceIn(0f, 1f)
            if (seatAlpha > 0f) {
                drawRoundRect(
                    color = Ink.HairlineStrong.copy(alpha = seatAlpha),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress()
                    scaleX = p
                    scaleY = p
                    alpha = p.coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(Ink.SurfaceHigh),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.08f))))
                drawPerson(color)
            }
        }
        if (handProgress != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        val p = handProgress()
                        scaleX = p
                        scaleY = p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(Ink.Amber),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PanTool, null, tint = Ink.OnAmber, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Head and shoulders, sized from the tile so the same drawing serves the host and the students. */
private fun DrawScope.drawPerson(color: Color) {
    val h = size.height
    val cx = size.width / 2f
    val body = color.copy(alpha = 0.92f)
    drawCircle(color = body, radius = h * 0.19f, center = Offset(cx, h * 0.40f))
    val bodyWidth = h * 0.92f
    // Taller than the tile on purpose — the tile's clip trims it into shoulders.
    drawRoundRect(
        color = body,
        topLeft = Offset(cx - bodyWidth / 2f, h * 0.66f),
        size = Size(bodyWidth, h),
        cornerRadius = CornerRadius(bodyWidth / 2f, bodyWidth / 2f),
    )
}

/** The call's own control row in miniature: mic, camera, share, whiteboard. */
@Composable
private fun ControlRow() {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Icons.Default.Mic, Icons.Default.Videocam, Icons.Default.ScreenShare, Icons.Default.Draw).forEach { icon ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Ink.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Ink.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A ringing bell stuck over the window's corner, bobbing a few dp on the shared clock. */
@Composable
private fun BellBadge(phase: State<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer { translationY = sin(phase.value * 2f * PI.toFloat()) * 4.dp.toPx() }
            .shadow(10.dp, CircleShape, ambientColor = Ink.Amber.copy(alpha = 0.3f), spotColor = Ink.Amber)
            .background(Ink.Amber, CircleShape)
            // A ring in the page colour lifts the badge off the window it overlaps.
            .border(3.dp, Ink.Canvas, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.NotificationsActive, null, tint = Ink.OnAmber, modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------ Below the hero

@Composable
private fun CreateFirstSessionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "createPress")
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .height(58.dp)
            .scale(pressScale)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Ink.Amber.copy(alpha = 0.30f),
                spotColor = Ink.Amber.copy(alpha = 0.60f),
            ),
        interactionSource = interaction,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Ink.Amber, contentColor = Ink.OnAmber),
    ) {
        Icon(Icons.Default.Videocam, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Ink.AmberSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Ink.Amber, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** False when the person has switched system animations off — the scene then sits still, fully assembled. */
@Composable
private fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}
