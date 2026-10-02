package com.rork.pro.tutor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What gets uploaded for one utterance. AAC at 32 kbps is ~8x smaller than raw WAV (a 6-second
 * sentence: ~24 KB instead of ~190 KB), which is what makes the call usable on slow mobile data.
 * If the Worker ever reports it cannot decode AAC, the app falls back to WAV for good.
 */
class AudioPayload(val base64: String, val format: String) {
    companion object {
        fun wav(pcm: ByteArray, sampleRate: Int = VoiceCapture.SAMPLE_RATE): AudioPayload =
            AudioPayload(Base64.encodeToString(wavBytes(normalized(pcm), sampleRate), Base64.NO_WRAP), "wav")

        /** AAC-LC in an ADTS stream (self-describing frames, no container needed); null if the phone has no encoder. */
        fun aac(pcm: ByteArray, sampleRate: Int = VoiceCapture.SAMPLE_RATE): AudioPayload? =
            runCatching { encodeAac(normalized(pcm), sampleRate) }.getOrNull()?.let { AudioPayload(Base64.encodeToString(it, Base64.NO_WRAP), "aac") }

        /**
         * Brings a quiet recording up to a healthy level (never more than ×6, never clipping) so speech
         * recognition hears every word. Near-silence is left alone: amplifying it would only raise the noise.
         */
        private fun normalized(pcm: ByteArray): ByteArray {
            var peak = 0
            var i = 0
            while (i + 1 < pcm.size) {
                val s = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
                val a = if (s < 0) -s else s
                if (a > peak) peak = a
                i += 2
            }
            if (peak < 300) return pcm
            val gain = minOf(0.85f * 32767f / peak, 6f)
            if (gain < 1.15f) return pcm
            val out = ByteArray(pcm.size)
            i = 0
            while (i + 1 < pcm.size) {
                val s = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
                val v = (s * gain).toInt().coerceIn(-32768, 32767)
                out[i] = (v and 0xFF).toByte()
                out[i + 1] = ((v shr 8) and 0xFF).toByte()
                i += 2
            }
            return out
        }

        fun wavBytes(pcm: ByteArray, sampleRate: Int): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }.array()
            return header + pcm
        }

        private fun encodeAac(pcm: ByteArray, sampleRate: Int): ByteArray {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val out = ByteArrayOutputStream(pcm.size / 6)
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var presentationUs = 0L
            val freqIndex = ADTS_RATES.indexOf(sampleRate).takeIf { it >= 0 } ?: 8
            try {
                while (true) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buf = codec.getInputBuffer(inIndex)!!
                            buf.clear()
                            val n = minOf(buf.remaining(), pcm.size - offset, 8192)
                            if (n <= 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buf.put(pcm, offset, n)
                                codec.queueInputBuffer(inIndex, 0, n, presentationUs, 0)
                                offset += n
                                presentationUs += n / 2 * 1_000_000L / sampleRate
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIndex >= 0) {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            val buf = codec.getOutputBuffer(outIndex)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val frame = ByteArray(info.size)
                            buf.get(frame)
                            out.write(adtsHeader(frame.size + 7, freqIndex))
                            out.write(frame)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return out.toByteArray()
        }

        private val ADTS_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

        private fun adtsHeader(length: Int, freqIndex: Int): ByteArray {
            val profile = 2 // AAC LC
            val channels = 1
            return byteArrayOf(
                0xFF.toByte(),
                0xF1.toByte(),
                (((profile - 1) shl 6) + (freqIndex shl 2) + (channels shr 2)).toByte(),
                (((channels and 3) shl 6) + (length shr 11)).toByte(),
                ((length and 0x7FF) shr 3).toByte(),
                (((length and 7) shl 5) + 0x1F).toByte(),
                0xFC.toByte(),
            )
        }
    }
}
