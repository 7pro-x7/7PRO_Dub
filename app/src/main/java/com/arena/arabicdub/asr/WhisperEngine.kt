package com.arena.arabicdub.asr

import android.content.Context
import com.arena.arabicdub.domain.Segment
import com.arena.arabicdub.models.ModelStore
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import java.io.File

/**
 * تفهم الكلام والترجمة محليًا على الجهاز عبر Whisper باستخدام sherpa-onnx.
 *
 * - task = "asr"       : النص الأصلي بالإنجليزية + توقيتات.
 * - task = "translate" : الترجمة العربية مباشرة من الصوت (Whisper يدعم الترجمة).
 */
class WhisperEngine(private val context: Context) {

    private var recognizer: OfflineRecognizer? = null
    private var loadedKey = ""
    private var loadedTask = ""

    /** تحميل النموذج المطلوب والمهمة (asr | translate). */
    fun load(modelKey: String, task: String) {
        if (recognizer != null && loadedKey == modelKey && loadedTask == task) return
        recognizer?.release()

        val dir = ModelStore.modelsDir(context)
        val (enc, dec, tokens) = ModelStore.whisperPaths(dir, modelKey)

        val whisper = OfflineWhisperModelConfig(
            encoder = enc.absolutePath,
            decoder = dec.absolutePath,
            language = "en",
            task = task,
            tailPaddings = -1,
            enableTokenTimestamps = true,
            enableSegmentTimestamps = true,
        )
        // ملف الرموز (tokens) مطلوب من OfflineModelConfig — كان مفقودًا وهو سبب فشل البناء.
        val modelConfig = OfflineModelConfig(
            whisper = whisper,
            tokens = tokens.absolutePath,
            numThreads = 4,
            debug = false,
        )
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(context.applicationContext.assets, config)
        loadedKey = modelKey
        loadedTask = task
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        loadedKey = ""
        loadedTask = ""
    }

    /**
     * معالجة ملف WAV (16kHz أحادي) وإرجاع مقاطع مؤقتة.
     * عند task="asr" يمتلئ textEn، وعند "translate" يمتلئ textAr.
     */
    fun transcribe(wav: File, task: String): List<Segment> {
        val rec = recognizer ?: throw IllegalStateException("نموذج Whisper غير محمّل")
        val wave = WaveReader.readWave(wav.absolutePath)
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(wave.samples, wave.sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            return buildSegments(
                tokens = result.tokens?.toList() ?: emptyList(),
                timestamps = result.timestamps?.toList() ?: emptyList(),
                task = task,
            )
        } finally {
            stream.release()
        }
    }

    /**
     * تجميع توكنات Whisper (مع توقيتاتها) في مقاطع محكية منطقية:
     * انقطاع زمني > 1.5ث أو جملة طويلة أو علامة نهاية جملة.
     */
    private fun buildSegments(tokens: List<String>, timestamps: List<Float>, task: String): List<Segment> {
        val segs = mutableListOf<Segment>()
        val buf = StringBuilder()
        var start = 0f
        var prevEnd = 0f
        var first = true

        fun flush(end: Float) {
            val t = buf.toString().trim()
            if (t.isNotEmpty()) {
                segs.add(
                    Segment(
                        start = start,
                        end = end.coerceAtLeast(start + 0.2f),
                        textEn = if (task == "asr") t else "",
                        textAr = if (task == "translate") t else "",
                    ),
                )
            }
            buf.clear()
        }

        val sentenceEnd = setOf('.', '!', '?', '…', ':')
        tokens.forEachIndexed { i, token ->
            val clean = token.trim()
            if (clean.isEmpty()) return@forEachIndexed
            val ts = timestamps.getOrNull(i) ?: start
            if (first) {
                start = ts
                first = false
            }
            if (buf.isNotEmpty()) {
                val gap = ts - prevEnd
                val longEnough = buf.length > 8
                if (gap > 1.5f || buf.length > 42 || (longEnough && clean.last() in sentenceEnd)) {
                    flush(prevEnd + 0.3f)
                    start = ts
                }
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(clean)
            prevEnd = ts
        }
        if (buf.isNotEmpty()) flush((timestamps.lastOrNull() ?: 0f) + 0.3f)

        // دمج المقاطع القصيرة جدًا في سابقتها
        val merged = mutableListOf<Segment>()
        segs.forEach { s ->
            val idx = merged.size - 1
            if (idx >= 0 && s.end - s.start < 0.35f) {
                val prev = merged[idx]
                merged[idx] = prev.copy(
                    end = s.end,
                    textEn = (prev.textEn + " " + s.textEn).trim(),
                    textAr = (prev.textAr + " " + s.textAr).trim(),
                )
            } else {
                merged.add(s)
            }
        }
        return merged
    }
}
