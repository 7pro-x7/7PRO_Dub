package com.rork.pro.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rork.pro.R
import com.rork.pro.classroom.IncomingClassroomCallActivity
import com.rork.pro.data.AlertNotifier
import com.rork.pro.data.PushCenter
import com.rork.pro.data.PushTokenRepository
import com.rork.pro.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Delivery transport for the two alerts that cannot wait for a poll.
 *
 * - `CLASSROOM_CALL` — "your teacher just started a class": rings like a phone call, over the
 *   lock screen, through a full-screen intent.
 * - `STAFF_ALERT` — anything an owner, admin or teacher must act on (a transfer to approve, a
 *   payout request, a course awaiting review). Sent by the `alert-push` Edge Function the moment
 *   the notification row is written, so it lands in seconds instead of waiting up to fifteen
 *   minutes for [com.rork.pro.data.PushCenter]'s background check — and it lands loudly.
 *
 * Everything else still goes through that poll, unchanged.
 */
class ClassroomFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // onNewToken can fire outside any activity/viewmodel lifecycle (e.g. a background token
        // rotation), so this uses its own short-lived scope rather than depending on one.
        CoroutineScope(Dispatchers.IO).launch { PushTokenRepository.onTokenRefreshed(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            "CLASSROOM_CALL" -> {
                val sessionId = data["session_id"]?.takeIf { it.isNotBlank() } ?: return
                showIncomingCall(
                    sessionId = sessionId,
                    title = data["title"].orEmpty(),
                    callerName = data["caller_name"].orEmpty(),
                )
            }
            "STAFF_ALERT" -> showStaffAlert(data)
            else -> Unit
        }
    }

    /**
     * The loud alert for staff and teachers.
     *
     * The server's own notification id doubles as the Android notification id, so if the
     * background poll later reaches the same row it replaces this notification instead of
     * stacking a second copy — and [PushCenter.markDelivered] normally stops that poll from
     * raising it again at all.
     */
    private fun showStaffAlert(data: Map<String, String>) {
        val id = data["notification_id"]?.toLongOrNull() ?: return
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: return
        val kind = data["kind"].orEmpty().uppercase()

        // FCM is sent directly by the backend, so apply the same per-account switch here before
        // raising the urgent alert. Missing preferences stay enabled for backwards compatibility.
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = runCatching {
                SessionRepository.notificationPreferences()
                    .firstOrNull { it.kind.uppercase() == kind }
                    ?.enabled != false
            }.getOrDefault(true)
            if (!enabled) return@launch

            AlertNotifier.show(
                context = this@ClassroomFcmService,
                id = id.toInt(),
                title = title,
                body = data["body"].orEmpty(),
                route = data["route"]?.takeIf { it.isNotBlank() },
                urgent = true,
            )
            PushCenter.markDelivered(this@ClassroomFcmService, id)
        }
    }

    /**
     * Posts a full-screen-intent notification. This — not a direct `startActivity` call from
     * here — is the only supported way to open an Activity over the lock screen / while the app
     * is closed on modern Android: the system itself launches [IncomingClassroomCallActivity]
     * when the device is locked, and shows it as a tappable heads-up alert otherwise (exactly how
     * every calling app's "incoming call" screen works).
     */
    private fun showIncomingCall(sessionId: String, title: String, callerName: String) {
        ensureCallChannel(this)

        val fullScreenIntent = Intent(this, IncomingClassroomCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingClassroomCallActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(IncomingClassroomCallActivity.EXTRA_TITLE, title)
            putExtra(IncomingClassroomCallActivity.EXTRA_CALLER_NAME, callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName.ifBlank { "7PRO" })
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        runCatching { NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val CALL_CHANNEL_ID = "7pro.classroom_call"
        const val CALL_NOTIFICATION_ID = 991100

        /**
         * High importance + a ringtone attached to the channel itself (rather than the
         * notification) is what makes this actually ring instead of just chiming once like a
         * normal alert — Android plays a notification channel's own sound on a loop for a
         * full-screen-intent call notification the way the dialer does.
         */
        fun ensureCallChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CALL_CHANNEL_ID) != null) return

            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(CALL_CHANNEL_ID, "Incoming classes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a teacher starts a live class you're invited to"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
