package com.rork.pro.ui.screens.tutor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.pro.character.CharacterRig
import com.rork.pro.character.RigConfidence
import com.rork.pro.character.drawCharacterFace
import com.rork.pro.character.prepareFace
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.StrCharacters
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.sin

private enum class Part { LEFT_EYE, RIGHT_EYE, MOUTH }
private enum class Handle { NONE, CENTER, WIDTH, HEIGHT }

private val Green = Color(0xFF16A34A)
private const val NUDGE = 0.003f
private const val LOUPE_MAGNIFICATION = 3f

private fun CharacterRig.center(p: Part): Offset = when (p) {
    Part.LEFT_EYE -> Offset(eyeLX, eyeLY)
    Part.RIGHT_EYE -> Offset(eyeRX, eyeRY)
    Part.MOUTH -> Offset(mouthX, mouthY)
}
private fun CharacterRig.halfW(p: Part): Float = if (p == Part.MOUTH) mouthHalfW else eyeHalfW
private fun CharacterRig.halfH(p: Part): Float = if (p == Part.MOUTH) mouthHalfH else eyeHalfH
private fun CharacterRig.withCenter(p: Part, c: Offset): CharacterRig = when (p) {
    Part.LEFT_EYE -> copy(eyeLX = c.x, eyeLY = c.y)
    Part.RIGHT_EYE -> copy(eyeRX = c.x, eyeRY = c.y)
    Part.MOUTH -> copy(mouthX = c.x, mouthY = c.y)
}.clamped()
private fun CharacterRig.withHalfW(p: Part, v: Float): CharacterRig = (if (p == Part.MOUTH) copy(mouthHalfW = v) else copy(eyeHalfW = v)).clamped()
private fun CharacterRig.withHalfH(p: Part, v: Float): CharacterRig = (if (p == Part.MOUTH) copy(mouthHalfH = v) else copy(eyeHalfH = v)).clamped()

/** Maps between image fractions (0..1) and pixels of the editing canvas, with zoom about a focus point. */
private class ViewMap(val boxW: Float, val boxH: Float, val iw: Float, val ih: Float, zoom: Float, focus: Offset) {
    val scale = minOf(boxW / iw, boxH / ih) * zoom
    private val t = ((zoom - 1f) / 3f).coerceIn(0f, 1f)
    private val fu = 0.5f + (focus.x - 0.5f) * t
    private val fv = 0.5f + (focus.y - 0.5f) * t
    fun toScreen(u: Float, v: Float) = Offset(boxW / 2f + (u - fu) * iw * scale, boxH / 2f + (v - fv) * ih * scale)
    fun toImage(p: Offset) = Offset(fu + (p.x - boxW / 2f) / (iw * scale), fv + (p.y - boxH / 2f) / (ih * scale))
    fun pxW(fracW: Float) = fracW * iw * scale
    fun pxH(fracH: Float) = fracH * ih * scale
}

/**
 * Where the character's eyes and mouth are — the automatic result is shown first, then the owner
 * refines it like drawing: pick a part, drag its centre to move it, drag the side / bottom
 * handles to size it, tap the picture to drop it somewhere else. A magnifier follows the finger,
 * zoom + arrow buttons give pixel-level control, and "Preview" plays the real blink and mouth
 * animation on the picture before anything is saved.
 */
@Composable
fun FaceRigEditor(
    bitmap: Bitmap,
    rig: CharacterRig,
    onRigChange: (CharacterRig) -> Unit,
    suggestion: RigConfidence?,
    detecting: Boolean,
    onAutoDetect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(Part.MOUTH) }
    var zoom by remember { mutableStateOf(1f) }
    var focus by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var preview by remember { mutableStateOf(false) }
    var loupe by remember { mutableStateOf<Offset?>(null) }

    val rigNow by rememberUpdatedState(rig)
    val selectedNow by rememberUpdatedState(selected)
    val zoomNow by rememberUpdatedState(zoom)
    val focusNow by rememberUpdatedState(focus)
    val rigChange by rememberUpdatedState(onRigChange)
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }

    fun select(p: Part) {
        selected = p
        focus = rigNow.center(p)
    }
    // A new automatic result moves the view onto the mouth so the owner can start checking right away.
    LaunchedEffect(suggestion, detecting) { if (!detecting) focus = rigNow.center(selectedNow) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuggestionBanner(suggestion, detecting)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PartChip(tr(StrCharacters.partLeftEye), selected == Part.LEFT_EYE, Modifier.weight(1f)) { select(Part.LEFT_EYE) }
            PartChip(tr(StrCharacters.partRightEye), selected == Part.RIGHT_EYE, Modifier.weight(1f)) { select(Part.RIGHT_EYE) }
            PartChip(tr(StrCharacters.partMouth), selected == Part.MOUTH, Modifier.weight(1f)) { select(Part.MOUTH) }
        }

        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Ink.Surface)) {
            if (preview) {
                PreviewCanvas(bitmap, rig)
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectTapGestures { pos ->
                                val m = ViewMap(size.width.toFloat(), size.height.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), zoomNow, focusNow)
                                val reach = 32.dp.toPx()
                                val r = rigNow
                                // Tapping another part's centre selects it; anywhere else drops the selected part there.
                                val other = Part.values().filter { it != selectedNow }.minByOrNull { p ->
                                    val c = m.toScreen(r.center(p).x, r.center(p).y); (pos - c).getDistance()
                                }
                                val otherDist = other?.let { p -> val c = m.toScreen(r.center(p).x, r.center(p).y); (pos - c).getDistance() } ?: Float.MAX_VALUE
                                if (other != null && otherDist <= reach) {
                                    selected = other
                                    focus = r.center(other)
                                } else {
                                    rigChange(r.withCenter(selectedNow, m.toImage(pos)))
                                }
                            }
                        }
                        .pointerInput(bitmap) {
                            var handle = Handle.NONE
                            var grab = Offset.Zero
                            detectDragGestures(
                                onDragStart = { start ->
                                    val m = ViewMap(size.width.toFloat(), size.height.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), zoomNow, focusNow)
                                    val r = rigNow
                                    val p = selectedNow
                                    val c = r.center(p)
                                    val cs = m.toScreen(c.x, c.y)
                                    val ws = Offset(cs.x + m.pxW(r.halfW(p)), cs.y)
                                    val hs = Offset(cs.x, cs.y + m.pxH(r.halfH(p)))
                                    val reach = 40.dp.toPx()
                                    val dc = (start - cs).getDistance()
                                    val dw = (start - ws).getDistance()
                                    val dh = (start - hs).getDistance()
                                    handle = when {
                                        dw <= reach && dw <= dh && dw <= dc -> Handle.WIDTH
                                        dh <= reach && dh <= dw && dh <= dc -> Handle.HEIGHT
                                        else -> Handle.CENTER
                                    }
                                    val startImg = m.toImage(start)
                                    grab = Offset(c.x - startImg.x, c.y - startImg.y)
                                    loupe = when (handle) {
                                        Handle.WIDTH -> Offset(c.x + r.halfW(p), c.y)
                                        Handle.HEIGHT -> Offset(c.x, c.y + r.halfH(p))
                                        else -> c
                                    }
                                },
                                onDrag = { change, _ ->
                                    val m = ViewMap(size.width.toFloat(), size.height.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), zoomNow, focusNow)
                                    val r = rigNow
                                    val p = selectedNow
                                    val c = r.center(p)
                                    val img = m.toImage(change.position)
                                    when (handle) {
                                        Handle.CENTER -> {
                                            val nc = Offset(img.x + grab.x, img.y + grab.y)
                                            rigChange(r.withCenter(p, nc))
                                            loupe = nc
                                        }
                                        Handle.WIDTH -> {
                                            val hw = abs(img.x - c.x).coerceAtLeast(0.008f)
                                            rigChange(r.withHalfW(p, hw))
                                            loupe = Offset(c.x + hw, c.y)
                                        }
                                        Handle.HEIGHT -> {
                                            val hh = abs(img.y - c.y).coerceAtLeast(0.004f)
                                            rigChange(r.withHalfH(p, hh))
                                            loupe = Offset(c.x, c.y + hh)
                                        }
                                        Handle.NONE -> {}
                                    }
                                    change.consume()
                                },
                                onDragEnd = { handle = Handle.NONE; loupe = null },
                                onDragCancel = { handle = Handle.NONE; loupe = null },
                            )
                        },
                ) {
                    val m = ViewMap(size.width, size.height, bitmap.width.toFloat(), bitmap.height.toFloat(), zoom, focus)
                    drawPicture(bitmap, m, paint)
                    for (p in Part.values()) drawPart(rig, p, m, p == selected)
                    val lp = loupe
                    if (lp != null) drawLoupe(bitmap, m, lp, paint)
                }
            }
            if (detecting) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Ink.Teal)
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrCharacters.detectingFace), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (!preview) {
            Text(tr(StrCharacters.editorHelp), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)

            // Zoom: 1× shows the whole picture; more magnifies around the selected part.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(StrCharacters.zoomLabel), color = Ink.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                Slider(
                    value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f,
                    colors = SliderDefaults.colors(thumbColor = Ink.Teal, activeTrackColor = Ink.Teal),
                    modifier = Modifier.weight(1f),
                )
                Text("%.1f×".format(zoom), color = Ink.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }

            // Size of the selected part.
            LabeledSlider(tr(StrCharacters.widthLabel), rig.halfW(selected), if (selected == Part.MOUTH) 0.02f..0.35f else 0.015f..0.2f) { rigChange(rig.withHalfW(selected, it)) }
            LabeledSlider(tr(StrCharacters.heightLabel), rig.halfH(selected), if (selected == Part.MOUTH) 0.006f..0.15f else 0.008f..0.15f) { rigChange(rig.withHalfH(selected, it)) }

            // Pixel-level nudging.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(tr(StrCharacters.nudgeLabel), color = Ink.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                listOf("←" to Offset(-NUDGE, 0f), "→" to Offset(NUDGE, 0f), "↑" to Offset(0f, -NUDGE), "↓" to Offset(0f, NUDGE)).forEach { (glyph, d) ->
                    NudgeButton(glyph) {
                        val c = rig.center(selected)
                        rigChange(rig.withCenter(selected, Offset(c.x + d.x, c.y + d.y)))
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(tr(StrCharacters.autoDetect), modifier = Modifier.weight(1f), enabled = !detecting) { onAutoDetect() }
            SecondaryAction(tr(StrCharacters.mirrorEyes), modifier = Modifier.weight(1f)) {
                // Make the other eye the mirror image of the selected one about the line between the eyes.
                val axis = (rig.eyeLX + rig.eyeRX) / 2f
                val src = if (selected == Part.RIGHT_EYE) Offset(rig.eyeRX, rig.eyeRY) else Offset(rig.eyeLX, rig.eyeLY)
                val mirrored = Offset(2f * axis - src.x, src.y)
                rigChange(if (selected == Part.RIGHT_EYE) rig.copy(eyeLX = mirrored.x, eyeLY = mirrored.y).clamped() else rig.copy(eyeRX = mirrored.x, eyeRY = mirrored.y).clamped())
            }
        }
        SecondaryAction(if (preview) tr(StrCharacters.backToEditing) else tr(StrCharacters.previewMotion), modifier = Modifier.fillMaxWidth(), tint = Ink.Teal) { preview = !preview }
    }
}

// ------------------------------------------------------------------------------------ pieces

@Composable
private fun SuggestionBanner(suggestion: RigConfidence?, detecting: Boolean) {
    if (detecting || suggestion == null) return
    val (text, color) = when (suggestion) {
        RigConfidence.LANDMARKS -> tr(StrCharacters.rigFoundAuto) to Ink.Teal
        RigConfidence.ESTIMATED -> tr(StrCharacters.rigEstimated) to Color(0xFFB45309)
    }
    Text(
        text, color = color, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Surface).padding(12.dp),
    )
}

@Composable
private fun PartChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) Ink.TextPrimary else Ink.Surface)
            .border(1.dp, if (selected) Ink.TextPrimary else Ink.Hairline, RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Ink.Canvas else Ink.TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun NudgeButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Surface).border(1.dp, Ink.Hairline, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Ink.Teal, activeTrackColor = Ink.Teal),
            modifier = Modifier.weight(1f),
        )
    }
}

/** The real blink and mouth animation on the picture, so the owner sees the result before saving. */
@Composable
private fun PreviewCanvas(bitmap: Bitmap, rig: CharacterRig) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    val parts = remember(bitmap, rig) { prepareFace(bitmap, rig) }
    // The same blink rhythm the call uses, and blinks a little more often so the owner sees one quickly.
    val blinkClock = remember { com.rork.pro.character.BlinkClock() }
    Canvas(Modifier.fillMaxSize()) {
        val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
        val s = minOf(size.width / iw, size.height / ih)
        val dw = iw * s; val dh = ih * s
        val ox = (size.width - dw) / 2f; val oy = (size.height - dh) / 2f
        val jaw = (0.5f + 0.5f * sin(t * 7f)) * (0.5f + 0.5f * sin(t * 1.9f + 1f))
        val blink = blinkClock.value(t.toDouble())
        drawCharacterFace(bitmap, rig, parts, jaw, blink, ox, oy, dw, dh)
    }
}

private fun DrawScope.drawPicture(bitmap: Bitmap, m: ViewMap, paint: android.graphics.Paint) {
    val a = m.toScreen(0f, 0f)
    val b = m.toScreen(1f, 1f)
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.clipRect(0f, 0f, size.width, size.height)
        nc.drawBitmap(bitmap, null, android.graphics.RectF(a.x, a.y, b.x, b.y), paint)
        nc.restoreToCount(save)
    }
}

/** One part as an outline (ellipse; the mouth also gets its lip line) plus, when selected, its three handles. */
private fun DrawScope.drawPart(rig: CharacterRig, p: Part, m: ViewMap, selected: Boolean) {
    val c = rig.center(p)
    val cs = m.toScreen(c.x, c.y)
    val rw = m.pxW(rig.halfW(p))
    val rh = m.pxH(rig.halfH(p)).coerceAtLeast(3f)
    val color = if (selected) Green else Color.White
    drawOval(color, topLeft = Offset(cs.x - rw, cs.y - rh), size = Size(rw * 2f, rh * 2f), style = Stroke(width = if (selected) 3.dp.toPx() else 1.5.dp.toPx()), alpha = if (selected) 1f else 0.85f)
    if (p == Part.MOUTH) {
        drawLine(color, Offset(cs.x - rw, cs.y), Offset(cs.x + rw, cs.y), strokeWidth = if (selected) 2.dp.toPx() else 1.dp.toPx(), alpha = 0.9f)
    }
    if (selected) {
        val ring = 11.dp.toPx()
        drawCircle(Green, radius = ring, center = cs)
        drawCircle(Color.White, radius = ring * 0.45f, center = cs)
        val small = 8.dp.toPx()
        for (h in listOf(Offset(cs.x + rw, cs.y), Offset(cs.x, cs.y + rh))) {
            drawCircle(Color.White, radius = small, center = h)
            drawCircle(Green, radius = small, center = h, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** A magnifier circle showing the picture around the point being moved, so the finger never hides it. */
private fun DrawScope.drawLoupe(bitmap: Bitmap, m: ViewMap, point: Offset, paint: android.graphics.Paint) {
    val radius = 56.dp.toPx()
    val margin = 12.dp.toPx()
    val target = m.toScreen(point.x, point.y)
    val cx = if (target.x < size.width / 2f) size.width - radius - margin else radius + margin
    val cy = radius + margin
    val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
    val half = radius / (m.scale * LOUPE_MAGNIFICATION)   // half the shown area, in bitmap pixels
    val bx = point.x * iw; val by = point.y * ih
    val sx0 = bx - half; val sx1 = bx + half; val sy0 = by - half; val sy1 = by + half
    // Only the part of the area that lies inside the picture can be drawn; shrink the destination to match.
    val ux0 = maxOf(sx0, 0f); val ux1 = minOf(sx1, iw); val uy0 = maxOf(sy0, 0f); val uy1 = minOf(sy1, ih)
    drawCircle(Color(0xFF111111), radius = radius, center = Offset(cx, cy))
    if (ux1 > ux0 && uy1 > uy0) {
        val span = radius * 2f
        val dst = android.graphics.RectF(
            cx - radius + (ux0 - sx0) / (sx1 - sx0) * span,
            cy - radius + (uy0 - sy0) / (sy1 - sy0) * span,
            cx - radius + (ux1 - sx0) / (sx1 - sx0) * span,
            cy - radius + (uy1 - sy0) / (sy1 - sy0) * span,
        )
        val src = android.graphics.Rect(ux0.toInt(), uy0.toInt(), ux1.toInt().coerceAtLeast(ux0.toInt() + 1), uy1.toInt().coerceAtLeast(uy0.toInt() + 1))
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val save = nc.save()
            val clip = android.graphics.Path().apply { addCircle(cx, cy, radius, android.graphics.Path.Direction.CW) }
            nc.clipPath(clip)
            nc.drawBitmap(bitmap, src, dst, paint)
            nc.restoreToCount(save)
        }
    }
    drawCircle(Color.White, radius = radius, center = Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
    drawLine(Green, Offset(cx - radius * 0.35f, cy), Offset(cx + radius * 0.35f, cy), strokeWidth = 2.dp.toPx())
    drawLine(Green, Offset(cx, cy - radius * 0.35f), Offset(cx, cy + radius * 0.35f), strokeWidth = 2.dp.toPx())
}
