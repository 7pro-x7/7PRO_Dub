package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Everything money related. Prices and orders are always produced by the server —
 * the app never computes or submits an amount.
 */
object CommerceRepository {

    private fun decodeError(response: JsonObject): String? =
        response["error"]?.jsonPrimitive?.contentOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()

    /** Asks the backend what this user, in their detected country, would pay. */
    suspend fun quote(
        itemType: String,
        courseId: String? = null,
        couponCode: String? = null,
        planId: String? = null,
    ): PriceQuote {
        val body = buildJsonObject {
            put("action", "quote")
            put("item_type", itemType)
            courseId?.let { put("course_id", it) }
            // An AI Tutor plan travels in the slot the server reserves for it (live_plan_id).
            planId?.let { put("live_plan_id", it) }
            couponCode?.takeIf { it.isNotBlank() }?.let { put("coupon_code", it) }
        }
        val result = Backend.invokeFunction("checkout", body).jsonObject
        decodeError(result)?.let { error(it) }
        return Backend.json.decodeFromJsonElement(PriceQuote.serializer(), result.getValue("quote"))
    }

    /**
     * Payment options the platform's gateway account actually offers in this currency.
     *
     * An empty list is a valid answer — it means the account exposes only its default
     * integration, and checkout simply proceeds without asking the learner to choose.
     */
    suspend fun paymentMethods(currency: String?): PaymentMethods {
        val body = buildJsonObject {
            put("action", "methods")
            put("item_type", "COURSE")
            currency?.takeIf { it.isNotBlank() }?.let { put("currency", it) }
        }
        val result = Backend.invokeFunction("checkout", body).jsonObject
        decodeError(result)?.let { error(it) }
        return Backend.json.decodeFromJsonElement(PaymentMethods.serializer(), result)
    }

    /**
     * The wallet numbers the owner has published for manual transfers.
     *
     * Row-level security already hides a wallet the owner switched off or never filled in, so
     * whatever comes back here is genuinely payable.
     */
    suspend fun manualAccounts(): List<ManualPaymentAccount> =
        runCatching {
            Backend.client.from("manual_payment_accounts")
                .select { order("sort_order", SortOrder.ASCENDING) }
                .decodeList<ManualPaymentAccount>()
                .filter { it.isUsable }
        }.getOrDefault(emptyList())

    /**
     * Files a transfer the learner has already made, for staff to confirm.
     *
     * The order is created and the proof attached in one server call, so a learner never ends up
     * with a pending order that has no request against it. Access stays closed until an owner or
     * admin approves; this only ever returns "waiting".
     */
    suspend fun submitManualPayment(
        itemType: String,
        courseId: String? = null,
        couponCode: String? = null,
        brand: String,
        senderPhone: String,
        proofPath: String,
        note: String? = null,
        planId: String? = null,
    ): CheckoutSession {
        val body = buildJsonObject {
            put("action", "create")
            put("item_type", itemType)
            courseId?.let { put("course_id", it) }
            planId?.let { put("live_plan_id", it) }
            couponCode?.takeIf { it.isNotBlank() }?.let { put("coupon_code", it) }
            put("manual", true)
            put("manual_brand", brand)
            put("sender_phone", senderPhone)
            put("proof_path", proofPath)
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
            put("idempotency_key", UUID.randomUUID().toString())
        }
        val result = Backend.invokeFunction("checkout", body).jsonObject
        decodeError(result)?.let { error(it) }
        return Backend.json.decodeFromJsonElement(CheckoutSession.serializer(), result)
    }

    /** The learner's own transfer request for a course, so the screen can show where it stands. */
    suspend fun myManualRequest(courseId: String): ManualPaymentRequest? {
        val userId = Backend.currentUserId ?: return null
        return runCatching {
            Backend.client.from("manual_payment_requests")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("course_id", courseId)
                    }
                    order("created_at", SortOrder.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<ManualPaymentRequest>()
        }.getOrNull()
    }

    /**
     * Creates the order server-side and returns the hosted gateway checkout URL.
     *
     * @param methodId the gateway integration the learner picked; the server re-checks that it
     *   belongs to the account and matches the order currency before charging through it.
     * @param walletPhone the mobile-wallet number to bill for Vodafone Cash and its siblings;
     *   the server validates and normalises it before any order is opened.
     */
    suspend fun startCheckout(
        itemType: String,
        courseId: String? = null,
        couponCode: String? = null,
        methodId: String? = null,
        walletPhone: String? = null,
        planId: String? = null,
    ): CheckoutSession {
        val body = buildJsonObject {
            put("action", "create")
            put("item_type", itemType)
            courseId?.let { put("course_id", it) }
            planId?.let { put("live_plan_id", it) }
            couponCode?.takeIf { it.isNotBlank() }?.let { put("coupon_code", it) }
            methodId?.takeIf { it.isNotBlank() }?.let { put("method_id", it) }
            walletPhone?.takeIf { it.isNotBlank() }?.let { put("wallet_phone", it) }
            put("idempotency_key", UUID.randomUUID().toString())
        }
        val result = Backend.invokeFunction("checkout", body).jsonObject
        decodeError(result)?.let { error(it) }
        return Backend.json.decodeFromJsonElement(CheckoutSession.serializer(), result)
    }

    suspend fun order(orderId: String): Order? =
        Backend.client.from("orders").select { filter { eq("id", orderId) } }.decodeSingleOrNull()

    suspend fun myOrders(): List<Order> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("orders")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", SortOrder.DESCENDING)
                limit(100)
            }
            .decodeList()
    }

    suspend fun myEnrollments(): List<Enrollment> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("enrollments")
            .select(
                Columns.raw(
                    "*, course:courses!enrollments_course_id_fkey(*, teacher:profiles!courses_teacher_id_fkey(id, full_name, avatar_url))",
                ),
            ) {
                filter {
                    eq("user_id", userId)
                    eq("status", "ACTIVE")
                }
            }
            .decodeList<Enrollment>()
            .filter { it.hasAccess }
    }

    /**
     * Courses the learner used to have but must pay for again: a free course that became paid, or
     * a monthly subscription that ran out. They appear in "Subscription needed" until renewed.
     */
    suspend fun myLockedEnrollments(): List<Enrollment> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("enrollments")
            .select(
                Columns.raw(
                    "*, course:courses!enrollments_course_id_fkey(*, teacher:profiles!courses_teacher_id_fkey(id, full_name, avatar_url))",
                ),
            ) {
                filter { eq("user_id", userId) }
            }
            .decodeList<Enrollment>()
            .filter { it.needsPayment && it.course?.status == "PUBLISHED" && it.course.isFreeCourse.not() }
    }

    suspend fun myCertificates(): List<Certificate> {
        val userId = Backend.currentUserId ?: return emptyList()
        return Backend.client.from("certificates")
            .select {
                filter { eq("user_id", userId) }
                order("issued_at", SortOrder.DESCENDING)
            }
            .decodeList()
    }
}
