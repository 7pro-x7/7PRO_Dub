package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * "احجز في أقرب جروب" — the student's way into the teacher-subscription system.
 *
 * Every step here files a request; none of them grants anything. The seat is created as
 * PENDING at the price the owner set, the transfer is attached to it as a claim, and only
 * an owner/admin approval turns it into a real subscription — through the exact same
 * approval path a teacher's own hand-entered student goes through, which is what keeps
 * the earnings calculation identical for both.
 */
object GroupBookingRepository {

    private fun me(): String = Backend.currentUserId ?: error("UNAUTHORIZED")

    // ── Catalogue ─────────────────────────────────────────────────────────

    /** Teachers open for booking right now, each with at least one open group. */
    suspend fun teachers(): List<BookingTeacher> =
        Backend.client.postgrest.rpc("booking_teachers", buildJsonObject { }).decodeList()

    /** The open groups of one teacher, with the seats actually left. */
    suspend fun groups(teacherId: String): List<BookingGroup> =
        Backend.client.postgrest.rpc(
            "booking_groups",
            buildJsonObject { put("p_teacher", teacherId) },
        ).decodeList()

    /**
     * The server's own "nearest group" pick.
     *
     * Matched against the student's latest placement level first, then by how empty the
     * group is — so the shortcut from the result screen lands somewhere sensible instead
     * of on whichever group happens to be first.
     */
    suspend fun nearestGroup(): NearestBooking? =
        runCatching {
            Backend.client.postgrest.rpc("recommend_nearest_booking", buildJsonObject { })
                .decodeList<NearestBooking>()
        }.getOrNull()?.firstOrNull()

    // ── Booking ───────────────────────────────────────────────────────────

    /**
     * Holds a seat in [groupId] for this student.
     *
     * Throws with the server's own error code — ALREADY_SUBSCRIBED, GROUP_NOT_AVAILABLE,
     * TEACHER_NOT_AVAILABLE, GROUP_FULL, PRICE_NOT_SET, PARENT_PHONE_INVALID — which the
     * booking screen maps to copy rather than showing a raw message.
     */
    suspend fun book(
        groupId: String,
        parentName: String,
        parentPhone: String,
        studentName: String,
        note: String? = null,
    ): BookingRequest {
        val result = Backend.rpcRaw(
            "request_group_booking",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_parent_name", parentName.trim())
                put("p_parent_phone", parentPhone.filter(Char::isDigit))
                put("p_student_name", studentName.trim())
                note?.takeIf { it.isNotBlank() }?.let { put("p_note", it.trim()) }
            },
        )
        return Backend.json.decodeFromJsonElement(BookingRequest.serializer(), result)
    }

    /**
     * Books one OR SEVERAL children in [groupId] in one go. The server checks the seats for all
     * of them first and creates every seat or none; each child is its own subscription (own
     * price, own renewal date). Same error codes as [book], plus TOO_MANY_STUDENTS and
     * DUPLICATE_STUDENT.
     */
    suspend fun bookBatch(
        groupId: String,
        parentName: String,
        parentPhone: String,
        students: List<String>,
        note: String? = null,
    ): BookingBatch {
        val result = Backend.rpcRaw(
            "request_group_booking_batch",
            buildJsonObject {
                put("p_group_id", groupId)
                put("p_parent_name", parentName.trim())
                put("p_parent_phone", parentPhone.filter(Char::isDigit))
                put("p_students", kotlinx.serialization.json.JsonArray(students.map { kotlinx.serialization.json.JsonPrimitive(it.trim()) }))
                note?.takeIf { it.isNotBlank() }?.let { put("p_note", it.trim()) }
            },
        )
        return Backend.json.decodeFromJsonElement(BookingBatch.serializer(), result)
    }

    /** Price and seats left of one group (the details step shows the total and limits the rows). */
    suspend fun groupInfo(groupId: String): BookingGroupInfo? =
        runCatching {
            Backend.client.postgrest.rpc("booking_group_info", buildJsonObject { put("p_group", groupId) })
                .decodeList<BookingGroupInfo>()
        }.getOrNull()?.firstOrNull()

    /** What is owed, right now, across these subscriptions — the payment screen's only source. */
    suspend fun paymentState(ids: List<String>): BookingPaymentState {
        val raw = Backend.rpcRaw(
            "booking_payment_state",
            buildJsonObject {
                put("p_ids", kotlinx.serialization.json.JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            },
        )
        return runCatching {
            Backend.json.decodeFromJsonElement(BookingPaymentState.serializer(), raw)
        }.getOrDefault(BookingPaymentState())
    }

    /** One transfer, one proof, for every listed child. */
    suspend fun submitPayments(
        subscriptionIds: List<String>,
        brand: String,
        senderPhone: String,
        proofPath: String,
        note: String? = null,
    ) {
        Backend.rpcVoid(
            "submit_subscription_payments",
            buildJsonObject {
                put("p_subscription_ids", kotlinx.serialization.json.JsonArray(subscriptionIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("p_brand", brand.uppercase())
                put("p_sender_phone", senderPhone.filter(Char::isDigit))
                put("p_proof_path", proofPath)
                note?.takeIf { it.isNotBlank() }?.let { put("p_note", it.trim()) }
            },
        )
    }

    /**
     * Attaches the wallet transfer the student made for an open booking or renewal.
     *
     * The server works out on its own which of the two this money is for, so the app never
     * has to tell it — and can never tell it wrong.
     */
    suspend fun submitPayment(
        subscriptionId: String,
        brand: String,
        senderPhone: String,
        proofPath: String,
        note: String? = null,
    ) {
        Backend.rpcVoid(
            "submit_subscription_payment",
            buildJsonObject {
                put("p_subscription_id", subscriptionId)
                put("p_brand", brand.uppercase())
                put("p_sender_phone", senderPhone.filter(Char::isDigit))
                put("p_proof_path", proofPath)
                note?.takeIf { it.isNotBlank() }?.let { put("p_note", it.trim()) }
            },
        )
    }

    // ── State ─────────────────────────────────────────────────────────────

    /** What the home banner should show, decided entirely by the server. */
    suspend fun state(): SubscriptionState {
        val raw = Backend.rpcRaw("my_subscription_state", buildJsonObject { })
        return runCatching {
            Backend.json.decodeFromJsonElement(SubscriptionState.serializer(), raw)
        }.getOrDefault(SubscriptionState())
    }

    /** The student's own subscription row, when a screen needs more than the banner state. */
    suspend fun mySubscription(): Subscription? =
        Backend.client.from("teacher_subscriptions")
            .select {
                filter {
                    eq("student_user_id", me())
                    neq("status", "PAUSED")
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<Subscription>()
            .firstOrNull()

    /**
     * Opens a renewal request, and is safe to call twice: an already-open request is
     * returned rather than duplicated, so a double tap cannot create two renewals.
     */
    suspend fun requestRenewal(subscriptionId: String) {
        Backend.rpcVoid(
            "request_subscription_renewal",
            buildJsonObject { put("p_subscription_id", subscriptionId) },
        )
    }

    /** The wallets the owner has published for transfers — RLS already hides the unusable ones. */
    suspend fun wallets(): List<ManualPaymentAccount> = CommerceRepository.manualAccounts()
}
