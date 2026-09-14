package com.arena.arabicdub.models

import android.content.Context
import com.arena.arabicdub.util.TarBzip2
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** ملف واحد قابل للتنزيل. */
data class ModelFile(val url: String, val destName: String)

/**
 * مواصفة حزمة نماذج.
 *
 * @param files       ملفات تُنزل مباشرة.
 * @param archiveUrl  إن وُجد: حزمة tar.bz2 تُنزل ثم تُفكّ على الجهاز (صوت Piper).
 * @param archiveExtractDir مجلد فك الحزمة داخل models/.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val files: List<ModelFile>,
    val sizeMb: Int,
    val archiveUrl: String = "",
    val archiveExtractDir: String = "",
)

/**
 * إدارة تنزيل النماذج (Whisper + Piper + الخط العربي) إلى مخزن التطبيق.
 *
 * كل النماذج مجانية ومفتوحة المصدر، وتُنزَّل مرة واحدة ثم تعمل دون إنترنت.
 */
object ModelStore {

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    // ------------------------- Whisper (تفهم كلام + ترجمة) -------------------------
    // المستودعات: csukuangfj/sherpa-onnx-whisper-* على Hugging Face (نسخ int8 المصغرة).

    private fun whisper(key: String, encoder: String, decoder: String, sizeMb: Int): ModelSpec =
        ModelSpec(
            id = "whisper-$key",
            displayName = "Whisper $key (int8)",
            files = listOf(
                ModelFile(
                    "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$key/resolve/main/$encoder",
                    "whisper-$key-encoder.onnx",
                ),
                ModelFile(
                    "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$key/resolve/main/$decoder",
                    "whisper-$key-decoder.onnx",
                ),
            ),
            sizeMb = sizeMb,
        )

    val whisperModels: Map<String, ModelSpec> = linkedMapOf(
        "tiny" to whisper("tiny", "tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", 103),
        "base" to whisper("base", "base-encoder.int8.onnx", "base-decoder.int8.onnx", 160),
        "small" to whisper("small", "small-encoder.int8.onnx", "small-decoder.int8.onnx", 375),
        "medium" to whisper("medium", "medium-encoder.int8.onnx", "medium-decoder.int8.onnx", 945),
    )

    // ------------------------- الصوت العربي (Piper ar_JO-kareem) -------------------------
    // حزمة رسمية من sherpa-onnx تحتوي: النموذج + tokens + بيانات espeak-ng.
    val piperAr = ModelSpec(
        id = "piper-ar",
        displayName = "الصوت العربي (Piper ar_JO-kareem)",
        files = emptyList(),
        sizeMb = 67,
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium.tar.bz2",
        archiveExtractDir = "piper-ar",
    )

    // ------------------------- خط عربي حارق للترجمة -------------------------
    val arFont = ModelSpec(
        id = "ar-font",
        displayName = "خط عربي (Noto Naskh Arabic)",
        files = listOf(
            ModelFile(
                "https://github.com/google/fonts/raw/main/ofl/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf",
                "NotoNaskhArabic.ttf",
            ),
        ),
        sizeMb = 6,
    )

    // ------------------------- مسارات الملفات -------------------------

    fun whisperPaths(dir: File, key: String): Pair<File, File> =
        Pair(File(dir, "whisper-$key-encoder.onnx"), File(dir, "whisper-$key-decoder.onnx"))

    fun piperPaths(dir: File): Triple<File, File, File> =
        Triple(
            File(dir, "piper-ar/ar_JO-kareem-medium.onnx"),
            File(dir, "piper-ar/tokens.txt"),
            File(dir, "piper-ar/espeak-ng-data"),
        )

    fun fontFile(dir: File): File = File(dir, "NotoNaskhArabic.ttf")

    /** هل حزمة [spec] جاهزة للاستخدام. */
    fun isReady(spec: ModelSpec, dir: File): Boolean {
        if (spec.archiveUrl.isNotEmpty()) {
            val (model, tokens, data) = piperPaths(dir)
            return model.exists() && tokens.exists() && data.isDirectory
        }
        return spec.files.all { f ->
            val f2 = File(dir, f.destName)
            f2.exists() && f2.length() > 1024
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    /**
     * التأكد من توفر حزمة النماذج (تنزيل ما ينقص منها).
     *
     * @param onProgress (معرف الحزمة، نسبة 0..1 أو -1 عند الخطأ)
     * @return true عند اكتمال الجاهزية.
     */
    fun ensureDownloaded(context: Context, spec: ModelSpec, onProgress: (String, Float) -> Unit): Boolean {
        val dir = modelsDir(context)
        return try {
            if (spec.archiveUrl.isNotEmpty()) {
                if (isReady(spec, dir)) return true
                val tar = File(dir, "${spec.id}.tar.bz2.part")
                downloadFile(spec.archiveUrl, tar) { p -> onProgress(spec.id, p) }
                TarBzip2.extractTo(tar, File(dir, spec.archiveExtractDir))
                tar.delete()
                return isReady(spec, dir)
            }
            for (f in spec.files) {
                val dest = File(dir, f.destName)
                if (dest.exists() && dest.length() > 1024) continue
                val part = File(dir, f.destName + ".part")
                downloadFile(f.url, part) { p -> onProgress(spec.id, p) }
                if (part.exists() && !part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
            }
            isReady(spec, dir)
        } catch (e: Exception) {
            onProgress(spec.id, -1f)
            false
        }
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("فشل التنزيل: HTTP ${resp.code} ($url)")
            val body = resp.body ?: throw IOException("استجابة فارغة")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastNotify = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 250L) {
                            lastNotify = now
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        }
    }
}
