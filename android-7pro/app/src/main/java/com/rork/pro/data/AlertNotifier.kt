package com.rork.pro.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rork.pro.MainActivity
import com.rork.pro.R

/**
 * The loud alert: what an owner, admin or teacher gets instead of a quiet chime.
 *
 * Everything the academy runs on reaches these three roles as a notification — a transfer waiting
 * on approval, a student seated, a payout request, a course sent for review — and every one of
 * them blocks somebody until it is acted on. A silent bar-icon was the wrong treatment for that,
 * so staff alerts play the app's own loud alert tone (res/raw/staff_alert — a short, bright,
 * full-volume notification chime, deliberately NOT the phone's call ringtone), vibrate, and push
 * through as a heads-up banner. The incoming-classroom call has its own channel and is untouched.
 *
 * Two things here are worth knowing before changing them:
 *
 * 1. A notification channel is immutable once created. Sound, vibration and importance are fixed
 *    at the moment of first creation and Android ignores every later edit — which is why the id
 *    carries a version ([STAFF_CHANNEL_ID]). Changing the alert's behaviour means bumping that
 *    number, not editing the channel, or existing installs keep the old silent settings forever.
 *    (The user's own per-channel changes in Android settings are theirs and are never overridden.)
 * 2. The vibration is fired explicitly as well as being set on the channel. The channel covers
 *    the normal case; the explicit pulse covers the device that has the app's notifications
 *    de-prioritised by a battery optimiser, where the channel's own vibration is quietly dropped.
 */
object AlertNotifier {

    /** Bump the suffix to change sound/vibration — see the note above. */
    const val STAFF_CHANNEL_ID = "7pro.staff_urgent.v2"

    /** Earlier channels that used the call ringtone; deleted so they stop showing in settings. */
    private val OLD_STAFF_CHANNEL_IDS = listOf("7pro.staff_urgent.v1")

    /** Everything that is not staff-urgent: students, and quiet informational alerts. */
    const val NORMAL_CHANNEL_ID = "7pro.important"

    /** Used instead of the two channels above whenever the person has switched alert sounds off. */
    const val SILENT_CHANNEL_ID = "7pro.silent"

    private val STAFF_VIBRATION = longArrayOf(0, 500, 200, 500, 200, 800)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        OLD_STAFF_CHANNEL_IDS.forEach { old ->
            if (manager.getNotificationChannel(old) != null) runCatching { manager.deleteNotificationChannel(old) }
        }

        if (manager.getNotificationChannel(SILENT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(SILENT_CHANNEL_ID, "7PRO (silent)", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Alerts without sound, used when alert sounds are switched off in the app"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }

        if (manager.getNotificationChannel(NORMAL_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NORMAL_CHANNEL_ID, "7PRO", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Payments, access changes and academy announcements"
                },
            )
        }

        if (manager.getNotificationChannel(STAFF_CHANNEL_ID) == null) {
            // A notification sound on the notification stream — not a ringtone on the ring stream —
            // so it sounds like an alert, never like an incoming call. Loudness comes from the tone
            // itself, which is mastered close to full scale.
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            manager.createNotificationChannel(
                NotificationChannel(
                    STAFF_CHANNEL_ID,
                    "Urgent — needs your action",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Approvals, transfers and anything waiting on you to act"
                    setSound(alertSoundUri(context), attributes)
                    enableVibration(true)
                    vibrationPattern = STAFF_VIBRATION
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    // Granted only if the person allows 7PRO through Do Not Disturb in Android's
                    // own settings; without that permission the system ignores this line rather
                    // than failing, so it costs nothing to ask.
                    setBypassDnd(true)
                },
            )
        }
    }

    /**
     * Posts one alert. [urgent] picks the loud channel — it is true for owner, admin and teacher
     * accounts, and false for everyone else, so a student's phone keeps behaving exactly as it did.
     *
     * [id] is the server-side notification id, used as the Android notification id too: if the same
     * alert arrives twice (a push and the background poll racing each other) the second one
     * *replaces* the first instead of stacking a duplicate.
     */
    fun show(
        context: Context,
        id: Int,
        title: String,
        body: String,
        route: String?,
        urgent: Boolean,
    ) {
        ensureChannels(context)
        // The person's own switch wins over the role: muted means muted, owner or not.
        val soundOn = AlertSoundPrefs.isEnabled(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PushCenter.EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val built = NotificationCompat.Builder(
            context,
            when {
                !soundOn -> SILENT_CHANNEL_ID
                urgent -> STAFF_CHANNEL_ID
                else -> NORMAL_CHANNEL_ID
            },
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .apply {
                if (!soundOn) {
                    setPriority(NotificationCompat.PRIORITY_LOW)
                    setSilent(true)
                } else if (urgent) {
                    // The channel carries this on Android 8+; these are what make it behave the
                    // same way on older versions, where the channel does not exist at all.
                    setPriority(NotificationCompat.PRIORITY_MAX)
                    setCategory(NotificationCompat.CATEGORY_REMINDER)
                    setVibrate(STAFF_VIBRATION)
                    setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                    setSound(alertSoundUri(context))
                } else {
                    setPriority(NotificationCompat.PRIORITY_DEFAULT)
                }
            }
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, built) }
        if (urgent && soundOn) vibrate(context)
    }

    /** The app's bundled loud alert tone (res/raw/staff_alert.wav). */
    private fun alertSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.staff_alert}")

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(STAFF_VIBRATION, -1))
            } else {
                // VibrationEffect does not exist below Android 8 — the old call still vibrates.
                @Suppress("DEPRECATION")
                vibrator.vibrate(STAFF_VIBRATION, -1)
            }
        }
    }

    /**
     * True when the signed-in account is one of the three roles that run the academy. Read from
     * the cached profile so it works in a background worker and in the FCM service, neither of
     * which has a ViewModel or a live session to ask.
     */
    fun isStaffAccount(): Boolean {
        val profile = SessionCache.profile() ?: return false
        return profile.isStaff || profile.isTeacher
    }
}
