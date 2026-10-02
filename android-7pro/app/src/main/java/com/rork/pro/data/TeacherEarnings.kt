package com.rork.pro.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One revenue stream (course sales or subscriptions) as computed by the server. */
@Serializable
data class StreamFigures(
    val total: Double = 0.0,
    @SerialName("teacher_share") val teacherShare: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    val count: Int = 0,
)

/**
 * Everything the earnings cards show for ONE teacher, straight from the server RPC
 * `teacher_earnings_overview`. The teacher's studio and the owner/admin's per-teacher view both
 * read this same object, so the two can never show different numbers.
 */
@Serializable
data class TeacherEarningsOverview(
    val course: StreamFigures = StreamFigures(),
    val subscriptions: StreamFigures = StreamFigures(),
    /** Null when the caller may see earnings but not the wallet (staff without finance.read). */
    val balance: TeacherBalance? = null,
)

@Serializable
internal data class ActiveGroupRow(
    @SerialName("group_name") val groupName: String? = null,
    val total: Double = 0.0,
    @SerialName("teacher_share") val teacherShare: Double = 0.0,
    @SerialName("owner_share") val ownerShare: Double = 0.0,
    @SerialName("subscription_count") val subscriptionCount: Int = 0,
)

/**
 * The single client entry point for a teacher's earnings. Teacher side and owner side both call
 * these — never re-derive the numbers on the device.
 */
object TeacherEarnings {
    /** Rolling [EarningsWindow] course earnings, active-subscription income and wallet. */
    suspend fun overview(teacherId: String): TeacherEarningsOverview {
        val raw = Backend.rpcRaw(
            "teacher_earnings_overview",
            buildJsonObject {
                put("p_teacher", teacherId)
                put("p_from", EarningsWindow.from())
                put("p_to", EarningsWindow.to())
            },
        )
        return Backend.json.decodeFromJsonElement(TeacherEarningsOverview.serializer(), raw)
    }

    /** Active-subscription income by group; always adds up to [overview]'s subscriptions figure.
     *  Pass null for the whole platform (staff with analytics.read / finance.read only). */
    suspend fun activeGroups(teacherId: String?): List<ActiveSubscriptionGroup> =
        Backend.client.postgrest.rpc(
            "subscription_active_groups",
            buildJsonObject { teacherId?.let { put("p_teacher", it) } },
        ).decodeList<ActiveGroupRow>().map {
            ActiveSubscriptionGroup(
                groupName = it.groupName.orEmpty(),
                monthlyTotal = it.total,
                teacherShare = it.teacherShare,
                ownerShare = it.ownerShare,
                count = it.subscriptionCount,
            )
        }
}
