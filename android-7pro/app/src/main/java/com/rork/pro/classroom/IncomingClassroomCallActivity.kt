package com.rork.pro.classroom

import android.app.KeyguardManager
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.rork.pro.MainActivity
import com.rork.pro.data.PushCenter
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.screens.classroom.ClassroomRoutes
import com.rork.pro.ui.theme.AppTheme
import com.rork.pro.ui.theme.Ink

/**
 * The "someone is calling you" screen: opened by [com.rork.pro.push.ClassroomFcmService]'s
 * full-screen-intent notification, over the lock screen if needed. Accept hands off into the
 * normal join flow (the lobby screen, exactly like tapping a session from the list); Decline just
 * dismisses — nothing is recorded server-side either way, the same as declining a phone call.
 */
class IncomingClassroomCallActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()

        startRinging()
        // The ringtone/vibration loop had no end: an unanswered call rang until someone touched
        // the phone. Treat it like a missed call after a normal ring duration.
        lifecycleScope.launch {
            kotlinx.coroutines.delay(RING_TIMEOUT_MS)
            if (!isFinishing) decline()
        }

        setContent {
            AppTheme {
                IncomingCallScreen(
                    callerName = callerName.ifBlank { "7PRO" },
                    title = title,
                    onAccept = { accept(sessionId) },
                    onDecline = { decline() },
                )
            }
        }
    }

    /** Manifest attributes (showWhenLocked/turnScreenOn) already cover API 27+; these calls are
     *  what actually does the work and are also the only mechanism on API 24-26. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        runCatching {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            // A ringtone plays once by default — most are a few seconds long, so the "call"
            // went silent long before the 45 s ring window ended and students missed it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
            ringtone?.play()
        }
        runCatching {
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
    }

    private fun dismissCallNotification() {
        runCatching {
            NotificationManagerCompat.from(this).cancel(com.rork.pro.push.ClassroomFcmService.CALL_NOTIFICATION_ID)
        }
    }

    /** Hands off to the normal in-app join flow — the lobby screen, which joins automatically
     *  here (autoJoin) — rather than trying to join the call directly from this activity. */
    private fun accept(sessionId: String) {
        stopRinging()
        dismissCallNotification()

        // Below Android 8.0 the in-app video cannot run, so answering opens the web classroom in
        // the browser, straight into this session (see ClassroomRepository.browserJoinUrl),
        // instead of a lobby screen that could only tell them the same thing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                com.rork.pro.ui.screens.classroom.openClassroomInBrowser(this@IncomingClassroomCallActivity, sessionId)
                finish()
            }
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // autoJoin: answering the call already means "take me in", so the lobby joins
            // by itself instead of asking for a second tap on "Join now".
            putExtra(PushCenter.EXTRA_ROUTE, ClassroomRoutes.lobby(sessionId, autoJoin = true))
        }
        startActivity(intent)
        finish()
    }

    private fun decline() {
        stopRinging()
        dismissCallNotification()
        finish()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CALLER_NAME = "caller_name"
        private const val RING_TIMEOUT_MS = 45_000L
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    title: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Ink.Canvas)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                tr(StrClassroom.incomingCall),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Ink.TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Videocam, null, tint = Ink.Teal, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                callerName,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(title, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallActionButton(
                    icon = Icons.Default.CallEnd,
                    label = tr(StrClassroom.declineCall),
                    color = Ink.Coral,
                    onClick = onDecline,
                )
                CallActionButton(
                    icon = Icons.Default.Videocam,
                    label = tr(StrClassroom.acceptCall),
                    color = Ink.Teal,
                    onClick = onAccept,
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}
