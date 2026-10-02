package com.rork.pro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** How a group subscription was (or is about to be) paid for. Computed by the server. */
object PaymentSource {
    /** The student paid from the app: a transfer proof sent from their own account. */
    const val APP_PAID = "APP_PAID"

    /** The teacher added it by hand; the money changed hands outside the app. */
    const val TEACHER_MANUAL = "TEACHER_MANUAL"

    /** The student asked from the app but it was activated with no accepted proof of payment. */
    const val NO_PAYMENT = "NO_PAYMENT"

    /** The student sent a transfer proof and it is waiting for review. */
    const val AWAITING_REVIEW = "AWAITING_REVIEW"

    /** The student asked from the app and has not paid yet. */
    const val AWAITING_PAYMENT = "AWAITING_PAYMENT"
}

@Serializable
private data class PaymentSourceRow(
    @SerialName("subscription_id") val subscriptionId: String,
    val source: String? = null,
)

object PaymentSources {

    /**
     * Adds the payment source to each subscription. A failure here must never hide the list itself,
     * so on any error the subscriptions come back unchanged (just without the label).
     */
    /** subscription id -> source, for whichever of [ids] the caller may see. Never throws. */
    suspend fun sourcesOf(ids: List<String>): Map<String, String> {
        val sources = HashMap<String, String>()
        if (ids.isEmpty()) return sources
        runCatching {
            ids.chunked(200).forEach { chunk ->
                val rows: List<PaymentSourceRow> = Backend.rpc(
                    "subscription_payment_sources",
                    buildJsonObject { put("p_subscription_ids", buildJsonArray { chunk.forEach { add(JsonPrimitive(it)) } }) },
                )
                rows.forEach { r -> r.source?.let { sources[r.subscriptionId] = it } }
            }
        }
        return sources
    }

    suspend fun attach(list: List<Subscription>): List<Subscription> {
        if (list.isEmpty()) return list
        val sources = sourcesOf(list.map { it.id })
        if (sources.isEmpty()) return list
        return list.map { sub -> sources[sub.id]?.let { sub.copy(paymentSource = it) } ?: sub }
    }
}
