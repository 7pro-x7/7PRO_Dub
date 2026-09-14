package com.arena.arabicdub.pipeline

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arena.arabicdub.asr.WhisperEngine
import com.arena.arabicdub.domain.DubEvent
import com.arena.arabicdub.domain.DubSettings
import com.arena.arabicdub.domain.DubStage
import com.arena.arabicdub.domain.PipelineResult
import com.arena.arabicdub.domain.Segment
import com.arena.arabicdub.domain.Source
import com.arena.arabicdub.domain.SourceSpec
import com.arena.arabicdub.ffmpeg.Ffmpeg
import com.arena.arabicdub.models.ModelSpec
import com.arena.arabicdub.models.ModelStore
import com.arena.arabicdub.subs.AssWriter
import com.arena.arabicdub.tts.PiperEngine
import com.arena.arabicdub.translate.MyMemoryTranslator
import com.arena.arabicdub.util.OutputSaver
import com.arena.arabicdub.youtube.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * خط إنتاج الدبلجة الكامل:
 *
 *  1. PREPARE      : نسخ الفيديو من الجهاز أو جلبه من يوتيوب عبر Piped.
 *  2. EXTRACT_AUDIO: استخراج صوت 16kHz أحادي (FFmpeg).
 *  3. MODELS       : تنزيل نماذج Whisper/Piper في أول تشغيل.
 *  4. TRANSCRIBE   : تفهم الكلام الإنجليزي محليًا (Whisper) مع التوقيتات.
 *  5. TRANSLATE    : ترجمة إلى العربية (MyMemory المجاني، أو Whisper محليًا).
 *  6. TTS          : نطق كل جملة عربية (Piper) وملاءمتها للنافذة الزمنية.
 *  7. MIX          : دمج الصوت العربي مع الصوت الأصلي المخفف (FFmpeg).
 *  8. COMPOSE      : دمج المسار الصوتي مع الفيديو (+ حرق الترجمة العربية).
 */
class DubbingPipeline(private val context: Context) {

    private val ffmpeg = Ffmpeg()
    private val whisper = WhisperEngine(context)
    private val piper = PiperEngine(context)
    private val translator = MyMemoryTranslator()
    private val youTube = YouTubeSource()

    /** إيقاف العمل الجاري. */
    fun cancel() {
        ffmpeg.cancel()
    }

    suspend fun run(
        spec: SourceSpec,
        settings: DubSettings,
        onEvent: (DubEvent) -> Unit,
    ): PipelineResult = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "dub-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            // ---------------- 1) تجهيز المصدر ----------------
            onEvent(DubEvent.StageChanged(DubStage.PREPARE))
            val source = prepareSource(spec, work, onEvent)
            coroutineContext.ensureActive()

            // ---------------- 2) استخراج الصوت ----------------
            onEvent(DubEvent.StageChanged(DubStage.EXTRACT_AUDIO))
            onEvent(DubEvent.Log("استخراج الصوت من الملف..."))
            val audioSource = source.originalAudio ?: source.videoFile
            val audio16k = File(work, "audio16k.wav")
            if (!ffmpeg.extractAudio16k(audioSource, audio16k, { p -> onEvent(DubEvent.Progress(p)) })) {
                throw IOException("تعذر استخراج الصوت من الملف — تأكد أنه يحتوي على مسار صوتي")
            }
            val totalDuration = ffmpeg.probeDurationSeconds(audioSource)
            onEvent(DubEvent.Log("تم استخراج الصوت (المدة: ${fmtTime(totalDuration)})"))
            coroutineContext.ensureActive()

            // ---------------- 3) تجهيز النماذج ----------------
            onEvent(DubEvent.StageChanged(DubStage.MODELS))
            val whisperSpec = ModelStore.whisperModels[settings.whisperModel]
                ?: throw IllegalArgumentException("نموذج غير معروف: ${settings.whisperModel}")
            ensureModels(whisperSpec, settings, source, onEvent)
            coroutineContext.ensureActive()

            // ---------------- 4) تفهم الكلام ----------------
            onEvent(DubEvent.StageChanged(DubStage.TRANSCRIBE))
            val task = if (settings.useOnlineTranslate) "asr" else "translate"
            onEvent(DubEvent.Log("تحميل نموذج Whisper (${settings.whisperModel})..."))
            whisper.load(settings.whisperModel, task)
            onEvent(
                DubEvent.Log(
                    if (task == "asr") "جاري التعرف على الكلام الإنجليزي (قد يستغرق دقائق)..."
                    else "جاري الترجمة إلى العربية مباشرة عبر Whisper (قد يستغرق دقائق)...",
                ),
            )
            var segments = whisper.transcribe(audio16k, task)
            if (segments.isEmpty()) {
                throw IOException("لم يُعثر على كلام واضح في الصوت — تأكد أن الفيديو يتحدث بالإنجليزية")
            }
            onEvent(DubEvent.Log("تم العثور على ${segments.size} مقطعًا كلاميًا"))
            coroutineContext.ensureActive()

            // ---------------- 5) الترجمة ----------------
            onEvent(DubEvent.StageChanged(DubStage.TRANSLATE))
            if (settings.useOnlineTranslate) {
                onEvent(DubEvent.Log("الترجمة إلى العربية عبر MyMemory (خدمة مجانية)..."))
                val ar = translator.translate(segments.map { it.textEn }) { done, total ->
                    onEvent(DubEvent.Progress(done.toFloat() / total))
                }
                segments = segments.mapIndexed { i, s -> s.copy(textAr = ar[i]) }
            } else {
                onEvent(DubEvent.Log("Whisper قام بالترجمة إلى العربية مباشرة"))
            }
            onEvent(DubEvent.Log("اكتملت الترجمة (${segments.size} جملة)"))
            coroutineContext.ensureActive()

            // ---------------- 6) توليد الصوت العربي ----------------
            onEvent(DubEvent.StageChanged(DubStage.TTS))
            onEvent(DubEvent.Log("تحميل الصوت العربي (Piper ar_JO-kareem)..."))
            piper.load()
            val clips = segments.mapIndexed { i, seg ->
                val raw = File(work, "tts_$i.wav")
                if (!piper.speak(seg.textAr, settings.ttsSpeed, raw)) {
                    throw IOException("فشل توليد الصوت للجملة ${i + 1}")
                }
                val fitted = File(work, "tts_fit_$i.wav")
                fitClip(raw, fitted, seg)
                onEvent(DubEvent.Progress((i + 1).toFloat() / segments.size))
                onEvent(DubEvent.Log("الجملة ${i + 1} من ${segments.size}"))
                fitted
            }
            coroutineContext.ensureActive()

            // ---------------- 7) دمج المسارات ----------------
            onEvent(DubEvent.StageChanged(DubStage.MIX))
            onEvent(DubEvent.Log("دمج الصوت العربي مع الصوت الأصلي..."))
            val finalAudio = File(work, "final_audio.wav")
            buildAudioTrack(audioSource, clips, segments, settings, finalAudio, onEvent)
            coroutineContext.ensureActive()

            // ---------------- 8) العرض النهائي ----------------
            onEvent(DubEvent.StageChanged(DubStage.COMPOSE))
            val isVideo = source.hasVideo
            val output = File(work, "output.${if (isVideo) "mp4" else "mp3"}")
            if (isVideo) {
                onEvent(
                    DubEvent.Log(
                        if (settings.burnSubtitles) "إعادة ترميز الفيديو مع حرق الترجمة العربية..."
                        else "دمج الصوت الجديد مع الفيديو...",
                    ),
                )
                val ok = if (settings.burnSubtitles) {
                    burnSubtitles(source, finalAudio, segments, output, onEvent)
                } else {
                    muxWithVideo(source, finalAudio, output, onEvent)
                }
                if (!ok) throw IOException("فشل الدمج النهائي للفيديو")
            } else {
                onEvent(DubEvent.Log("إنشاء الملف الصوتي النهائي..."))
                if (!ffmpeg.run(
                        listOf(
                            "-y",
                            "-i", finalAudio.absolutePath,
                            "-c:a", "libmp3lame", "-b:a", "192k",
                            output.absolutePath,
                        ),
                        totalSeconds = ffmpeg.probeDurationSeconds(finalAudio),
                        onProgress = { p -> onEvent(DubEvent.Progress(p)) },
                    )
                ) {
                    throw IOException("فشل إنشاء الملف الصوتي")
                }
            }

            onEvent(DubEvent.Log("حفظ الملف النهائي في Movies/ArabicDub..."))
            val saved = OutputSaver.save(context, output, source.title, isVideo)
            onEvent(DubEvent.Progress(1f))
            PipelineResult(saved, isVideo, segments)
        } finally {
            whisper.release()
            piper.release()
            // حذف مساحة العمل (تشمل ملفات يوتيوب المؤقتة أيضًا)
            work.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------

    private suspend fun prepareSource(
        spec: SourceSpec,
        work: File,
        onEvent: (DubEvent) -> Unit,
    ): Source = when (spec) {
        is SourceSpec.LocalVideo -> {
            onEvent(DubEvent.Log("نسخ الملف المحدد إلى مساحة العمل..."))
            val (file, title) = copyLocalVideo(spec.uri, work)
            val hasVideo = ffmpeg.hasVideoTrack(file)
            onEvent(DubEvent.Log("الملف: $title ${if (hasVideo) "(فيديو)" else "(صوت فقط)"}"))
            Source(
                videoFile = file,
                originalAudio = null,
                title = title,
                hasVideo = hasVideo,
            )
        }

        is SourceSpec.YouTubeLink -> {
            val id = YouTubeSource.extractVideoId(spec.url)
                ?: throw IllegalArgumentException("هذا ليس رابط يوتيوب صالحًا")
            onEvent(DubEvent.Log("جلب بيانات الفيديو عبر Piped (واجهة مفتوحة المصدر)..."))
            val stream = youTube.resolve(id)
            onEvent(DubEvent.Log("عنوان الفيديو: ${stream.title}"))

            val videoFile = File(work, "yt_video.mp4")
            val audioFile = File(work, "yt_audio.m4a")

            if (stream.videoUrl != null) {
                onEvent(DubEvent.Log("تنزيل الفيديو (حتى 720p لتقليل الحجم)..."))
                youTube.download(stream.videoUrl, videoFile) { p ->
                    onEvent(DubEvent.Progress(p, indeterminate = p < 1f))
                }
            } else {
                onEvent(DubEvent.Log("لا يتوفر مسار فيديو mp4 — سيتم إخراج صوتي فقط"))
            }
            onEvent(DubEvent.Log("تنزيل الصوت الأصلي..."))
            youTube.download(stream.audioUrl, audioFile) { p ->
                onEvent(DubEvent.Progress(p, indeterminate = p < 1f))
            }

            if (stream.videoUrl != null) {
                Source(
                    videoFile = videoFile,
                    originalAudio = audioFile,
                    title = stream.title,
                    hasVideo = true,
                    videoIsMp4 = stream.videoIsMp4,
                    isDownloaded = true,
                )
            } else {
                Source(
                    videoFile = audioFile,
                    originalAudio = null,
                    title = stream.title,
                    hasVideo = false,
                    isDownloaded = true,
                )
            }
        }
    }

    private suspend fun ensureModels(
        whisperSpec: ModelSpec,
        settings: DubSettings,
        source: Source,
        onEvent: (DubEvent) -> Unit,
    ) {
        val dir = ModelStore.modelsDir(context)

        if (!ModelStore.isReady(whisperSpec, dir)) {
            onEvent(DubEvent.Log("تحميل نموذج ${whisperSpec.displayName} (~${whisperSpec.sizeMb} م.ب) — يُنصح بالاتصال بالواي فاي"))
            if (!ModelStore.ensureDownloaded(context, whisperSpec, { _, p ->
                    if (p >= 0f) onEvent(DubEvent.Progress(p))
                })) {
                throw IOException("فشل تحميل نموذج التعرف — تحقق من اتصال الإنترنت ثم أعد المحاولة")
            }
        }
        if (!ModelStore.isReady(ModelStore.piperAr, dir)) {
            onEvent(DubEvent.Log("تحميل الصوت العربي (~67 م.ب) — يُنصح بالاتصال بالواي فاي"))
            if (!ModelStore.ensureDownloaded(context, ModelStore.piperAr, { _, p ->
                    if (p >= 0f) onEvent(DubEvent.Progress(p))
                })) {
                throw IOException("فشل تحميل الصوت العربي — تحقق من اتصال الإنترنت ثم أعد المحاولة")
            }
        }
        if (settings.burnSubtitles && source.hasVideo && !ModelStore.isReady(ModelStore.arFont, dir)) {
            onEvent(DubEvent.Log("تحميل الخط العربي للترجمة (~6 م.ب)"))
            ModelStore.ensureDownloaded(context, ModelStore.arFont, { _, _ -> })
        }
        onEvent(DubEvent.Log("النماذج جاهزة"))
    }

    /** ملاءمة المقطع داخل نافذته الزمنية (تسريع حتى 2x). */
    private fun fitClip(raw: File, fitted: File, seg: Segment) {
        val dur = ffmpeg.probeDurationSeconds(raw)
        val window = (seg.end - seg.start).coerceAtLeast(0.3f)
        if (dur <= 0f || dur <= window + 0.05f) {
            if (!raw.renameTo(fitted)) raw.copyTo(fitted, overwrite = true)
            return
        }
        val rate = (dur / window).coerceIn(1.0f, 2.0f)
        if (!ffmpeg.fitToDuration(raw, fitted, rate)) {
            if (!raw.renameTo(fitted)) raw.copyTo(fitted, overwrite = true)
        }
    }

    /**
     * بناء المسار الصوتي النهائي:
     * [الصوت الأصلي × مستوى الخلفية] + كل المقاطع العربية في توقيتها
     * عبر فلتر filter_complex (adelay + amix).
     */
    private suspend fun buildAudioTrack(
        originalAudio: File,
        clips: List<File>,
        segments: List<Segment>,
        settings: DubSettings,
        out: File,
        onEvent: (DubEvent) -> Unit,
    ) {
        val n = clips.size
        val inputs = mutableListOf("-y", "-i", originalAudio.absolutePath)
        clips.forEach { inputs += listOf("-i", it.absolutePath) }

        val filter = StringBuilder()
        filter.append(
            "[0:a]aformat=sample_rates=22050:channel_layouts=mono," +
                "volume=" + String.format(Locale.US, "%.3f", settings.backgroundVolume) + "[bg];",
        )
        clips.forEachIndexed { i, _ ->
            val delayMs = (segments[i].start * 1000).toLong().coerceAtLeast(0)
            filter.append(
                "[" + (i + 1) + ":a]aformat=sample_rates=22050:channel_layouts=mono," +
                    "adelay=" + delayMs + ":all=1[d" + i + "];",
            )
        }
        val allInputs = "[bg]" + (0 until n).joinToString("") { "[d$it]" }
        filter.append(allInputs)
        filter.append("amix=inputs=").append(n + 1)
        filter.append(":normalize=0:dropout_transition=0[aout]")

        val ok = ffmpeg.run(
            inputs + listOf(
                "-filter_complex", filter.toString(),
                "-map", "[aout]",
                "-ar", "22050",
                "-c:a", "pcm_s16le",
                out.absolutePath,
            ),
            totalSeconds = ffmpeg.probeDurationSeconds(originalAudio),
            onProgress = { p -> onEvent(DubEvent.Progress(p)) },
        )
        if (!ok) throw IOException("فشل دمج المسارات الصوتية")
    }

    /**
     * دمج الفيديو مع المسار الجديد.
     * - إذا كان الفيديو mp4/h264: نسخ الصورة دون إعادة ترميز (copy) — أسرع.
     * - وإلا (webm/av1/...) نعيد ترميز الصورة إلى h264 حتى يعمل الحاوية mp4.
     */
    private suspend fun muxWithVideo(
        source: Source,
        finalAudio: File,
        out: File,
        onEvent: (DubEvent) -> Unit,
    ): Boolean {
        val videoCodec = if (source.videoIsMp4) listOf("-c:v", "copy")
        else listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "20")
        return ffmpeg.run(
            listOf(
                "-y",
                "-i", source.videoFile.absolutePath,
                "-i", finalAudio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0",
                *videoCodec.toTypedArray(),
                "-c:a", "aac", "-b:a", "192k",
                "-shortest",
                out.absolutePath,
            ),
            totalSeconds = ffmpeg.probeDurationSeconds(source.videoFile),
            onProgress = { p -> onEvent(DubEvent.Progress(p)) },
        )
    }

    /**
     * حرق الترجمة العربية داخل الفيديو (يتطلب إعادة ترميز libx264).
     */
    private suspend fun burnSubtitles(
        source: Source,
        finalAudio: File,
        segments: List<Segment>,
        out: File,
        onEvent: (DubEvent) -> Unit,
    ): Boolean {
        val assFile = File(source.videoFile.parentFile, "subs.ass")
        AssWriter.write(assFile, segments)
        val fontsDir = ModelStore.modelsDir(context).absolutePath
        val ok = ffmpeg.run(
            listOf(
                "-y",
                "-i", source.videoFile.absolutePath,
                "-i", finalAudio.absolutePath,
                "-vf", "subtitles=${escapeFilterPath(assFile.absolutePath)}:fontsdir=${escapeFilterPath(fontsDir)}",
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                "-c:a", "aac", "-b:a", "192k",
                "-shortest",
                out.absolutePath,
            ),
            totalSeconds = ffmpeg.probeDurationSeconds(source.videoFile),
            onProgress = { p -> onEvent(DubEvent.Progress(p)) },
        )
        assFile.delete()
        return ok
    }

    /** يحمي مسارًا من رموز فصل الفلاتر الخاصة بـ FFmpeg. */
    private fun escapeFilterPath(path: String): String =
        path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")

    /** نسخ ملف المحتوى المحلي إلى مساحة العمل وإرجاع (الملف، الاسم). */
    private suspend fun copyLocalVideo(uri: Uri, work: File): Pair<File, String> {
        var name = "video"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx) ?: name
            }
        }
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank { "mp4" }
        val safeExt = ext.takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } } ?: "mp4"
        val dest = File(work, "source.$safeExt")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("تعذر قراءة الملف — أعد اختياره")
        input.use { i -> dest.outputStream().use { i.copyTo(it) } }
        if (!dest.exists() || dest.length() == 0L) throw IOException("الملف فارغ")
        return dest to name
    }

    private fun fmtTime(seconds: Float): String {
        val s = seconds.toLong()
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}
