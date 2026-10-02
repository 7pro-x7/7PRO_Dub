package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val COURSE_TEACHER_JOIN = "teacher:profiles!courses_teacher_id_fkey(id, full_name, avatar_url)"

/** Which courses a dashboard may work on. */
enum class CourseScope {
    /** A teacher editing their own catalogue. */
    MINE,

    /** The owner or an admin holding `courses.manage`, working across every teacher. */
    ALL,
}

/** Everything the editor needs for one course, loaded in a single pass. */
data class CourseDraft(
    val course: Course,
    val sections: List<CourseSection>,
    val lessons: List<Lesson>,
) {
    /** Lessons that were never filed under a section, shown after the sectioned ones. */
    val looseLessons: List<Lesson> get() = lessons.filter { it.sectionId == null }

    fun lessonsIn(sectionId: String): List<Lesson> = lessons.filter { it.sectionId == sectionId }
}

/**
 * Course authoring for both dashboards.
 *
 * Row level security is the real boundary: a teacher can only touch rows belonging to their own
 * courses, while `courses.manage` holders reach every course. The scope below simply keeps the
 * queries honest so a teacher never even asks for another teacher's rows.
 */
object CourseStudioRepository {

    private fun me(): String = Backend.currentUserId ?: error("UNAUTHORIZED")

    suspend fun courses(scope: CourseScope, search: String? = null, status: String? = null): List<Course> =
        Backend.client.from("courses")
            .select(Columns.raw("*, $COURSE_TEACHER_JOIN")) {
                filter {
                    if (scope == CourseScope.MINE) eq("teacher_id", me())
                    status?.takeIf { it.isNotBlank() }?.let { eq("status", it) }
                    search?.takeIf { it.isNotBlank() }?.let { ilike("title", "%$it%") }
                }
                order("updated_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList()

    suspend fun draft(courseId: String): CourseDraft {
        val course: Course = Backend.client.from("courses")
            .select(Columns.raw("*, $COURSE_TEACHER_JOIN")) { filter { eq("id", courseId) } }
            .decodeSingle()
        return CourseDraft(course, sections(courseId), lessons(courseId))
    }

    suspend fun sections(courseId: String): List<CourseSection> =
        Backend.client.from("course_sections")
            .select {
                filter { eq("course_id", courseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun lessons(courseId: String): List<Lesson> =
        Backend.client.from("lessons")
            .select {
                filter { eq("course_id", courseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    /** Teachers available to own a course, offered to staff when they create one. */
    suspend fun teacherOptions(): List<Profile> =
        Backend.client.from("profiles")
            .select {
                filter { eq("role", "TEACHER") }
                order("full_name", Order.ASCENDING)
                limit(200)
            }
            .decodeList()

    // ------------------------------------------------------------------ course

    suspend fun createCourse(title: String, teacherId: String? = null): Course =
        Backend.client.from("courses")
            .insert(
                buildJsonObject {
                    put("teacher_id", teacherId ?: me())
                    put("title", title)
                    put("status", "DRAFT")
                },
            ) { select(Columns.raw("*, $COURSE_TEACHER_JOIN")) }
            .decodeSingle<Course>()

    suspend fun saveCourse(
        id: String,
        title: String,
        subtitle: String,
        description: String,
        thumbnailUrl: String,
        level: String,
        categoryId: String?,
        price: Double,
        currency: String,
        isFree: Boolean,
        certificateEnabled: Boolean,
        teacherId: String? = null,
        monthlyPrice: Double? = null,
        monthlyOnly: Boolean = false,
    ): Course {
        val course = Backend.client.from("courses")
            .update(
                buildJsonObject {
                    put("title", title)
                    put("subtitle", subtitle.ifBlank { null })
                    put("description", description.ifBlank { null })
                    put("thumbnail_url", thumbnailUrl.ifBlank { null })
                    put("level", level.ifBlank { null })
                    put("category_id", categoryId)
                    put("base_price", if (isFree) 0.0 else price)
                    // Always sent, so clearing the field (or making the course free) switches
                    // the monthly option off instead of leaving a stale price behind.
                    put("monthly_price", if (isFree) null else monthlyPrice)
                    // Subscription-only: the server needs a monthly price and derives the rest itself.
                    put("monthly_only", !isFree && monthlyOnly)
                    put("base_currency", currency.ifBlank { "EGP" })
                    put("is_free", isFree)
                    put("certificate_enabled", certificateEnabled)
                    teacherId?.let { put("teacher_id", it) }
                },
            ) {
                select(Columns.raw("*, $COURSE_TEACHER_JOIN"))
                filter { eq("id", id) }
            }
            .decodeSingle<Course>()
        // Auto-translate title/subtitle/description to the other language
        Translator.translate("courses", id)
        return course
    }

    /**
     * Moves a course between draft, review and published.
     *
     * The database decides what the caller is actually allowed to do: staff set any status,
     * a teacher publishes directly only while the owner leaves `courses.teacher_direct_publish`
     * on, otherwise the request is turned into a review submission.
     */
    suspend fun setStatus(courseId: String, status: String, note: String? = null): Course =
        Backend.rpc(
            "set_course_status",
            buildJsonObject {
                put("p_course_id", courseId)
                put("p_status", status)
                put("p_note", note)
            },
        )

    suspend fun deleteCourse(courseId: String) {
        Backend.rpcVoid("delete_course", buildJsonObject { put("p_course_id", courseId) })
    }

    /** Force-delete a course even if students are enrolled. Owner/admin only. */
    suspend fun deleteCourseForce(courseId: String) {
        Backend.rpcVoid("delete_course_force", buildJsonObject { put("p_course_id", courseId) })
    }

    // ----------------------------------------------------------------- sections

    suspend fun addSection(courseId: String, title: String, sortOrder: Int) {
        Backend.client.from("course_sections").insert(
            buildJsonObject {
                put("course_id", courseId)
                put("title", title)
                put("sort_order", sortOrder)
            },
        )
    }

    suspend fun renameSection(id: String, title: String) {
        Backend.client.from("course_sections")
            .update(buildJsonObject { put("title", title) }) { filter { eq("id", id) } }
        Translator.translate("course_sections", id)
    }

    suspend fun deleteSection(id: String) {
        Backend.client.from("course_sections").delete { filter { eq("id", id) } }
    }

    /** Persists a new running order after a move; lessons and sections share this shape. */
    suspend fun reorderSections(ordered: List<CourseSection>) {
        ordered.forEachIndexed { index, section ->
            if (section.sortOrder != index) {
                Backend.client.from("course_sections")
                    .update(buildJsonObject { put("sort_order", index) }) { filter { eq("id", section.id) } }
            }
        }
    }

    // ------------------------------------------------------------------ lessons

    suspend fun saveLesson(
        id: String?,
        courseId: String,
        sectionId: String?,
        title: String,
        kind: String,
        videoUrl: String,
        documentUrl: String,
        content: String,
        durationSeconds: Int,
        sortOrder: Int,
        isPreview: Boolean,
        /** Reading-lesson blocks; null keeps the lesson a plain one (video, file, quiz). */
        blocks: List<LessonBlock>? = null,
        allowPdf: Boolean = true,
        allowTxt: Boolean = true,
    ) {
        val cleanBlocks = blocks?.filter { block ->
            when (block.type) {
                LessonBlock.IMAGE -> !block.url.isNullOrBlank()
                LessonBlock.LIST -> block.items.any { it.isNotBlank() }
                else -> block.text.isNotBlank()
            }
        }?.map { block -> block.copy(items = block.items.filter { it.isNotBlank() }) }
        // A block lesson also keeps its whole text in `content`, so search, translation, the web
        // pages and older app builds still have something to show.
        val plainContent = if (cleanBlocks.isNullOrEmpty()) content else cleanBlocks.toPlainText(title)
        val payload = buildJsonObject {
            put("course_id", courseId)
            put("section_id", sectionId)
            put("title", title)
            put("kind", kind)
            put("video_url", videoUrl.ifBlank { null })
            put("document_url", documentUrl.ifBlank { null })
            put("content", plainContent.ifBlank { null })
            put("duration_seconds", durationSeconds)
            put("sort_order", sortOrder)
            put("is_preview", isPreview)
            put(
                "blocks",
                if (cleanBlocks.isNullOrEmpty()) {
                    kotlinx.serialization.json.JsonNull
                } else {
                    Backend.json.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(LessonBlock.serializer()),
                        cleanBlocks,
                    )
                },
            )
            put("allow_pdf", allowPdf)
            put("allow_txt", allowTxt)
        }
        if (id == null) {
            Backend.client.from("lessons").insert(payload)
        } else {
            Backend.client.from("lessons").update(payload) { filter { eq("id", id) } }
        }
        // Auto-translate lesson content to the other language
        val lessonId = id ?: Backend.client.from("lessons")
            .select { filter { eq("course_id", courseId) }; order("created_at", Order.DESCENDING); limit(1) }
            .decodeList<Lesson>().firstOrNull()?.id
        if (lessonId != null) Translator.translate("lessons", lessonId)
    }

    suspend fun deleteLesson(id: String) {
        Backend.client.from("lesson_quizzes").delete { filter { eq("lesson_id", id) } }
        Backend.client.from("lessons").delete { filter { eq("id", id) } }
    }

    suspend fun reorderLessons(ordered: List<Lesson>) {
        ordered.forEachIndexed { index, lesson ->
            if (lesson.sortOrder != index) {
                Backend.client.from("lessons")
                    .update(buildJsonObject { put("sort_order", index) }) { filter { eq("id", lesson.id) } }
            }
        }
    }

    /**
     * Rewrites `sort_order` across the whole course so it reads section by section.
     *
     * Lessons carry one running order for the entire course — that is what the student player
     * follows — so adding, deleting or moving anything has to renumber the rest, otherwise a
     * lesson added to the first section would play after the last one.
     */
    suspend fun resequence(courseId: String) {
        val sections = sections(courseId)
        val lessons = lessons(courseId)
        val ordered = buildList {
            sections.forEach { section -> addAll(lessons.filter { it.sectionId == section.id }) }
            addAll(lessons.filter { it.sectionId == null })
        }
        reorderLessons(ordered)
    }
}
