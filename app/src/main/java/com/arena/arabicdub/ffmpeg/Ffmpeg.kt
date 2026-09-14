package com.arena.arabicdub.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * غلاف بسيط حول FFmpeg (ffmpeg-kit 6.0) للعمليات التي يحتاجها التطبيق:
 * استخراج الصوت، تغيير السرعة، قراءة المدة، والتنفيذ مع التقدم.
 *
 * ملاحظة: واجهة ffmpeg-kit 6.0 مختلفة عن 5.x — لا يوجد ExecuteProgressCallback
 * ولا FFprobeKit.probe؛ التقدم يأتي عبر StatisticsCallback والمدة عبر
 * FFprobeKit.getMediaInformation().
 */
class Ffmpeg {

    private val currentSession = AtomicReference<FFmpegSession?>(null)

    /** إيقاف الأمر الجاري (إن وُجد). */
    fun cancel() {
        currentSession.getAndSet(null)?.let { runCatching { it.cancel() } }
    }

    /**
     * تنفيذ أمر FFmpeg وحجب الخيط حتى الانتهاء.
     *
     * @param totalSeconds إن كانت أكبر من صفر، يُقدَّر التقدم = (إحصاءات الوقت ÷ المدة الكلية)
     *                     ويستقبل [onProgress] القيم بين 0 و1. وإلا لا يُستدعى.
     * @param onProgress   يستقبل 0f..1f كلما تقدم التنفيذ.
     * @return هل نجح الأمر.
     */
    fun run(
        args: List<String>,
        totalSeconds: Float = 0f,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean {
        val command = args.joinToString(" ")
        val latch = CountDownLatch(1)
        val result = AtomicReference<ReturnCode?>(null)

        // تعبيرات object صريحة (لا نعتمد على SAM conversion لضمان التوافق)
        val statsCb = object : StatisticsCallback {
            override fun apply(stats: Statistics) {
                if (onProgress != null && totalSeconds > 0f) {
                    onProgress((stats.time / totalSeconds).toFloat().coerceIn(0f, 1f))
                }
            }
        }
        val completeCb = object : FFmpegSessionCompleteCallback {
            override fun apply(session: FFmpegSession) {
                result.set(session.returnCode)
                currentSession.set(null)
                latch.countDown()
            }
        }

        val session = FFmpegKit.executeAsync(command, completeCb, null, statsCb)
        if (session != null) currentSession.set(session)

        // مهلة طويلة: الفيديوهات الطويلة + إعادة الترميز قد يستغرقان وقتًا
        if (!latch.await(3, TimeUnit.HOURS)) {
            runCatching { session?.cancel() }
            return false
        }
        currentSession.set(null)

        val rc = result.get()
        return rc != null && ReturnCode.isSuccess(rc)
    }

    /** مدة الملف بالثواني (0f إن تعذرت القراءة). */
    fun probeDurationSeconds(file: File): Float {
        val mi = mediaInfo(file) ?: return 0f
        return mi.duration?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    }

    /** هل يوجد مسار فيديو في الملف. */
    fun hasVideoTrack(file: File): Boolean {
        val mi = mediaInfo(file) ?: return false
        return mi.streams?.any { it.type == "video" } ?: false
    }

    /** معلومات الملف عبر ffprobe (null عند الفشل). */
    private fun mediaInfo(file: File): MediaInformation? {
        val session =
            runCatching { FFprobeKit.getMediaInformation(file.absolutePath) }.getOrNull()
                ?: return null
        if (!ReturnCode.isSuccess(session.returnCode)) return null
        return runCatching { session.mediaInformation }.getOrNull()
    }

    /**
     * استخراج صوت أحادي 16kHz (الصيغة التي يطلبها Whisper).
     */
    fun extractAudio16k(
        input: File,
        output: File,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = run(
        listOf(
            "-y",
            "-i", input.absolutePath,
            "-vn", "-ac", "1", "-ar", "16000",
            "-c:a", "pcm_s16le",
            output.absolutePath,
        ),
        totalSeconds = 0f,
        onProgress = onProgress,
    )

    /**
     * تسريع/إبطاء مقطع الصوت بمعامل [rate] لوضعه داخل نافذة زمنية.
     */
    fun fitToDuration(input: File, output: File, rate: Float): Boolean = run(
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
