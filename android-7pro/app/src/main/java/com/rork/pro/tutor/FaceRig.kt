package com.rork.pro.tutor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** What the tutor is doing right now — drives posture, eyes and brows. */
enum class TutorMood { NEUTRAL, HAPPY, ENCOURAGING }
enum class TutorActivity { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * The 15 standard visemes (Oculus/Meta naming, used by most avatar tools) with an equivalent
 * blend of ARKit face shapes for models that only ship the 52 ARKit shapes.
 */
enum class Viseme(val oculus: String, val arkit: Map<String, Float>) {
    SIL("viseme_sil", emptyMap()),
    PP("viseme_PP", mapOf("mouthClose" to 0.55f, "mouthPressLeft" to 0.45f, "mouthPressRight" to 0.45f, "jawOpen" to 0.04f)),
    FF("viseme_FF", mapOf("mouthRollLower" to 0.55f, "mouthUpperUpLeft" to 0.2f, "mouthUpperUpRight" to 0.2f, "jawOpen" to 0.1f)),
    TH("viseme_TH", mapOf("jawOpen" to 0.2f, "tongueOut" to 0.35f, "mouthLowerDownLeft" to 0.15f, "mouthLowerDownRight" to 0.15f)),
    DD("viseme_DD", mapOf("jawOpen" to 0.25f, "mouthStretchLeft" to 0.15f, "mouthStretchRight" to 0.15f)),
    KK("viseme_kk", mapOf("jawOpen" to 0.3f, "mouthStretchLeft" to 0.1f, "mouthStretchRight" to 0.1f)),
    CH("viseme_CH", mapOf("jawOpen" to 0.18f, "mouthFunnel" to 0.45f, "mouthPucker" to 0.2f)),
    SS("viseme_SS", mapOf("jawOpen" to 0.1f, "mouthStretchLeft" to 0.4f, "mouthStretchRight" to 0.4f, "mouthSmileLeft" to 0.1f, "mouthSmileRight" to 0.1f)),
    NN("viseme_nn", mapOf("jawOpen" to 0.2f, "mouthClose" to 0.1f)),
    RR("viseme_RR", mapOf("jawOpen" to 0.2f, "mouthFunnel" to 0.3f, "mouthPucker" to 0.15f)),
    AA("viseme_aa", mapOf("jawOpen" to 0.65f, "mouthLowerDownLeft" to 0.3f, "mouthLowerDownRight" to 0.3f)),
    E("viseme_E", mapOf("jawOpen" to 0.35f, "mouthStretchLeft" to 0.45f, "mouthStretchRight" to 0.45f, "mouthSmileLeft" to 0.15f, "mouthSmileRight" to 0.15f)),
    I("viseme_I", mapOf("jawOpen" to 0.2f, "mouthStretchLeft" to 0.55f, "mouthStretchRight" to 0.55f, "mouthSmileLeft" to 0.3f, "mouthSmileRight" to 0.3f)),
    O("viseme_O", mapOf("jawOpen" to 0.45f, "mouthFunnel" to 0.6f)),
    U("viseme_U", mapOf("jawOpen" to 0.18f, "mouthPucker" to 0.8f, "mouthFunnel" to 0.25f));

    companion object {
        /** English spelling → mouth shapes. Not phonetics, but close enough to read as speech. */
        fun fromText(text: String): List<Viseme> {
            val s = text.lowercase()
            val out = ArrayList<Viseme>(s.length)
            var i = 0
            fun push(v: Viseme) { if (out.lastOrNull() != v) out.add(v) }
            while (i < s.length) {
                val c = s[i]
                val next = if (i + 1 < s.length) s[i + 1] else ' '
                val pair = "$c$next"
                when {
                    pair == "th" -> { push(TH); i += 2; continue }
                    pair == "sh" || pair == "ch" -> { push(CH); i += 2; continue }
                    pair == "oo" || pair == "ou" -> { push(U); i += 2; continue }
                    pair == "ee" || pair == "ea" -> { push(I); i += 2; continue }
                }
                when (c) {
                    'a' -> push(AA); 'e' -> push(E); 'i', 'y' -> push(I); 'o' -> push(O); 'u', 'w', 'q' -> push(U)
                    'm', 'b', 'p' -> push(PP); 'f', 'v' -> push(FF); 'l', 'n' -> push(NN)
                    't', 'd' -> push(DD); 's', 'z', 'c', 'x' -> push(SS); 'k', 'g' -> push(KK)
                    'r' -> push(RR); 'j' -> push(CH); 'h' -> push(AA)
                    ' ', ',', '.', '!', '?', ';', ':' -> if (out.lastOrNull() != SIL) out.add(SIL)
                }
                i++
            }
            return out.ifEmpty { listOf(SIL) }
        }
    }
}

/**
 * Maps a spoken clip onto a per-slice viseme track: the letters of the line are spread across the
 * slices where the voice is actually sounding, so mouth shapes land on syllables, not on silence.
 */
class VisemeTrack(clip: SpokenClip) {
    private val track: Array<Viseme>

    init {
        val env = clip.envelope
        val seq = Viseme.fromText(clip.text).filter { it != Viseme.SIL }.ifEmpty { listOf(Viseme.AA) }
        val voiced = env.count { it > VOICED }
        var k = 0
        track = Array(env.size) { i ->
            if (env[i] <= VOICED || voiced == 0) Viseme.SIL else seq[min(seq.lastIndex, k++ * seq.size / voiced)]
        }
    }

    fun at(ms: Long): Viseme {
        val i = (ms / SpokenClip.ENVELOPE_MS).toInt()
        return if (i in track.indices) track[i] else Viseme.SIL
    }

    private companion object { const val VOICED = 0.06f }
}

/** Head pose in radians, applied by the renderer to the model root. */
data class HeadPose(val yaw: Float, val pitch: Float, val roll: Float, val breathe: Float)

/**
 * Procedural face animation: blinking, eye darts, brows, smile, breathing and head motion, plus
 * lip-sync while speaking. Pure math on a clock, so it costs almost nothing per frame and looks
 * the same on every device. Output is a set of named shape weights (ARKit + viseme names); the
 * renderer applies whichever of them the loaded model actually has.
 */
class FaceDriver {
    private val rnd = Random(7)
    private var nextBlinkAt = 1.2
    private var blinkStart = -1.0
    private var gazeX = 0f; private var gazeY = 0f
    private var gazeTargetX = 0f; private var gazeTargetY = 0f
    private var nextGazeAt = 0.8
    private val smoothed = HashMap<String, Float>()
    private var lastT = 0.0

    var activity: TutorActivity = TutorActivity.IDLE
    var mood: TutorMood = TutorMood.NEUTRAL
    /** Live microphone level while the student talks (0..1): the tutor nods along a little. */
    var listenLevel: Float = 0f

    /** Weights for this frame. [tSec] is a monotonic clock in seconds. */
    fun frame(tSec: Double, clip: SpokenClip?, clipMs: Long, visemes: VisemeTrack?): Pair<Map<String, Float>, HeadPose> {
        val dt = (tSec - lastT).coerceIn(0.0, 0.1).toFloat()
        lastT = tSec
        val target = HashMap<String, Float>(40)

        // ---- mouth (lip-sync)
        var jaw = 0f
        if (activity == TutorActivity.SPEAKING && clip != null && clipMs >= 0) {
            val loud = clip.loudnessAt(clipMs)
            val v = visemes?.at(clipMs) ?: Viseme.AA
            jaw = loud
            if (v != Viseme.SIL) {
                target[v.oculus] = 0.35f + 0.65f * loud
                v.arkit.forEach { (k, w) -> target[k] = max(target[k] ?: 0f, w * (0.45f + 0.75f * loud)) }
            } else {
                target["viseme_sil"] = 1f - loud
            }
            target["jawOpen"] = max(target["jawOpen"] ?: 0f, loud * 0.55f)
        }

        // ---- expression by mood/activity
        val smile = when {
            activity == TutorActivity.LISTENING -> 0.18f
            mood == TutorMood.HAPPY -> 0.42f
            mood == TutorMood.ENCOURAGING -> 0.3f
            else -> 0.15f
        } * (1f - jaw * 0.5f)
        target["mouthSmileLeft"] = max(target["mouthSmileLeft"] ?: 0f, smile)
        target["mouthSmileRight"] = max(target["mouthSmileRight"] ?: 0f, smile)
        target["cheekSquintLeft"] = smile * 0.35f
        target["cheekSquintRight"] = smile * 0.35f
        when (activity) {
            TutorActivity.LISTENING -> { target["browInnerUp"] = 0.18f + listenLevel * 0.15f }
            TutorActivity.THINKING -> { target["browInnerUp"] = 0.3f; target["browOuterUpLeft"] = 0.25f; target["mouthPressLeft"] = 0.2f }
            TutorActivity.SPEAKING -> { target["browInnerUp"] = jaw * 0.25f }
            TutorActivity.IDLE -> {}
        }

        // ---- blinks (every 2.5-5.5 s, a quick 150 ms close/open; sometimes a double blink)
        if (tSec >= nextBlinkAt && blinkStart < 0) blinkStart = tSec
        var blink = 0f
        if (blinkStart >= 0) {
            val p = ((tSec - blinkStart) / 0.16).toFloat()
            blink = if (p < 0.5f) p * 2f else (2f - p * 2f)
            if (p >= 1f) {
                blinkStart = -1.0
                nextBlinkAt = tSec + if (rnd.nextFloat() < 0.15f) 0.25 else 2.5 + rnd.nextDouble() * 3.0
                blink = 0f
            }
        }
        target["eyeBlinkLeft"] = blink.coerceIn(0f, 1f)
        target["eyeBlinkRight"] = blink.coerceIn(0f, 1f)

        // ---- gaze: mostly on the student, small saccades; thinking looks up and away
        if (tSec >= nextGazeAt) {
            if (activity == TutorActivity.THINKING) { gazeTargetX = 0.35f; gazeTargetY = 0.35f }
            else { gazeTargetX = (rnd.nextFloat() - 0.5f) * 0.18f; gazeTargetY = (rnd.nextFloat() - 0.5f) * 0.1f }
            nextGazeAt = tSec + 0.6 + rnd.nextDouble() * 1.8
        }
        val g = 1f - exp(-dt * 18f)
        gazeX += (gazeTargetX - gazeX) * g
        gazeY += (gazeTargetY - gazeY) * g
        if (gazeX > 0) { target["eyeLookOutLeft"] = gazeX; target["eyeLookInRight"] = gazeX }
        else { target["eyeLookInLeft"] = -gazeX; target["eyeLookOutRight"] = -gazeX }
        if (gazeY > 0) { target["eyeLookUpLeft"] = gazeY; target["eyeLookUpRight"] = gazeY }
        else { target["eyeLookDownLeft"] = -gazeY; target["eyeLookDownRight"] = -gazeY }

        // ---- smoothing (mouth fast, the rest softer), blinks and gaze stay crisp
        val out = HashMap<String, Float>(target.size + smoothed.size)
        val keys = target.keys + smoothed.keys
        for (k in keys) {
            val goal = target[k] ?: 0f
            val speed = when {
                k.startsWith("eyeBlink") || k.startsWith("eyeLook") -> 1f
                k.startsWith("viseme") || k.startsWith("jaw") || k.startsWith("mouth") || k == "tongueOut" -> 1f - exp(-dt * 28f)
                else -> 1f - exp(-dt * 6f)
            }
            val cur = smoothed[k] ?: 0f
            val v = cur + (goal - cur) * speed
            if (abs(v) < 0.002f && goal == 0f) smoothed.remove(k) else { smoothed[k] = v; out[k] = v }
        }

        // ---- head: slow idle drift, attentive tilt when listening, nods on stressed syllables
        val t = tSec.toFloat()
        val drift = 0.035f
        var yaw = sin(t * 0.37f) * drift + sin(t * 0.91f) * drift * 0.4f
        var pitch = sin(t * 0.29f + 1.3f) * drift * 0.6f
        var roll = sin(t * 0.23f + 0.5f) * drift * 0.5f
        when (activity) {
            TutorActivity.LISTENING -> { roll += 0.05f; pitch += 0.02f + listenLevel * 0.03f * sin(t * 6f) }
            TutorActivity.THINKING -> { yaw += 0.08f; pitch -= 0.05f }
            TutorActivity.SPEAKING -> { pitch += jaw * 0.035f; yaw += sin(t * 1.7f) * 0.02f }
            TutorActivity.IDLE -> {}
        }
        val breathe = (sin(t * 2f * PI.toFloat() / 4.2f) + 1f) * 0.5f
        return out to HeadPose(yaw, pitch, roll, breathe)
    }
}
