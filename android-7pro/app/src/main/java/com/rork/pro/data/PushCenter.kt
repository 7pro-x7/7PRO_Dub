package com.rork.pro.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Turns the account's notification history into device notifications.
 *
 * Nothing is invented here: every alert is a row the server already wrote — a confirmed payment,
 * an access change, a decision on a course, an owner announcement. Each row is shown at most
 * once (the highest id already delivered is remembered), so nothing repeats or duplicates, and
 * anything the person has already read in the app is never raised on the device.
 */
object PushCenter {

    const val EXTRA_ROUTE = "7pro.route"

    private const val PREFS = "7pro.push"
    private const val KEY_LAST_ID = "last_delivered_id"

    /** Kinds worth interrupting someone for. Anything else stays in the in-app history only. */
    private val IMPORTANT = setOf(
        "PAYMENT",
        "ORDER",
        "ORDER_PAID",
        "REFUND",
        "SUBSCRIPTION",
        "ENROLLMENT",
        "COURSE",
        "COURSE_REVIEW",
        "CERTIFICATE",
        "SECURITY",
        "ACCOUNT",
        "ADMIN",
        "TEACHER",
        "PAYOUT",
        "ANNOUNCEMENT",
        "TEST_RESULT",
        "SUBSCRIPTION_REQUEST",
        "RENEWAL_REQUEST",
        "ACTIVATION_REQUEST",
        "PAYOUT_REQUEST",
        "SUPPORT",
        "CLASSROOM",
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lastDelivered(context: Context): Long = prefs(context).getLong(KEY_LAST_ID, 0L)

    private fun setLastDelivered(context: Context, id: Long) {
        prefs(context).edit().putLong(KEY_LAST_ID, id).apply()
    }

    /** Forgets the delivery watermark, so a new account does not inherit another's history. */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_LAST_ID).apply()
    }

    fun ensureChannel(context: Context) = AlertNotifier.ensureChannels(context)

    /**
     * Raises the delivery watermark for an alert that already reached the device another way —
     * currently the instant FCM push staff and teachers now get (see ClassroomFcmService). Without
     * this the next poll would treat that same row as undelivered and ring it a second time.
     */
    fun markDelivered(context: Context, id: Long) {
        if (id > lastDelivered(context)) setLastDelivered(context, id)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads what is new for the signed-in account and raises it on the device.
     *
     * Safe to call from anywhere and as often as needed: it is a no-op when signed out, when
     * notifications are blocked, or when there is nothing newer than the last delivery.
     */
    suspend fun sync(context: Context) {
        if (!Backend.isConfigured || Backend.currentUserId == null) return
        if (!canPost(context)) return

        val notifications = runCatching { SessionRepository.notifications() }.getOrNull().orEmpty()
        if (notifications.isEmpty()) return
        val preferences = runCatching { SessionRepository.notificationPreferences() }
            .getOrNull()
            .orEmpty()
            .associate { it.kind.uppercase() to it.enabled }

        val newest = notifications.maxOf { it.id }
        val watermark = lastDelivered(context)

        // First run on a device only records where the history is, instead of firing a backlog.
        if (watermark == 0L) {
            setLastDelivered(context, newest)
            return
        }
        if (newest <= watermark) return

        ensureChannel(context)
        // Owner, admin and teacher get the loud treatment; a student's phone is untouched.
        val urgent = AlertNotifier.isStaffAccount()

        notifications
            .filter {
                it.id > watermark &&
                    it.readAt == null &&
                    it.kind.uppercase() in IMPORTANT &&
                    preferences[it.kind.uppercase()] != false
            }
            .sortedBy { it.id }
            .forEach { notification ->
                AlertNotifier.show(
                    context = context,
                    id = notification.id.toInt(),
                    title = notification.title,
                    body = notification.body.orEmpty(),
                    route = notification.deepLink,
                    // A support message is worth a sound for a student too, not only for staff.
                    urgent = urgent || notification.kind.uppercase() == "SUPPORT",
                )
            }

        setLastDelivered(context, newest)
    }
}
