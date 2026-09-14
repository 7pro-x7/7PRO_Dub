package com.arena.arabicdub.tts

import android.content.Context
import com.arena.arabicdub.models.ModelStore
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * توليد كلام عربي محليًا على الجهاز عبر Piper (صوت ar_JO-kareem، 22050Hz)
 * باستخدام sherpa-onnx — مجاني ومفتوح المصدر بالكامل.
 */
class PiperEngine(private val context: Context) {

    private var tts: OfflineTts? = null

    val isLoaded: Boolean
        get() = tts != null

    fun load() {
        if (tts != null) return
        val dir = ModelStore.modelsDir(context)
        val (model, tokens, dataDir) = ModelStore.piperPaths(dir)

        val vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath,
            tokens = tokens.absolutePath,
            dataDir = dataDir.absolutePath,
        )
        val modelConfig = OfflineTtsModelConfig(vits = vits, numThreads = 4, debug = false)
        val config = OfflineTtsConfig(
            model = modelConfig,
            maxNumSentences = 1,
            silenceScale = 0.2f,
        )
        tts = OfflineTts(context.applicationContext.assets, config)
    }

    fun release() {
        tts?.release()
        tts = null
    }

    /**
     * توليد ملف WAV لنص [text] العربي.
     * @param speed 1.0 عادي، أكبر = أسرع.
     * @return true عند النجاح.
     */
    fun speak(text: String, speed: Float, out: File): Boolean {
        val engine = tts ?: throw IllegalStateException("الصوت العربي غير محمّل")
        val genConfig = GenerationConfig(sid = 0, speed = speed, silenceScale = 0.2f)
        // ملاحظة: لا توجد دالة generateWithConfig بدون callback في sherpa-onnx —
        // الاسم الصحيح هو generateWithConfigAndCallback (مع callback يُرجع 1 للمتابعة).
        val audio: GeneratedAudio = engine.generateWithConfigAndCallback(
            text = text,
            config = genConfig,
            callback = { _ -> 1 },
        )
        return audio.save(filename = out.absolutePath)
    }
}
