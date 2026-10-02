package com.rork.pro.data

import android.content.Context

/**
 * The person's own on/off switch for 7PRO alert sounds (Profile > Notification settings).
 *
 * It is stored on the device, not the account: a phone left on a desk can be muted without
 * silencing the owner's other phone. It is read from places that have no ViewModel — the FCM
 * service and the background poll — so it lives in plain SharedPreferences. On by default.
 *
 * When off: no ding while a support chat or the team inbox is open, and every 7PRO notification
 * is posted to a silent channel (no sound, no vibration). The notification itself still shows.
 */
object AlertSoundPrefs {
    private const val PREFS = "7pro.push"
    private const val KEY = "alert_sound_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }
}
