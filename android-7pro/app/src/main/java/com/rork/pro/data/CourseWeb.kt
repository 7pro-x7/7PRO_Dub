package com.rork.pro.data

import java.net.URLEncoder

/**
 * Where the public 7PRO courses page (web-courses/public/index.html) is hosted. Anyone can copy
 * these links from inside the app and send them on — the page needs no sign-in.
 *
 * Change this single constant if the hostname ever changes.
 */
object CourseWeb {
    const val BASE_URL = "https://7pro-courses.7pro7777.workers.dev/"

    /** Every published course. */
    fun catalogUrl(): String = BASE_URL

    /** One course's page: content outline and price. */
    fun courseUrl(courseId: String): String = "$BASE_URL?course=${URLEncoder.encode(courseId, "UTF-8")}"
}
