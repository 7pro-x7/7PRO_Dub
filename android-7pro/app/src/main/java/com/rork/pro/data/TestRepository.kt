package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Adaptive placement test flow. Grading and level mapping happen entirely server-side. */
object TestRepository {

    suspend fun start(testId: String): StartAttempt =
        Backend.rpc("start_test_attempt", buildJsonObject { put("p_test_id", testId) })

    suspend fun next(attemptId: String): NextQuestion =
        Backend.rpc("next_test_question", buildJsonObject { put("p_attempt_id", attemptId) })

    /**
     * Records one answer and gets it graded on the server.
     *
     * Every parameter is always sent, including the empty ones: PostgREST picks a function by its
     * exact argument list, so leaving `p_text` or `p_order` out made the whole call fail with
     * "function not found" — which is why answers were never scored.
     */
    suspend fun answer(
        attemptId: String,
        questionId: String,
        selected: List<Int> = emptyList(),
        text: String? = null,
        order: List<Int>? = null,
    ): AnswerResult =
        Backend.rpc(
            "submit_test_answer",
            buildJsonObject {
                put("p_attempt_id", attemptId)
                put("p_question_id", questionId)
                put("p_selected", JsonArray(selected.map { JsonPrimitive(it) }))
                put("p_text", text)
                put("p_order", order?.let { o -> JsonArray(o.map { JsonPrimitive(it) }) } ?: JsonNull)
                put("p_match", buildJsonObject { })
            },
        )

    suspend fun finish(attemptId: String): TestResult =
        Backend.rpc("finish_test_attempt", buildJsonObject { put("p_attempt_id", attemptId) })

    /**
     * Reads a stored result without touching it.
     *
     * Opening a past result used to call [finish], which is a write: a test the learner had
     * left open was closed just by looking at an old score.
     */
    suspend fun result(attemptId: String): TestResult =
        Backend.rpc("test_attempt_result", buildJsonObject { put("p_attempt_id", attemptId) })

    /**
     * The test or exercise an attempt belongs to — its `kind` and its own `passing_score` are
     * what the result screen needs to decide what to show and what the learner needed to clear.
     */
    suspend fun attemptTest(attemptId: String): PlacementTest? {
        val attempt = Backend.client.from("test_attempts")
            .select { filter { eq("id", attemptId) } }
            .decodeSingleOrNull<TestAttemptRow>() ?: return null
        return Backend.client.from("placement_tests")
            .select { filter { eq("id", attempt.testId) } }
            .decodeSingleOrNull<PlacementTest>()
    }

    /**
     * Every question in an attempt with what the learner actually answered — the data behind the
     * "what did I get wrong" review.
     *
     * Two rows are joined by hand rather than in one PostgREST embed, because `test_answers`
     * holds only a question id: the question text, options and correct answer live on
     * `test_questions`, and a question the teacher has since deleted simply drops out rather
     * than failing the whole request.
     *
     * Visibility is the database's call, not this function's: the learner sees their own answers
     * and the exercise's owning teacher sees their students' (migration
     * 20260918000000_answers_visible_to_exercise_owner). Anyone else gets an empty list, which
     * is why the caller should treat "no rows" as "nothing to show", never as an error.
     */
    suspend fun review(attemptId: String): List<AnswerReview> {
        // Read through a server function: students can't select from test_questions directly
        // (that would expose every answer key), so the database hands back only the questions
        // of a finished attempt of their own — with the correct answers.
        val rows: List<ReviewPayload> =
            Backend.rpc("attempt_review", buildJsonObject { put("p_attempt_id", attemptId) })
        return rows.map { AnswerReview(it.answer, it.question) }
    }

    @kotlinx.serialization.Serializable
    private class ReviewPayload(val answer: TestAnswerRow, val question: TestQuestionRow)

    suspend fun recommendations(level: String): Recommendations =
        Backend.rpc("recommendations_for_level", buildJsonObject { put("p_level", level) })

    suspend fun latestResult(): TestAttemptRow? {
        val userId = Backend.currentUserId ?: return null
        return Backend.client.from("test_attempts")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "SUBMITTED")
                }
                order("submitted_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<TestAttemptRow>()
            .firstOrNull()
    }

    suspend fun history(): List<TestAttemptRow> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("test_attempts")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "SUBMITTED")
                }
                order("submitted_at", Order.DESCENDING)
                limit(20)
            }
            .decodeList()
    }

    /**
     * Best submitted score per test, for gating a sequence: item N+1 only unlocks once item N
     * was passed. "Passed" is decided by the caller against that test's own `passing_score` —
     * this only reports the highest `percent` the learner has actually achieved, so the
     * comparison stays a plain, checkable number rather than a hidden verdict.
     */
    suspend fun bestPercentByTest(testIds: List<String>): Map<String, Double> {
        val userId = Backend.currentUserId ?: return emptyMap()
        if (testIds.isEmpty()) return emptyMap()
        return Backend.client.from("test_attempts")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "SUBMITTED")
                    isIn("test_id", testIds)
                }
            }
            .decodeList<TestAttemptRow>()
            .groupBy { it.testId }
            .mapValues { (_, attempts) -> attempts.maxOf { it.percent } }
    }
}

/**
 * Whether each test in an ordered list is locked: the first one is always open, and every
 * later one stays locked until the test right before it has been passed. "Passed" means the
 * learner's best submitted `percent` met or beat that specific test's own `passing_score` — a
 * plain, per-test comparison rather than a fixed number, since a teacher's exercise can set its
 * own bar independently of the academy's placement tests.
 */
fun lockStatesFor(tests: List<PlacementTest>, bestPercent: Map<String, Double>): List<Boolean> {
    var previousPassed = true
    return tests.map { test ->
        val locked = !previousPassed
        previousPassed = (bestPercent[test.id] ?: 0.0) >= test.passingScore
        locked
    }
}
