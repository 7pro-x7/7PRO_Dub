package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TEACHER_JOIN = "teacher:profiles!courses_teacher_id_fkey(id, full_name, avatar_url)"

/** Read access to the published catalogue: banners, categories, courses and teachers. */
object CatalogRepository {

    suspend fun banners(placement: String = "HOME_HERO"): List<Banner> =
        Backend.client.from("cms_banners")
            .select {
                filter {
                    eq("placement", placement)
                    eq("is_active", true)
                }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun homeShortcuts(): List<HomeShortcut> =
        Backend.client.from("home_shortcuts")
            .select {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun categories(): List<Category> =
        Backend.client.from("categories")
            .select {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun publishedCourses(
        categoryId: String? = null,
        level: String? = null,
        search: String? = null,
        featuredOnly: Boolean = false,
        limit: Long = 40,
    ): List<Course> =
        Backend.client.from("courses")
            .select(Columns.raw("*, $TEACHER_JOIN")) {
                filter {
                    eq("status", "PUBLISHED")
                    categoryId?.let { eq("category_id", it) }
                    level?.let { eq("level", it) }
                    if (featuredOnly) eq("is_featured", true)
                    search?.takeIf { it.isNotBlank() }?.let { ilike("title", "%$it%") }
                }
                order("is_featured", Order.DESCENDING)
                order("rating_avg", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun course(id: String): Course? =
        Backend.client.from("courses")
            .select(Columns.raw("*, $TEACHER_JOIN")) { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun lessons(courseId: String): List<Lesson> =
        Backend.client.from("lessons")
            .select {
                filter { eq("course_id", courseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    /** Curriculum sections used to group the lesson list. Empty for courses that never used them. */
    suspend fun courseSections(courseId: String): List<CourseSection> =
        Backend.client.from("course_sections")
            .select {
                filter { eq("course_id", courseId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun trackCourseView(courseId: String) {
        runCatching { Backend.rpcVoid("track_course_view", buildJsonObject { put("p_course_id", courseId) }) }
    }

    /** Only OWNER-approved teachers who are open for new students are bookable. */
    suspend fun bookableTeachers(level: String? = null): List<TeacherProfile> =
        Backend.client.from("teacher_profiles")
            .select(Columns.raw("*, profile:profiles!teacher_profiles_id_fkey(id, full_name, avatar_url)")) {
                filter {
                    eq("status", "APPROVED")
                    eq("accepting_new_students", true)
                    level?.let { contains("subjects", listOf(it)) }
                }
                order("rating_avg", Order.DESCENDING)
            }
            .decodeList()

    suspend fun teacherProfile(teacherId: String): TeacherProfile? =
        Backend.client.from("teacher_profiles")
            .select(Columns.raw("*, profile:profiles!teacher_profiles_id_fkey(id, full_name, avatar_url)")) {
                filter { eq("id", teacherId) }
            }
            .decodeSingleOrNull()

    suspend fun teacherCourses(teacherId: String): List<Course> =
        Backend.client.from("courses")
            .select(Columns.raw("*, $TEACHER_JOIN")) {
                filter {
                    eq("teacher_id", teacherId)
                    eq("status", "PUBLISHED")
                }
                order("rating_avg", Order.DESCENDING)
            }
            .decodeList()

    suspend fun reviewsFor(
        teacherId: String? = null,
        courseId: String? = null,
        limit: Long = 20,
    ): List<Review> =
        Backend.client.from("reviews")
            .select(Columns.raw("*, author:profiles!reviews_user_id_fkey(id, full_name, avatar_url)")) {
                filter {
                    eq("is_hidden", false)
                    teacherId?.let { eq("teacher_id", it) }
                    courseId?.let { eq("course_id", it) }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun submitReview(
        targetType: String,
        rating: Int,
        body: String,
        teacherId: String? = null,
        courseId: String? = null,
    ) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        Backend.client.from("reviews").insert(
            buildJsonObject {
                put("user_id", userId)
                put("target_type", targetType)
                put("rating", rating)
                put("body", body)
                teacherId?.let { put("teacher_id", it) }
                courseId?.let { put("course_id", it) }
            },
        )
    }

    /** Academy placement tests only — a teacher's exercises are a separate shelf. In the order
     *  the admin arranged them, since later tests unlock only once the earlier ones are passed. */
    suspend fun publishedTests(): List<PlacementTest> =
        Backend.client.from("placement_tests")
            .select {
                filter {
                    eq("status", "PUBLISHED")
                    eq("kind", "PLACEMENT")
                }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    /** Published exercises of one teacher, in the order the teacher arranged them — the same
     *  order that gates which exercise unlocks next. */
    suspend fun teacherExercises(teacherId: String): List<PlacementTest> =
        Backend.client.from("placement_tests")
            .select {
                filter {
                    eq("status", "PUBLISHED")
                    eq("kind", "EXERCISE")
                    eq("owner_id", teacherId)
                }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    /**
     * Teachers a learner can pick from: only those who actually have a published exercise,
     * so the chooser never shows an empty shelf.
     */
    suspend fun exerciseTeachers(): List<TeacherProfile> {
        val published: List<PlacementTest> = Backend.client.from("placement_tests")
            .select {
                filter {
                    eq("status", "PUBLISHED")
                    eq("kind", "EXERCISE")
                }
            }
            .decodeList()
        val ids = published.mapNotNull { it.ownerId }.distinct()
        if (ids.isEmpty()) return emptyList()
        return Backend.client.from("teacher_profiles")
            .select(Columns.raw("*, profile:profiles!teacher_profiles_id_fkey(id, full_name, avatar_url)")) {
                filter { isIn("id", ids) }
                order("rating_avg", Order.DESCENDING)
            }
            .decodeList()
    }

    /** Is the signed-in user an approved student of this teacher? Mirrors the server rule that
     *  gates start_test_attempt, so the UI can show "join this teacher" instead of a dead tap. */
    suspend fun isStudentOf(teacherId: String): Boolean = runCatching {
        Backend.client.postgrest.rpc(
            "is_student_of_teacher",
            buildJsonObject { put("p_teacher", teacherId) },
        ).decodeAs<Boolean>()
    }.getOrDefault(false)

    /** Server-side "may I start this teacher's exercises?" (owner/admin, the teacher themself,
     *  the owner's own exercises, or an approved student). Same rule start_test_attempt enforces. */
    suspend fun canTakeExercise(teacherId: String): Boolean = runCatching {
        Backend.client.postgrest.rpc(
            "can_take_exercise",
            buildJsonObject {
                put("p_owner", teacherId)
                put("p_course", JsonNull)
            },
        ).decodeAs<Boolean>()
    }.getOrDefault(false)

    /** How many published exercises each of those teachers offers. */
    suspend fun exerciseCounts(): Map<String, Int> =
        Backend.client.from("placement_tests")
            .select {
                filter {
                    eq("status", "PUBLISHED")
                    eq("kind", "EXERCISE")
                }
            }
            .decodeList<PlacementTest>()
            .mapNotNull { it.ownerId }
            .groupingBy { it }
            .eachCount()

    suspend fun settings(): Map<String, String> =
        Backend.client.from("app_settings").select().decodeList<AppSetting>()
            .associate { it.key to it.value.toString().trim('"') }
}
