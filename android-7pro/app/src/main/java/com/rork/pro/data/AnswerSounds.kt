package com.rork.pro.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.rork.pro.R

/**
 * Short encouraging sounds played the moment a student's answer is graded in an exercise or a
 * level test: a bright rising chime for a correct answer and a soft, gentle tone for a wrong one
 * (deliberately not harsh, so it nudges the student to try again instead of discouraging them).
 *
 * SoundPool keeps both clips decoded in memory, so playback is instant with no MediaPlayer
 * start-up lag. It uses the game/"sonification" usage, so it respects the media volume and stays
 * silent when the phone is muted.
 */
object AnswerSounds {
    private var pool: SoundPool? = null
    private var correctId = 0
    private var wrongId = 0
    private val loaded = mutableSetOf<Int>()

    @Synchronized
    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val app = context.applicationContext
        val sp = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        sp.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        correctId = sp.load(app, R.raw.answer_correct, 1)
        wrongId = sp.load(app, R.raw.answer_wrong, 1)
        pool = sp
        return sp
    }

    /** Call early (e.g. when the test opens) so the first sound doesn't miss its load window. */
    fun preload(context: Context) {
        runCatching { ensure(context) }
    }

    fun play(context: Context, correct: Boolean) {
        runCatching {
            val sp = ensure(context)
            val id = if (correct) correctId else wrongId
            if (id in loaded) sp.play(id, 1f, 1f, 1, 0, 1f)
        }
    }
}
