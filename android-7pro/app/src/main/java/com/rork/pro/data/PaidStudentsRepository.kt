package com.rork.pro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How a student got into a paid course. */
object PaidSource {
    const val PAID = "PAID"          // paid money
    const val MONTHLY = "MONTHLY"    // monthly subscription
    const val COUPON = "COUPON"      // a coupon covered it
    const val GRANTED = "GRANTED"    // opened by hand by the owner / a teacher
    const val FREE_ERA = "FREE_ERA"  // joined while the course was free, never paid
    val ALL = listOf(PAID, MONTHLY, COUPON, GRANTED, FREE_ERA)
}

/** Whether the student can open the course right now. */
object PaidState {
    const val ACTIVE = "ACTIVE"
    const val EXPIRED = "EXPIRED"  // monthly subscription ran out
    const val CLOSED = "CLOSED"    // access is closed
    val ALL = listOf(ACTIVE, EXPIRED, CLOSED)
}

@Serializable
data class PaidStudent(
    @SerialName("enrollment_id") val enrollmentId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("course_id") val courseId: String,
    @SerialName("course_title") val courseTitle: String,
    @SerialName("course_title_ar") val courseTitleAr: String? = null,
    @SerialName("course_title_en") val courseTitleEn: String? = null,
    val source: String = PaidSource.FREE_ERA,
    val state: String = PaidState.CLOSED,
    @SerialName("access_type") val accessType: String = "ONE_TIME",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("paid_total") val paidTotal: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("last_paid_at") val lastPaidAt: String? = null,
    @SerialName("enrolled_at") val enrolledAt: String? = null,
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "—"
    val displayCourse: String get() = lang(courseTitle, courseTitleAr, courseTitleEn)
    val isMonthly: Boolean get() = accessType == "MONTHLY"
}

@Serializable
data class PaidStudentsBySource(
    @SerialName("PAID") val paid: Int = 0,
    @SerialName("MONTHLY") val monthly: Int = 0,
    @SerialName("COUPON") val coupon: Int = 0,
    @SerialName("GRANTED") val granted: Int = 0,
    @SerialName("FREE_ERA") val freeEra: Int = 0,
) {
    fun of(source: String): Int = when (source) {
        PaidSource.PAID -> paid
        PaidSource.MONTHLY -> monthly
        PaidSource.COUPON -> coupon
        PaidSource.GRANTED -> granted
        PaidSource.FREE_ERA -> freeEra
        else -> 0
    }
    val total: Int get() = paid + monthly + coupon + granted + freeEra
}

@Serializable
data class PaidStudentsByState(
    @SerialName("ACTIVE") val active: Int = 0,
    @SerialName("EXPIRED") val expired: Int = 0,
    @SerialName("CLOSED") val closed: Int = 0,
) {
    fun of(state: String): Int = when (state) {
        PaidState.ACTIVE -> active
        PaidState.EXPIRED -> expired
        PaidState.CLOSED -> closed
        else -> 0
    }
}

@Serializable
data class PaidStudentsSummary(
    /** Distinct people on any paid course. */
    val students: Int = 0,
    /** Distinct people who paid, subscribed monthly or used a coupon. */
    val buyers: Int = 0,
    val enrollments: Int = 0,
    @SerialName("paid_total") val paidTotal: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("by_source") val bySource: PaidStudentsBySource = PaidStudentsBySource(),
    @SerialName("by_state") val byState: PaidStudentsByState = PaidStudentsByState(),
)

/** Everyone on a paid course, and the owner's controls over their access. The server checks every permission. */
object PaidStudentsRepository {

    suspend fun summary(courseId: String?): PaidStudentsSummary =
        Backend.rpc("admin_paid_students_summary", buildJsonObject { put("p_course", courseId) })

    suspend fun list(
        courseId: String?,
        source: String?,
        state: String?,
        query: String?,
        userId: String? = null,
    ): List<PaidStudent> =
        Backend.rpc(
            "admin_paid_students",
            buildJsonObject {
                put("p_course", courseId)
                put("p_source", source)
                put("p_state", state)
                put("p_query", query?.trim()?.takeIf { it.isNotEmpty() })
                put("p_limit", 300)
                put("p_user", userId)
            },
        )

    suspend fun close(enrollmentId: String) = action(enrollmentId, "CLOSE")
    suspend fun open(enrollmentId: String) = action(enrollmentId, "OPEN")
    suspend fun extend(enrollmentId: String, days: Int = 30) = action(enrollmentId, "EXTEND", days)

    private suspend fun action(enrollmentId: String, action: String, days: Int = 30) {
        Backend.rpcVoid(
            "admin_enrollment_action",
            buildJsonObject {
                put("p_enrollment", enrollmentId)
                put("p_action", action)
                put("p_days", days)
            },
        )
    }
}
