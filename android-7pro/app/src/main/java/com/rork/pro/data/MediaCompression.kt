package com.rork.pro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private const val TAG = "MediaCompression"

/**
 * Downscales and re-encodes a picked photo before it ever leaves the device. Most phone
 * cameras shoot well past what a course thumbnail or a question image needs on screen, so this
 * alone is most of the saving — a photo at 4000px and 8MB commonly comes back under 300KB at
 * 1600px, with no visible loss at the sizes the app actually displays images.
 */
object ImageCompressor {
    /** Long side cap. Generous for anything the app shows full-screen, wasteful past it. */
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 82

    fun compress(bytes: ByteArray): ByteArray? = runCatching {
        // Read only the size first, then decode already shrunk (inSampleSize). Decoding a 48–108MP
        // photo at full size needs 200–400MB of memory and can take the whole app down with an
        // OutOfMemoryError on ordinary phones, before the downscale below ever runs.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // Camera photos are stored sideways with an EXIF "rotate me" flag; decoding drops the flag,
        // so apply it here or every portrait photo would come back rotated.
        val bitmap = rotateByExif(decoded, bytes)
        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== bitmap) scaled.recycle()
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        out.toByteArray()
    }.onFailure { Log.w(TAG, "Image compression failed, uploading original", it) }.getOrNull()

    private fun rotateByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return runCatching { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) }
            .getOrDefault(bitmap)
    }
}

/**
 * Re-encodes a video's picture track at a capped resolution and bitrate, straight through
 * Android's own hardware codec — no bundled library, nothing downloaded. The audio track is
 * copied across untouched (no re-encoding), which keeps this to the well-trodden half of the
 * MediaCodec API and avoids a second, riskier audio pipeline.
 *
 * This is the most delicate piece of code in the app: encoder behaviour (supported color
 * formats, bitrate ceilings, orientation handling) varies across manufacturers in ways that
 * are hard to fully verify without a bench of real devices. It is written defensively — any
 * failure returns null rather than a half-written file — but it deserves real-device testing
 * across a few Android versions before shipping, not just a read-through.
 */
object VideoCompressor {
    private const val MAX_SHORT_SIDE = 720
    private const val VIDEO_BITRATE = 2_000_000
    private const val TIMEOUT_US = 10_000L

    fun compress(inputPath: String, outputFile: File): File? = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        var videoTrack = -1
        var audioTrack = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            when {
                mime.startsWith("video/") && videoTrack < 0 -> { videoTrack = i; videoFormat = format }
                mime.startsWith("audio/") && audioTrack < 0 -> { audioTrack = i; audioFormat = format }
            }
        }
        if (videoTrack < 0 || videoFormat == null) { extractor.release(); return null }

        val srcWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val srcHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val shortSide = minOf(srcWidth, srcHeight)
        if (shortSide <= MAX_SHORT_SIDE) { extractor.release(); return null } // Already small; not worth the re-encode risk.
        val scale = MAX_SHORT_SIDE.toFloat() / shortSide
        // Encoders require even dimensions.
        val dstWidth = ((srcWidth * scale).roundToInt() / 2) * 2
        val dstHeight = ((srcHeight * scale).roundToInt() / 2) * 2

        val decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME)!!)
        val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, dstWidth, dstHeight).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()
        decoder.configure(videoFormat, inputSurface, null, 0)
        decoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1
        var muxerStarted = false

        extractor.selectTrack(videoTrack)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        // Surface-fed encoders never see BUFFER_FLAG_END_OF_STREAM on their own — that flag
        // only travels through the decoder's *output* here, since frames reach the encoder via
        // the shared Surface rather than through queueInputBuffer. Without an explicit
        // signalEndOfInputStream() once the decoder has drained, the encoder has no way to know
        // input has stopped, so it never emits its own EOS and the loop below spins forever
        // (previously: an unbounded hang on every video whose short side exceeds 720px, not a
        // handled failure — the very thing this function's runCatching was supposed to prevent).
        var encoderEndOfStreamSignaled = false
        // Belt-and-suspenders against this exact class of bug on some other device/codec quirk
        // we haven't seen: past this many empty passes (each capped at TIMEOUT_US), give up and
        // fail cleanly instead of hanging the upload forever. ~10s of nothing arriving from
        // either codec means this file is never going to finish compressing.
        var stalledPasses = 0
        val maxStalledPasses = 1000

        while (!outputDone) {
            if (stalledPasses > maxStalledPasses) error("VIDEO_COMPRESSION_STALLED")
            var progressed = false

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    progressed = true
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var decoderOutputAvailable = true
            while (decoderOutputAvailable) {
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        progressed = true
                        val isDecoderEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outIndex, true)
                        if (isDecoderEos && !encoderEndOfStreamSignaled) {
                            encoder.signalEndOfInputStream()
                            encoderEndOfStreamSignaled = true
                        }
                    }
                    else -> decoderOutputAvailable = false
                }
            }

            var encoderOutputAvailable = true
            while (encoderOutputAvailable) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        progressed = true
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        if (audioTrack < 0 || muxerAudioTrack >= 0) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        progressed = true
                        val encoded = encoder.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(muxerVideoTrack, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    else -> encoderOutputAvailable = false
                }
            }

            stalledPasses = if (progressed) 0 else stalledPasses + 1
        }

        decoder.stop(); decoder.release()
        encoder.stop(); encoder.release()
        extractor.unselectTrack(videoTrack)

        // Audio: copied sample-for-sample, no decode/re-encode.
        if (audioTrack >= 0 && audioFormat != null) {
            extractor.selectTrack(audioTrack)
            muxerAudioTrack = muxer.addTrack(audioFormat)
            if (!muxerStarted) { muxer.start(); muxerStarted = true }
            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, info)
                extractor.advance()
            }
        }

        if (muxerStarted) muxer.stop()
        muxer.release()
        extractor.release()
        outputFile.takeIf { it.exists() && it.length() > 0 }
    }.onFailure { Log.w(TAG, "Video compression failed, uploading original", it) }.getOrNull()
}


/**
 * Re-encodes an audio clip (voice note, listening-question audio, MP3, WAV, M4A...) to AAC-LC in
 * an .m4a container through Android's own MediaCodec — nothing bundled, nothing downloaded.
 * Speech at 64 kbps mono (96 kbps stereo) sounds the same to a learner and is a fraction of the size
 * of a WAV or a high-bitrate MP3.
 *
 * Like [VideoCompressor] it is defensive: any failure, an unsupported layout, or a source that is
 * already at or below the target bitrate returns null so the caller uploads the original untouched.
 */
object AudioCompressor {
    private const val BITRATE_MONO = 64_000
    private const val BITRATE_STEREO = 96_000
    private const val TIMEOUT_US = 10_000L
    private const val MAX_STALLED_PASSES = 1000

    fun compress(inputPath: String, outputFile: File): File? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor.setDataSource(inputPath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return null

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (channels !in 1..2) return null
            val targetBitrate = if (channels == 1) BITRATE_MONO else BITRATE_STEREO
            val sourceBitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE) else 0
            // Already small enough: re-encoding would only lose quality.
            if (sourceBitrate in 1..targetBitrate) return null

            extractor.selectTrack(track)
            decoder = MediaCodec.createDecoderByType(mime).apply { configure(format, null, null, 0); start() }
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
            }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1

            val dec = decoder!!
            val enc = encoder!!
            val mux = muxer!!
            val info = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var bytesFed = 0L
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var stalled = 0

            // Pushes decoded PCM into the encoder in slices that fit its input buffers, draining
            // the encoder meanwhile so neither side can block the other.
            fun drainEncoder(): Boolean {
                var progressed = false
                var more = true
                while (more) {
                    val out = enc.dequeueOutputBuffer(encInfo, 0)
                    when {
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrack = mux.addTrack(enc.outputFormat); mux.start(); muxerStarted = true; progressed = true
                        }
                        out >= 0 -> {
                            progressed = true
                            val buf = enc.getOutputBuffer(out)!!
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) encInfo.size = 0
                            if (encInfo.size > 0 && muxerStarted) mux.writeSampleData(muxerTrack, buf, encInfo)
                            enc.releaseOutputBuffer(out, false)
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { encoderDone = true; more = false }
                        }
                        else -> more = false
                    }
                }
                return progressed
            }

            fun feedEncoder(data: ByteArray, size: Int, eos: Boolean) {
                var offset = 0
                var waits = 0
                while (offset < size || eos) {
                    val idx = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (idx < 0) {
                        drainEncoder()
                        if (++waits > MAX_STALLED_PASSES) error("AUDIO_ENCODER_STALLED")
                        continue
                    }
                    waits = 0
                    val buf = enc.getInputBuffer(idx)!!
                    buf.clear()
                    val n = minOf(buf.remaining(), size - offset)
                    if (n > 0) buf.put(data, offset, n)
                    val ptsUs = bytesFed / (2L * channels) * 1_000_000L / sampleRate
                    val last = eos && offset + n >= size
                    enc.queueInputBuffer(idx, 0, n, ptsUs, if (last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    bytesFed += n
                    offset += n
                    drainEncoder()
                    if (last) return
                }
            }

            while (!encoderDone) {
                if (stalled > MAX_STALLED_PASSES) error("AUDIO_COMPRESSION_STALLED")
                var progressed = false

                if (!inputDone) {
                    val idx = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        progressed = true
                        val size = extractor.readSampleData(dec.getInputBuffer(idx)!!, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            dec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val out = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            progressed = true
                            val f = dec.outputFormat
                            // The encoder is configured for the source layout; anything else (e.g. SBR
                            // doubling the rate) means we can't mux it correctly, so keep the original.
                            if (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate ||
                                f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels
                            ) error("AUDIO_FORMAT_MISMATCH")
                        }
                        out >= 0 -> {
                            progressed = true
                            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            val pcm = ByteArray(info.size)
                            if (info.size > 0) {
                                val b = dec.getOutputBuffer(out)!!
                                b.position(info.offset); b.limit(info.offset + info.size); b.get(pcm)
                            }
                            dec.releaseOutputBuffer(out, false)
                            if (pcm.isNotEmpty() || eos) feedEncoder(pcm, pcm.size, eos)
                            if (eos) decoderDone = true
                        }
                    }
                }

                if (drainEncoder()) progressed = true
                stalled = if (progressed) 0 else stalled + 1
            }

            if (muxerStarted) mux.stop()
            outputFile.takeIf { it.exists() && it.length() > 0 }
        } catch (t: Throwable) {
            Log.w(TAG, "Audio compression failed, uploading original", t)
            null
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { encoder?.stop() }; runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
