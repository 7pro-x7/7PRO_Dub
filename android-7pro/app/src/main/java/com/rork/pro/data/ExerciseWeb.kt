package com.rork.pro.data

import java.net.URLEncoder

/**
 * Where the 7PRO web exercises page (web-exercises/public/index.html) is hosted. A teacher copies
 * a link from the exercise studio and sends it to the students subscribed with them.
 *
 * The link alone opens nothing: the page asks the server, which only serves a teacher's
 * exercises to the teacher, staff, and students with an approved subscription to that teacher.
 *
 * Change this single constant if the hostname ever changes.
 */
object ExerciseWeb {
    const val BASE_URL = "https://7pro-exercises.7pro7777.workers.dev/"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Every published exercise of one teacher. */
    fun teacherUrl(teacherId: String): String = "$BASE_URL?teacher=${enc(teacherId)}"

    /** Straight into one exercise of that teacher. */
    fun exerciseUrl(teacherId: String, exerciseId: String): String =
        "${teacherUrl(teacherId)}&ex=${enc(exerciseId)}"
}
