package com.rork.pro.ui.screens.courses

import com.rork.pro.data.Course
import com.rork.pro.data.Enrollment
import com.rork.pro.ui.screens.admin.SearchMatch

/**
 * Precise course search, done on the device over the already-loaded catalogue.
 *
 * Every word the learner types must appear (in any order) somewhere in the course's title,
 * subtitle, description (Arabic, English and the original text), the teacher's name or the
 * level. Letters are normalised the same way on both sides — case, Arabic diacritics, alef /
 * yaa / taa-marbuta variants, Arabic-Indic digits, punctuation — so "اللغه الانجليزيه" finds
 * "اللغة الإنجليزية". Nothing fuzzy: a word is either there or the course is hidden.
 * Results are ordered by how well the *title* matches before anything else.
 */
internal object CourseSearch {

    private fun titles(c: Course) = listOf(c.title, c.titleAr, c.titleEn)

    fun matches(query: String, c: Course): Boolean = SearchMatch.matches(
        query,
        c.title, c.titleAr, c.titleEn,
        c.subtitle, c.subtitleAr, c.subtitleEn,
        c.description, c.descriptionAr, c.descriptionEn,
        c.teacher?.fullName, c.level,
    )

    /** 3 = a title equals the query, 2 = a title starts with it, 1 = a title contains it, 0 = matched elsewhere. */
    private fun rank(query: String, c: Course): Int =
        titles(c).filterNotNull().maxOfOrNull { SearchMatch.rank(query, it) } ?: 0

    /** True when every word is found inside the titles alone (stronger than matching the description). */
    private fun inTitle(query: String, c: Course): Boolean =
        SearchMatch.matches(query, c.title, c.titleAr, c.titleEn)

    fun filter(query: String, courses: List<Course>): List<Course> {
        if (SearchMatch.tokens(query).isEmpty()) return courses
        return courses
            .filter { matches(query, it) }
            .sortedWith(
                compareByDescending<Course> { rank(query, it) }
                    .thenByDescending { inTitle(query, it) },
            )
    }

    fun filterEnrollments(query: String, list: List<Enrollment>): List<Enrollment> {
        if (SearchMatch.tokens(query).isEmpty()) return list
        // An enrolment without its course row can't be judged, so it is hidden while searching.
        return list.filter { e -> e.course?.let { matches(query, it) } == true }
    }
}
