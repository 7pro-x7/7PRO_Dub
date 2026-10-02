package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ProgressResult(
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
    @SerialName("lessons_total") val lessonsTotal: Int = 0,
    @SerialName("lessons_done") val lessonsDone: Int = 0,
    @SerialName("certificate_serial") val certificateSerial: String? = null,
)

@Serializable
data class QuizResult(
    val score: Double = 0.0,
    @SerialName("max_score") val maxScore: Double = 0.0,
    val percent: Double = 0.0,
    val passed: Boolean = false,
)

/** Course consumption: lesson progress, quizzes and certificate issuance. */
object LearningRepository {

    /** The learner's row for this course, whatever its state (active, expired or awaiting payment). */
    suspend fun enrollmentAnyState(courseId: String): Enrollment? {
        val userId = Backend.currentUserId ?: return null
        return Backend.client.from("enrollments")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("course_id", courseId)
                }
            }
            .decodeSingleOrNull()
    }

    /**
     * The learner's enrollment only while it actually grants access. An expired monthly
     * subscription, or a free course that has since become paid, returns null here — exactly like
     * someone who never enrolled — so every screen keeps the lessons locked and offers payment.
     */
    suspend fun enrollment(courseId: String): Enrollment? =
        enrollmentAnyState(courseId)?.takeIf { it.hasAccess }

    suspend fun progressFor(courseId: String): List<LessonProgress> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("lesson_progress")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("course_id", courseId)
                }
            }
            .decodeList()
    }

    suspend fun markProgress(lessonId: String, seconds: Int, completed: Boolean): ProgressResult =
        Backend.rpc(
            "mark_lesson_progress",
            buildJsonObject {
                put("p_lesson_id", lessonId)
                put("p_seconds", seconds)
                put("p_completed", completed)
            },
        )

    suspend fun quizzes(lessonId: String): List<LessonQuiz> =
        Backend.client.from("lesson_quizzes")
            .select {
                filter { eq("lesson_id", lessonId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun submitQuiz(lessonId: String, answers: Map<String, List<Int>>): QuizResult {
        val payload = JsonObject(
            answers.mapValues { (_, indexes) -> JsonArray(indexes.map { JsonPrimitive(it) }) },
        )
        return Backend.rpc(
            "submit_lesson_quiz",
            buildJsonObject {
                put("p_lesson_id", lessonId)
                put("p_answers", payload)
            },
        )
    }
}
