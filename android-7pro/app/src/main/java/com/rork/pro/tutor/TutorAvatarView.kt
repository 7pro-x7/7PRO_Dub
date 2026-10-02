package com.rork.pro.tutor

import android.content.Context
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.sin

/**
 * Downloads the tutor's .glb once and keeps it, keyed by version, so replacing the model in
 * Supabase (ai_tutor_configure) reaches every phone without an app update.
 */
object AvatarStore {
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }

    fun cached(context: Context, version: Int): File? =
        file(context, version).takeIf { it.exists() && it.length() > 1024 }

    /** Returns the model file, downloading it if needed; [onProgress] gets 0..1. Null on failure. */
    suspend fun ensure(context: Context, url: String, version: Int, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        cached(context, version)?.let { return@withContext it }
        val dir = File(context.filesDir, "tutor").apply { mkdirs() }
        val part = File(dir, "download.part")
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val body = res.body ?: error("empty")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val target = file(context, version)
            if (!part.renameTo(target)) error("rename failed")
            dir.listFiles()?.filter { it.name.startsWith("avatar-v") && it != target }?.forEach { it.delete() }
            target
        }.getOrElse {
            part.delete()
            null
        }
    }

    private fun file(context: Context, version: Int) = File(File(context.filesDir, "tutor"), "avatar-v$version.glb")

    /** Bump when app/src/main/assets/tutor/tutor.glb is replaced, so phones re-copy it. */
    private const val BUNDLED_VERSION = 2

    /**
     * Mr. Adam ships inside the app (assets/tutor/tutor.glb, ~2.8 MB), so the 3D tutor works on
     * first launch with nothing to download. It is copied once to a real file for Filament.
     * A model set later in Supabase (ai_tutor_configure) replaces it without an app update.
     */
    suspend fun bundled(context: Context, female: Boolean = false): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "tutor").apply { mkdirs() }
            val tag = if (female) "female" else "male"
            val target = File(dir, "bundled-$tag-$BUNDLED_VERSION.glb")
            if (!target.exists() || target.length() < 1024) {
                val part = File(dir, "bundled-$tag.part")
                context.assets.open(if (female) "tutor/tutor_female.glb" else "tutor/tutor.glb").use { input -> part.outputStream().use { input.copyTo(it) } }
                if (!part.renameTo(target)) error("rename failed")
                dir.listFiles()?.filter { it.name.startsWith("bundled-") && !it.name.endsWith("-$BUNDLED_VERSION.glb") }?.forEach { it.delete() }
            }
            target
        }.getOrNull()
    }
}

/** Live state the avatar reads every frame (not Compose state, to avoid 60 recompositions/s). */
class AvatarFeed {
    val driver = FaceDriver()
    @Volatile var clip: SpokenClip? = null
    @Volatile var visemes: VisemeTrack? = null
    @Volatile var clipPositionMs: () -> Long = { -1L }
    @Volatile var micLevel: () -> Float = { 0f }

    fun frame(t: Double): Pair<Map<String, Float>, HeadPose> {
        driver.listenLevel = micLevel()
        return driver.frame(t, clip, clipPositionMs(), visemes)
    }

    /** Loudness right now, for the 2D presence. */
    fun loudness(): Float {
        val c = clip ?: return 0f
        return c.loudnessAt(clipPositionMs())
    }
}

/**
 * The tutor's face: the real-time 3D model when the phone can run it and the model is on the
 * device, otherwise [TutorPresence] — a calm animated "voice presence" that reacts to the same
 * speech, so the call works identically on every phone.
 */
@Composable
fun TutorAvatar(
    feed: AvatarFeed,
    modelFile: File?,
    accent: Color,
    modifier: Modifier = Modifier,
    onModelFailed: () -> Unit = {},
    /** An owner-uploaded character to render as a 2D cutout instead of the 3D head, if set. */
    character: com.rork.pro.character.TutorCharacter? = null,
) {
    val context = LocalContext.current
    val can3d = remember { AvatarRenderer.deviceSupports3d(context) }
    var failed by remember(modelFile) { mutableStateOf(false) }
    var ready by remember(modelFile) { mutableStateOf(false) }

    if (character != null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            var bitmap by remember(character.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(character.imageUrl) {
                bitmap = com.rork.pro.character.loadCutout(context, character.imageUrl)
            }
            val loaded = bitmap
            if (loaded != null) {
                com.rork.pro.character.CutoutAvatar(feed, loaded, character.rig(), accent, Modifier.fillMaxSize())
            } else {
                TutorPresence(feed, accent, Modifier.fillMaxSize())
            }
        }
        return
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (!ready) TutorPresence(feed, accent, Modifier.fillMaxSize())
        if (can3d && modelFile != null && !failed) {
            var renderer by remember(modelFile) { mutableStateOf<AvatarRenderer?>(null) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        renderer = AvatarRenderer(
                            textureView = this,
                            modelFile = modelFile,
                            frameSource = { t -> feed.frame(t) },
                            onReady = { ready = true },
                            onFailed = { failed = true; ready = false; onModelFailed() },
                        ).also { it.start() }
                    }
                },
            )
            DisposableEffect(modelFile) {
                onDispose { renderer?.destroy() }
            }
        }
    }
}

/** 2D fallback: layered rings breathing with the tutor's voice and the student's microphone. */
@Composable
fun TutorPresence(feed: AvatarFeed, accent: Color, modifier: Modifier = Modifier) {
    var voice by remember { mutableFloatStateOf(0f) }
    var mic by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(feed) {
        while (true) {
            withFrameMillis {
                voice += (feed.loudness() - voice) * 0.35f
                mic += (feed.micLevel() - mic) * 0.25f
            }
        }
    }
    val spin by rememberInfiniteTransition(label = "presence").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Canvas(modifier) {
        val r = min(size.width, size.height) * 0.26f
        val c = Offset(size.width / 2f, size.height * 0.46f)
        val breathe = (sin(Math.toRadians(spin.toDouble() * 6)).toFloat() + 1f) * 0.5f
        for (i in 3 downTo 1) {
            val grow = 1f + i * 0.28f + voice * 0.22f * i + mic * 0.12f * i
            drawCircle(accent.copy(alpha = 0.06f + 0.04f * (3 - i) + voice * 0.08f), r * grow, c)
        }
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)), c, r * (1.04f + voice * 0.1f)),
            r * (1f + voice * 0.08f + breathe * 0.02f),
            c,
        )
        drawCircle(Color.White.copy(alpha = 0.18f + mic * 0.4f), r * (1.18f + mic * 0.2f), c, style = Stroke(width = r * 0.035f))
    }
}
