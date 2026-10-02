package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An exercise linked into a course, as the course's editor and its students see it. */
@Serializable
data class CourseExerciseItem(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("section_id") val sectionId: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val title: String = "",
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int = 0,
    val status: String = "PUBLISHED",
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("owner_name") val ownerName: String = "",
    /** How many courses this same exercise is linked into. */
    @SerialName("course_count") val courseCount: Int = 1,
    /** Whether the signed-in user may take it out of this course. */
    @SerialName("can_unlink") val canUnlink: Boolean = false,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)

    /** The same exercise in the shape the existing exercise rows and runner expect. */
    fun asPlacementTest(): PlacementTest = PlacementTest(
        id = exerciseId,
        title = title,
        titleAr = titleAr,
        titleEn = titleEn,
        kind = "EXERCISE",
        questionCount = questionCount,
        timeLimitSeconds = timeLimitSeconds,
        status = status,
        ownerId = ownerId,
        isLocked = isLocked,
    )
}

/** Everything the course editor needs about a course's exercises, plus what the caller may do. */
@Serializable
data class CourseExercisePanel(
    @SerialName("can_manage") val canManage: Boolean = false,
    /** May link exercises into this course at all (own ones for a teacher). */
    @SerialName("can_link") val canLink: Boolean = false,
    /** May link any teacher's exercise (owner, or an admin with course_content.manage). */
    @SerialName("can_link_any") val canLinkAny: Boolean = false,
    val items: List<CourseExerciseItem> = emptyList(),
)

/** A candidate in the "link an exercise" picker. */
@Serializable
data class LinkableExercise(
    val id: String,
    val title: String = "",
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("question_count") val questionCount: Int = 0,
    val status: String = "DRAFT",
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("owner_name") val ownerName: String = "",
    val mine: Boolean = false,
    @SerialName("course_count") val courseCount: Int = 0,
    @SerialName("already_linked") val alreadyLinked: Boolean = false,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
}

@Serializable
private data class LinkRow(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("course_id") val courseId: String,
)

/**
 * Exercises inside courses. One exercise can be linked into many courses; linking never copies
 * it, so an edit shows up everywhere it is linked. Every write is a permission-checked server
 * function: the owner (and admins with `course_content.manage`) may link any exercise into any
 * course, a teacher only their own exercises into their own courses — and only once the owner
 * has switched that on for them.
 */
object CourseContentRepository {

    suspend fun panel(courseId: String): CourseExercisePanel =
        Backend.client.postgrest.rpc(
            "course_exercise_list",
            buildJsonObject { put("p_course_id", courseId) },
        ).decodeAs()

    suspend fun linkable(courseId: String, all: Boolean, search: String?): List<LinkableExercise> =
        Backend.client.postgrest.rpc(
            "linkable_exercises",
            buildJsonObject {
                put("p_course_id", courseId)
                put("p_scope", if (all) "ALL" else "MINE")
                put("p_search", search?.trim()?.ifBlank { null })
            },
        ).decodeList()

    suspend fun link(courseId: String, exerciseIds: List<String>, sectionId: String?): Int =
        Backend.client.postgrest.rpc(
            "link_exercises_to_course",
            buildJsonObject {
                put("p_course_id", courseId)
                put("p_exercise_ids", JsonArray(exerciseIds.map { JsonPrimitive(it) }))
                put("p_section_id", sectionId)
            },
        ).decodeAs()

    suspend fun unlink(courseId: String, exerciseId: String) {
        Backend.client.postgrest.rpc(
            "unlink_exercise_from_course",
            buildJsonObject {
                put("p_course_id", courseId)
                put("p_exercise_id", exerciseId)
            },
        )
    }

    suspend fun reorder(courseId: String, orderedExerciseIds: List<String>) {
        Backend.client.postgrest.rpc(
            "reorder_course_exercises",
            buildJsonObject {
                put("p_course_id", courseId)
                put("p_exercise_ids", JsonArray(orderedExerciseIds.map { JsonPrimitive(it) }))
            },
        )
    }

    /** Which courses each of these exercises is linked into — for the exercise cards. */
    suspend fun coursesOf(exerciseIds: List<String>): Map<String, Set<String>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        return Backend.client.from("course_exercise_links")
            .select(Columns.raw("exercise_id,course_id")) {
                filter { isIn("exercise_id", exerciseIds) }
            }
            .decodeList<LinkRow>()
            .groupBy({ it.exerciseId }, { it.courseId })
            .mapValues { it.value.toSet() }
    }
}
