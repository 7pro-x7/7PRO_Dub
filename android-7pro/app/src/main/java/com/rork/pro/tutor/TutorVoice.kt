package com.rork.pro.tutor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One line of the tutor's speech, decoded to PCM so the mouth can follow the real sound.
 *
 * [envelope] holds the loudness of every [ENVELOPE_MS] slice, normalised to 0..1 against the
 * clip's own loud parts — that is what drives the jaw, frame-accurate against [TutorVoice.positionMs].
 */
class SpokenClip(val pcm: ShortArray, val sampleRate: Int, val envelope: FloatArray, val text: String) {
    val durationMs: Long get() = pcm.size * 1000L / sampleRate

    fun loudnessAt(ms: Long): Float {
        if (ms < 0 || envelope.isEmpty()) return 0f
        val i = (ms / ENVELOPE_MS).toInt()
        if (i >= envelope.size) return 0f
        // Linear blend between slices keeps the jaw from stepping visibly at 50 fps.
        val frac = (ms % ENVELOPE_MS).toFloat() / ENVELOPE_MS
        val next = if (i + 1 < envelope.size) envelope[i + 1] else 0f
        return envelope[i] * (1 - frac) + next * frac
    }

    companion object {
        const val ENVELOPE_MS = 20
    }
}

/**
 * Decodes the Worker's MP3 with the phone's own MediaCodec (present on every Android device)
 * and plays it through an AudioTrack, whose playback head gives the exact position for lip-sync.
 */
class TutorVoice(private val context: Context) {

    @Volatile var positionMs: Long = -1
        private set

    @Volatile var current: SpokenClip? = null
        private set

    @Volatile private var track: AudioTrack? = null

    suspend fun decode(mp3Base64: String, text: String): SpokenClip = withContext(Dispatchers.IO) {
        val bytes = Base64.decode(mp3Base64, Base64.DEFAULT)
        val file = File.createTempFile("tutor-", ".mp3", context.cacheDir)
        try {
            file.writeBytes(bytes)
            decodeFile(file, text)
        } finally {
            file.delete()
        }
    }

    /** Decodes PCM returned by the self-hosted Piper + OpenVoice clone endpoint. */
    suspend fun decodePcm(base64: String, sampleRate: Int, text: String): SpokenClip = withContext(Dispatchers.IO) {
        require(sampleRate in 8_000..48_000) { "Invalid PCM sample rate" }
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        require(bytes.isNotEmpty() && bytes.size % 2 == 0) { "Invalid PCM audio" }
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val pcm = ShortArray(buffer.remaining())
        buffer.get(pcm)
        SpokenClip(pcm, sampleRate, envelope(pcm, sampleRate), text)
    }

    private fun decodeFile(file: File, text: String): SpokenClip {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("no audio track")
        extractor.selectTrack(trackIndex)
        val inFormat = extractor.getTrackFormat(trackIndex)
        val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = ShortArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = shorts.remaining()
                        val tmp = ShortArray(n)
                        shorts.get(tmp)
                        // Down-mix to mono: one voice, one channel, half the memory.
                        if (channels <= 1) out.add(tmp, n) else {
                            val frames = n / channels
                            val mono = ShortArray(frames)
                            for (i in 0 until frames) {
                                var s = 0
                                for (c in 0 until channels) s += tmp[i * channels + c]
                                mono[i] = (s / channels).toShort()
                            }
                            out.add(mono, frames)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        val pcm = out.build()
        return SpokenClip(pcm, sampleRate, envelope(pcm, sampleRate), text)
    }

    private fun envelope(pcm: ShortArray, rate: Int): FloatArray {
        val win = max(1, rate * SpokenClip.ENVELOPE_MS / 1000)
        val count = pcm.size / win
        val raw = FloatArray(count)
        for (w in 0 until count) {
            var sum = 0.0
            val start = w * win
            for (i in start until start + win) {
                val v = pcm[i].toDouble()
                sum += v * v
            }
            raw[w] = sqrt(sum / win).toFloat()
        }
        // Normalise to the clip's own 92nd percentile so quiet and loud TTS voices both animate fully.
        val ref = raw.sortedArray().let { if (it.isEmpty()) 1f else it[min(it.lastIndex, (it.size * 0.92f).toInt())] }.coerceAtLeast(1f)
        val gate = ref * 0.12f
        return FloatArray(count) { i -> if (raw[i] < gate) 0f else ((raw[i] - gate) / (ref - gate)).coerceIn(0f, 1f) }
    }

    /** Plays [clip] to the end (or until cancelled), updating [positionMs] as it goes. */
    suspend fun play(clip: SpokenClip) = withContext(Dispatchers.IO) {
        stop()
        val minBuf = AudioTrack.getMinBufferSize(clip.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(clip.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBuf, clip.sampleRate / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        current = clip
        positionMs = 0
        try {
            t.play()
            var written = 0
            val chunk = clip.sampleRate / 20 // 50 ms per write
            while (written < clip.pcm.size) {
                currentCoroutineContext().ensureActive()
                val n = min(chunk, clip.pcm.size - written)
                val w = t.write(clip.pcm, written, n)
                if (w < 0) break
                written += w
                positionMs = t.playbackHeadPosition.toLong() * 1000 / clip.sampleRate
            }
            // Let the tail of the buffer actually reach the speaker.
            while (currentCoroutineContext().isActive) {
                val head = t.playbackHeadPosition.toLong()
                positionMs = head * 1000 / clip.sampleRate
                if (head >= written - clip.sampleRate / 100) break
                delay(16)
            }
        } finally {
            positionMs = -1
            current = null
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
        }
    }

    fun stop() {
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    // ------------------------------------------------------------------ on-phone voice

    @Volatile private var tts: TextToSpeech? = null
    private var ttsReady: Boolean? = null

    private suspend fun engine(): TextToSpeech? {
        ttsReady?.let { return if (it) tts else null }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var created: TextToSpeech? = null
                created = TextToSpeech(context.applicationContext) { status ->
                    val ok = status == TextToSpeech.SUCCESS
                    ttsReady = ok
                    tts = if (ok) created else null
                    if (cont.isActive) cont.resume(if (ok) created else null)
                }
            }
        }
    }

    /**
     * Voices a line with the phone's own text-to-speech — used for Arabic replies (Workers AI has
     * no Arabic voice) and whenever the server voice failed. The audio is rendered to a WAV file
     * first, so it plays through the same pipeline and the tutor's lips still follow it.
     * Returns null if this phone has no voice for the language.
     */
    suspend fun synthesizeLocally(text: String, lang: String, female: Boolean): SpokenClip? {
        val engine = engine() ?: return null
        val locale = if (lang == "ar") Locale("ar", "EG") else Locale.US
        val file = File.createTempFile("tutor-local-", ".wav", context.cacheDir)
        try {
            val ready = withContext(Dispatchers.Main) {
                var res = engine.setLanguage(locale)
                if (lang == "ar" && (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)) {
                    res = engine.setLanguage(Locale("ar"))
                }
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) return@withContext false
                // Android has no official "gender" field on a Voice, but installed voices are usually named
                // for it ("...#female_1-local", "en-us-x-sfg-network", etc). Pick one that actually matches
                // instead of leaving the engine on whatever its own default voice happens to be — that default
                // is a fixed voice on many devices and won't change with pitch alone.
                pickGenderVoice(engine, female)?.let { engine.voice = it }
                engine.setPitch(if (female) 1.12f else 0.9f)
                engine.setSpeechRate(0.95f)
                true
            }
            if (!ready) return null
            val id = UUID.randomUUID().toString()
            val done = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(true) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(false) }
                    override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done.complete(false) }
                })
                if (engine.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) done.complete(false)
            }
            val ok = withTimeoutOrNull(20_000) { done.await() } ?: false
            if (!ok || file.length() < 100) return null
            return withContext(Dispatchers.IO) { readWav(file, text) ?: runCatching { decodeFile(file, text) }.getOrNull() }
        } finally {
            file.delete()
        }
    }

    /**
     * Best-effort match for a voice actually named for [wantFemale]'s gender, among the voices this engine
     * currently has installed for whatever language is already selected. Returns null (keep the engine's own
     * default voice, still pitch-adjusted) when nothing on this device is named clearly enough to trust.
     */
    private fun pickGenderVoice(engine: TextToSpeech, wantFemale: Boolean): Voice? {
        val current = engine.voice ?: return null
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        val want = if (wantFemale) "female" else "male"
        val sameLanguage = voices.filter {
            it.locale.language == current.locale.language &&
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        // Offline voices first: this call must not fail (or add latency) just because the network is weak.
        return sameLanguage
            .sortedBy { it.isNetworkConnectionRequired }
            .firstOrNull { genderHint(it.name) == want }
    }

    /** "female"/"woman" checked before "male"/"man" so a name like "...female..." is never misread as male. */
    private fun genderHint(voiceName: String): String? {
        val n = voiceName.lowercase()
        return when {
            "female" in n || "woman" in n -> "female"
            "male" in n || "man" in n -> "male"
            else -> null
        }
    }

    /** Minimal PCM-16 WAV reader (what Android TTS writes). */
    private fun readWav(file: File, text: String): SpokenClip? = runCatching {
        val bytes = file.readBytes()
        val bb = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var pos = 12
        var rate = 0; var channels = 1; var bits = 16
        var dataStart = -1; var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataStart = pos + 8
                dataLen = min(len, bytes.size - dataStart)
                break
            }
            pos += 8 + len + (len and 1)
        }
        if (dataStart < 0 || rate <= 0 || bits != 16) return null
        val frames = dataLen / 2 / max(1, channels)
        val pcm = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += bb.getShort(dataStart + (i * channels + c) * 2).toInt()
            pcm[i] = (sum / max(1, channels)).toShort()
        }
        SpokenClip(pcm, rate, envelope(pcm, rate), text)
    }.getOrNull()

    fun release() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ttsReady = null
    }

    private class ShortArrayBuilder {
        private var data = ShortArray(48_000)
        private var size = 0
        fun add(src: ShortArray, n: Int) {
            if (size + n > data.size) data = data.copyOf(max(data.size * 2, size + n))
            System.arraycopy(src, 0, data, size, n)
            size += n
        }
        fun build(): ShortArray = data.copyOf(size)
    }
}
