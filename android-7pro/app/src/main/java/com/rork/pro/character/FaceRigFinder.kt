package com.rork.pro.character

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a suggested [CharacterRig] was found — tells the owner how much to trust it. */
enum class RigConfidence {
    /** A face-landmark model located the eyes and lips on a real (or realistic) face. */
    LANDMARKS,

    /** No face was recognised (a drawing, a mascot, a cropped face): proportions plus a colour search. */
    ESTIMATED,
}

data class RigSuggestion(val rig: CharacterRig, val confidence: RigConfidence)

/**
 * Finds where the eyes and mouth are the moment a picture is chosen, so the owner starts from a
 * correct placement instead of an empty one and only fine-tunes it in the editor.
 *
 *  1. [MediaPipeLandmarks] — MediaPipe Face Landmarker (Apache-2.0), 478 landmarks, fully
 *     on-device. Precise on photos and realistic faces.
 *  2. [HeuristicRig] — for drawings and mascots the landmark model does not recognise: the head
 *     box is estimated from the cutout, then eyes (dark blobs) and mouth (red/dark blob) are
 *     searched for in the expected zones. Always returns something; flagged as ESTIMATED.
 */
object FaceRigFinder {
    /** [source] is the picture as chosen (best for detection); [cutout] the same picture, background removed. */
    suspend fun find(context: Context, source: Bitmap, cutout: Bitmap): RigSuggestion = withContext(Dispatchers.Default) {
        val landmarks = runCatching { MediaPipeLandmarks.detect(context, source) }.getOrNull()
        if (landmarks != null) {
            RigSuggestion(landmarks.clamped(), RigConfidence.LANDMARKS)
        } else {
            RigSuggestion(HeuristicRig.estimate(cutout).clamped(), RigConfidence.ESTIMATED)
        }
    }
}
