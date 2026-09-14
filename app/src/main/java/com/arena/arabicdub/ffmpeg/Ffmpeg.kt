package com.arena.arabicdub.ffmpeg

import com.arthenica.ffmpegkit.ExecuteProgressCallback
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionCompleteCallback
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * غلاف بسيط حول FFmpeg (ffmpeg-kit) للعمليات التي يحتاجها التطبيق:
 * استخراج الصوت، تغيير السرعة، قراءة المدة، والدمج.
 */
class Ffmpeg {

    private val currentSession = AtomicReference<FFmpegSession?>(null)

    /** إيقاف الأمر الجاري (إن وُجد). */
    fun cancel() {
        currentSession.getAndSet(null)?.let { runCatching { it.cancel() } }
    }

    /**
     * تنفيذ أمر FFmpeg.
     *
     * @param args       arguments بدون كلمة "ffmpeg".
     * @param onProgress يستقبل 0f..1f كلما تقدم التنفيذ (قد لا يُستدعى لبعض الأوامر السريعة).
     * @return هل نجح الأمر.
     */
    fun run(args: List<String>, onProgress: ((Float) -> Unit)? = null): Boolean {
        val command = args.joinToString(" ")
        val latch = CountDownLatch(1)
        val result = AtomicReference<ReturnCode?>(null)

        val progressCb: ExecuteProgressCallback = { session ->
            currentSession.set(session)
            val total = session.totalDuration ?: 0f
            val pos = session.time ?: 0f
            if (total > 0f) onProgress?.invoke((pos / total).coerceIn(0f, 1f))
        }
        val completeCb: SessionCompleteCallback = { session ->
            result.set(session?.returnCode)
            currentSession.set(null)
            latch.countDown()
        }

        FFmpegKit.executeAsync(command, progressCb, null, completeCb)
        // مهلة طويلة: الفيديوهات الطويلة + الترميز قد يستغرقان وقتًا
        latch.await(3, TimeUnit.HOURS)

        currentSession.set(null)
        val rc = result.get()
        return rc != null && rc.isSuccess
    }

    /** مدة الملف بالثواني (0f إن تعذرت القراءة). */
    fun probeDurationSeconds(file: File): Float {
        val rc = FFprobeKit.probe(file.absolutePath) ?: return 0f
        if (!rc.isSuccess) return 0f
        val fmt = rc.mediaInformation?.format ?: return 0f
        val ms = fmt.duration ?: 0f
        return (ms / 1000f).coerceAtLeast(0f)
    }

    /** هل يوجد مسار فيديو في الملف. */
    fun hasVideoTrack(file: File): Boolean {
        val rc = FFprobeKit.probe(file.absolutePath) ?: return false
        if (!rc.isSuccess) return false
        val streams = rc.mediaInformation?.streams ?: return false
        return streams.any { it.codecType == "video" }
    }

    /**
     * استخراج صوت أحادي 16kHz (الصيغة التي يطلبها Whisper).
     */
    fun extractAudio16k(input: File, output: File, onProgress: ((Float) -> Unit)? = null): Boolean =
        run(
            listOf(
                "-y",
                "-i", input.absolutePath,
                "-vn", "-ac", "1", "-ar", "16000",
                "-c:a", "pcm_s16le",
                output.absolutePath,
            ),
            onProgress,
        )

    /**
     * تسريع/إبطاء مقطع الصوت بمعامل [rate] لوضعه داخل نافذة زمنية.
     */
    fun fitToDuration(input: File, output: File, rate: Float): Boolean =
        run(
            listOf(
                "-y",
                "-i", input.absolutePath,
                "-af", tempoFilter(rate),
                "-ar", "22050",
                output.absolutePath,
            ),
        )

    /** يبني سلسلة atempo (نطاقها 0.5..2.0) لأي معامل. */
    private fun tempoFilter(rate: Float): String {
        var r = rate.coerceIn(0.25f, 4.0f)
        val parts = mutableListOf<String>()
        while (r > 2.0f) {
            parts.add("atempo=2.0"); r /= 2.0f
        }
        while (r < 0.5f) {
            parts.add("atempo=0.5"); r /= 0.5f
        }
        parts.add(String.format(Locale.US, "atempo=%.3f", r))
        return parts.joinToString(",")
    }
}
