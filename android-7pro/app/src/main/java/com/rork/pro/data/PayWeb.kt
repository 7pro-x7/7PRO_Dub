package com.rork.pro.data

import java.net.URLEncoder

/**
 * Where the 7PRO web payment page (web-pay/public/index.html) is hosted. It is the browser
 * version of the "حجز جروب" screen and of the renewal payment, so a student can pay from a link
 * a teacher or the owner sends them, without opening the app first.
 *
 * Change this single constant if the hostname ever changes.
 */
object PayWeb {
    const val BASE_URL = "https://7pro-pay.7pro7777.workers.dev/"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Renewal link for one student's subscription.
     *
     * Safe to send around: the id alone grants nothing. The page only opens the renewal for the
     * student whose account owns the subscription, and the server re-checks that on every call —
     * anyone else who opens the link is told it belongs to a different account.
     */
    fun renewalUrl(subscriptionId: String): String = "$BASE_URL?renew=${enc(subscriptionId)}"

    /** The full booking flow, optionally jumping straight to one teacher or one group. */
    fun bookingUrl(teacherId: String? = null, groupId: String? = null): String {
        val query = buildList {
            teacherId?.takeIf { it.isNotBlank() }?.let { add("teacher=${enc(it)}") }
            groupId?.takeIf { it.isNotBlank() }?.let { add("group=${enc(it)}") }
        }
        return if (query.isEmpty()) BASE_URL else "$BASE_URL?${query.joinToString("&")}"
    }
}
