package com.rork.pro.character

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Best-effort eye and mouth placement for pictures a face-landmark model does not recognise
 * (drawings, mascots, stylised characters). It is a starting point for the owner to adjust, never
 * a final answer, and the editor says so.
 *
 * 1. The head box comes from the opaque area of the cutout (top of the figure, sized from its width).
 * 2. Default eye / mouth positions use typical head proportions.
 * 3. Each is then pulled onto what is actually drawn there: eyes = the darkest compact blob in
 *    the eye zone on each side, mouth = the reddest / darkest blob in the mouth zone.
 */
internal object HeuristicRig {
    private const val MAX_SIDE = 256

    fun estimate(cutout: Bitmap): CharacterRig {
        val f = MAX_SIDE.toFloat() / max(cutout.width, cutout.height)
        val small = if (f < 1f) Bitmap.createScaledBitmap(cutout, max(1, (cutout.width * f).toInt()), max(1, (cutout.height * f).toInt()), true) else cutout
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (Color.alpha(px[y * w + x]) > 40) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) { minX = 0; maxX = w - 1; minY = 0; maxY = h - 1 }
        val bw = (maxX - minX + 1).toFloat()
        val bh = (maxY - minY + 1).toFloat()

        // Head box. Without a visible neck: a tall figure is a full body (head ≈ its width), otherwise
        // the head is most of the picture. With a neck (the row width collapses under 45% of the widest
        // row so far, e.g. below the chin of a bust) the head ends there.
        var headH = if (bh > bw * 1.5f) bw * 1.1f else bh * 0.88f
        var widest = 0f
        for (y in minY..maxY) {
            var left = -1; var right = -1
            for (x in minX..maxX) {
                if (Color.alpha(px[y * w + x]) > 40) { if (left < 0) left = x; right = x }
            }
            val rowW = if (left < 0) 0f else (right - left + 1).toFloat()
            widest = max(widest, rowW)
            if (y - minY >= 0.27f * bw && rowW < 0.45f * widest) {
                val candidate = (y - minY) / 0.96f
                if (candidate >= 0.3f * bw && candidate <= 1.4f * bw) headH = candidate
                break
            }
        }
        val headW = min(bw, headH * 0.85f)
        val headTop = minY.toFloat()

        // Horizontal centre of the head: mean x of the opaque pixels in the upper part of the figure.
        var sumX = 0L; var cnt = 0
        val rowEnd = min(h - 1, (headTop + headH * 0.6f).toInt())
        for (y in minY..rowEnd) for (x in minX..maxX) {
            if (Color.alpha(px[y * w + x]) > 40) { sumX += x; cnt++ }
        }
        val cx = if (cnt > 0) sumX.toFloat() / cnt else (minX + maxX) / 2f

        // Defaults from typical proportions.
        var eyeLX = cx - 0.20f * headW; var eyeRX = cx + 0.20f * headW
        var eyeLY = headTop + 0.44f * headH; var eyeRY = eyeLY
        var eyeHW = 0.09f * headW; var eyeHH = 0.06f * headH
        var mouthX = cx; var mouthY = headTop + 0.70f * headH
        var mouthHW = 0.14f * headW; var mouthHH = 0.035f * headH

        // Refine each eye onto the darkest compact blob in its zone.
        fun eyeBlob(side: Int): FloatArray? {
            val x0 = (cx + side * 0.08f * headW).toInt(); val x1 = (cx + side * 0.36f * headW).toInt()
            val (xa, xb) = if (x0 <= x1) x0 to x1 else x1 to x0
            return darkBlob(px, w, h, xa, xb, (headTop + 0.30f * headH).toInt(), (headTop + 0.58f * headH).toInt())
        }
        val bl = eyeBlob(-1)
        val br = eyeBlob(+1)
        if (bl != null) { eyeLX = bl[0]; eyeLY = bl[1]; eyeHW = max(eyeHW * 0.6f, bl[2] * 1.9f); eyeHH = max(eyeHH * 0.6f, bl[3] * 1.5f) }
        if (br != null) { eyeRX = br[0]; eyeRY = br[1]; eyeHW = if (bl != null) (eyeHW + max(0.09f * headW * 0.6f, br[2] * 1.9f)) / 2f else max(eyeHW * 0.6f, br[2] * 1.9f) }
        // Eyes on a face sit at the same height: if both were found and disagree wildly, trust neither y.
        if (bl != null && br != null) {
            if (abs(eyeLY - eyeRY) > 0.06f * headH) { val m = (eyeLY + eyeRY) / 2f; eyeLY = m; eyeRY = m }
        }

        // Refine the mouth onto the reddest / darkest blob in its zone.
        val mb = mouthBlob(px, w, h, (cx - 0.24f * headW).toInt(), (cx + 0.24f * headW).toInt(), (headTop + 0.58f * headH).toInt(), (headTop + 0.90f * headH).toInt())
        if (mb != null) { mouthX = mb[0]; mouthY = mb[1]; mouthHW = max(0.06f * headW, mb[2] * 1.1f); mouthHH = max(0.02f * headH, mb[3]) }

        return CharacterRig(
            mouthX = mouthX / w, mouthY = mouthY / h, mouthHalfW = mouthHW / w, mouthHalfH = mouthHH / h,
            eyeLX = eyeLX / w, eyeLY = eyeLY / h, eyeRX = eyeRX / w, eyeRY = eyeRY / h,
            eyeHalfW = eyeHW / w, eyeHalfH = eyeHH / h,
        )
    }

    private fun lum(c: Int): Float = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    /** Centre and half-size (x, y, halfW, halfH, in pixels) of the darkest compact blob in the window, or null. */
    private fun darkBlob(px: IntArray, w: Int, h: Int, x0: Int, x1: Int, y0: Int, y1: Int): FloatArray? {
        val xs = max(0, x0); val xe = min(w - 1, x1); val ys = max(0, y0); val ye = min(h - 1, y1)
        if (xe <= xs || ye <= ys) return null
        val hist = IntArray(256)
        var total = 0
        for (y in ys..ye) for (x in xs..xe) {
            val c = px[y * w + x]
            if (Color.alpha(c) < 200) continue
            hist[lum(c).toInt().coerceIn(0, 255)]++; total++
        }
        if (total < 30) return null
        // Threshold = darkest 8% of the window, but never lighter than "clearly dark".
        var acc = 0; var thr = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= total * 0.08f) { thr = v; break } }
        thr = min(thr, 110)
        return blob(px, w, xs, xe, ys, ye) { c -> lum(c) <= thr }
    }

    private fun mouthBlob(px: IntArray, w: Int, h: Int, x0: Int, x1: Int, y0: Int, y1: Int): FloatArray? {
        val xs = max(0, x0); val xe = min(w - 1, x1); val ys = max(0, y0); val ye = min(h - 1, y1)
        if (xe <= xs || ye <= ys) return null
        fun score(c: Int): Float = (Color.red(c) - (Color.green(c) + Color.blue(c)) / 2f) + (255f - lum(c)) * 0.3f
        val scores = ArrayList<Float>()
        for (y in ys..ye) for (x in xs..xe) {
            val c = px[y * w + x]
            if (Color.alpha(c) >= 200) scores.add(score(c))
        }
        if (scores.size < 30) return null
        scores.sort()
        // The mouth must stand clearly apart from the surrounding skin: well above the window's median.
        val thr = scores[scores.size / 2] + 30f
        return blob(px, w, xs, xe, ys, ye) { c -> score(c) >= thr }
    }

    private fun blob(px: IntArray, w: Int, xs: Int, xe: Int, ys: Int, ye: Int, hit: (Int) -> Boolean): FloatArray? {
        var n = 0; var sx = 0.0; var sy = 0.0
        var bx0 = Int.MAX_VALUE; var bx1 = -1; var by0 = Int.MAX_VALUE; var by1 = -1
        for (y in ys..ye) for (x in xs..xe) {
            val c = px[y * w + x]
            if (Color.alpha(c) < 200 || !hit(c)) continue
            n++; sx += x; sy += y
            if (x < bx0) bx0 = x
            if (x > bx1) bx1 = x
            if (y < by0) by0 = y
            if (y > by1) by1 = y
        }
        if (n < 6) return null
        return floatArrayOf((sx / n).toFloat(), (sy / n).toFloat(), (bx1 - bx0 + 1) / 2f, (by1 - by0 + 1) / 2f)
    }
}
