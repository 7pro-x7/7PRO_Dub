package com.rork.pro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where a teacher's subscriber stands today. Computed by the server with the teacher's own rules. */
object SubState {
    const val ACTIVE = "ACTIVE"
    const val DUE = "DUE"            // renewal within 7 days
    const val OVERDUE = "OVERDUE"
    const val PAUSED = "PAUSED"
    const val PENDING = "PENDING"    // waiting for approval
    const val REJECTED = "REJECTED"
    val ALL = listOf(OVERDUE, DUE, ACTIVE, PAUSED, PENDING, REJECTED)
}

@Serializable
data class TeacherSubscriber(
    @SerialName("subscription_id") val subscriptionId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String? = null,
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("group_name") val groupName: String,
    val level: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("next_renewal_date") val nextRenewalDate: String,
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("monthly_amount") val monthlyAmount: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("billing_cycle") val billingCycle: String = "MONTHLY",
    val state: String = SubState.ACTIVE,
    val notes: String? = null,
    @SerialName("student_user_id") val studentUserId: String? = null,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("account_email") val accountEmail: String? = null,
    @SerialName("account_avatar") val accountAvatar: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TeacherSubscribersByState(
    @SerialName("ACTIVE") val active: Int = 0,
    @SerialName("DUE") val due: Int = 0,
    @SerialName("OVERDUE") val overdue: Int = 0,
    @SerialName("PAUSED") val paused: Int = 0,
    @SerialName("PENDING") val pending: Int = 0,
    @SerialName("REJECTED") val rejected: Int = 0,
) {
    fun of(state: String): Int = when (state) {
        SubState.ACTIVE -> active
        SubState.DUE -> due
        SubState.OVERDUE -> overdue
        SubState.PAUSED -> paused
        SubState.PENDING -> pending
        SubState.REJECTED -> rejected
        else -> 0
    }
    /** Everything except the rejected ones: what the default list shows. */
    val shown: Int get() = active + due + overdue + paused + pending
}

@Serializable
data class TeacherSubscribersSummary(
    /** Subscriptions that count (approved: active, due, overdue or paused). */
    val total: Int = 0,
    /** Distinct people behind them. */
    val students: Int = 0,
    val teachers: Int = 0,
    @SerialName("monthly_value") val monthlyValue: Double = 0.0,
    val currency: String = "EGP",
    @SerialName("by_state") val byState: TeacherSubscribersByState = TeacherSubscribersByState(),
)

/** Everyone subscribed with a teacher. Reads and dates go through server functions that check the permission. */
object TeacherSubscribersRepository {

    suspend fun summary(teacherId: String?): TeacherSubscribersSummary =
        Backend.rpc("admin_teacher_subscribers_summary", buildJsonObject { put("p_teacher", teacherId) })

    suspend fun list(teacherId: String?, state: String?, query: String?): List<TeacherSubscriber> =
        Backend.rpc(
            "admin_teacher_subscribers",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_state", state)
                put("p_query", query?.trim()?.takeIf { it.isNotEmpty() })
                put("p_limit", 400)
            },
        )

    suspend fun setDates(subscriptionId: String, start: String, next: String) {
        Backend.rpcVoid(
            "admin_set_subscription_dates",
            buildJsonObject {
                put("p_subscription", subscriptionId)
                put("p_start", start)
                put("p_next", next)
            },
        )
    }

    suspend fun setPaused(subscriptionId: String, paused: Boolean) {
        Backend.rpcVoid(
            "set_subscription_paused",
            buildJsonObject {
                put("p_subscription_id", subscriptionId)
                put("p_paused", paused)
            },
        )
    }
}
