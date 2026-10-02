package com.rork.pro.character

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.rork.pro.tutor.AvatarFeed
import com.rork.pro.tutor.TutorActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Where an uploaded character's eyes and mouth sit, every value a fraction (0..1) of the cutout
 * image — x across the width, y down the height, sizes as half-extents of the shape.
 *
 * "Left" and "right" eye are as seen on screen (left = smaller x). Both eyes share one size.
 */
data class CharacterRig(
    val mouthX: Float,
    val mouthY: Float,
    /** Half the mouth's width (corner to centre). */
    val mouthHalfW: Float,
    /** Half the mouth's resting height (upper lip to lower lip, halved). */
    val mouthHalfH: Float,
    val eyeLX: Float,
    val eyeLY: Float,
    val eyeRX: Float,
    val eyeRY: Float,
    val eyeHalfW: Float,
    val eyeHalfH: Float,
) {
    /** Keeps every value inside a range the renderer and the database both accept. */
    fun clamped(): CharacterRig = copy(
        mouthX = mouthX.coerceIn(0f, 1f), mouthY = mouthY.coerceIn(0f, 1f),
        mouthHalfW = mouthHalfW.coerceIn(0.01f, 0.45f), mouthHalfH = mouthHalfH.coerceIn(0.004f, 0.2f),
        eyeLX = eyeLX.coerceIn(0f, 1f), eyeLY = eyeLY.coerceIn(0f, 1f),
        eyeRX = eyeRX.coerceIn(0f, 1f), eyeRY = eyeRY.coerceIn(0f, 1f),
        eyeHalfW = eyeHalfW.coerceIn(0.01f, 0.25f), eyeHalfH = eyeHalfH.coerceIn(0.005f, 0.2f),
    )

    companion object {
        val DEFAULT = CharacterRig(
            mouthX = 0.5f, mouthY = 0.62f, mouthHalfW = 0.09f, mouthHalfH = 0.03f,
            eyeLX = 0.38f, eyeLY = 0.35f, eyeRX = 0.62f, eyeRY = 0.35f, eyeHalfW = 0.06f, eyeHalfH = 0.035f,
        )
    }
}

/**
 * Renders an owner-uploaded character in the tutor call. Goes through the exact same states as
 * the 3D tutors — [AvatarFeed]/[FaceDriver] drive both — so listening, thinking, speaking and
 * lip-sync behave identically; only the drawing technique differs. See [drawCharacterFace].
 */
@Composable
fun CutoutAvatar(feed: AvatarFeed, image: ImageBitmap, rig: CharacterRig, accent: Color, modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf(0L) }
    LaunchedEffect(feed) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { frame = it - start }
    }
    val bitmap = remember(image) { image.asAndroidBitmap() }
    val parts = remember(bitmap, rig) { prepareFace(bitmap, rig) }
    val blinkClock = remember { BlinkClock() }
    val jawSmoother = remember { JawSmoother() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val tSec = frame / 1_000_000_000.0
            val (weights, pose) = feed.frame(tSec)
            // The lip-sync drives "jawOpen" up to about 0.6: rescale to 0..1 and smooth it (quick to
            // open, a little slower to close) so the lips never jitter between frames.
            val jaw = jawSmoother.step((weights["jawOpen"] ?: 0f) / 0.6f, tSec)
            // A photo needs a human blink rhythm, not the 3D model's quick symmetric one.
            val blink = blinkClock.value(tSec)
            val breathe = pose.breathe

            val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
            val scale = minOf(size.width / iw, size.height / ih)
            val dw = iw * scale; val dh = ih * scale
            val ox = (size.width - dw) / 2f
            val oy = (size.height - dh) / 2f + (breathe - 0.5f) * 6f + (sin(tSec * 1.3) * 2f).toFloat()

            drawCharacterFace(bitmap, rig, parts, jaw, blink, ox, oy, dw, dh)
        }
    }
}

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * A natural blink rhythm for a photo: the lid drops fast (75 ms, accelerating), stays shut for a
 * moment (40 ms), then lifts more slowly (170 ms, easing out). Blinks come every 2.2-5.5 s at
 * random, and about one in seven is a quick double blink, the way people actually blink.
 */
class BlinkClock(private val rnd: java.util.Random = java.util.Random()) {
    private var next = 1.0 + rnd.nextDouble() * 2.0
    private var start = -1.0
    private var doubled = false

    fun value(t: Double): Float {
        if (start < 0 && t >= next) start = t
        if (start < 0) return 0f
        val ms = (t - start) * 1000.0
        return when {
            ms < CLOSE_MS -> { val p = (ms / CLOSE_MS).toFloat(); p * p }
            ms < CLOSE_MS + HOLD_MS -> 1f
            ms < CLOSE_MS + HOLD_MS + OPEN_MS -> { val q = 1f - ((ms - CLOSE_MS - HOLD_MS) / OPEN_MS).toFloat(); q * q * q }
            else -> {
                start = -1.0
                next = if (!doubled && rnd.nextFloat() < 0.15f) { doubled = true; t + 0.12 } else { doubled = false; t + 2.2 + rnd.nextDouble() * 3.3 }
                0f
            }
        }
    }

    private companion object {
        const val CLOSE_MS = 75.0
        const val HOLD_MS = 40.0
        const val OPEN_MS = 170.0
    }
}

/** Follows the lip-sync level: opens quickly, closes a bit slower, never jumps between frames. */
class JawSmoother {
    private var v = 0f
    private var last = -1.0
    fun step(target: Float, t: Double): Float {
        val goal = target.coerceIn(0f, 1f)
        val dt = if (last < 0) 0.016f else (t - last).toFloat().coerceIn(0f, 0.1f)
        last = t
        val rate = if (goal > v) 30f else 16f
        v += (goal - v) * (1f - kotlin.math.exp(-rate * dt))
        return v
    }
}

/** A small crop of the picture around the eye or mouth that is warped on its own, and where it sits (source pixels). */
class Patch(val bitmap: Bitmap, val x: Int, val y: Int)

/** The pieces [drawCharacterFace] animates. Build once per (picture, rig) with [prepareFace]. */
class FaceParts(val leftEye: Patch?, val rightEye: Patch?, val mouth: Patch?, val paint: android.graphics.Paint)

private const val EYE_MW = 24
private const val EYE_MH = 36
private const val MOUTH_MW = 44
private const val MOUTH_MH = 72

/** Skin above the eye (× eye half-height) that slides down as the upper lid. Stops short of the brow. */
private const val LID_STRIP = 1.4f
/** Skin below the eye (× eye half-height) that the lower lid lifts. */
private const val LOWER_STRIP = 0.9f
/** Where the lids meet when shut, below the eye's centre (× eye half-height): lids close low, like real ones. */
private const val CLOSE_AT = 0.3f

/** The line the lips part along sits a little above the marked mouth centre (× mouth half-height). */
private const val SEAM_UP = 0.12f
/** How much higher the mouth corners sit than the middle (× mouth half-height): the lips' natural curve. */
private const val SEAM_LIFT = 0.28f
/** How far the lower lip drops at full voice (× mouth half-width). Small: a talking mouth, not a yawn. */
private const val MAX_DROP = 0.32f

private class MouthGeometry(rig: CharacterRig, iw: Float, ih: Float) {
    val mx = rig.mouthX * iw
    val my = rig.mouthY * ih
    val hw = (rig.mouthHalfW * iw).coerceAtLeast(6f)
    val hh = (rig.mouthHalfH * ih).coerceAtLeast(3f)
    val seam = my - SEAM_UP * hh
    val lift = SEAM_LIFT * hh
    val band = max(3f, 0.22f * hh)
    /** The parting line at image column [x]: curved up towards the corners. */
    fun seamAt(x: Float): Float {
        val u = ((x - mx) / hw).coerceIn(-1.3f, 1.3f)
        return seam - lift * u * u
    }
    /** How much of the drop a column gets: all of it in the middle, none past the corners. */
    fun fx(x: Float): Float = 1f - smooth(hw * 0.6f, hw * 2.2f, abs(x - mx))
}

/** Crops the areas around both eyes and the mouth out of [bitmap], sized from [rig]. */
fun prepareFace(bitmap: Bitmap, rig: CharacterRig): FaceParts {
    val iw = bitmap.width
    val ih = bitmap.height
    fun crop(x0: Float, y0: Float, x1: Float, y1: Float): Patch? {
        val l = x0.toInt().coerceIn(0, iw - 1)
        val t = y0.toInt().coerceIn(0, ih - 1)
        val r = x1.toInt().coerceIn(l + 1, iw)
        val b = y1.toInt().coerceIn(t + 1, ih)
        if (r - l < 4 || b - t < 4) return null
        return Patch(Bitmap.createBitmap(bitmap, l, t, r - l, b - t), l, t)
    }
    val hw = (rig.eyeHalfW * iw).coerceAtLeast(4f)
    val hh = (rig.eyeHalfH * ih).coerceAtLeast(3f)
    fun eye(ex: Float, ey: Float) = crop(
        ex * iw - 1.7f * hw, ey * ih - hh - LID_STRIP * hh - 1f,
        ex * iw + 1.7f * hw, ey * ih + hh + LOWER_STRIP * hh + 1f,
    )
    val m = MouthGeometry(rig, iw.toFloat(), ih.toFloat())
    val mouth = crop(m.mx - 3.7f * m.hw, m.seam - m.lift * 1.7f - 3f, m.mx + 3.7f * m.hw, m.my + 4.2f * m.hw)
    return FaceParts(eye(rig.eyeLX, rig.eyeLY), eye(rig.eyeRX, rig.eyeRY), mouth, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
}

/**
 * Draws the character into the rectangle ([ox], [oy], [dw] × [dh]). At rest that is the untouched
 * picture; while it talks and blinks, only small patches around the eyes and mouth are warped, and
 * every patch fades to zero movement at its edges, so there are no seams. Nothing is painted over
 * the face except the inside of the open mouth and, when shut, a faint lash line.
 *
 *  - [blink] 0..1: the upper lid (the character's own skin above the eye, with its real lashes on
 *    its edge) slides down along the eye's curve while the lower lid lifts a little, and the eye
 *    itself is folded away between them. The lids meet low, on a line that follows the lower lid.
 *    The brows stay still.
 *  - [jaw] 0..1: the lips part along the mouth's own curve: the upper lip and upper teeth stay
 *    exactly as in the picture, the lower lip and chin move down smoothly, and the gap shows a
 *    shaded mouth interior. No teeth are ever drawn on top of the real ones.
 */
fun DrawScope.drawCharacterFace(
    bitmap: Bitmap, rig: CharacterRig, parts: FaceParts,
    jaw: Float, blink: Float,
    ox: Float, oy: Float, dw: Float, dh: Float,
) {
    val iw = bitmap.width.toFloat()
    val ih = bitmap.height.toFloat()
    val s = dw / iw
    val b = blink.coerceIn(0f, 1f)
    val hwE = (rig.eyeHalfW * iw).coerceAtLeast(4f)
    val hhE = (rig.eyeHalfH * ih).coerceAtLeast(3f)
    val m = MouthGeometry(rig, iw, ih)
    val drop = m.hw * MAX_DROP * jaw.coerceIn(0f, 1f)

    /** How open the eye is at column offset [dx]: 1 in the middle, tapering to 0 at the corners. */
    fun eyeProfile(dx: Float): Float {
        val u = dx / (hwE * 1.08f)
        val q = (1f - u * u).coerceAtLeast(0f)
        return Math.pow(q.toDouble(), 0.75).toFloat()
    }

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.drawBitmap(bitmap, null, android.graphics.RectF(ox, oy, ox + dw, oy + dh), parts.paint)

        if (b > 0.02f) {
            fun lids(p: Patch?, exN: Float, eyN: Float) {
                if (p == null) return
                val ex = exN * iw
                val ey = eyN * ih
                val yA = ey - hhE - LID_STRIP * hhE
                val yB = ey + hhE + LOWER_STRIP * hhE
                meshPatch(nc, p, EYE_MW, EYE_MH, ox, oy, s, parts.paint) { sx, sy ->
                    val dx = sx - ex
                    val pr = eyeProfile(dx)
                    val top = ey - hhE * pr
                    val bot = ey + hhE * pr
                    val meet = ey + hhE * CLOSE_AT * pr
                    val e1 = top + b * (meet - top)   // where the upper lid's edge has got to
                    val e2 = bot - b * (bot - meet)   // where the lower lid's edge has got to
                    val f = when {
                        sy <= yA -> sy
                        sy < top -> yA + (sy - yA) / max(top - yA, 1e-3f) * (e1 - yA)
                        sy <= bot -> e1 + (sy - top) / max(bot - top, 1e-3f) * (e2 - e1)
                        sy < yB -> e2 + (sy - bot) / max(yB - bot, 1e-3f) * (yB - e2)
                        else -> sy
                    }
                    val wx = 1f - smooth(hwE * 0.9f, hwE * 1.45f, abs(dx))
                    sy + wx * (f - sy)
                }
            }
            lids(parts.leftEye, rig.eyeLX, rig.eyeLY)
            lids(parts.rightEye, rig.eyeRX, rig.eyeRY)
        }

        if (drop > 0.8f) {
            val p = parts.mouth
            if (p != null) {
                meshPatch(nc, p, MOUTH_MW, MOUTH_MH, ox, oy, s, parts.paint) { sx, sy ->
                    val ty = sy - m.seamAt(sx)
                    val g = smooth(0f, m.band, ty) * (1f - smooth(m.hw * 1.8f, m.hw * 4f, ty))
                    sy + drop * m.fx(sx) * g
                }
            }
        }
    }

    // The inside of the mouth, in the gap between the still upper lip and the lowered lower lip:
    // a lens that follows the lips' curve, darkest in the middle, warmer towards the tongue, drawn
    // twice (a slightly larger faint pass first) so its edge melts into the lips.
    if (drop > 0.8f && parts.mouth != null) {
        val w = m.hw * 0.86f
        fun lens(grow: Float): Path = Path().apply {
            val n = 48
            for (i in 0..n) {
                val u = -1f + 2f * i / n
                val x = m.mx + u * w
                val y = m.seamAt(x) - 0.5f - grow
                if (i == 0) moveTo(ox + x * s, oy + y * s) else lineTo(ox + x * s, oy + y * s)
            }
            for (i in n downTo 0) {
                val u = -1f + 2f * i / n
                val x = m.mx + u * w
                val pr = Math.pow((1f - u * u).toDouble(), 0.55).toFloat()
                val y = m.seamAt(x) + (drop * m.fx(x) + m.band * 0.55f) * pr + grow
                lineTo(ox + x * s, oy + y * s)
            }
            close()
        }
        val brush = Brush.verticalGradient(
            0f to Color(0xFF461E20), 0.22f to Color(0xFF220B0D), 0.65f to Color(0xFF1A080A), 1f to Color(0xFF78323A),
            startY = oy + (m.seam - m.lift) * s,
            endY = oy + (m.seam + drop + m.band) * s,
        )
        drawPath(lens(1.5f), brush, alpha = 0.27f)
        drawPath(lens(0f), brush, alpha = 0.92f)
    }

    // When the eyes are nearly shut, a soft lash line along where the lids meet.
    if (b > 0.72f) {
        val k = (b - 0.72f) / 0.28f
        for ((exN, eyN) in listOf(rig.eyeLX to rig.eyeLY, rig.eyeRX to rig.eyeRY)) {
            val ex = exN * iw
            val ey = eyN * ih
            val line = Path().apply {
                val n = 24
                for (i in 0..n) {
                    val u = -0.86f + 1.72f * i / n
                    val x = ex + u * hwE
                    val y = ey + hhE * CLOSE_AT * eyeProfile(u * hwE)
                    if (i == 0) moveTo(ox + x * s, oy + y * s) else lineTo(ox + x * s, oy + y * s)
                }
            }
            drawPath(line, Color(0xFF3A241C), alpha = 0.18f * k, style = Stroke(width = max(3f, hhE * 0.30f) * s, cap = StrokeCap.Round))
            drawPath(line, Color(0xFF26160F), alpha = 0.37f * k, style = Stroke(width = max(2f, hhE * 0.12f) * s, cap = StrokeCap.Round))
        }
    }
}

/**
 * Draws [p] as a mesh whose vertices keep their x and take the y returned by [newY] (source-pixel
 * coordinates in, source-pixel y out), so each patch can be warped without touching the rest.
 */
private fun meshPatch(
    nc: android.graphics.Canvas, p: Patch, mw: Int, mh: Int,
    ox: Float, oy: Float, s: Float, paint: android.graphics.Paint,
    newY: (sx: Float, sy: Float) -> Float,
) {
    val verts = FloatArray((mw + 1) * (mh + 1) * 2)
    val pw = p.bitmap.width.toFloat()
    val ph = p.bitmap.height.toFloat()
    var k = 0
    for (j in 0..mh) {
        val sy = p.y + ph * j / mh
        for (i in 0..mw) {
            val sx = p.x + pw * i / mw
            verts[k++] = ox + sx * s
            verts[k++] = oy + newY(sx, sy) * s
        }
    }
    nc.drawBitmapMesh(p.bitmap, mw, mh, verts, 0, null, 0, paint)
}

/** Downloads and decodes a character's cutout PNG for rendering. Null on any failure. */
suspend fun loadCutout(context: android.content.Context, url: String): androidx.compose.ui.graphics.ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val bytes = java.net.URL(url).openStream().use { it.readBytes() }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
