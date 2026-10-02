package com.rork.pro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive

/** A user found while choosing who to open a course for. */
@Serializable
data class GrantUser(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /** Already inside the course (bought it, subscribed or was granted). */
    @SerialName("has_access") val hasAccess: Boolean = false,
    /** Inside because someone opened it for them by hand, so it can be closed again. */
    @SerialName("is_granted") val isGranted: Boolean = false,
)

/** One person the course is currently open for by hand. */
@Serializable
data class GrantedUser(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("granted_at") val grantedAt: String? = null,
    @SerialName("granted_by_name") val grantedByName: String? = null,
)

/** A course a grant screen can work on, whichever way the list was obtained. */
@Serializable
data class GrantCourse(
    val id: String,
    val title: String,
    @SerialName("title_ar") val titleAr: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
) {
    val displayTitle: String get() = lang(title, titleAr, titleEn)
}

/** What the signed-in teacher may do: switched on by the owner, limited to these courses. */
@Serializable
data class MyGrantScope(
    val enabled: Boolean = false,
    val courses: List<GrantCourse> = emptyList(),
)

/** The owner's view of one teacher's permission. */
@Serializable
data class TeacherGrantPermission(
    val enabled: Boolean = false,
    @SerialName("course_ids") val courseIds: List<String> = emptyList(),
)

@Serializable
data class GrantResult(val ok: Boolean = false, val reason: String? = null)

/**
 * Opening a paid course for a chosen person, and closing it again.
 *
 * Every rule (who may do it, for which course, for which people) is enforced by the server
 * functions; the app only asks and shows the answer.
 */
object CourseGrantRepository {

    suspend fun searchUsers(courseId: String, query: String): List<GrantUser> =
        Backend.rpc(
            "grant_course_search_users",
            buildJsonObject {
                put("p_course", courseId)
                put("p_query", query)
            },
        )

    suspend fun grant(userId: String, courseId: String): GrantResult =
        Backend.rpc(
            "grant_course_access",
            buildJsonObject {
                put("p_user", userId)
                put("p_course", courseId)
            },
        )

    suspend fun revoke(userId: String, courseId: String): GrantResult =
        Backend.rpc(
            "revoke_course_grant",
            buildJsonObject {
                put("p_user", userId)
                put("p_course", courseId)
            },
        )

    suspend fun granted(courseId: String): List<GrantedUser> =
        Backend.rpc("list_course_grants", buildJsonObject { put("p_course", courseId) })

    /** Owner / staff: every paid course. */
    suspend fun staffCourses(): List<GrantCourse> =
        CourseStudioRepository.courses(CourseScope.ALL)
            .filter { !it.isFreeCourse }
            .map { GrantCourse(it.id, it.title, it.titleAr, it.titleEn) }

    /** Teacher: only what the owner allowed. */
    suspend fun myScope(): MyGrantScope = Backend.rpc("my_grant_scope")

    // ---- the owner switching this on for a teacher ----

    suspend fun teacherPermission(teacherId: String): TeacherGrantPermission =
        Backend.rpc("get_teacher_grant_permission", buildJsonObject { put("p_teacher", teacherId) })

    suspend fun setTeacherPermission(teacherId: String, enabled: Boolean?, courseIds: List<String>?) {
        Backend.rpcVoid(
            "set_teacher_grant_permission",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_enabled", enabled)
                if (courseIds != null) put("p_course_ids", buildJsonArray { courseIds.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }
}
