package com.rork.pro.classroom.whiteboard

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Canvas as ComposeGraphicsCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rork.pro.data.ClassroomStrokeRow
import com.rork.pro.ui.components.LessonVideo
import com.rork.pro.ui.components.resolveLessonMedia
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.roundToInt

/**
 * [points] are stored in normalized board space (0f..1f on both axes) whenever [normalized] is
 * true, which every stroke written by this build is. Raw pixel coordinates were the old format
 * and are the reason a teacher's drawing landed somewhere else entirely on a student's screen:
 * a phone, a tablet and a browser all have different canvas sizes, so px-for-px replay only
 * ever lined up on identical devices. Legacy rows (no `norm` flag) are still rendered as pixels
 * so an in-progress board drawn by an older client does not disappear.
 */
private data class LocalStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val eraser: Boolean = false,
    val normalized: Boolean = true,
    /** Pen width as a fraction of the board's width (new rows), so it looks the same on every screen. */
    val widthNorm: Float? = null,
    /** True when the points are relative to the fixed [BOARD_ASPECT] board, not the whole canvas. */
    val boardSpace: Boolean = false,
)

/** Where the person last left the drawing toolbar, so reopening the whiteboard keeps it there. */
private var savedToolbarOffset = Offset.Zero

private val PALETTE = listOf(Ink.Coral, Color(0xFF3FAF8E), Color(0xFF4FA3FF), Color(0xFFF5F0E6), Color(0xFFE0954F))

/**
 * Every device draws and replays on the SAME board: a fixed 9:16 rectangle, centred and scaled to
 * fit the screen. Points and pen width are stored relative to that rectangle, and the shared
 * picture / PDF is laid out inside it too — so a line lands on exactly the same spot of the
 * picture, at the same relative thickness, on a teacher's phone, a student's phone and the browser.
 * (Before, points were relative to each screen, so different screen shapes stretched and shifted
 * every drawing.)
 */
private const val BOARD_ASPECT = 9f / 16f

private fun boardRect(w: Float, h: Float): androidx.compose.ui.geometry.Rect {
    val bw = minOf(w, h * BOARD_ASPECT)
    val bh = bw / BOARD_ASPECT
    val left = (w - bw) / 2f
    val top = (h - bh) / 2f
    return androidx.compose.ui.geometry.Rect(left, top, left + bw, top + bh)
}

/** Lays [content] out in the same centred 9:16 rectangle [boardRect] describes. */
@Composable
private fun BoardFrame(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val bw = minOf(maxWidth, maxHeight * BOARD_ASPECT)
        Box(Modifier.size(bw, bw / BOARD_ASPECT), content = content)
    }
}

/**
 * Read-only rendering of whatever a session's shared material currently is — image, video, or a
 * PDF's first page — with none of [WhiteboardCanvas]'s drawing tools. Used as that canvas's own
 * backdrop (strokes layer on top of it there), and, since a share is a first-class main-call-screen
 * action now rather than something reachable only inside the whiteboard, also used directly on the
 * main screen (see ClassroomCallActivity's `showingSharedMedia`) for anyone who has not opened the
 * whiteboard to draw — so a shared photo/video/file shows up where the video already is, instead of
 * requiring a trip to a separate tool just to look at it.
 */
@Composable
fun SharedMediaBackground(
    backgroundUrl: String?,
    backgroundType: String,
    modifier: Modifier = Modifier,
    strokes: List<ClassroomStrokeRow> = emptyList(),
) {
    val context = LocalContext.current
    var pdfBitmap by remember(backgroundUrl) { mutableStateOf<Bitmap?>(null) }
    var boardError by remember(backgroundUrl) { mutableStateOf<String?>(null) }

    // Pinch-to-zoom state for the still-image cases (photo, PDF page) below. Reset whenever a
    // new file is shared so the next image always starts back at its normal size instead of
    // inheriting whatever zoom level the previous one was left at. A video keeps its own player
    // controls untouched — pinching a video view would fight with ExoPlayer's own gestures — so
    // zoom only applies to IMAGE/PDF.
    var zoomScale by remember(backgroundUrl) { mutableStateOf(1f) }
    var zoomOffset by remember(backgroundUrl) { mutableStateOf(Offset.Zero) }
    val zoomable = backgroundType == "IMAGE" || backgroundType == "PDF"

    LaunchedEffect(backgroundUrl, backgroundType) {
        pdfBitmap = null
        if (backgroundType == "PDF" && !backgroundUrl.isNullOrBlank()) {
            val rendered = withContext(Dispatchers.IO) { downloadAndRenderPdfFirstPage(context, backgroundUrl) }
            pdfBitmap = rendered
            boardError = if (rendered == null) tr(StrClassroom.pdfLoadFailed) else null
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .let { base ->
                if (!zoomable) return@let base
                base
                    .pointerInput(backgroundUrl) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            // Once back at 1x there is nothing to pan around, so snap the pan
                            // back to center too — otherwise a fully zoomed-out image could sit
                            // shifted off to one side with no way to zoom-drag it back.
                            zoomOffset = if (zoomScale <= 1f) Offset.Zero else zoomOffset + pan
                        }
                    }
                    .pointerInput(backgroundUrl) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoomScale = 1f
                                zoomOffset = Offset.Zero
                            },
                        )
                    }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .let { base ->
                    if (!zoomable) base else base.graphicsLayer {
                        scaleX = zoomScale; scaleY = zoomScale
                        translationX = zoomOffset.x; translationY = zoomOffset.y
                    }
                },
        ) {
            BoardFrame(Modifier.fillMaxSize()) {
                when {
                    backgroundType == "IMAGE" && !backgroundUrl.isNullOrBlank() ->
                        AsyncImage(model = backgroundUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                    backgroundType == "VIDEO" && !backgroundUrl.isNullOrBlank() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LessonVideo(resolveLessonMedia(backgroundUrl), modifier = Modifier.fillMaxWidth())
                        }
                    backgroundType == "PDF" && pdfBitmap != null ->
                        Image(bitmap = pdfBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            if (strokes.isNotEmpty()) StrokesOverlay(strokes, Modifier.fillMaxSize())
        }
        boardError?.let { message ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.Coral.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Read-only strokes, with no background and no drawing gestures — the same decode/bake path
 * [WhiteboardCanvas] uses internally, with everything about backgrounds and pointer input left
 * out. Meant to sit directly on top of the main call screen's video (or shared media), so a
 * stroke shows up for the teacher and every student the instant it's drawn, instead of only for
 * whoever has the whiteboard tool open — see ClassroomCallActivity, which layers this over the
 * video/shared-media stack unconditionally rather than gating it behind `showWhiteboard`.
 *
 * Transparent where nothing is drawn, and adds no pointer handling of its own, so it never
 * blocks taps/gestures meant for whatever is underneath it.
 */
@Composable
fun StrokesOverlay(strokes: List<ClassroomStrokeRow>, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val persisted = remember(strokes) { strokes.filter { it.action == "STROKE" }.mapNotNull { decodeStroke(it) } }
    val bitmap = remember(persisted, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            renderStrokesToBitmap(persisted, canvasSize.width, canvasSize.height, density)
        } else {
            null
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        bitmap?.let { drawImage(it) }
    }
}

/**
 * A shared drawing surface: pen + eraser, pinch-to-zoom/pan, an optional image, video, or
 * PDF-first-page background kept in sync for every participant (not just whoever set it), and
 * every finished stroke pushed to [onStroke] for Realtime sync to the rest of the room.
 *
 * Picking and uploading the background itself is not this composable's job any more — it now
 * happens from the main call screen's own share button, visible without opening the whiteboard
 * at all (see ClassroomCallActivity), so [backgroundUrl]/[backgroundType] simply render whatever
 * the session's board row currently holds.
 *
 * [strokes] is the authoritative, already-ordered history from the repository, so a stroke drawn
 * locally shows immediately from the in-progress gesture and again — harmlessly, visually
 * identical — once its own row round-trips back through Realtime.
 */
@Composable
fun WhiteboardCanvas(
    strokes: List<ClassroomStrokeRow>,
    canClear: Boolean,
    backgroundUrl: String?,
    backgroundType: String,
    onStroke: (JsonObject) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var color by remember { mutableStateOf(PALETTE.first()) }
    var eraser by remember { mutableStateOf(false) }
    var strokeWidth by remember { mutableStateOf(6f) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var boardError by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // The toolbar can be dragged anywhere over the board. Its resting spot (top corner) is only
    // the starting point; this offset is added on top, and the last bounds it was laid out with
    // are kept so a drag can be clamped to stay fully on screen in both LTR and RTL.
    var toolbarOffset by remember { mutableStateOf(savedToolbarOffset) }
    // Where the bar sits with NO drag offset (its resting corner), its size, and the board size —
    // measured before the offset is applied, so they never move while the bar is being dragged.
    var toolbarBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var toolbarParent by remember { mutableStateOf(IntSize.Zero) }

    /**
     * Keeps the WHOLE bar on the board. The total offset is clamped against the resting position
     * (not each drag step against last frame's bounds, which lagged a frame behind fast drags and
     * let the bar overshoot the edge and vanish). An inverted range — a bar wider than the board —
     * pins it to the start edge instead of throwing.
     */
    fun clampToolbar(o: Offset): Offset {
        if (toolbarParent.width <= 0 || toolbarBounds.width <= 0f) return o
        val minX = -toolbarBounds.left
        val maxX = toolbarParent.width - toolbarBounds.right
        val minY = -toolbarBounds.top
        val maxY = toolbarParent.height - toolbarBounds.bottom
        return Offset(
            if (minX <= maxX) o.x.coerceIn(minX, maxX) else minX,
            if (minY <= maxY) o.y.coerceIn(minY, maxY) else minY,
        )
    }

    val persisted = remember(strokes) { strokes.filter { it.action == "STROKE" }.mapNotNull { decodeStroke(it) } }

    // Every historical stroke used to be replayed with drawLine() on every single pointer-move
    // event of whatever is currently being drawn — fine for a handful of strokes, but a long
    // class session can rack up thousands of points, and redrawing all of them dozens of times
    // a second is what made the pen feel like it couldn't keep up ("doesn't stay fixed"): points
    // dropped or the line lagged behind the finger. Baking the settled strokes into a bitmap
    // once per finished stroke (not per point) means the hot path while actively drawing is just
    // one image blit plus the handful of points in the stroke currently being drawn.
    val persistedBitmap = remember(persisted, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            renderStrokesToBitmap(persisted, canvasSize.width, canvasSize.height, density)
        } else {
            null
        }
    }

    // Every participant (not only whoever set it) renders the same background from the URL
    // stored on classroom_whiteboard_boards. Images render directly via Coil; a PDF's first page
    // is downloaded once and rasterized locally since Android has no PDF <Image>.
    LaunchedEffect(backgroundUrl, backgroundType) {
        pdfBitmap = null
        if (backgroundType == "PDF" && !backgroundUrl.isNullOrBlank()) {
            val rendered = withContext(Dispatchers.IO) { downloadAndRenderPdfFirstPage(context, backgroundUrl) }
            pdfBitmap = rendered
            // Previously a download/render failure just left the canvas blank with no
            // indication anything was even supposed to be there — every participant would
            // silently see nothing where the teacher's PDF page should be. The technical
            // cause itself is already logged inside downloadAndRenderPdfFirstPage.
            boardError = if (rendered == null) tr(StrClassroom.pdfLoadFailed) else null
        }
    }

    Box(modifier.background(Ink.Canvas)) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // A plain detectTransformGestures() reacts to a *single* finger too (pan
                    // with zoom held at 1x), and it consumes that finger's position changes as
                    // it goes. Since this pointerInput sits ahead of the drawing one below on
                    // the same Box, it was swallowing every one-finger stroke before the pen
                    // ever saw it — drawing either broke into disconnected segments or just
                    // panned the board instead of leaving a line. Only starting to track pan/
                    // zoom once a second finger joins is what lets one finger draw normally and
                    // two fingers pinch/pan, instead of the two gestures fighting over the same
                    // touch.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                                    offset += panChange
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
                .pointerInput(color, eraser, strokeWidth) {
                    // Captured in normalized board space so the same stroke lands in the same
                    // place on every screen it is replayed on — phone, tablet or browser.
                    val board = boardRect(size.width.toFloat(), size.height.toFloat())
                    fun norm(p: Offset) = Offset(
                        if (board.width > 0f) ((p.x - board.left) / board.width).coerceIn(0f, 1f) else 0f,
                        if (board.height > 0f) ((p.y - board.top) / board.height).coerceIn(0f, 1f) else 0f,
                    )
                    detectDragGestures(
                        onDragStart = { start -> currentPoints = listOf(norm(start)) },
                        onDrag = { change, _ -> currentPoints = currentPoints + norm(change.position) },
                        onDragEnd = {
                            if (currentPoints.size > 1) {
                                onStroke(encodeStroke(currentPoints, color, strokeWidth, eraser, board.width))
                            }
                            currentPoints = emptyList()
                        },
                    )
                },
        ) {
            BoardFrame(Modifier.fillMaxSize()) {
                when {
                    backgroundType == "IMAGE" && !backgroundUrl.isNullOrBlank() ->
                        AsyncImage(model = backgroundUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                    backgroundType == "VIDEO" && !backgroundUrl.isNullOrBlank() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LessonVideo(resolveLessonMedia(backgroundUrl), modifier = Modifier.fillMaxWidth())
                        }
                    backgroundType == "PDF" && pdfBitmap != null ->
                        Image(bitmap = pdfBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            // Rendered into its own offscreen compositing layer so an eraser stroke can use
            // BlendMode.Clear to punch a real hole of transparent pixels into *this layer only*.
            // Once that layer is flattened onto whatever sits underneath it in the Box (the
            // image/PDF background, or the plain canvas color), the erased area shows that
            // background through — an actual reveal, not another stroke painted in the
            // background color on top of it (which breaks the moment there is an image/PDF
            // behind the strokes, since "the background color" is no longer what's there).
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                // Settled strokes: one cheap image draw, however many strokes are in it.
                persistedBitmap?.let { drawImage(it) }
                // Only the stroke currently being drawn is rasterized fresh on this frame.
                if (currentPoints.size > 1) {
                    drawStrokes(listOf(LocalStroke(currentPoints, color, strokeWidth, eraser, boardSpace = true)))
                }
                // A faint frame shows exactly where everyone can draw — the same board on every screen.
                val frame = boardRect(size.width, size.height)
                drawRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 176.dp, start = 12.dp)
                // Measured BEFORE the offset below, so these are the resting bounds and do not
                // shift while dragging. Re-clamping here also rescues a bar that a rotation, a
                // smaller window, or an older saved position had left off-screen.
                .onGloballyPositioned { coords ->
                    toolbarBounds = coords.boundsInParent()
                    toolbarParent = coords.parentLayoutCoordinates?.size ?: IntSize.Zero
                    val fixed = clampToolbar(toolbarOffset)
                    if (fixed != toolbarOffset) {
                        toolbarOffset = fixed
                        savedToolbarOffset = fixed
                    }
                }
                // absoluteOffset, not offset: offset {} is mirrored in RTL, so in Arabic a drag to
                // the right moved the bar LEFT while the clamp assumed right — the bar ran past
                // the edge and disappeared. absoluteOffset follows the finger in both directions.
                .absoluteOffset { IntOffset(toolbarOffset.x.roundToInt(), toolbarOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { savedToolbarOffset = toolbarOffset },
                        onDragCancel = { savedToolbarOffset = toolbarOffset },
                    ) { change, drag ->
                        change.consume()
                        toolbarOffset = clampToolbar(toolbarOffset + drag)
                    }
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.Surface.copy(alpha = 0.95f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Grip that says "this bar moves". The whole bar drags, the grip is just the hint.
            Icon(Icons.Default.DragIndicator, null, tint = Ink.TextSecondary, modifier = Modifier.size(18.dp))
            PALETTE.forEach { swatch ->
                Box(Modifier.size(22.dp).clip(CircleShape).background(swatch).clickable { color = swatch; eraser = false })
            }
            ToolIcon(Icons.Default.Edit, active = !eraser) { eraser = false }
            // The eraser mode already existed in the data model (LocalStroke.eraser, encodeStroke,
            // BlendMode.Clear rendering) but nothing in the toolbar could ever turn it on — the
            // only way to remove anything was the trash icon, which wipes the entire board. This
            // is the missing button that lets someone erase just what they drew.
            ToolIcon(Icons.Default.Backspace, active = eraser) { eraser = true }
            ToolIcon(Icons.Default.ZoomOutMap, active = false) { scale = 1f; offset = Offset.Zero }
            if (canClear) {
                ToolIcon(Icons.Default.Delete, active = false) { onClear() }
            }
        }

        // Previously any failure here (upload rejected, PDF download/render failed) left the
        // board silently blank or unchanged with nothing on screen to explain why — now every
        // participant looking at the board sees exactly what went wrong.
        boardError?.let { message ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.Coral.copy(alpha = 0.16f))
                    .clickable { boardError = null }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Ink.AmberSoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Ink.Amber else Ink.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

/** Shared by the live per-frame draw and the cached-bitmap render below, so both stay in sync. */
private fun DrawScope.drawStrokes(strokesToDraw: List<LocalStroke>) {
    strokesToDraw.forEach { s ->
        if (s.points.size < 2) return@forEach
        val rect = boardRect(size.width, size.height)
        val pts = when {
            s.normalized && s.boardSpace ->
                s.points.map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
            // Rows from older builds: relative to that device's whole canvas.
            s.normalized -> s.points.map { Offset(it.x * size.width, it.y * size.height) }
            else -> s.points
        }
        val baseWidth = if (s.boardSpace && s.widthNorm != null) s.widthNorm * rect.width else s.width
        val blendMode = if (s.eraser) BlendMode.Clear else BlendMode.SrcOver
        // The drawn color is irrelevant for an eraser stroke — BlendMode.Clear
        // ignores source color and zeroes out destination alpha/color instead.
        val strokeColor = if (s.eraser) Color.Transparent else s.color
        val eraserWidth = if (s.eraser) baseWidth * 2.2f else baseWidth
        for (i in 0 until pts.size - 1) {
            drawLine(
                color = strokeColor,
                start = pts[i],
                end = pts[i + 1],
                strokeWidth = eraserWidth,
                cap = StrokeCap.Round,
                blendMode = blendMode,
            )
        }
    }
}

/**
 * Bakes every settled stroke into a bitmap once (called again only when [strokesToDraw] or the
 * canvas size changes), so the per-frame draw while actively drawing a new stroke doesn't have to
 * replay the whole board's history. Eraser strokes still punch real transparent holes here, same
 * as before — they just do it once when committed instead of on every frame after that.
 */
private fun renderStrokesToBitmap(
    strokesToDraw: List<LocalStroke>,
    width: Int,
    height: Int,
    density: androidx.compose.ui.unit.Density,
): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    val canvas = ComposeGraphicsCanvas(bitmap)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
        drawStrokes(strokesToDraw)
    }
    return bitmap
}

/** [points] must already be normalized to 0f..1f — see [LocalStroke]. */
private fun encodeStroke(points: List<Offset>, color: Color, width: Float, eraser: Boolean, boardWidthPx: Float): JsonObject = buildJsonObject {
    put("color", "#" + Integer.toHexString(color.toArgb()).padStart(8, '0'))
    put("width", width.toDouble())
    put("eraser", eraser)
    // Marks the coordinate space so a client can tell this row apart from a legacy pixel row.
    put("norm", true)
    // New coordinate space: relative to the fixed 9:16 board, with the pen width relative to it too.
    put("bd", true)
    put("wn", if (boardWidthPx > 0f) (width / boardWidthPx).toDouble() else 0.0)
    put(
        "points",
        buildJsonArray {
            points.forEach { p -> add(buildJsonArray { add(JsonPrimitive(p.x)); add(JsonPrimitive(p.y)) }) }
        },
    )
}

private fun decodeStroke(row: ClassroomStrokeRow): LocalStroke? {
    val obj = row.stroke ?: return null
    return runCatching {
        val eraser = obj["eraser"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val width = obj["width"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 6f
        val colorHex = obj["color"]?.jsonPrimitive?.content ?: "#FFCBA35B"
        // android.graphics.Color.parseColor accepts both "#RRGGBB" and "#AARRGGBB" directly.
        // The stored color is only meaningful for a pen stroke — an eraser stroke is rendered
        // with BlendMode.Clear regardless of it (see WhiteboardCanvas), so a bad/legacy value
        // here can never break erasing.
        val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Ink.TextPrimary)
        val points = obj["points"]?.jsonArray?.map { pair ->
            val arr = pair.jsonArray
            Offset(arr[0].jsonPrimitive.content.toFloat(), arr[1].jsonPrimitive.content.toFloat())
        } ?: emptyList()
        val normalized = obj["norm"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val boardSpace = obj["bd"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val widthNorm = obj["wn"]?.jsonPrimitive?.content?.toFloatOrNull()?.takeIf { it > 0f }
        LocalStroke(points, color, width, eraser, normalized, widthNorm, boardSpace)
    }.onFailure {
        // One malformed row shouldn't blank the whole board — dropping just that stroke (via
        // mapNotNull at the call site) is the right resilience move — but it used to do that
        // with zero trace anywhere, which made a real encoding bug indistinguishable from
        // "nothing went wrong". Logged, not surfaced to the user: a single skipped stroke
        // among possibly hundreds is not something worth interrupting the class over.
        android.util.Log.w("WhiteboardCanvas", "Skipping unparseable stroke row id=${row.id}: ${it.message}")
    }.getOrNull()
}

/** Downloads a small PDF into cache and rasterizes only its first page — plenty for a lesson slide. */
private fun downloadAndRenderPdfFirstPage(context: android.content.Context, url: String): Bitmap? =
    runCatching {
        val tempFile = File.createTempFile("classroom_wb_", ".pdf", context.cacheDir)
        java.net.URL(url).openStream().use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.onFailure {
        android.util.Log.e("WhiteboardCanvas", "downloadAndRenderPdfFirstPage failed for url=$url", it)
    }.getOrNull()
