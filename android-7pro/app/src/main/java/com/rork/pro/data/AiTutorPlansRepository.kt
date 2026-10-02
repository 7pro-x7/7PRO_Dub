package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Owner-side numbers for the AI Tutor's paid plans (from `ai_tutor_sales_summary`). */
data class AiTutorSales(
    val activeSubscribers: Int = 0,
    val paidOrders: Int = 0,
    /** Revenue by currency, after refunds. */
    val revenue: Map<String, Double> = emptyMap(),
    /** Replies sold in packs, how many were used, and how many expired unused. */
    val repliesSold: Int = 0,
    val repliesUsed: Int = 0,
    val repliesExpired: Int = 0,
)

/**
 * The AI Tutor's paid plans. Everything about a plan — price, price after discount, how long it
 * lasts, how many replies a day it gives — is set by the owner and enforced by the server: the app
 * only ever shows what the database says and never computes what a buyer owes (the checkout
 * function quotes and charges; see CommerceRepository).
 */
object AiTutorPlansRepository {

    /** Plans a student can buy right now, cheapest first within the owner's own ordering. */
    suspend fun activePlans(): List<AiTutorPlan> =
        Backend.client.from("ai_tutor_plans")
            .select {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<AiTutorPlan>()
            .sortedWith(compareBy({ it.sortOrder }, { it.price }))

    /** Every plan, including switched-off ones (row-level security shows those to the owner only). */
    suspend fun allPlans(): List<AiTutorPlan> =
        Backend.client.from("ai_tutor_plans")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<AiTutorPlan>()
            .sortedWith(compareBy({ it.sortOrder }, { it.price }))

    /** Fields the owner edits. [saleEndsInDays] null keeps the current end date; 0 clears it (no end). */
    data class PlanInput(
        val name: String,
        val nameAr: String,
        val description: String,
        val descriptionAr: String,
        val price: Double,
        val salePrice: Double?,
        val saleEndsInDays: Int?,
        val currency: String,
        val periodDays: Int,
        val replies: Int,
        val sortOrder: Int,
        val isActive: Boolean,
    )

    private fun PlanInput.toJson(): JsonObject = buildJsonObject {
        put("name", name.trim())
        put("name_ar", nameAr.trim())
        put("description", description.trim())
        put("description_ar", descriptionAr.trim())
        put("price", price)
        // The database refuses a sale price that is not below the price, so an empty or too-high one is sent as "no sale".
        put("sale_price", salePrice?.takeIf { it >= 0 && it < price })
        if (saleEndsInDays != null) {
            put(
                "sale_ends_at",
                if (saleEndsInDays > 0 && salePrice != null) {
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(saleEndsInDays.toLong()).toString()
                } else {
                    null
                },
            )
        }
        put("currency", currency.trim().uppercase())
        put("period_days", periodDays)
        put("replies", replies)
        put("sort_order", sortOrder)
        put("is_active", isActive)
    }

    suspend fun createPlan(input: PlanInput) {
        Backend.client.from("ai_tutor_plans").insert(input.toJson())
    }

    suspend fun updatePlan(id: String, input: PlanInput) {
        Backend.client.from("ai_tutor_plans").update(input.toJson()) { filter { eq("id", id) } }
    }

    suspend fun setActive(id: String, active: Boolean) {
        Backend.client.from("ai_tutor_plans")
            .update(buildJsonObject { put("is_active", active) }) { filter { eq("id", id) } }
    }

    /** Fails if orders already point at the plan: switch it off instead. */
    suspend fun deletePlan(id: String) {
        Backend.client.from("ai_tutor_plans").delete { filter { eq("id", id) } }
    }

    /** Free replies every student gets each day, before any plan (0 = the tutor is paid-only). */
    suspend fun setFreeDailyReplies(count: Int) {
        Backend.rpcVoid("ai_tutor_configure", buildJsonObject { put("p_per_user_daily", count.coerceIn(0, 1000)) })
    }

    suspend fun sales(): AiTutorSales {
        val raw = Backend.rpcRaw("ai_tutor_sales_summary") as? JsonObject ?: return AiTutorSales()
        fun int(key: String) = (raw[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
        val revenue = (raw["revenue"] as? JsonObject).orEmpty().mapNotNull { (currency, value) ->
            (value as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.let { currency to it }
        }.toMap()
        return AiTutorSales(int("active_subscribers"), int("paid_orders"), revenue, int("replies_sold"), int("replies_used"), int("replies_expired"))
    }
}
