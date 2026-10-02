package com.rork.pro.character

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.abs

/**
 * Eye and lip positions from MediaPipe Face Landmarker (Apache-2.0), running entirely on the
 * phone. The model file (`assets/character_ai/face_landmarker.task`) is fetched at build time by
 * the `downloadCharacterAiModel` Gradle task. If the file is missing, or no face is found, this
 * returns null and [FaceRigFinder] falls back to [HeuristicRig].
 *
 * Landmark indices are from MediaPipe's canonical face mesh:
 *  - eye corners 33/133 (image-left eye) and 362/263 (image-right eye), lids 159/145 and 386/374
 *  - lip corners 61/291, outer lip top 0 and bottom 17
 */
internal object MediaPipeLandmarks {
    private const val MODEL = "character_ai/face_landmarker.task"
    private const val MAX_SIDE = 1024

    fun detect(context: Context, source: Bitmap): CharacterRig? {
        if (runCatching { context.assets.open(MODEL).close() }.isFailure) return null

        val f = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
        val sized = if (f < 1f) Bitmap.createScaledBitmap(source, (source.width * f).toInt().coerceAtLeast(1), (source.height * f).toInt().coerceAtLeast(1), true) else source
        val argb = if (sized.config == Bitmap.Config.ARGB_8888) sized else sized.copy(Bitmap.Config.ARGB_8888, false) ?: return null

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.25f)
            .setMinFacePresenceConfidence(0.25f)
            .build()
        val landmarker = FaceLandmarker.createFromOptions(context, options)
        try {
            val result = landmarker.detect(BitmapImageBuilder(argb).build())
            val face = result.faceLandmarks().firstOrNull() ?: return null
            if (face.size < 400) return null
            fun x(i: Int) = face[i].x()
            fun y(i: Int) = face[i].y()

            // Eye centre = middle of the corners horizontally, middle of the lids vertically.
            fun eye(outer: Int, inner: Int, upper: Int, lower: Int): FloatArray = floatArrayOf(
                (x(outer) + x(inner)) / 2f, (y(upper) + y(lower)) / 2f,
                abs(x(inner) - x(outer)) / 2f, abs(y(lower) - y(upper)) / 2f,
            )
            val a = eye(33, 133, 159, 145)
            val b = eye(263, 362, 386, 374)
            val (left, right) = if (a[0] <= b[0]) a to b else b to a
            val eyeHalfW = ((left[2] + right[2]) / 2f) * 1.1f
            val eyeHalfH = maxOf(((left[3] + right[3]) / 2f) * 1.25f, eyeHalfW * 0.4f)

            val mouthX = (x(61) + x(291)) / 2f
            val mouthY = (y(0) + y(17)) / 2f
            val mouthHalfW = abs(x(291) - x(61)) / 2f
            val mouthHalfH = abs(y(17) - y(0)) / 2f

            // A picture the model mis-reads (upside down, tiny, mouth above the eyes) is not trusted.
            val eyeLine = (left[1] + right[1]) / 2f
            if (mouthY <= eyeLine || mouthHalfW < 0.01f || right[0] - left[0] < 0.03f) return null

            return CharacterRig(
                mouthX = mouthX, mouthY = mouthY, mouthHalfW = mouthHalfW, mouthHalfH = mouthHalfH,
                eyeLX = left[0], eyeLY = left[1], eyeRX = right[0], eyeRY = right[1],
                eyeHalfW = eyeHalfW, eyeHalfH = eyeHalfH,
            )
        } finally {
            landmarker.close()
        }
    }
}
