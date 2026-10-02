package com.rork.pro.tutor

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hands-free listening for the tutor call: records 16 kHz mono PCM and decides on its own when
 * the student has started and finished a sentence, so nobody has to hold a button.
 *
 * Voice-activity detection is a small adaptive energy gate — no model to download, so it runs
 * the same on a 1 GB phone as on a flagship:
 *  - the first ~300 ms calibrate the room's noise floor, which then keeps tracking slowly;
 *  - speech starts once the level stays well above that floor for [START_MS];
 *  - the sentence ends after [END_SILENCE_MS] of quiet (1.5 s for beginners, who pause to find the
 *    next word and must not be cut off mid-sentence), or at [MAX_UTTERANCE_MS].
 * Only the spoken part (plus a short lead-in) is sent, which keeps uploads and Whisper cost low.
 */
class VoiceCapture {

    sealed interface Result {
        /** A finished utterance as raw 16 kHz mono PCM; [AudioPayload] turns it into AAC or WAV. */
        class Speech(val pcm: ByteArray, val durationMs: Long) : Result
        /** The student never started speaking within [NO_SPEECH_TIMEOUT_MS]. */
        data object Silence : Result
        /** The microphone could not be opened (permission revoked, used by another app...). */
        data object MicUnavailable : Result
    }

    /** 0..1 live input level, for the "listening" ring on screen. */
    @Volatile var level: Float = 0f
        private set

    /** True from the moment speech is detected until the utterance ends. */
    @Volatile var hearingSpeech: Boolean = false
        private set

    /** Records one explicit, fixed-length voice reference for the user's selected cloned voice. */
    @SuppressLint("MissingPermission") // The character editor asks for RECORD_AUDIO before recording.
    suspend fun recordSample(durationMs: Long = 8_000L): ByteArray? = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext null
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME_SAMPLES * 2 * 4),
            )
        }.getOrNull() ?: return@withContext null
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext null
        }
        val targetSamples = (SAMPLE_RATE * durationMs / 1000).toInt()
        val frame = ShortArray(FRAME_SAMPLES)
        val sample = ByteArrayOutputStream(targetSamples * 2)
        try {
            record.startRecording()
            var recordedSamples = 0
            while (recordedSamples < targetSamples && currentCoroutineContext().isActive) {
                val count = record.read(frame, 0, minOf(frame.size, targetSamples - recordedSamples))
                if (count <= 0) break
                writePcm(sample, frame, count)
                recordedSamples += count
            }
            if (recordedSamples < SAMPLE_RATE * 3) null else sample.toByteArray()
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    @SuppressLint("MissingPermission") // The screen asks for RECORD_AUDIO before starting a call.
    suspend fun listenOnce(endSilenceMs: Int = END_SILENCE_MS): Result = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext Result.MicUnavailable
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME_SAMPLES * 2 * 4),
            )
        } catch (e: Exception) {
            return@withContext Result.MicUnavailable
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext Result.MicUnavailable
        }
        // Best-effort cleanup of the phone's own speaker bleed and room noise, where supported.
        val aec = runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null }.getOrNull()
        val ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null }.getOrNull()

        val frame = ShortArray(FRAME_SAMPLES)
        val preRoll = ArrayDeque<ShortArray>()           // audio just before speech was confirmed
        val speech = ByteArrayOutputStream()
        var noiseFloor = 0.0
        var calibrated = 0
        var loudMs = 0
        var quietMs = 0
        var speechMs = 0L
        var waitedMs = 0L
        var started = false

        try {
            record.startRecording()
            hearingSpeech = false
            while (currentCoroutineContext().isActive) {
                val read = record.read(frame, 0, FRAME_SAMPLES)
                if (read <= 0) continue
                val rms = rms(frame, read)

                if (calibrated < CALIBRATION_FRAMES) {
                    noiseFloor = if (calibrated == 0) rms else (noiseFloor * calibrated + rms) / (calibrated + 1)
                    calibrated++
                    level = 0f
                    continue
                }

                val threshold = max(noiseFloor * START_RATIO, ABS_MIN_RMS)
                level = ((rms - noiseFloor) / (threshold * 3)).toFloat().coerceIn(0f, 1f)

                if (!started) {
                    // Follow slow changes in background noise (a fan switching on, a TV in the next room).
                    if (rms < threshold) noiseFloor = noiseFloor * 0.97 + rms * 0.03
                    preRoll.addLast(frame.copyOf(read))
                    while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    loudMs = if (rms > threshold) loudMs + FRAME_MS else 0
                    waitedMs += FRAME_MS
                    if (loudMs >= START_MS) {
                        started = true
                        hearingSpeech = true
                        preRoll.forEach { writePcm(speech, it, it.size) }
                        speechMs = preRoll.size * FRAME_MS.toLong()
                        preRoll.clear()
                    } else if (waitedMs >= NO_SPEECH_TIMEOUT_MS) {
                        return@withContext Result.Silence
                    }
                } else {
                    writePcm(speech, frame, read)
                    speechMs += FRAME_MS
                    val stillTalking = rms > max(noiseFloor * END_RATIO, ABS_MIN_RMS * 0.7)
                    quietMs = if (stillTalking) 0 else quietMs + FRAME_MS
                    if (quietMs >= endSilenceMs || speechMs >= MAX_UTTERANCE_MS) break
                }
            }
        } finally {
            hearingSpeech = false
            level = 0f
            runCatching { record.stop() }
            record.release()
            aec?.release()
            ns?.release()
        }

        if (!started || speechMs < MIN_UTTERANCE_MS) return@withContext Result.Silence
        Result.Speech(speech.toByteArray(), speechMs)
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) {
            val v = buf[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / n)
    }

    private fun writePcm(out: ByteArrayOutputStream, buf: ShortArray, n: Int) {
        val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) bb.putShort(buf[i])
        out.write(bb.array())
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_MS = 20
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        private const val CALIBRATION_FRAMES = 15            // 300 ms
        private const val PRE_ROLL_FRAMES = 15               // keep 300 ms before speech onset
        private const val START_MS = 140
        /** Quiet that ends a sentence for intermediate and advanced students. */
        const val END_SILENCE_MS = 1100
        /** Beginners think between words: they get a longer pause before the recording is closed. */
        const val END_SILENCE_BEGINNER_MS = 1500

        /** How long a pause closes the recording for the given level ("beginner", "intermediate", "advanced"). */
        fun endSilenceFor(level: String): Int = if (level == "beginner") END_SILENCE_BEGINNER_MS else END_SILENCE_MS

        private const val MIN_UTTERANCE_MS = 450L
        const val MAX_UTTERANCE_MS = 14_000L
        private const val NO_SPEECH_TIMEOUT_MS = 12_000L
        private const val START_RATIO = 3.2
        private const val END_RATIO = 2.0
        private const val ABS_MIN_RMS = 350.0
    }
}
