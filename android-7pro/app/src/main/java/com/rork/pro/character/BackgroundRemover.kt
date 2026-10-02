package com.rork.pro.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * On-device background removal for the AI Tutor's character upload feature, using U-2-Net-p
 * (MIT license — https://github.com/danielgatis/rembg) via ONNX Runtime Mobile (MIT license).
 * Nothing leaves the phone: no server call, no per-use cost, no size limit beyond the model
 * itself (~4.7 MB, fetched once at build time — see app/build.gradle.kts).
 */
class BackgroundRemover(private val context: Context) {

    private var session: OrtSession? = null
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private fun ensureSession(): OrtSession {
        session?.let { return it }
        val bytes = context.assets.open("character_ai/u2netp.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
        return env.createSession(bytes, opts).also { session = it }
    }

    /** Cuts [source] out from its background. Returns an ARGB bitmap with a transparent background. */
    suspend fun removeBackground(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val sess = ensureSession()
        val input = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        val tensorData = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        // CHW, ImageNet-normalised — the preprocessing U-2-Net was trained with.
        for (c in 0 until 3) {
            val mean = MEAN[c]; val std = STD[c]
            for (y in 0 until INPUT_SIZE) for (x in 0 until INPUT_SIZE) {
                val px = input.getPixel(x, y)
                val v = when (c) { 0 -> Color.red(px); 1 -> Color.green(px); else -> Color.blue(px) } / 255f
                tensorData.put((v - mean) / std)
            }
        }
        tensorData.rewind()
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val maskFloats = OnnxTensor.createTensor(env, tensorData, shape).use { tensor ->
            sess.run(mapOf(sess.inputNames.first() to tensor)).use { result ->
                // OrtSession.Result iterates as Map.Entry<String, OnnxValue> in ONNX Runtime's
                // Java API (no index access) — take the first (only) output tensor's value.
                val out = result.iterator().next().value.value
                flattenMask(out)
            }
        }
        // Normalise the mask to 0..1 (the raw output isn't always pre-clamped) and upscale to
        // the source resolution with bilinear sampling for a smoother cutout edge.
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (v in maskFloats) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = (hi - lo).coerceAtLeast(1e-6f)

        val w = source.width; val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val fy = y.toFloat() / h * (INPUT_SIZE - 1)
            val y0 = fy.toInt().coerceIn(0, INPUT_SIZE - 1); val y1 = (y0 + 1).coerceAtMost(INPUT_SIZE - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = x.toFloat() / w * (INPUT_SIZE - 1)
                val x0 = fx.toInt().coerceIn(0, INPUT_SIZE - 1); val x1 = (x0 + 1).coerceAtMost(INPUT_SIZE - 1)
                val tx = fx - x0
                val m00 = maskFloats[y0 * INPUT_SIZE + x0]; val m01 = maskFloats[y0 * INPUT_SIZE + x1]
                val m10 = maskFloats[y1 * INPUT_SIZE + x0]; val m11 = maskFloats[y1 * INPUT_SIZE + x1]
                val m = (m00 * (1 - tx) + m01 * tx) * (1 - ty) + (m10 * (1 - tx) + m11 * tx) * ty
                val alpha = (((m - lo) / range).coerceIn(0f, 1f) * 255).toInt()
                val i = y * w + x
                pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        out
    }

    /** Model output is [1,1,H,W]; ONNX Runtime's Java API hands it back as nested Java arrays. */
    private fun flattenMask(out: Any?): FloatArray {
        val result = FloatArray(INPUT_SIZE * INPUT_SIZE)
        var idx = 0
        fun walk(node: Any?) {
            when (node) {
                is FloatArray -> for (v in node) { if (idx < result.size) result[idx++] = v }
                is Array<*> -> for (n in node) walk(n)
                else -> {}
            }
        }
        walk(out)
        return result
    }

    fun close() {
        session?.close()
        session = null
    }

    companion object {
        private const val INPUT_SIZE = 320
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
