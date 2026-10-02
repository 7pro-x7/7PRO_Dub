package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val OWNER_JOIN = "*, owner:profiles!placement_tests_owner_id_fkey(id, full_name, avatar_url)"

/**
 * Teacher exercises: the same question engine as the placement test, but owned by a teacher.
 *
 * Nothing here decides who may write: the database policies do. A teacher only ever reaches
 * exercises they own, while `tests.manage` holders reach every teacher's exercise — and neither
 * of these calls can touch a placement test, because they are all pinned to `kind = EXERCISE`.
 */
object ExerciseRepository {

    private const val KIND = "EXERCISE"

    /** Exercises created by the signed-in teacher, in the teacher's own drag-and-drop order. */
    suspend fun mine(): List<PlacementTest> {
        val me = Backend.currentUserId ?: error("UNAUTHORIZED")
        return Backend.client.from("placement_tests")
            .select(Columns.raw(OWNER_JOIN)) {
                filter {
                    eq("kind", KIND)
                    eq("owner_id", me)
                }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()
    }

    /**
     * Persists a drag-reordered list of a teacher's exercises: only rows whose position actually
     * changed get written.
     */
    suspend fun reorderExercises(ordered: List<PlacementTest>) {
        ordered.forEachIndexed { index, exercise ->
            if (exercise.sortOrder != index) {
                Backend.client.from("placement_tests")
                    .update(buildJsonObject { put("sort_order", index) }) {
                        filter {
                            eq("id", exercise.id)
                            eq("kind", KIND)
                        }
                    }
            }
        }
    }

    /**
     * Who has sat this exercise, newest first — the teacher's view of their own students.
     *
     * The database decides visibility, not this call: `attempts_own` lets the exercise's owner
     * read attempts on it, and (since migration 20260918000000) `answers_own` lets them read the
     * answers too. A teacher asking about someone else's exercise simply gets nothing back.
     *
     * Profiles are fetched separately rather than as an embed so a missing or restricted profile
     * leaves one row without a name instead of failing the whole list.
     */
    suspend fun attempts(exerciseId: String): List<StudentAttempt> {
        val attempts = Backend.client.from("test_attempts")
            .select {
                filter {
                    eq("test_id", exerciseId)
                    eq("status", "SUBMITTED")
                }
                order("submitted_at", Order.DESCENDING)
            }
            .decodeList<TestAttemptRow>()
        if (attempts.isEmpty()) return emptyList()

        val ids = attempts.mapNotNull { it.userId }.distinct()
        val profiles = if (ids.isEmpty()) {
            emptyMap()
        } else {
            runCatching {
                Backend.client.from("profiles")
                    .select { filter { isIn("id", ids) } }
                    .decodeList<Profile>()
                    .associateBy { it.id }
            }.getOrDefault(emptyMap())
        }
        return attempts.map { StudentAttempt(it, profiles[it.userId]) }
    }

    /** The signed-in teacher's levels (or, for owner/admin, [ownerId]'s), in their own order. */
    suspend fun sections(ownerId: String? = null): List<ExerciseSection> {
        val owner = ownerId ?: Backend.currentUserId ?: error("UNAUTHORIZED")
        return Backend.client.from("exercise_sections")
            .select {
                filter { eq("owner_id", owner) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()
    }

    /** The levels a given teacher's students can see, for the student-facing grouped list. */
    suspend fun sectionsOf(teacherId: String): List<ExerciseSection> =
        Backend.client.from("exercise_sections")
            .select {
                filter { eq("owner_id", teacherId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    /** [ownerId] is set only by owner/admin creating a level for a teacher; otherwise it is the caller. */
    suspend fun createSection(title: String, ownerId: String? = null): ExerciseSection {
        val owner = ownerId ?: Backend.currentUserId ?: error("UNAUTHORIZED")
        val nextOrder = sections(owner).size
        return Backend.client.from("exercise_sections")
            .insert(
                buildJsonObject {
                    put("owner_id", owner)
                    put("title", title.trim())
                    put("sort_order", nextOrder)
                },
            ) { select() }
            .decodeSingle()
    }

    suspend fun renameSection(id: String, title: String) {
        Backend.client.from("exercise_sections")
            .update(buildJsonObject { put("title", title.trim()) }) { filter { eq("id", id) } }
    }

    /**
     * Removes a level. Its exercises are not deleted: the foreign key clears their section_id,
     * so they fall back to the unfiled group rather than vanishing with the level.
     */
    suspend fun deleteSection(id: String) {
        Backend.client.from("exercise_sections").delete { filter { eq("id", id) } }
    }

    suspend fun reorderSections(ordered: List<ExerciseSection>) {
        ordered.forEachIndexed { index, section ->
            if (section.sortOrder != index) {
                Backend.client.from("exercise_sections")
                    .update(buildJsonObject { put("sort_order", index) }) { filter { eq("id", section.id) } }
            }
        }
    }

    /** Files an exercise under a level, or passes null to take it out of every level. */
    suspend fun setSection(exerciseId: String, sectionId: String?) {
        Backend.client.from("placement_tests")
            .update(
                // put(key, String?) writes JSON null for a null value, which is exactly how an
                // exercise is taken out of every level.
                buildJsonObject { put("section_id", sectionId) },
            ) {
                filter {
                    eq("id", exerciseId)
                    eq("kind", KIND)
                }
            }
    }

    /**
     * Exercises linked into one course, in the course's own order — for the course's detail
     * page. One exercise may be linked into many courses. Students only get published ones;
     * starting one is still checked by the server (enrolled in any course it is linked to).
     */
    suspend fun forCourse(courseId: String): List<PlacementTest> =
        CourseContentRepository.panel(courseId).items
            .filter { it.status == "PUBLISHED" }
            .map { it.asPlacementTest() }

    /**
     * Attaches (or clears, passing null) the course this exercise belongs to — an additional
     * filing option alongside [setSection]. Runs through the `set_exercise_course` RPC rather
     * than a plain update so the server can check the course actually belongs to this exercise's
     * teacher before linking it.
     */
    suspend fun setCourse(exerciseId: String, courseId: String?) {
        Backend.client.postgrest.rpc(
            "set_exercise_course",
            buildJsonObject {
                put("p_exercise_id", exerciseId)
                put("p_course_id", courseId)
            },
        )
    }

    /** The teacher's lock. A locked exercise still lists for students; it just can't be started. */
    suspend fun setLocked(exerciseId: String, locked: Boolean) {
        Backend.client.from("placement_tests")
            .update(buildJsonObject { put("is_locked", locked) }) {
                filter {
                    eq("id", exerciseId)
                    eq("kind", KIND)
                }
            }
    }

    /** The owner's published exercises a teacher may copy. The database returns nothing else. */
    suspend fun ownerExercises(): List<OwnerExerciseOffer> =
        Backend.client.postgrest.rpc("owner_exercises_available_to_copy", buildJsonObject { })
            .decodeList()

    /**
     * Copies one owner exercise (with its questions) into the signed-in teacher's account as a
     * draft. The server refuses any exercise that is not the owner's, so this can never pull in
     * another teacher's work.
     */
    suspend fun copyOwnerExercise(exerciseId: String) {
        Backend.client.postgrest.rpc(
            "copy_owner_exercise",
            buildJsonObject { put("p_exercise_id", exerciseId) },
        )
    }

    /**
     * Copies a whole owner level at once: every published exercise in it that this teacher has
     * not copied yet, as drafts inside a level of the same name in the teacher's own account.
     * Returns how many were copied (0 when everything was already copied).
     */
    suspend fun copyOwnerLevel(sectionId: String): Int =
        Backend.client.postgrest.rpc(
            "copy_owner_level",
            buildJsonObject { put("p_section_id", sectionId) },
        ).decodeAs<Int>()

    /**
     * Owner/admin: copies any teacher's exercise (with its questions) into their own account as a
     * draft, to edit, publish or link to a course. The server checks the permission.
     */
    suspend fun staffCopy(exerciseId: String) {
        Backend.client.postgrest.rpc(
            "staff_copy_exercise",
            buildJsonObject { put("p_exercise_id", exerciseId) },
        )
    }

    /** Every teacher's exercise, for the owner/admin console. */
    suspend fun all(): List<PlacementTest> =
        Backend.client.from("placement_tests")
            .select(Columns.raw(OWNER_JOIN)) {
                filter { eq("kind", KIND) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    /**
     * One teacher's exercises, every status, for the owner/admin console's per-teacher view —
     * `all()` scoped down to a single owner, in that teacher's own drag-and-drop order.
     */
    suspend fun ofTeacher(teacherId: String): List<PlacementTest> =
        Backend.client.from("placement_tests")
            .select(Columns.raw(OWNER_JOIN)) {
                filter {
                    eq("kind", KIND)
                    eq("owner_id", teacherId)
                }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun create(
        title: String,
        description: String,
        questionCount: Int,
        timeLimitSeconds: Int,
        teacherId: String? = null,
        /** The level the teacher picked while creating it; null files it under "no level". */
        sectionId: String? = null,
    ): PlacementTest {
        val owner = teacherId ?: Backend.currentUserId ?: error("UNAUTHORIZED")
        val nextOrder = Backend.client.from("placement_tests")
            .select {
                filter {
                    eq("kind", KIND)
                    eq("owner_id", owner)
                }
            }
            .decodeList<JsonObject>()
            .size
        val test = Backend.client.from("placement_tests")
            .insert(
                buildJsonObject {
                    put("kind", KIND)
                    put("owner_id", owner)
                    put("title", title.trim())
                    put("description", description.trim().ifBlank { null })
                    put("is_adaptive", false)
                    put("question_count", questionCount)
                    put("time_limit_seconds", timeLimitSeconds)
                    put("sort_order", nextOrder)
                    put("section_id", sectionId)
                },
            ) { select() }
            .decodeSingle<PlacementTest>()
        Translator.translate("placement_tests", test.id)
        return test
    }

    suspend fun update(id: String, title: String, description: String, questionCount: Int, timeLimitSeconds: Int) {
        Backend.client.from("placement_tests").update(
            buildJsonObject {
                put("title", title.trim())
                put("description", description.trim().ifBlank { null })
                put("question_count", questionCount)
                put("time_limit_seconds", timeLimitSeconds)
            },
        ) {
            filter {
                eq("id", id)
                eq("kind", KIND)
            }
        }
        Translator.translate("placement_tests", id)
    }

    suspend fun setStatus(id: String, status: String) {
        Backend.client.from("placement_tests").update(buildJsonObject { put("status", status) }) {
            filter {
                eq("id", id)
                eq("kind", KIND)
            }
        }
    }

    suspend fun delete(id: String) {
        Backend.client.from("placement_tests").delete {
            filter {
                eq("id", id)
                eq("kind", KIND)
            }
        }
    }

    suspend fun questions(exerciseId: String): List<TestQuestionRow> =
        Backend.client.from("test_questions")
            .select {
                filter { eq("test_id", exerciseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun questionCounts(exerciseIds: List<String>): Map<String, Int> {
        if (exerciseIds.isEmpty()) return emptyMap()
        return Backend.client.from("test_questions")
            .select(Columns.raw("test_id")) { filter { isIn("test_id", exerciseIds) } }
            .decodeList<JsonObject>()
            .mapNotNull { it["test_id"]?.toString()?.trim('"') }
            .groupingBy { it }
            .eachCount()
    }

    /** Media is optional: an exercise question can be plain text or carry image, audio and video. */
    suspend fun addQuestion(
        exerciseId: String,
        kind: String,
        skill: String,
        prompt: String,
        options: List<String>,
        correctIndexes: List<Int>,
        correctText: List<String>,
        explanation: String,
        imageUrl: String,
        audioUrl: String,
        videoUrl: String,
        sortOrder: Int,
    ) {
        Backend.client.from("test_questions").insert(
            buildJsonObject {
                put("test_id", exerciseId)
                put("kind", kind)
                put("skill", skill)
                put("difficulty", 3)
                put("prompt", prompt.trim())
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("correct_indexes", JsonArray(correctIndexes.map { JsonPrimitive(it) }))
                put("correct_text", JsonArray(correctText.map { JsonPrimitive(it) }))
                put("explanation", explanation.trim().ifBlank { null })
                put("media_image_url", imageUrl.trim().ifBlank { null })
                put("media_audio_url", audioUrl.trim().ifBlank { null })
                put("media_video_url", videoUrl.trim().ifBlank { null })
                put("sort_order", sortOrder)
            },
        )
        // Auto-translate exercise question to the other language
        val inserted = Backend.client.from("test_questions")
            .select { filter { eq("test_id", exerciseId) }; order("created_at", Order.DESCENDING); limit(1) }
            .decodeList<JsonObject>()
        val qId = inserted.firstOrNull()?.get("id")?.toString()?.trim('"')
        if (qId != null) Translator.translate("test_questions", qId)
    }

    /**
     * Moves one question into another exercise, placed at the end of that exercise's bank.
     *
     * The row itself moves (same id), so its translations and media travel with it. `tq_write` checks `can_manage_test` on both the old and the new `test_id`, so a
     * teacher can only move questions between exercises they own.
     */
    suspend fun moveQuestion(questionId: String, targetExerciseId: String) {
        val nextOrder = Backend.client.from("test_questions")
            .select(Columns.raw("id")) { filter { eq("test_id", targetExerciseId) } }
            .decodeList<JsonObject>()
            .size
        Backend.client.from("test_questions").update(
            buildJsonObject {
                put("test_id", targetExerciseId)
                put("sort_order", nextOrder)
            },
        ) { filter { eq("id", questionId) } }
    }

    suspend fun deleteQuestion(id: String) {
        Backend.client.from("test_questions").delete { filter { eq("id", id) } }
    }

    /**
     * The teacher's per-question lock. A locked (inactive) question stays in the bank and can
     * still be edited, but `start_test_attempt`/`next_test_question` skip it when serving
     * questions to a student — the same idea as [setLocked] on a whole exercise, one question
     * at a time.
     */
    suspend fun setQuestionActive(id: String, active: Boolean) {
        Backend.client.from("test_questions")
            .update(buildJsonObject { put("is_active", active) }) { filter { eq("id", id) } }
    }

    suspend fun updateQuestion(
        id: String,
        kind: String,
        skill: String,
        prompt: String,
        options: List<String>,
        correctIndexes: List<Int>,
        correctText: List<String>,
        explanation: String,
        imageUrl: String,
        audioUrl: String,
        videoUrl: String,
    ) {
        Backend.client.from("test_questions").update(
            buildJsonObject {
                put("kind", kind)
                put("skill", skill)
                put("prompt", prompt.trim())
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                put("correct_indexes", JsonArray(correctIndexes.map { JsonPrimitive(it) }))
                put("correct_text", JsonArray(correctText.map { JsonPrimitive(it) }))
                put("explanation", explanation.trim().ifBlank { null })
                put("media_image_url", imageUrl.trim().ifBlank { null })
                put("media_audio_url", audioUrl.trim().ifBlank { null })
                put("media_video_url", videoUrl.trim().ifBlank { null })
            },
        ) { filter { eq("id", id) } }
    }
}
