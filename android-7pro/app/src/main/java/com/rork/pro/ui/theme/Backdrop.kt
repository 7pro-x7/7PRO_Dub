package com.rork.pro.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 7PRO's background treatment — the graded canvas plus the decorative layer drawn on top of it.
 *
 * Every full-screen surface in the app used to paint a two-stop vertical gradient inline, which
 * meant the app's most-seen surface was defined in a dozen places and looked like a flat slab in
 * dark mode. This is that surface, in one place:
 *
 * 1. A three-stop vertical grade (top → canvas → deep), so a screen has a direction — light
 *    above, weight below — instead of one uniform fill. In dark mode the stops sit on a true
 *    black scale rather than the old warm charcoal.
 * 2. Two wide radial washes in the brand's copper and teal, at alphas low enough to read as
 *    light rather than as shapes.
 * 3. Drawn ornament: a sparse dot grid across the top band, and three concentric arcs sweeping
 *    out of the bottom corner. Both reference the graph-paper-and-compass vocabulary of a
 *    studying surface, which is the point — a decorative layer that means nothing to the
 *    product is just noise on every screen.
 *
 * All of it is drawn in a single `drawBehind` pass with gradients and primitives only — no
 * blur (which needs API 31 and a render-effect pass per frame), no layers, no bitmaps. The dot
 * grid is bounded to the top band so its count stays in the low hundreds regardless of screen
 * height rather than scaling with it.
 */
fun Modifier.appBackdrop(@Suppress("UNUSED_PARAMETER") ornament: Boolean = true): Modifier = this.drawBehind {
    if (AppPalette.sky) {
        // Every screen (learner, owner / admin, teacher): the same sky / navy wash and soft clouds as Home.
        drawRect(HomeSky.pageBrush())
        val r1 = size.width * 0.62f
        val c1 = Offset(size.width * 1.02f, size.height * 0.40f)
        drawCircle(Brush.radialGradient(listOf(HomeSky.Blob, Color.Transparent), center = c1, radius = r1), r1, c1)
        val r2 = size.width * 0.70f
        val c2 = Offset(-size.width * 0.10f, size.height * 0.66f)
        drawCircle(Brush.radialGradient(listOf(HomeSky.Blob, Color.Transparent), center = c2, radius = r2), r2, c2)
    } else {
        // Legacy flat canvas (only if AppPalette.sky is switched off). The grade, brand glows and
        // drawn ornament below are kept (unused).
        drawRect(Ink.Canvas)
    }
}

/**
 * Sparse graph-paper dots fading out down the top band. Fading rather than stopping is what
 * keeps it from reading as a striped header: the grid ends because it runs out of light, not
 * because it hit an edge.
 */
private fun DrawScope.drawDotGrid() {
    val step = 26.dp.toPx()
    val band = minOf(size.height * 0.34f, 260.dp.toPx())
    val radius = 1.1f.dp.toPx()
    var y = step
    while (y < band) {
        val fade = 1f - (y / band)
        var x = step
        while (x < size.width) {
            drawCircle(
                color = Ink.Ornament.copy(alpha = Ink.Ornament.alpha * fade),
                radius = radius,
                center = Offset(x, y),
            )
            x += step
        }
        y += step
    }
}

/** Three concentric arcs sweeping out of the bottom-start corner — a compass sweep, drawn wide
 *  enough that only the curve itself is ever on screen, never the whole circle. */
private fun DrawScope.drawCornerArcs() {
    val center = Offset(-size.width * 0.12f, size.height * 1.04f)
    val base = size.width * 0.55f
    repeat(3) { index ->
        val radius = base + base * 0.28f * index
        val alpha = Ink.Ornament.alpha * (1f - index * 0.26f)
        drawArc(
            color = Ink.Ornament.copy(alpha = alpha),
            startAngle = -95f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/**
 * Fine diagonal hairlines in the top-end corner — the folded-corner mark of a worked page.
 *
 * Drawn as a short run of parallel lines whose length steps down, so the block reads as a
 * deliberate corner motif rather than as a texture that happens to stop.
 */
private fun DrawScope.drawPaperHatch() {
    val gap = 9.dp.toPx()
    val originX = size.width
    val longest = minOf(size.width * 0.30f, 132.dp.toPx())
    repeat(9) { index ->
        val inset = gap * (index + 1)
        val length = longest - index * (longest / 14f)
        drawLine(
            color = Ink.Ornament.copy(alpha = Ink.Ornament.alpha * (1f - index * 0.09f)),
            start = Offset(originX - inset, 0f),
            end = Offset(originX - inset - length * 0.55f, length),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * A warm shade pulled in from all four edges, so the cream canvas reads as a lit sheet rather
 * than a flat fill. Two linear passes instead of a radial one: a radial vignette on a tall
 * phone screen darkens the middle of the long edges far more than the corners, which looks
 * like a vertical band rather than like light.
 */
private fun DrawScope.drawEdgeShade() {
    val shade = Ink.EdgeShade
    val reach = (minOf(size.height * 0.22f, 220.dp.toPx()) / size.height).coerceIn(0.05f, 0.35f)
    val horizontal = minOf(size.width * 0.3f, 110.dp.toPx())
    drawRect(
        Brush.verticalGradient(
            0f to shade,
            reach to Color.Transparent,
            1f - reach to Color.Transparent,
            1f to shade,
        ),
    )
    drawRect(
        Brush.horizontalGradient(
            colors = listOf(shade, Color.Transparent),
            startX = 0f,
            endX = horizontal,
        ),
    )
    drawRect(
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, shade),
            startX = size.width - horizontal,
            endX = size.width,
        ),
    )
}

/**
 * The quiet version — grade and glows, no drawn ornament.
 *
 * For screens whose own content is the decoration (a video player, the in-call screen, a full
 * -bleed image): the wash still gives them depth, while dots and arcs behind moving content
 * would read as dirt on the lens.
 */
fun Modifier.appBackdropPlain(): Modifier = appBackdrop(ornament = false)

/**
 * The faint top-to-bottom sheen on a raised surface.
 *
 * A card lit from above is the single cheapest cue that it sits on top of the page rather than
 * being a hole cut into it — and on a black canvas, where a flat fill has no edge to catch, it
 * is close to the only one that works without adding a heavy border.
 */
fun surfaceSheenBrush(): Brush = Brush.verticalGradient(listOf(Ink.SurfaceSheen, Ink.Surface))
