package com.rork.pro.data

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * The short "new message" sound for the support chat, played while a chat or the team inbox is
 * open on screen. It uses the phone's own notification tone on the notification stream, so it
 * follows the notification volume and stays silent on a muted phone.
 *
 * When the app is closed or in the background the sound comes from the push notification instead.
 */
object SupportSounds {
    private var lastPlayedAt = 0L

    fun playIncoming(context: Context) {
        if (!AlertSoundPrefs.isEnabled(context)) return
        val now = System.currentTimeMillis()
        // Several messages landing together should ding once, not stack.
        if (now - lastPlayedAt < 1_200) return
        lastPlayedAt = now
        runCatching {
            val app = context.applicationContext
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val tone = RingtoneManager.getRingtone(app, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            tone.play()
        }
    }
}
