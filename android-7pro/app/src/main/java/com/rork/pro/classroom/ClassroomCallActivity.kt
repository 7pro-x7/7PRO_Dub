package com.rork.pro.classroom

import android.Manifest
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.modules.core.PermissionListener
import com.rork.pro.data.Backend
import com.rork.pro.data.ClassroomChatMessage
import com.rork.pro.data.ClassroomWeb
import com.rork.pro.data.MediaRepository
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.StrLiveTranslation
import com.rork.pro.classroom.translation.LiveTranslationCaption
import com.rork.pro.classroom.translation.LiveTranslationPanel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.AppTheme
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetActivityInterface
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import org.jitsi.meet.sdk.JitsiMeetView
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "ClassroomCallActivity"

/**
 * Hosts the call.
 *
 * Runs as its own Activity — outside MainActivity's Compose NavHost — because the Jitsi SDK
 * manages a React Native runtime underneath [JitsiMeetView] with its own lifecycle needs
 * (`onUserLeaveHint`, permission callbacks, `dispose()`); isolating it here means the rest of
 * the app's navigation graph never has to know about that.
 *
 * All classroom-specific UI (chat/Q&A, raised hands, whiteboard, attendance, the leave/end
 * button) is drawn in Compose *around* the Jitsi view rather than using Jitsi's own toolbar —
 * see [SevenProApp] for which of Jitsi's built-in features are turned off to avoid two
 * divergent, unsynced copies of the same feature (chat, raise-hand).
 */
/**
 * Hosts the Jitsi call.
 *
 * BASE CLASS — this must stay a [FragmentActivity], not a plain `ComponentActivity`.
 * The Jitsi Meet SDK bundles `react-native-screens`, whose `ScreenContainer` resolves a
 * *FragmentManager* from the host Activity when it attaches to the window. A plain
 * ComponentActivity has no FragmentManager at all, so that lookup failed and threw
 * `IllegalStateException: In order to use RNScreens components your app's activity need to
 * extend ReactActivity` — which is exactly the crash reported from a real device: the call
 * screen opened black and the app died instantly, and every subsequent tap re-triggered it.
 * FragmentActivity extends ComponentActivity, so Compose (`setContent`), `by viewModels()`,
 * and `registerForActivityResult` all keep working unchanged; it just additionally provides
 * the `supportFragmentManager` that RNScreens requires. FragmentActivity is used rather than
 * AppCompatActivity deliberately: AppCompatActivity would additionally require an AppCompat
 * theme, and this app's `Theme.RorkApp` inherits from `android:Theme.Material.Light.NoActionBar`,
 * which would itself crash at startup.
 *
 * androidx.fragment is NOT declared as an explicit dependency for this. It arrives transitively
 * with the Jitsi Meet SDK (React Native depends on it), already at a version aligned with the
 * rest of the AndroidX graph. Pinning it by hand to a separate version instead pulled an older
 * androidx.fragment alongside this project's much newer androidx.activity (1.12.4) and
 * androidx.lifecycle (2.10.0) — an inconsistent AndroidX set, which is a classic cause of a
 * release build failing while a debug build still succeeds.
 */
class ClassroomCallActivity : FragmentActivity(), JitsiMeetActivityInterface, DefaultHardwareBackBtnHandler {

    private var jitsiView: JitsiMeetView? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * True only once the user has actually chosen to end their participation — the on-screen
     * Leave button, the moderator's confirmed "End for everyone", or Jitsi itself reporting
     * CONFERENCE_TERMINATED (the call is already over at that point, nothing left to preserve).
     *
     * Exiting the app any other way — system Back, Home, the recents switcher, the screen
     * turning off — must NOT end the call. Those paths now only minimize (see
     * [tryEnterPictureInPicture] and the Back-press override below); [onDestroy] reads this flag
     * to decide whether tearing down the Jitsi connection is actually appropriate, instead of
     * doing it unconditionally the moment this Activity happens to go away.
     */
    private var callEndedIntentionally = false
    // Activity teardown also needs to know whether it is allowed to mark the leave as
    // intentional. An automatic retry exhaustion must dispose Jitsi without ending the class.
    private var leaveIntentionalOnDestroy = false

    /**
     * How many times this screen has auto-rejoined after Jitsi reported CONFERENCE_TERMINATED
     * without the person having tapped Leave / End for everyone. Reset to 0 the moment a rejoin
     * actually lands (CONFERENCE_JOINED) so an old, unrelated rough patch earlier in a long class
     * never eats into the retry budget for a fresh one later.
     */
    private var reconnectAttempts = 0
    // A teacher must not be thrown out of their own class by a long dropout: they keep retrying
    // (backing off up to 15 s per try, ~15 min in total); students give up after about two minutes.
    private val maxReconnectAttempts: Int get() = if (joinInfo.isModerator) 60 else 12
    private val reconnectDelayMs = 2500L

    /**
     * Guards against the call silently spinning on "Joining…" forever. Jitsi only ever fires
     * CONFERENCE_TERMINATED for a connection it had already established and then lost — an XMPP
     * handshake that never completes in the first place (unreachable/misconfigured domain,
     * connectivity too weak to finish it) fires neither JOINED nor TERMINATED, so nothing in
     * [jitsiBroadcastReceiver] ever runs and the screen was stuck with zero feedback and no way
     * out short of force-closing the app. This timer is started right after every [view.join]
     * call (first attempt and every retry) and cancelled the moment CONFERENCE_JOINED actually
     * lands; if it fires first, the join is treated as failed and reported through
     * [ClassroomCallViewModel.reportJoinTimeout] as a normal, retryable error banner.
     */
    /**
     * True once this screen has actually gone through [onStop] — i.e. the next [onResume] is a
     * real "came back from the background" event, not just the very first resume after
     * [onCreate] or the harmless resume that follows a permission dialog / PiP resize. Read once
     * and cleared in [onResume] so [ClassroomCallViewModel.refreshOnForeground] only fires for a
     * genuine return trip. See that method for why a real background stretch needs an active
     * nudge instead of waiting on the passive reconnect timers.
     */
    private var wasStopped = false

    private var joinWatchdogJob: kotlinx.coroutines.Job? = null
    private val joinTimeoutMs = 45_000L
    /** One silent retry before the person ever sees a "couldn't join" banner. */
    private var silentJoinRetryUsed = false
    // Compose attaches JitsiMeetView asynchronously. Calling join() before that attachment can
    // leave the SDK's React Native screen permanently on "Joining…".
    private var joinStarted = false

    /**
     * True between CONFERENCE_JOINED and the next CONFERENCE_TERMINATED — i.e. this device is
     * really in the room right now. Every (re)join path checks it, so a slow first join that
     * lands after the watchdog fired, or Jitsi recovering on its own, is never joined a second
     * time on top (which is what put the same teacher in the room twice).
     */
    private var conferenceJoined = false

    /**
     * Set just before we send HANG_UP ourselves to clear out a stale attempt before rejoining,
     * so the CONFERENCE_TERMINATED that hang-up produces is not mistaken for a dropped call
     * (which would schedule yet another rejoin on top of the one already in progress).
     */
    private var selfHangUpPending = false
    private var rejoinJob: kotlinx.coroutines.Job? = null

    /**
     * Resolved by the broadcast receiver the moment CONFERENCE_TERMINATED confirms our own
     * [selfHangUpPending] hang-up actually landed. [scheduleRejoin] awaits this instead of
     * guessing a fixed delay — see its own doc for why a guess was the root cause of the same
     * person showing up twice in the room.
     */
    private var hangUpConfirmed: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /**
     * True while this screen is silently retrying the Jitsi connection after an automatic
     * CONFERENCE_TERMINATED — read by [CallScreen] to show a small "reconnecting" indicator
     * instead of the screen just vanishing with no explanation for a few seconds.
     */
    val reconnectingCall = mutableStateOf(false)

    /** When this device entered the class — the top bar's elapsed timer counts from here. */
    val callStartedAtMs: Long = System.currentTimeMillis()

    /**
     * Called from the Compose layer's own Leave / End-for-everyone actions — the only places
     * outside this class allowed to decide the call is really over. See [callEndedIntentionally].
     */
    fun markCallEndedIntentionally() {
        callEndedIntentionally = true
        leaveIntentionalOnDestroy = true
        // Withdraw the Android 12+ "auto enter PiP" permission immediately. Without this, a
        // deliberate Leave / End-for-everyone that happens to coincide with the home animation
        // would shrink the finished call into a floating window instead of closing it.
        refreshPipParams()
    }

    /**
     * True while the call is showing as the small floating window over the phone's home screen
     * or over another app.
     *
     * Compose state rather than a plain Boolean on purpose: [CallScreen] reads it to strip the
     * screen down to the bare video while shrunk. A PiP window is a couple of centimetres wide,
     * so drawing our normal chrome inside it (top switch chips, the scrolling control bar,
     * banners, floating chat toasts) fills the whole thing with unreadable, un-tappable
     * controls and leaves almost no room for the actual faces — which is the entire point of
     * keeping the class visible.
     */
    val inPictureInPicture = mutableStateOf(false)

    /** Whether the "display over other apps" permission (for the green sharing frame) is granted. */
    val frameOverlayAllowed = mutableStateOf(false)

    fun refreshFrameOverlayPermission() {
        frameOverlayAllowed.value = android.provider.Settings.canDrawOverlays(this)
    }

    fun frameOverlayAsked(): Boolean = callPrefs.getBoolean("asked_frame_overlay", false)

    fun markFrameOverlayAsked() {
        callPrefs.edit().putBoolean("asked_frame_overlay", true).apply()
    }

    fun requestFrameOverlayPermission() {
        markFrameOverlayAsked()
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private val supportsPip: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Aspect ratio + behaviour of the floating window.
     *
     * The ratio follows the phone's current orientation instead of being hard-coded: a portrait
     * phone shrinks to a portrait window (tall, matching the video it contains) and a rotated
     * one to a landscape window, so the video is never letterboxed inside its own thumbnail.
     *
     * `setAutoEnterEnabled` (Android 12+) is the important one. [onUserLeaveHint] — the hook
     * this screen relied on before — is NOT delivered reliably when the user leaves by the
     * gesture-navigation swipe-up-to-home, which is how most modern phones are driven. Without
     * auto-enter, the call therefore just vanished off screen for exactly the people most
     * likely to do it. With it, Android shrinks the Activity itself as part of the home
     * animation, with no hook required.
     */
    private fun buildPipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val metrics = resources.displayMetrics
        val ratio = if (metrics.widthPixels >= metrics.heightPixels) Rational(16, 9) else Rational(9, 16)
        val builder = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(!callEndedIntentionally)
            // Live video: let the window resize smoothly rather than cross-fading through a
            // frozen snapshot every time it changes size.
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /**
     * Re-publishes the params above. Must be called whenever something they depend on changes
     * (rotation, and the moment the call is genuinely ending) — otherwise Android 12+ would
     * happily auto-shrink a call that is in the middle of hanging up.
     */
    private fun refreshPipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsPip) return
        runCatching { buildPipParams()?.let { setPictureInPictureParams(it) } }
            .onFailure { Log.w(TAG, "Could not publish picture-in-picture params", it) }
    }

    /**
     * Shrinks the call into a small floating window instead of hanging up. Used for both the
     * system Home button ([onUserLeaveHint]) and the system Back button (below) — the two most
     * common ways someone "exits" this screen without meaning to leave the class.
     *
     * Driven directly through the platform's own `enterPictureInPictureMode(params)` rather
     * than the SDK's `JitsiMeetView.enterPictureInPicture()`. The SDK helper shrinks the window
     * with its own internal defaults and gives us no way to set the aspect ratio or the
     * auto-enter flag, and it is a no-op when the SDK's `pip.enabled` flag path disagrees with
     * ours. The SDK call is still kept as a fallback below, for any device where the platform
     * call is refused.
     *
     * @return false if PiP could not be entered at all (OS too old, PiP turned off for 7PRO in
     *   Android's per-app settings, or the platform refusing) so the caller can fall back to
     *   simply backgrounding the task instead of leaving the user on a dead screen.
     */
    private fun tryEnterPictureInPicture(): Boolean {
        if (callEndedIntentionally || isFinishing) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsPip) return false
        if (inPictureInPicture.value) return true

        val entered = runCatching {
            val params = buildPipParams()
            if (params != null) enterPictureInPictureMode(params) else false
        }.onFailure {
            Log.w(TAG, "Platform enterPictureInPictureMode failed", it)
        }.getOrDefault(false)

        if (entered) return true

        // Fallback: whatever the SDK is willing to do on this device.
        return runCatching { jitsiView?.enterPictureInPicture(); true }
            .onFailure { Log.w(TAG, "Could not enter picture-in-picture; backgrounding without it", it) }
            .getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture.value = isInPictureInPictureMode
    }

    // The SDK no longer supports a JitsiMeetView listener object (removed from the library);
    // conference events are now delivered as LocalBroadcastManager broadcasts instead, the same
    // way the SDK's own JitsiMeetActivity base class consumes them.
    private val jitsiBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            // Matched on the raw action string rather than on BroadcastEvent.Type enum
            // constants. The event *types* we need beyond CONFERENCE_TERMINATED
            // (audio/video mute changes, screen-share toggled) are not present under the same
            // enum names in every jitsi-meet-sdk release, so referencing them as constants
            // makes this file fail to COMPILE on a version that lacks one. Action strings are
            // the SDK's stable public contract and an unrecognized one simply never matches,
            // which is a no-op instead of a build break or a crash.
            val data = runCatching { BroadcastEvent(intent).data }.getOrNull()
            when {
                action.endsWith("CONFERENCE_TERMINATED", ignoreCase = true) -> {
                    // Logged before deciding what to do so a crash-log pull shows *why* Jitsi
                    // ended the call rather than only showing the screen disappear with no
                    // trace — this event's data commonly carries an "error" reason when
                    // termination was a failure rather than a normal hangup (room/server
                    // unreachable, etc.).
                    Log.w(TAG, "CONFERENCE_TERMINATED for session=$sessionId, data=$data")

                    conferenceJoined = false
                    if (selfHangUpPending) {
                        // Our own HANG_UP from scheduleRejoin(), clearing the stale attempt before
                        // a fresh join — scheduleRejoin() is already about to join again.
                        selfHangUpPending = false
                        // Unblock scheduleRejoin()'s wait so the next join() fires the instant
                        // this hang-up is actually confirmed, not after a guessed delay.
                        hangUpConfirmed?.complete(Unit)
                        return
                    }
                    if (callEndedIntentionally) {
                        // Already tearing down through the person's own Leave / End-for-
                        // everyone action (see markCallEndedIntentionally, which is set BEFORE
                        // those paths call finish() themselves) — this broadcast is just Jitsi
                        // confirming a hangup we already asked for. Nothing further to do here.
                        return
                    }

                    // This broadcast is NOT proof anyone chose to end the class — Jitsi fires it
                    // just as readily for a dropped connection (a network blip, the OS
                    // reclaiming the socket while this screen sat backgrounded, the bridge
                    // briefly unreachable) as for a genuine hangup. Immediately finishing the
                    // screen here used to be exactly that: a class silently disappearing off a
                    // student's or teacher's phone over a recoverable hiccup, with no way back
                    // in short of manually rejoining from the session list. So instead of ending
                    // anything, try to silently rejoin the same room a few times first — the
                    // person only ever sees the class actually end when they tap Leave / End for
                    // everyone, or when every retry below has failed.
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        Log.w(TAG, "Auto-rejoining after CONFERENCE_TERMINATED (attempt $reconnectAttempts/$maxReconnectAttempts)")
                        runOnUiThread { reconnectingCall.value = true }
                        // Jitsi often shows its own "disconnected — rejoining in N seconds" dialog
                        // for the same drop and would rejoin by itself. scheduleRejoin() hangs
                        // that pending attempt up first, so exactly one rejoin happens.
                        scheduleRejoin((reconnectDelayMs * reconnectAttempts).coerceAtMost(15_000L))
                    } else {
                        // Retries exhausted — at this point it really does look unrecoverable
                        // (no network for a sustained stretch, or the room/server genuinely
                        // gone). Recorded as an automatic leave, not an intentional one, so the
                        // class stays LIVE for everyone else and can be rejoined from the
                        // session list instead of reading as "already ended".
                        Log.w(TAG, "Giving up after $reconnectAttempts failed rejoin attempts")
                        joinWatchdogJob?.cancel()
                        callEndedIntentionally = true
                        leaveIntentionalOnDestroy = false
                        runOnUiThread { notifyModeratorLeftOpenClass(); vm.leave(intentional = false); finish() }
                    }
                }
                // A rejoin (automatic or the very first join) actually landed — clear the retry
                // budget and the "reconnecting" indicator so they don't carry stale state into
                // whatever happens next in the call.
                action.endsWith("CONFERENCE_JOINED", ignoreCase = true) -> {
                    reconnectAttempts = 0
                    silentJoinRetryUsed = false
                    conferenceJoined = true
                    joinStarted = true
                    joinWatchdogJob?.cancel()
                    rejoinJob?.cancel()
                    runOnUiThread {
                        reconnectingCall.value = false
                        // A slow join that finally landed after the watchdog already showed
                        // "couldn't join" — the banner is now false, so it goes away by itself.
                        vm.clearJoinTimeoutError()
                        // The join options above only describe the state at join() time. The
                        // ViewModel is the source of truth for the mic button, so push it to the
                        // now-live conference: an auto-rejoin (or a mic that came up muted while
                        // the permission prompt was still on screen) can otherwise leave the
                        // button saying "live" while nobody can hear the person.
                        vm.onConferenceJoined()
                    }
                }
                // Keeps our bottom bar's mic/camera/screen-share icons truthful even when the
                // state changed for a reason other than our own button (a hardware mute
                // control, Jitsi's own auto-mute, the user cancelling the screen-share
                // permission dialog) — without this the button could show "unmuted" while the
                // real track is muted, or vice versa.
                action.endsWith("AUDIO_MUTED_CHANGED", ignoreCase = true) ->
                    extractBoolean(data, "muted")?.let { muted -> runOnUiThread { vm.onJitsiAudioMutedChanged(muted) } }
                action.endsWith("VIDEO_MUTED_CHANGED", ignoreCase = true) ->
                    extractBoolean(data, "muted")?.let { muted ->
                        runOnUiThread { vm.onJitsiVideoMutedChanged(muted) }
                        // A teacher's own camera choice is remembered for their next class.
                        if (joinInfo.isModerator && conferenceJoined && hasCameraPermission()) {
                            callPrefs.edit().putBoolean(PREF_TEACHER_CAMERA_ON, !muted).apply()
                        }
                    }
                action.endsWith("SCREEN_SHARE_TOGGLED", ignoreCase = true) ->
                    extractBoolean(data, "enabled", "on", "sharing")?.let { sharing ->
                        runOnUiThread { vm.onJitsiScreenShareChanged(sharing) }
                    }
                else -> Unit
            }
        }
    }

    /**
     * The exact key Jitsi puts a mute/screen-share flag under in [BroadcastEvent.getData] isn't
     * pinned to one name across SDK releases, so this tries the candidates in order and accepts
     * either a real Boolean or the string "true"/"false" (React Native bridges sometimes cross
     * as one or the other) instead of assuming a shape and silently doing nothing if it's wrong.
     */
    private fun extractBoolean(data: Map<String, Any>?, vararg keys: String): Boolean? {
        if (data == null) return null
        for (key in keys) {
            when (val value = data[key]) {
                is Boolean -> return value
                is String -> value.toBooleanStrictOrNull()?.let { return it }
                else -> Unit
            }
        }
        Log.w(TAG, "Could not find any of ${keys.toList()} in Jitsi event data=$data")
        return null
    }

    /**
     * [JitsiCommands] implementation — the only place that talks to the Jitsi SDK for
     * mic/camera/screen-share, since sending its broadcast commands needs a Context.
     *
     * The command Intents are built by hand from the SDK's documented external-API action
     * strings rather than through `BroadcastIntentHelper`. That helper's method names have
     * changed across jitsi-meet-sdk releases, and a wrong name there is a hard COMPILE error
     * that blocks the whole build — whereas these action strings are the SDK's stable,
     * documented contract (they're exactly what the helper builds internally) and are resolved
     * at runtime, so a mismatch degrades to "the command does nothing" instead of "nothing
     * builds". Each method reports whether the broadcast was actually dispatched.
     */
    private val jitsiCommands = object : JitsiCommands {
        override fun setAudioMuted(muted: Boolean): Boolean = sendJitsiCommand("setAudioMuted") {
            Intent("org.jitsi.meet.SET_AUDIO_MUTED").putExtra("muted", muted)
        }

        override fun setVideoMuted(muted: Boolean): Boolean = sendJitsiCommand("setVideoMuted") {
            Intent("org.jitsi.meet.SET_VIDEO_MUTED").putExtra("muted", muted)
        }

        override fun toggleScreenShare(enabled: Boolean): Boolean = sendJitsiCommand("toggleScreenShare") {
            Intent("org.jitsi.meet.TOGGLE_SCREEN_SHARE").putExtra("enabled", enabled)
        }
    }

    private fun sendJitsiCommand(name: String, build: () -> Intent): Boolean = runCatching {
        LocalBroadcastManager.getInstance(this).sendBroadcast(build())
        true
    }.getOrElse {
        Log.e(TAG, "Failed to dispatch Jitsi command '$name'", it)
        false
    }

    val sessionId: String by lazy { intent.getStringExtra(EXTRA_SESSION_ID).orEmpty() }

    /** The class title shown in the top bar; null when the launcher didn't pass one. */
    val sessionTitle: String? by lazy { intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } }

    /**
     * Switches between the front and back camera. Sent as the SDK's documented external-API action
     * string for the same reason as the other commands above: an unknown action degrades to "does
     * nothing" instead of breaking the build.
     */
    fun flipCamera() {
        sendJitsiCommand("toggleCamera") { Intent("org.jitsi.meet.TOGGLE_CAMERA") }
    }
    private val joinInfo: com.rork.pro.data.ClassroomJoinInfo by lazy {
        com.rork.pro.data.ClassroomJoinInfo(
            roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty(),
            roomPassword = intent.getStringExtra(EXTRA_ROOM_PASSWORD).orEmpty(),
            jitsiDomain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty(),
            roleInSession = intent.getStringExtra(EXTRA_ROLE).orEmpty(),
            isModerator = intent.getBooleanExtra(EXTRA_MODERATOR, false),
            displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty(),
            micLocked = intent.getBooleanExtra(EXTRA_MIC_LOCKED, false),
            cameraLocked = intent.getBooleanExtra(EXTRA_CAMERA_LOCKED, false),
        )
    }

    private val vm: ClassroomCallViewModel by viewModels { ClassroomCallViewModel.Factory(sessionId, joinInfo) }

    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Self-contained "give me the crash without adb" net for this screen specifically.
     *
     * Nobody watching this call has Android Studio or `adb` open, so an uncaught crash here
     * previously meant exactly what was reported: the screen just disappears with zero visible
     * trace of why. This installs a scoped [Thread.UncaughtExceptionHandler] for as long as this
     * Activity is alive, writes whatever crashed into SharedPreferences, and restores the
     * previous handler in [onDestroy] so this never touches crashes anywhere else in the app.
     * The next time this screen opens, [showPendingCrashIfAny] reads it back and shows it in a
     * plain, no-dependencies [android.app.AlertDialog] with a "copy" button — something anyone
     * can screenshot or paste back, no developer tools required.
     */
    private fun installCrashCapture() {
        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(writer))
                getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(CRASH_KEY, writer.toString())
                    .putLong(CRASH_AT_KEY, System.currentTimeMillis())
                    .apply()
            }
            // Never swallow the crash — Android still needs to see it to restart the process
            // cleanly. This only ever adds a save-to-disk step in front of the normal handling.
            previousUncaughtHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun showPendingCrashIfAny() {
        val prefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(CRASH_KEY, null) ?: return
        val at = prefs.getLong(CRASH_AT_KEY, 0L)
        prefs.edit().remove(CRASH_KEY).remove(CRASH_AT_KEY).apply()

        // Only worth showing if it just happened (this session's own crash) — an old leftover
        // crash from days ago re-surfacing here would be confusing rather than helpful.
        if (System.currentTimeMillis() - at > 5 * 60_000L) return

        android.app.AlertDialog.Builder(this)
            .setTitle(tr(StrClassroom.crashFriendlyTitle))
            .setMessage(tr(StrClassroom.crashFriendlyBody))
            .setPositiveButton(tr(StrClassroom.copyDetailsForSupport)) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("classroom_crash", saved))
            }
            .setNegativeButton(tr(Str.close), null)
            .setCancelable(true)
            .show()
    }

    /**
     * Requested explicitly, up front, instead of leaving it entirely to Jitsi's own internal
     * flow — a real device report showed no permission dialog ever appearing before the call
     * failed, and relying solely on the SDK's lazy internal request (via
     * [requestPermissions]/[onRequestPermissionsResult] below, which still exists for anything
     * Jitsi asks for mid-call, like screen share) left it unclear whether that path had ever
     * actually run. Asking here, before `join()`, guarantees the user sees the system prompt
     * before the call even attempts to use the camera/mic.
     */
    private val callPermissions: Array<String> by lazy {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    private val callPrefs by lazy { getSharedPreferences("classroom_call_prefs", Context.MODE_PRIVATE) }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Students always arrive with the camera off (nobody should be broadcast before they choose).
     * A teacher arrives with the camera ON by default and afterwards with whatever they last chose,
     * so they no longer have to switch it on at the start of every class. A locked camera or a
     * missing permission always wins.
     */
    private fun startCameraMuted(): Boolean =
        joinInfo.cameraLocked ||
            !(joinInfo.isModerator && callPrefs.getBoolean(PREF_TEACHER_CAMERA_ON, true) && hasCameraPermission())

    private fun hasAllCallPermissions(): Boolean =
        callPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    // Must be a property (registered unconditionally, before onCreate finishes) — registering
    // this lazily/conditionally inside a click handler or after STARTED throws at runtime.
    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.filterValues { granted -> !granted }.keys
            if (denied.isNotEmpty()) {
                // Proceed anyway rather than blocking the whole call: Jitsi degrades gracefully
                // by starting with camera/mic muted when a permission is missing, which is still
                // useful (whiteboard, chat, listening) — better than the user being stuck on a
                // permission wall with no way to at least observe the class.
                Log.w(TAG, "Starting call with permission(s) denied: $denied")
            }
            startJitsiConference()
        }

    private fun startJitsiConference() {
        val view = jitsiView ?: return
        if (!view.isAttachedToWindow) {
            view.postDelayed({
                if (!isFinishing && !callEndedIntentionally) startJitsiConference()
            }, 100L)
            return
        }
        if (joinStarted) return
        joinStarted = true
        // Some already-created sessions still carry meet.jit.si (or a blank/legacy host)
        // because the server migration was added after the session was created. Normalize it
        // here so teacher and student use the same reachable conference server.
        val conferenceDomain = ClassroomWeb.normalizeJitsiDomain(joinInfo.jitsiDomain)
        Log.i(TAG, "Joining session=$sessionId on conference domain=$conferenceDomain")
        val optionsBuilder = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL("https://$conferenceDomain"))
            .setRoom(joinInfo.roomName)
            .setUserInfo(JitsiMeetUserInfo().apply { displayName = joinInfo.displayName })
            // A student whose mic/camera the teacher already had locked — a standing "lock all
            // mics", or a per-student lock from before they joined — must never go live even for
            // an instant. Applying this as part of the conference's own initial config is what
            // actually guarantees that: doing it afterward, by sending a mute command once the
            // participant list loads, races the Jitsi SDK's own startup and can lose that race
            // (the mic briefly live, or the command arriving before anything is listening for
            // it and simply being dropped).
            // Joins muted ONLY when the teacher locked this person's mic. (An older comment here
            // claimed every student joined muted by default; the code never did that, and a
            // default-muted student who is not told to tap the mic is indistinguishable from
            // "the teacher and student can't hear each other".)
            .setAudioMuted(joinInfo.micLocked)
            // Camera OFF on arrival for everyone, teacher included — not only when it was
            // locked. Opening a lesson used to broadcast whatever the phone was pointed at the
            // instant the screen appeared, before anyone had a chance to decide they were ready
            // to be seen, and that is the wrong default for a room full of students at home.
            // One tap on the camera button turns it on (unless the teacher locked that person's
            // camera, which this still respects as a hard lock rather than a default).
            .setVideoMuted(startCameraMuted())
            // Belt-and-suspenders on top of the same flag in SevenProApp's default options:
            // this is the fix for the confirmed RNScreens/ReactActivity crash, so it is set
            // explicitly here too rather than trusting only the process-wide default to survive
            // untouched. See SevenProApp for the full explanation.
            .setFeatureFlag("prejoinpage.enabled", false)
            // Mirrors SevenProApp's defaults — set per-call too so nothing can bounce the user
            // out of 7PRO into a browser or the standalone Jitsi app mid-session.
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("security-options.enabled", false)
            .setFeatureFlag("lobby-mode.enabled", false)
            .setFeatureFlag("settings.enabled", false)
            .setFeatureFlag("help.enabled", false)
            // Screen share is exactly what makes P2P (Jitsi's default for a 2-person call) show
            // this symptom: P2P only carries one video stream in each direction, so the instant
            // someone starts sharing their screen, the SDK has to tear down the direct P2P
            // connection and renegotiate through the bridge (JVB) mid-call. That renegotiation
            // is what shows up as several seconds of a black frame for the viewer before
            // anything appears. Forcing every call through the bridge from the start (P2P off)
            // means screen share never has to trigger that switch — it's already on the bridge.
            .setFeatureFlag("p2p.enabled", false)
        // Our own top bar already names the class, and the SDK's subject banner was rendering
        // the raw room id over the video, so the subject is deliberately left unset.
        // No JWT here for the public meet.jit.si fallback — see the migration notes: real
        // per-user authentication at the conferencing-server level requires a self-hosted
        // Jitsi deployment issuing a JWT (wired here when the classroom-jitsi-token edge
        // function is deployed and returns one).
        applyAudioCleanupConfig(optionsBuilder)
        intent.getStringExtra(EXTRA_JWT)?.let { optionsBuilder.setToken(it) }
        runCatching { view.join(optionsBuilder.build()) }
            .onFailure {
                joinStarted = false
                Log.e(TAG, "Jitsi join failed for session=$sessionId", it)
                vm.reportJoinTimeout(domain = conferenceDomain, roomName = joinInfo.roomName)
            }

        joinWatchdogJob?.cancel()
        joinWatchdogJob = lifecycleScope.launch {
            delay(joinTimeoutMs)
            // joinStarted is deliberately NOT reset here: the SDK is usually still connecting
            // (public servers can take longer than the timeout), and resetting it let the next
            // join() run on top of that attempt — two copies of the same person in the room.
            if (!isFinishing && !callEndedIntentionally && !conferenceJoined) {
                Log.w(TAG, "No CONFERENCE_JOINED within ${joinTimeoutMs}ms of join() for session=$sessionId, room=${joinInfo.roomName}, domain=$conferenceDomain")
                if (!silentJoinRetryUsed) {
                    silentJoinRetryUsed = true
                    scheduleRejoin(0L)
                } else {
                    vm.reportJoinTimeout(domain = conferenceDomain, roomName = joinInfo.roomName)
                }
            }
        }
    }

    /** Called from the error banner's Retry action when the join-watchdog timeout fires. */
    fun retryJoin() {
        vm.clearError()
        reconnectAttempts = 0
        if (conferenceJoined) return // already in the room — nothing to retry
        scheduleRejoin(0L)
    }

    /**
     * The single way this screen rejoins. Before joining again it hangs up whatever attempt the
     * SDK may still be holding (a join still in flight, or Jitsi's own reload countdown), so
     * this device is never in the room twice. Skips entirely if the call recovered on its own
     * while we were waiting.
     *
     * The old version waited a *guessed* fixed 2s after sending hang-up before joining again.
     * That guess is what actually let the same person appear twice in the room: CONFERENCE_
     * TERMINATED confirms the SDK tore down its own local state, but on a slow or flaky
     * network the server can take longer than 2s to notice the old connection is gone. If the
     * fresh join() lands in that gap, the room briefly — sometimes not so briefly — holds two
     * live occupants with the same display name: the still-draining old one and the new one.
     * Waiting for the actual [hangUpConfirmed] signal (capped, so a lost broadcast can't hang
     * this forever) plus a short settle buffer closes that gap instead of guessing past it.
     * Joining is also held off while the device has no usable network at all, since sending a
     * fresh join() into a connection that is already down just produces another drop-and-ghost
     * cycle a moment later.
     */
    private fun scheduleRejoin(delayMs: Long) {
        joinWatchdogJob?.cancel()
        rejoinJob?.cancel()
        rejoinJob = lifecycleScope.launch {
            delay(delayMs)
            if (isFinishing || callEndedIntentionally || conferenceJoined) return@launch
            if (joinStarted) {
                val confirmed = kotlinx.coroutines.CompletableDeferred<Unit>()
                hangUpConfirmed = confirmed
                selfHangUpPending = true
                sendJitsiCommand("hangUp") { Intent("org.jitsi.meet.HANG_UP") }
                // Wait for the real confirmation first; 3s is only the ceiling for a broadcast
                // that never arrives (SDK quirk / OS killed the receiver briefly), not the
                // expected case.
                kotlinx.coroutines.withTimeoutOrNull(3000L) { confirmed.await() }
                hangUpConfirmed = null
                if (isFinishing || callEndedIntentionally || conferenceJoined) return@launch
                // Give the server a short moment to finish processing the departure it was just
                // confirmed, rather than racing a fresh join() in right on top of it.
                delay(400L)
                if (isFinishing || callEndedIntentionally || conferenceJoined) return@launch
            }
            selfHangUpPending = false
            // Don't send the fresh join into a connection we already know is down — wait here
            // (bounded) for the network to actually come back instead of manufacturing another
            // drop immediately, which is exactly the kind of churn that leaves ghost occupants
            // behind.
            var waitedForNetworkMs = 0L
            while (!hasUsableNetwork() && waitedForNetworkMs < 8000L) {
                delay(500L)
                waitedForNetworkMs += 500L
                if (isFinishing || callEndedIntentionally || conferenceJoined) return@launch
            }
            joinStarted = false
            startJitsiConference()
        }
    }

    /**
     * Everything that makes the room quiet: background-noise suppression, echo cancellation and
     * the anti-howl (that whistling/squealing Larsen feedback) settings.
     *
     * These are `config.js` overrides rather than feature flags — the same keys a self-hosted
     * Jitsi deployment would set server-side, applied per call so they hold no matter which
     * server the room lives on. Sent through [JitsiMeetConferenceOptions.Builder.setConfigOverride],
     * which is resolved by the SDK's JS layer at runtime: an override the deployed Jitsi version
     * does not recognise is ignored, never a crash and never a build break.
     *
     * Why each one:
     *
     * - `disableAP` / `disableAEC` / `disableNS` / `disableAGC` / `disableHPF` — the WebRTC
     *   audio-processing chain: overall processing, Acoustic Echo Cancellation, Noise
     *   Suppression, Automatic Gain Control, High-Pass Filter. AEC is what stops the teacher's
     *   voice coming back out of a student's speaker, into that student's mic, and round again
     *   as a whistle; NS is what strips steady background noise (fans, street, hiss); HPF cuts
     *   low-frequency rumble and mic handling noise. They are pinned to `false` (= keep the
     *   processing ON) explicitly instead of trusting a default that a deployment's own
     *   config.js, or a future SDK release, is free to flip.
     *
     * - `audioQuality.stereo` / top-level `stereo` — the decisive one, and the usual reason a
     *   Jitsi call sounds noisy despite everything above. Stereo capture SWITCHES OFF echo
     *   cancellation, noise suppression and gain control wholesale: they are mono-only
     *   algorithms. A classroom wants clean speech, not stereo, so this is forced off.
     *
     * - `enableOpusDtx` — discontinuous transmission: a participant who is not speaking sends
     *   nothing at all instead of a continuous stream of their room tone. With a large class
     *   this alone removes most of the accumulated background hiss, because silence is actually
     *   silent rather than thirty quiet rooms summed together.
     *
     * - `opusMaxAverageBitrate` — mono speech at 24 kbps, which also keeps the call alive on
     *   the weak mobile connections this app already warns about.
     *
     * - `enableNoisyMicDetection` / `disableAudioLevels` — lets Jitsi tell a participant that
     *   *their own* mic is the noisy one (it needs audio levels to do so), so the teacher is
     *   not left guessing which of thirty students to chase.
     *
     * - `startWithAudioMuted` — the conference-level twin of the [setAudioMuted] call above, so
     *   a student is muted on arrival even if the SDK reaches the room through a path where the
     *   per-call muted flag has not been applied yet.
     */
    private fun applyAudioCleanupConfig(builder: JitsiMeetConferenceOptions.Builder) {
        runCatching {
            builder
                .setConfigOverride("disableAP", false)
                .setConfigOverride("disableAEC", false)
                .setConfigOverride("disableNS", false)
                .setConfigOverride("disableAGC", false)
                .setConfigOverride("disableHPF", false)
                .setConfigOverride("stereo", false)
                .setConfigOverride("enableOpusDtx", true)
                .setConfigOverride("enableNoisyMicDetection", true)
                .setConfigOverride("disableAudioLevels", false)
                .setConfigOverride("startWithAudioMuted", joinInfo.micLocked)
                // The `p2p.enabled` FEATURE FLAG set in startJitsiConference is not a flag the SDK
                // knows, so it silently did nothing and 2-person calls (teacher + one student)
                // still tried a direct phone-to-phone connection. When that ICE negotiation
                // fails (mobile CGNAT, strict Wi-Fi) both sides sit in a "connected" call with no
                // audio and no video until it finally falls back. `p2p` is a *config* option, so
                // it has to be overridden here: everything goes through the bridge from the start.
                .setConfigOverride("p2p", Bundle().apply { putBoolean("enabled", false) })
                // Conference-level twin of the setVideoMuted(true) above, for the same reason:
                // the camera must not go live for even a frame before the user asks for it.
                .setConfigOverride("startWithVideoMuted", startCameraMuted())
                .setConfigOverride(
                    "audioQuality",
                    Bundle().apply {
                        putBoolean("stereo", false)
                        putBoolean("enableOpusDtx", true)
                        putInt("opusMaxAverageBitrate", 24000)
                    },
                )
        }.onFailure {
            // Never let an audio-tuning knob stop the class from starting.
            Log.w(TAG, "Could not apply audio cleanup config overrides", it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCapture()
        showPendingCrashIfAny()

        val view = JitsiMeetView(this)
        jitsiView = view
        vm.jitsiCommands = jitsiCommands
        vm.setInitialCameraMuted(startCameraMuted())
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).cancel(sessionId.hashCode()) }

        // Back must go through the dispatcher, not an `onBackPressed()` override: with
        // targetSdk 36 on Android 16 (predictive back on by default) the system no longer calls
        // onBackPressed() at all, so Back simply finished this Activity — which onStop() treats
        // as "the person chose to leave", and for a teacher that ENDS THE CLASS FOR EVERYONE.
        // Let the Jitsi host delegate consume back while its React Native screen is active; if
        // it has nothing to consume, invokeDefaultOnBackPressed above shrinks to PiP instead.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                JitsiMeetActivityDelegate.onBackPressed()
            }
        })

        val intentFilter = IntentFilter().apply {
            for (type in BroadcastEvent.Type.values()) addAction(type.getAction())
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(jitsiBroadcastReceiver, intentFilter)

        // Attach the Jitsi view before calling join(). Starting the conference while the view is
        // still unattached (the old order requested permissions/joined first and only then called
        // setContent) can leave the React Native bridge in its initial loading state forever on
        // some devices. The permission callback also runs after this point, so every join path
        // now starts with a real, laid-out host view.
        setContent {
            AppTheme {
                CallScreen(vm = vm, isModerator = joinInfo.isModerator, jitsiView = view, activity = this)
            }
        }

        if (hasAllCallPermissions()) {
            startJitsiConference()
        } else {
            callPermissionLauncher.launch(callPermissions)
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerNetworkCallback()

        // Arm PiP before the user can possibly leave the screen — on Android 12+ auto-enter is
        // read by the system at the moment the home gesture starts, so publishing it later
        // (e.g. in onResume only) can miss a very fast exit.
        refreshPipParams()
    }

    /**
     * Reports connectivity to the ViewModel — in BOTH directions, which is the whole fix for the
     * red "weak connection" line that appeared during a perfectly good lesson and then never went
     * away.
     *
     * Two bugs did that, and both are gone:
     *
     * 1. It treated a missing NET_CAPABILITY_VALIDATED as "weak". VALIDATED means Android has
     *    completed its own captive-portal probe to a Google endpoint — it is routinely absent on
     *    mobile data, on networks that block those probes, and for the first seconds of any new
     *    connection, all while the call itself is running fine. NET_CAPABILITY_INTERNET is the
     *    honest question ("is there a route to the internet") and is what's checked now.
     * 2. Nothing ever cleared it. onAvailable fires when a network *appears*; the network the call
     *    started on is already there, so it never fires again — once onCapabilitiesChanged had
     *    flipped the state to RECONNECTING, no code path could put it back. Now every callback,
     *    recovery included, publishes the current state, and the state is re-read from the system
     *    rather than inferred from which callback happened to fire.
     */
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = publishNetworkState()
            override fun onAvailable(network: Network) = publishNetworkState()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                publishNetworkState()
        }
        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
        // Seed from the network the call is already on, instead of waiting for a callback that
        // may not arrive until connectivity changes.
        publishNetworkState()
    }

    private fun publishNetworkState() {
        val online = hasUsableNetwork()
        runOnUiThread { vm.setNetworkOnline(online) }
    }

    /**
     * True when the system has an active network that claims a route to the internet. Anything
     * unreadable counts as online on purpose: an unknown connectivity state must never be the
     * reason a teacher is told their connection is bad.
     */
    private fun hasUsableNetwork(): Boolean = runCatching {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrElse {
        Log.w(TAG, "Could not read network state", it)
        true
    }

    override fun onUserLeaveHint() {
        tryEnterPictureInPicture()
    }

    /**
     * This Activity handles orientation itself (see the manifest's configChanges), so rotating
     * never recreates it — which also means the aspect ratio published in [buildPipParams]
     * would stay stuck at whatever the orientation was when the call started unless it is
     * re-published here.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshPipParams()
    }

    override fun onStop() {
        super.onStop()
        // Closing the floating window with its X button finishes this Activity outright — that
        // IS a person choosing to leave, and it is the one exit route that never passes through
        // the on-screen Leave button. Without this, onDestroy would see callEndedIntentionally
        // == false, skip the teardown, and leave the Jitsi connection and the session's LEAVE
        // record dangling.
        // Only when nothing else already decided how this call ends: our own Leave / End /
        // retry-exhaustion paths set callEndedIntentionally BEFORE finish(), and the old code
        // overwrote their choice here. For a teacher, swiping the floating window away is easy
        // to do by accident, so it leaves the teacher's own seat (they can rejoin from the
        // session list) but never ends the class for everyone — that stays behind the
        // confirmed "End for everyone" button.
        if (isFinishing && !callEndedIntentionally) {
            callEndedIntentionally = true
            leaveIntentionalOnDestroy = !joinInfo.isModerator
            refreshPipParams()
            notifyModeratorLeftOpenClass()
        }
        wasStopped = true
    }

    /**
     * This Activity is singleTask, so opening a class while one is already running (for
     * example from the floating window → Home → "Join now" on another class) lands here instead
     * of creating a second call. Silently ignoring it left the person in the OLD class while
     * believing they had joined the new one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val requested = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (requested.isNotBlank() && requested != sessionId) {
            Toast.makeText(this, tr(StrClassroom.alreadyInClass), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * The class was ended (by the teacher, another moderator, or the server's auto-expiry).
     * Previously nothing reacted to it: students stayed in the Jitsi room with each other,
     * unsupervised, and kept heartbeating themselves back to JOINED on a finished session.
     */
    /**
     * A teacher who closes the floating window (or whose connection finally gives up) leaves a
     * class that is still running. Tell them, with one tap back in, instead of leaving them to
     * find the class in the list while students wait.
     */
    private fun notifyModeratorLeftOpenClass() {
        if (!joinInfo.isModerator) return
        runCatching {
            com.rork.pro.data.AlertNotifier.show(
                context = applicationContext,
                id = sessionId.hashCode(),
                title = tr(StrClassroom.leftOpenClassTitle),
                body = tr(StrClassroom.leftOpenClassBody),
                route = "classroom/lobby/$sessionId?autoJoin=true",
                urgent = false,
            )
        }
    }

    /** The teacher removed this student: close the call and never rejoin. */
    fun closeBecauseRemoved() {
        if (callEndedIntentionally || isFinishing) return
        joinWatchdogJob?.cancel()
        rejoinJob?.cancel()
        callEndedIntentionally = true
        leaveIntentionalOnDestroy = false
        refreshPipParams()
        vm.leave(intentional = false)
        Toast.makeText(this, tr(StrClassroom.removedFromClass), Toast.LENGTH_LONG).show()
        finish()
    }

    fun closeBecauseSessionEnded() {
        if (callEndedIntentionally || isFinishing) return
        joinWatchdogJob?.cancel()
        rejoinJob?.cancel()
        callEndedIntentionally = true
        // The session is already over server-side; this only records this person's own LEAVE.
        leaveIntentionalOnDestroy = false
        refreshPipParams()
        vm.leave(intentional = false)
        Toast.makeText(this, tr(StrClassroom.sessionEndedNotice), Toast.LENGTH_LONG).show()
        finish()
    }

    /** Required by Jitsi's React Native host delegate. Without forwarding these lifecycle
     * events the SDK can render its shell but never complete the conference handshake. */
    override fun onResume() {
        super.onResume()
        JitsiMeetActivityDelegate.onHostResume(this)
        refreshFrameOverlayPermission()
        if (wasStopped) {
            wasStopped = false
            // Real chat/whiteboard/presence data, not the video call itself — Jitsi's own
            // CONFERENCE_TERMINATED handling above already covers the call reconnecting.
            vm.refreshOnForeground()
        }
    }

    override fun onPause() {
        JitsiMeetActivityDelegate.onHostPause(this)
        super.onPause()
    }

    override fun invokeDefaultOnBackPressed() {
        // Preserve the classroom behavior when Jitsi has no active back-handler.
        if (!tryEnterPictureInPicture()) moveTaskToBack(true)
    }

    // `listener` must be the nullable `PermissionListener?` — not `PermissionListener` — to
    // exactly match the parameter type on PermissionAwareActivity.requestPermissions (which
    // JitsiMeetActivityInterface extends, now that react-native's PermissionAwareActivity is
    // itself Kotlin with that parameter declared nullable). A non-null listener here compiles to
    // the same erased JVM signature but Kotlin no longer recognizes it as overriding that
    // abstract member — the same "accidental override" trap the note above already worked around
    // for onRequestPermissionsResult's Array<String> vs Array<out String> — and because nothing
    // then implements it, ClassroomCallActivity itself fails as "not abstract and does not
    // implement abstract member".
    override fun requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        listener: PermissionListener?,
    ) {
        // Delegates the SDK's own runtime-permission prompt (camera/mic/etc) to the platform
        // dialog; JitsiMeetActivityDelegate stashes `listener` and invokes it from
        // onRequestPermissionsResult below once the user responds, exactly as the SDK's own
        // JitsiMeetActivity base class does.
        JitsiMeetActivityDelegate.requestPermissions(this, permissions, requestCode, listener)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Required for the SDK's screen-share command (the TOGGLE_SCREEN_SHARE broadcast sent
        // in jitsiCommands below): Android's MediaProjection consent dialog reports back through
        // onActivityResult, and the SDK's own JitsiMeetActivity base class forwards it exactly this
        // way. Without this override, tapping "share screen" opens the system permission dialog but
        // the SDK never learns whether the user granted it, so the share silently never starts.
        JitsiMeetActivityDelegate.onActivityResult(this, requestCode, resultCode, data)
    }

    // NOTE: the parameter must be the invariant `Array<String>` — not `Array<out String>` — to
    // exactly match the JVM signature FragmentActivity inherits from
    // ActivityCompat.OnRequestPermissionsResultCallback. Using `out` here compiles to the same
    // JVM signature but Kotlin no longer recognizes it as the same override, which is what
    // triggered the "Accidental override" compile error.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        ScreenShareFrame.hide(this)
        JitsiMeetActivityDelegate.onHostDestroy(this)
        // Restore whatever handler was installed before this screen touched it — this net is
        // scoped to ClassroomCallActivity only and must never linger over the rest of the app.
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(jitsiBroadcastReceiver)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        rejoinJob?.cancel()
        hangUpConfirmed?.cancel()
        hangUpConfirmed = null
        // The ViewModel can outlive this Activity instance (briefly, or across a config change
        // before the new onCreate reassigns it) — never leave it holding a reference to a
        // destroyed Activity via the anonymous JitsiCommands object.
        if (vm.jitsiCommands === jitsiCommands) vm.jitsiCommands = null

        // Recording a LEAVE (in the database, for everyone else in the class) must stay tied
        // only to an *intentional* end — the whole point of [callEndedIntentionally] is that
        // this Activity can also be destroyed by the system for reasons that have nothing to do
        // with anyone actually leaving (a configuration change this build doesn't already
        // handle, Android reclaiming the Activity under memory pressure while backgrounded).
        // Marking the class as left / ended in those cases would be exactly the bug this flag
        // exists to fix.
        if (callEndedIntentionally) {
            // A deliberate Leave/End already records an intentional leave. The
            // CONFERENCE_TERMINATED retry-exhaustion path records an automatic leave before
            // finishing, so this call is idempotent through ViewModel.leftAlready.
            vm.leave(intentional = leaveIntentionalOnDestroy)
        }
        // The native Jitsi connection itself, though, is disposed unconditionally — not only
        // when isFinishing/callEndedIntentionally. An earlier version of this method left it
        // attached and running in that "system destroyed us, nobody actually left" case, on the
        // theory that the class should keep going in the background. What it actually produced:
        // this Activity instance's jitsiView had no owner left to ever hang it up, so the next
        // time the class was reopened, onCreate() built a *second* JitsiMeetView and joined
        // fresh — landing two live occupants with the same display name in the room (the
        // orphaned old one and the new one), the exact ghost-duplicate/echo symptom this class
        // already fights for the ordinary reconnect path (see scheduleRejoin's hangUpConfirmed
        // wait above). Disposing here every time closes that second path the same way: the
        // class stays LIVE for everyone else (no vm.leave() outside the intentional case above),
        // but *this* device's connection to it is always cleanly torn down, and reopening the
        // class is what rejoins — the same clean single join every other rejoin already goes
        // through, never a second connection racing an unowned first one.
        jitsiView?.dispose()
        jitsiView = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_ROOM_NAME = "room_name"
        private const val EXTRA_ROOM_PASSWORD = "room_password"
        private const val EXTRA_DOMAIN = "domain"
        private const val EXTRA_ROLE = "role"
        private const val EXTRA_MODERATOR = "moderator"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_MIC_LOCKED = "mic_locked"
        private const val EXTRA_CAMERA_LOCKED = "camera_locked"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_JWT = "jwt"
        private const val PREF_TEACHER_CAMERA_ON = "teacher_camera_on"
        private const val CRASH_PREFS = "classroom_call_crash"
        private const val CRASH_KEY = "last_crash_trace"
        private const val CRASH_AT_KEY = "last_crash_at"

        fun launch(
            context: Context,
            sessionId: String,
            info: com.rork.pro.data.ClassroomJoinInfo,
            title: String? = null,
            jwt: String? = null,
        ) {
            val intent = Intent(context, ClassroomCallActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_ROOM_NAME, info.roomName)
                putExtra(EXTRA_ROOM_PASSWORD, info.roomPassword)
                putExtra(EXTRA_DOMAIN, info.jitsiDomain)
                putExtra(EXTRA_ROLE, info.roleInSession)
                putExtra(EXTRA_MODERATOR, info.isModerator)
                putExtra(EXTRA_DISPLAY_NAME, info.displayName)
                putExtra(EXTRA_MIC_LOCKED, info.micLocked)
                putExtra(EXTRA_CAMERA_LOCKED, info.cameraLocked)
                title?.let { putExtra(EXTRA_TITLE, it) }
                jwt?.let { putExtra(EXTRA_JWT, it) }
            }
            context.startActivity(intent)
        }
    }
}

/**
 * The Compose layer drawn around the Jitsi view, styled like Zoom: a top bar (camera flip, invite
 * link, class title + timer, red End/Leave), the Jitsi view itself (shrinks to a corner thumbnail
 * rather than disappearing while the whiteboard or a shared file is open, so audio/video are never
 * interrupted), an error banner for anything that failed, a chat/Q&A/raised-hands/participants
 * panel, and a five-button bottom toolbar that depends on the role (teacher: mic, camera, share,
 * participants, more; student: mic, camera, raise hand, chat, more). See ZoomCallChrome.kt.
 */
/**
 * Builds a plain web link to this same Jitsi room and puts it on the clipboard, so the
 * teacher/moderator can paste it into any messaging app (WhatsApp, SMS…) for a student to open
 * directly in a mobile or desktop browser — no app install required. This mirrors exactly what
 * [ClassroomCallActivity.startJitsiConference] already joins with (same domain, same room name),
 * so anyone opening the link lands in the identical room. The generated classroom password is
 * carried as a bearer key so the browser can join without requiring an account login.
 */
private fun copyInviteLink(context: Context, sessionId: String, info: com.rork.pro.data.ClassroomJoinInfo) {
    // Browser invite: students without Android open the 7PRO Web Classroom directly.
    // The hostname lives in one place: com.rork.pro.data.ClassroomWeb.
    val link = com.rork.pro.data.ClassroomWeb.inviteUrl(sessionId, info.roomPassword)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("classroom_invite_link", link))
    Toast.makeText(context, tr(StrClassroom.linkCopied), Toast.LENGTH_SHORT).show()
}

/**
 * One floating chat message on the main call screen, carrying its own enter/exit animation
 * state so it can slide/fade out *before* actually leaving the on-screen queue (see
 * [ChatToastStack]) instead of just vanishing the instant its time is up.
 */
private class ChatToastEntry(val message: ClassroomChatMessage) {
    val visible = MutableTransitionState(false).apply { targetState = true }
}

/**
 * The floating stack of the most recent chat/question messages, shown over the live video (or
 * whiteboard) for a few seconds — the same [ChatBubbleRow] the Chat panel itself uses, so a
 * message looks identical whether it is caught here or read later in the panel, for whoever is
 * teaching and whoever is learning alike.
 */
@Composable
private fun ChatToastStack(toasts: SnapshotStateList<ChatToastEntry>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        toasts.toList().forEach { entry ->
            key(entry.message.id) {
                AnimatedVisibility(
                    visibleState = entry.visible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    ChatBubbleRow(entry.message, isMine = entry.message.senderId == Backend.currentUserId)
                }
                LaunchedEffect(entry) {
                    delay(4500)
                    entry.visible.targetState = false
                    delay(300)
                    toasts.remove(entry)
                }
            }
        }
    }
}

@Composable
private fun CallScreen(
    vm: ClassroomCallViewModel,
    isModerator: Boolean,
    jitsiView: JitsiMeetView,
    activity: ClassroomCallActivity,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    // While shrunk into the floating window there is room for the video and nothing else — see
    // ClassroomCallActivity.inPictureInPicture. Every piece of chrome below is gated on this.
    val inPip by activity.inPictureInPicture
    var showWhiteboard by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var panelTab by remember { mutableStateOf(0) }
    var confirmEnd by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<com.rork.pro.data.ClassroomParticipant?>(null) }
    // Zoom-style green frame around the phone while sharing the screen: a system overlay (visible
    // over other apps) when the permission is granted, otherwise a border drawn by this screen.
    val frameOverlayAllowed by activity.frameOverlayAllowed
    var askFrameOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(state.screenSharing, frameOverlayAllowed) {
        if (state.screenSharing && frameOverlayAllowed) ScreenShareFrame.show(activity) else ScreenShareFrame.hide(activity)
        if (state.screenSharing && !frameOverlayAllowed && isModerator && !activity.frameOverlayAsked()) askFrameOverlay = true
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ScreenShareFrame.hide(activity) } }
    // Live Voice Translation — only ever drawn when the owner has switched it on.
    val translationState by vm.translation.state.collectAsStateWithLifecycle()
    var showTranslation by remember { mutableStateOf(false) }

    // -------------------------------------------------------------- shared material (image/video/file)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showShare by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var uploadingMedia by remember { mutableStateOf(false) }
    // A locally-dismissed banner stays dismissed only for that one file; a new share (a
    // different URL) always reappears even if the previous one was closed.
    var dismissedMediaUrl by remember { mutableStateOf<String?>(null) }

    // One upload path for both pickers below, so a gallery pick and a file pick behave
    // identically: same bucket, same classification, same "everyone sees it" broadcast. This
    // used to live inside the whiteboard, reachable only after opening it — moved here so
    // sharing a file is a first-class action on the main call screen itself, for the teacher,
    // and so what gets shared is guaranteed to surface for every participant (see the
    // sharedMediaRevision effect below), not only for whoever happens to already be looking at
    // the whiteboard.
    fun uploadAndShare(uri: Uri) {
        scope.launch {
            uploadingMedia = true
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                // compressVideo = false: this share happens while the Jitsi call is live and
                // already holds the device's hardware video encoder for the outgoing camera
                // stream — running VideoCompressor's own hardware encode/decode pass at the same
                // time risks failing to grab the codec, stalling, or crashing mid-call. See
                // MediaRepository.read's doc for the full reasoning.
                val file = MediaRepository.read(context, uri, compressVideo = false)
                val url = MediaRepository.upload(file, "classroom-whiteboards")
                val type = when {
                    mime.contains("pdf", true) -> "PDF"
                    mime.contains("video", true) -> "VIDEO"
                    else -> "IMAGE"
                }
                vm.setWhiteboardBackground(url, type)
                // Shared material renders straight onto the main call screen now (see
                // showingSharedMedia below) — no need to flip into the whiteboard tool just to
                // see it, for the sharer or anyone else.
            }.onFailure { vm.reportUploadError(it) }
            uploadingMedia = false
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { uploadAndShare(it) } }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { uploadAndShare(it) }
    }

    // The moment a *new* share lands (see ClassroomCallViewModel.refreshBoardBackground), it
    // renders straight onto the main call screen (see showingSharedMedia below) for every
    // participant — nobody has to notice a badge or tap into the whiteboard tool to see it. A
    // fresh share also clears any earlier dismissal, so a second file shared right after the
    // first one was closed still shows up instead of being treated as already-seen.
    LaunchedEffect(state.sharedMediaRevision) {
        if (state.sharedMediaRevision > 0) dismissedMediaUrl = null
    }

    // Someone else started presenting. The presentation arrives inside the Jitsi video, so
    // anything drawn over that video — the whiteboard, a file shared earlier — is now standing
    // in front of the thing everyone is meant to be looking at. Both step aside on their own;
    // the banner below is the way back to the file, and the whiteboard chip is the way back to
    // the board, so nothing is lost by getting out of the way.
    LaunchedEffect(state.remoteScreenShareBy) {
        if (state.remoteScreenShareBy != null) {
            showWhiteboard = false
            dismissedMediaUrl = state.whiteboardBackgroundUrl
        }
    }

    // -------------------------------------------------------------- chat, surfaced live
    // New messages float over the main screen for a few seconds instead of staying invisible
    // until someone opens the chat panel — for whoever sent it and for everyone else alike.
    LaunchedEffect(state.ended) {
        if (state.ended) activity.closeBecauseSessionEnded()
    }
    LaunchedEffect(state.removed) {
        if (state.removed) activity.closeBecauseRemoved()
    }

    val chatToasts = remember { mutableStateListOf<ChatToastEntry>() }
    var seenChatCount by remember { mutableStateOf(-1) }
    LaunchedEffect(state.chat.size, state.chatHistoryLoaded) {
        // Nothing counts as "new" until the saved transcript has loaded; otherwise opening a
        // class that already had messages popped the last few old ones up as if just sent.
        if (!state.chatHistoryLoaded) return@LaunchedEffect
        if (seenChatCount == -1) {
            // First composition, or history just finished loading: nothing here is a "new"
            // message worth a toast — only messages that arrive live from now on are.
            seenChatCount = state.chat.size
            return@LaunchedEffect
        }
        if (state.chat.size > seenChatCount) {
            state.chat.subList(seenChatCount, state.chat.size).forEach { msg ->
                chatToasts.add(ChatToastEntry(msg))
            }
            seenChatCount = state.chat.size
            while (chatToasts.size > 3) chatToasts.removeAt(0)
        }
    }

    fun openPanel(tab: Int) {
        panelTab = tab
        showPanel = true
        showTranslation = false
    }

    // Unread, not total: the chat badge used to show every message ever sent in the class, so it
    // never went away after reading and said nothing about whether anything new had arrived.
    var readChatCount by remember { mutableStateOf(0) }
    val chatPanelOpen = showPanel && panelTab == 0
    LaunchedEffect(chatPanelOpen, state.chat.size) {
        if (chatPanelOpen) readChatCount = state.chat.size
    }
    val unreadChat = state.chat.drop(readChatCount).count { it.senderId != Backend.currentUserId }
    var readQuestionCount by remember { mutableStateOf(0) }
    val questionsPanelOpen = showPanel && panelTab == 1
    LaunchedEffect(questionsPanelOpen, state.questions.size) {
        if (questionsPanelOpen) readQuestionCount = state.questions.size
    }
    val unreadQuestions = state.questions.drop(readQuestionCount).count { it.senderId != Backend.currentUserId }

    // Whatever the moderator most recently shared (image/video/PDF) — rendered right on the main
    // call screen so chat, screen share, camera video, and shared photos/files all live in the
    // same place instead of photos/files needing a trip to the separate whiteboard tool. Only
    // actual drawing still needs that tool.
    val sharedType = state.whiteboardBackgroundType
    val sharedUrl = state.whiteboardBackgroundUrl
    val showingSharedMedia = !showWhiteboard && sharedType != "NONE" && !sharedUrl.isNullOrBlank() && sharedUrl != dismissedMediaUrl

    Column(
        Modifier
            .fillMaxSize()
            .background(Zm.Bg)
            .then(
                if (!inPip && state.screenSharing && !frameOverlayAllowed) {
                    Modifier.border(4.dp, Zm.Green, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            ),
    ) {
    if (!inPip) {
        val reconnectingCall by activity.reconnectingCall
        // Reconnecting / syncing / offline replace the timer line (amber = self-healing, red = no
        // internet) — the same signals the old status row carried, now under the title.
        val statusText: String
        val statusColor: Color
        when {
            reconnectingCall -> { statusText = tr(StrClassroom.reconnectingCall); statusColor = Zm.Yellow }
            state.connection == ConnectionState.OFFLINE -> { statusText = tr(StrClassroom.offline); statusColor = Zm.Red }
            // RECONNECTING only means the chat/whiteboard websocket is being rebuilt in the
            // background — the call itself (Jitsi) is unaffected and chat/board keep arriving
            // through the periodic re-read, so it is shown as plain "active" in green instead of
            // a yellow "syncing" that people read as a problem. Real trouble still shows: no
            // internet is red and a dropped call ("reconnectingCall" above) is yellow.
            // Healthy: the elapsed class time, ticking inside the top bar itself.
            else -> { statusText = ""; statusColor = Zm.Green }
        }
        ZoomTopBar(
            title = activity.sessionTitle ?: tr(StrClassroom.title),
            status = statusText.ifEmpty { null },
            elapsedSinceMs = activity.callStartedAtMs,
            statusColor = statusColor,
            endLabel = tr(if (isModerator) StrClassroom.endShort else StrClassroom.leaveCall),
            onFlipCamera = { activity.flipCamera() },
            onEnd = {
                if (isModerator) confirmEnd = true
                else { activity.markCallEndedIntentionally(); vm.leave(); activity.finish() }
            },
        )
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        Box(
            // Inside the floating window the video always takes the whole thing: the corner
            // thumbnail layout used when the whiteboard is open would otherwise shrink the
            // faces to a thumbnail *inside* a thumbnail.
            modifier = if (!inPip && (showWhiteboard || showingSharedMedia)) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(width = 120.dp, height = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, Zm.Green, RoundedCornerShape(10.dp))
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            // Kept as ONE AndroidView call site, with only the Modifier above changing, because
            // re-running this factory with a jitsiView that already has a parent throws
            // "The specified child already has a parent". The defensive detach makes that safe
            // even if Compose does decide to rebuild the node (a PiP transition is a
            // configuration change, which is exactly when that can happen).
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        (jitsiView.parent as? ViewGroup)?.removeView(jitsiView)
                        addView(jitsiView)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // From here down, every block is gated on `!inPip`: while shrunk, the class stays
        // visible and audible and nothing else is drawn. The full UI comes back untouched the
        // moment the teacher taps the window to return.
        if (!inPip && showWhiteboard) {
            com.rork.pro.classroom.whiteboard.WhiteboardCanvas(
                strokes = state.strokes,
                canClear = isModerator,
                backgroundUrl = state.whiteboardBackgroundUrl,
                backgroundType = state.whiteboardBackgroundType,
                onStroke = { stroke -> vm.pushStroke("STROKE", stroke) },
                onClear = { vm.pushStroke("CLEAR", null) },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!inPip) {
            if (showingSharedMedia) {
                // The main screen's own view of what was shared — read-only. Switching to the
                // Whiteboard tab shows this same background with drawing tools on top of it.
                com.rork.pro.classroom.whiteboard.SharedMediaBackground(
                    backgroundUrl = sharedUrl,
                    backgroundType = sharedType,
                    modifier = Modifier.fillMaxSize(),
                    // Strokes live INSIDE the media view so pinch-zoom moves them together with the
                    // picture and they stay exactly on the spot they were drawn.
                    strokes = if (state.remoteScreenShareBy == null) state.strokes else emptyList(),
                )
            }
            // Strokes themselves are no longer gated behind opening the whiteboard tool: they
            // land here, directly over whatever is already on screen (a shared file, or the
            // live video), for the teacher and every student alike the instant they're drawn —
            // except while someone else is actively screen-sharing, which already steps every
            // drawn-over layer aside (see the remoteScreenShareBy effect above) so strokes don't
            // reappear in front of the thing everyone is meant to be looking at.
            if (state.remoteScreenShareBy == null && !showingSharedMedia) {
                com.rork.pro.classroom.whiteboard.StrokesOverlay(
                    strokes = state.strokes,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!inPip) Column(Modifier.fillMaxWidth()) {

            // No banner for "you are sharing" any more: the green square button in the bottom bar
            // shows it and is also the stop button, so nothing extra crowds the top of the screen.

            // A presentation is running on someone else's device. Said plainly, because the
            // Jitsi video changing to a shared screen is otherwise indistinguishable from the
            // teacher's camera pointing at something — and because the participant who started
            // it gets no confirmation that the room can actually see it.
            if (state.remoteScreenShareBy != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Zm.Green.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ScreenShare,
                        null,
                        tint = Zm.Green,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr(StrClassroom.presentingNow),
                        color = Zm.Green,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // A dismissible banner for anything vm surfaced via [ClassroomCallViewModel.error] —
            // chat/whiteboard sync failures, a denied mic-lock attempt, a failed screen-share toggle,
            // etc. Replaces the old behavior of those failures happening with zero visible signal.
            // A permission refusal ("You do not have permission to do that.") is never shown in the
            // call: it is noise to the person in the class, so that one error code is skipped here.
            error?.takeIf { it.code != "FORBIDDEN" }?.let { err ->
                InkCard(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = Zm.Bar.copy(alpha = 0.96f),
                    borderColor = Zm.Red.copy(alpha = 0.4f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(err.message, color = Zm.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        // The join-watchdog timeout is the one error here with an obvious,
                        // single retry action (re-attempt the Jitsi join) — everything else
                        // just gets dismissed, since there is no one universal "retry" for a
                        // failed mic-lock or a screen-share toggle.
                        if (err.code == "JOIN_TIMEOUT") {
                            SecondaryAction(tr(StrClassroom.retryJoin), tint = Zm.Text) { activity.retryJoin() }
                            Spacer(Modifier.width(8.dp))
                        }
                        SecondaryAction(tr(Str.close), tint = Zm.Text) { vm.clearError() }
                    }
                }
            }
        }

        // What is on screen right now, said the way Zoom does: a small pill (the shared file's
        // kind, or the whiteboard) with a 44dp close button beside it. Anchored to the
        // bottom-end corner — forced to LTR just for this element so "end" always lands bottom-
        // *right* on screen regardless of the app's RTL Arabic layout — and draggable, so it
        // never sits fixed on top of a participant's face in the grid underneath: a teacher can
        // drag it wherever it isn't in the way. It re-anchors to that corner the next time a
        // *different* share starts (uploadingMedia / showWhiteboard / sharedUrl changing resets
        // the remembered drag offset) rather than staying wherever a previous, unrelated share
        // was last dragged to.
        if (!inPip && (showWhiteboard || uploadingMedia)) {
            var pillDragOffset by remember(uploadingMedia, showWhiteboard, sharedUrl) {
                mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        // Clears the bottom toolbar (mic/video/etc.) so the badge never lands
                        // on top of a button it would then be intercepting drags/taps from.
                        .padding(end = 12.dp, bottom = 96.dp)
                        .offset {
                            IntOffset(pillDragOffset.x.roundToInt(), pillDragOffset.y.roundToInt())
                        }
                        .pointerInput(uploadingMedia, showWhiteboard, sharedUrl) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                pillDragOffset += dragAmount
                            }
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(Zm.Pill)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uploadingMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Zm.Green)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(StrClassroom.uploadingShare), color = Zm.Text, style = MaterialTheme.typography.bodySmall)
                    } else if (showWhiteboard) {
                        Icon(Icons.Default.Draw, null, tint = Zm.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(StrClassroom.whiteboard), color = Zm.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        ZoomOverlayButton(Icons.Default.Close, tr(Str.close)) { showWhiteboard = false }
                    }
                }
            }
        }

        // Floats the last few chat/question messages over the video (or whiteboard) for
        // everyone in the call, live — hidden while the panel is already open since that would
        // just be showing the same messages twice at once.
        if (!inPip && !showPanel && chatToasts.isNotEmpty()) {
            ChatToastStack(
                chatToasts,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 60.dp, bottom = 12.dp),
            )
        }

        if (!inPip && translationState.available) {
            LiveTranslationCaption(
                caption = translationState.caption,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            )
        }

        if (!inPip && showTranslation && translationState.available) {
            LiveTranslationPanel(
                state = translationState,
                micLocked = state.myMicLocked,
                onLanguage = vm::setTranslationLanguage,
                onToggleSpeaking = vm::toggleTranslationSpeaking,
                onDismiss = { showTranslation = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }

        if (!inPip && showPanel) {
            ClassroomPanel(
                state = state,
                isModerator = isModerator,
                initialTab = panelTab,
                onSend = { body, isQuestion -> vm.sendChat(body, isQuestion) },
                onMarkAnswered = vm::markAnswered,
                onLowerHand = vm::lowerHandOf,
                onSetParticipantMedia = vm::setParticipantMedia,
                onMuteAll = vm::muteAllStudents,
                onRemoveStudent = { pendingRemove = it },
                onDismiss = { showPanel = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Pinch hint for a freshly shared photo/PDF page — fades out on its own after a few seconds.
        var showZoomHint by remember(sharedUrl) { mutableStateOf(true) }
        LaunchedEffect(sharedUrl) { delay(4000); showZoomHint = false }
        if (!inPip && showingSharedMedia && showZoomHint && (sharedType == "IMAGE" || sharedType == "PDF")) {
            ZoomPill(
                Icons.Default.ZoomIn,
                tr(StrClassroom.pinchToZoom),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                iconTint = Zm.TextDim,
            )
        }

        // "Share" sheet — the same three choices for everyone: screen, photo/video, file.
        if (!inPip && showShare) {
            ZoomSheet(
                items = listOf(
                    if (state.screenSharing) {
                        ZoomSheetItem(Icons.Default.StopScreenShare, tr(StrClassroom.stopSharingScreen), tint = Zm.Red) {
                            showShare = false; vm.toggleScreenShare()
                        }
                    } else {
                        ZoomSheetItem(Icons.Default.ScreenShare, tr(StrClassroom.shareScreen)) {
                            showShare = false; vm.toggleScreenShare()
                        }
                    },
                    ZoomSheetItem(Icons.Default.PhotoLibrary, tr(StrClassroom.fromGallery)) {
                        showShare = false
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    ZoomSheetItem(Icons.Default.InsertDriveFile, tr(StrClassroom.fromFiles)) {
                        showShare = false
                        filePicker.launch(arrayOf("image/*", "video/*", "application/pdf"))
                    },
                ),
                onDismiss = { showShare = false },
            )
        }

        // "More" sheet — everything that doesn't earn a slot in the five-button bar. Which items
        // appear depends on the role: the teacher's bar has Share + Participants, so chat, raised
        // hands and the whiteboard live here; the student's bar has Chat + Share, so
        // participants, raise hand and the whiteboard live here.
        if (!inPip && showMore) {
            val joinedCount = state.participants.count { it.status == "JOINED" }
            val whiteboardItem = ZoomSheetItem(Icons.Default.Draw, tr(StrClassroom.whiteboard), tint = if (showWhiteboard) Zm.Green else Zm.Text) {
                showMore = false; showWhiteboard = !showWhiteboard
            }
            val translationItem = ZoomSheetItem(Icons.Default.Translate, tr(StrLiveTranslation.button)) {
                showMore = false; showTranslation = true; showPanel = false
            }
            val copyItem = ZoomSheetItem(Icons.Default.ContentCopy, tr(StrClassroom.copyInviteLink)) {
                showMore = false; copyInviteLink(activity, activity.sessionId, vm.info)
            }
            val moreItems = buildList {
                if (isModerator) {
                    add(ZoomSheetItem(Icons.Default.Chat, tr(StrClassroom.chat), badge = unreadChat.takeIf { it > 0 }) {
                        showMore = false; openPanel(0)
                    })
                    add(ZoomSheetItem(Icons.Default.PanTool, tr(StrClassroom.raisedHands), badge = state.raisedHands.size.takeIf { it > 0 }) {
                        showMore = false; openPanel(2)
                    })
                    // Screen sharing now lives only here — it no longer also has its own slot in
                    // the five-button bar below, so there is a single place to reach for it.
                    add(
                        if (state.screenSharing) {
                            ZoomSheetItem(Icons.Default.StopScreenShare, tr(StrClassroom.stopSharingScreen), tint = Zm.Red) {
                                showMore = false; vm.toggleScreenShare()
                            }
                        } else {
                            ZoomSheetItem(Icons.Default.ScreenShare, tr(StrClassroom.shareShort)) {
                                showMore = false; showShare = true
                            }
                        },
                    )
                    add(whiteboardItem)
                } else {
                    add(ZoomSheetItem(Icons.Default.Groups, tr(StrClassroom.participantsTab), badge = joinedCount.takeIf { it > 0 }) {
                        showMore = false; openPanel(3)
                    })
                    add(
                        ZoomSheetItem(
                            Icons.Default.PanTool,
                            tr(if (state.myHandRaised) StrClassroom.lowerHand else StrClassroom.raiseHand),
                            tint = if (state.myHandRaised) Zm.Yellow else Zm.Text,
                        ) { showMore = false; vm.toggleHand() },
                    )
                    add(whiteboardItem)
                }
                // The Questions tab (ask / mark answered) had no entry point at all — students could
                // not ask a question and the teacher could not see or answer one.
                add(
                    ZoomSheetItem(
                        Icons.Default.QuestionAnswer,
                        tr(StrClassroom.questionsTab),
                        badge = if (isModerator) {
                            state.questions.count { !it.answered }.takeIf { it > 0 }
                        } else {
                            unreadQuestions.takeIf { it > 0 }
                        },
                    ) { showMore = false; openPanel(1) },
                )
                if (translationState.available) add(translationItem)
                // The link carries the room key, and anyone holding it can join as a guest — so
                // only the teacher/moderator gets it. A student who could copy it could post it
                // in any group chat and bring in outsiders.
                if (isModerator) add(copyItem)
            }
            ZoomSheet(items = moreItems, onDismiss = { showMore = false })
        }
    }

    // -------------------------------------------------------------- bottom toolbar (Zoom style)
    if (!inPip) {
        val joinedCount = state.participants.count { it.status == "JOINED" }
        ZoomBottomBar {
            ZoomToolButton(
                icon = if (state.micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = tr(if (state.micMuted) StrClassroom.unmuteShort else StrClassroom.muteShort),
                tint = if (state.micMuted) Zm.Red else Zm.Text,
            ) { vm.toggleMic() }

            ZoomToolButton(
                icon = if (state.cameraMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                label = tr(if (state.cameraMuted) StrClassroom.startVideo else StrClassroom.stopVideo),
                tint = if (state.cameraMuted) Zm.Red else Zm.Text,
            ) { vm.toggleCamera() }

            if (isModerator) {
                ZoomToolButton(
                    icon = Icons.Default.Chat,
                    label = tr(StrClassroom.chat),
                    badge = unreadChat.takeIf { it > 0 && !chatPanelOpen },
                ) { showMore = false; if (showPanel && panelTab == 0) showPanel = false else openPanel(0) }

                ZoomToolButton(
                    icon = Icons.Default.Groups,
                    label = tr(StrClassroom.participantsTab),
                    badge = joinedCount.takeIf { it > 0 },
                ) { showMore = false; if (showPanel && panelTab == 3) showPanel = false else openPanel(3) }
            } else {
                ZoomToolButton(
                    icon = Icons.Default.Chat,
                    label = tr(StrClassroom.chat),
                    badge = unreadChat.takeIf { it > 0 && !chatPanelOpen },
                ) { showMore = false; if (showPanel && panelTab == 0) showPanel = false else openPanel(0) }

                ZoomToolButton(
                    icon = if (state.screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    label = tr(if (state.screenSharing) StrClassroom.stopSharingScreen else StrClassroom.shareShort),
                    tint = if (state.screenSharing) Zm.Green else Zm.Text,
                ) { showMore = false; if (state.screenSharing) vm.toggleScreenShare() else showShare = true }
            }

            // The one control for "something is being shared": a solid green square that is both the
            // indicator and the stop button. Screen share -> stops it. A photo/video/file -> the
            // teacher ends it for everyone; a student just hides / shows it on their own screen.
            val mediaShared = sharedType != "NONE" && !sharedUrl.isNullOrBlank()
            val mediaIcon = when (sharedType) {
                "VIDEO" -> Icons.Default.Videocam
                "PDF" -> Icons.Default.InsertDriveFile
                else -> Icons.Default.PhotoLibrary
            }
            if (state.screenSharing) {
                ZoomToolButton(
                    icon = Icons.Default.StopScreenShare,
                    label = tr(StrClassroom.stopSharingScreen),
                    tint = Zm.Green,
                    filled = true,
                ) { showMore = false; vm.toggleScreenShare() }
            } else if (mediaShared) {
                if (isModerator) {
                    ZoomToolButton(
                        icon = mediaIcon,
                        label = tr(StrClassroom.stopSharingMedia),
                        tint = Zm.Green,
                        filled = true,
                    ) { showMore = false; vm.stopSharedMedia() }
                } else {
                    val hidden = sharedUrl == dismissedMediaUrl
                    ZoomToolButton(
                        icon = mediaIcon,
                        label = tr(if (hidden) StrClassroom.showSharedMedia else StrClassroom.hideSharedMedia),
                        tint = if (hidden) Zm.Yellow else Zm.Green,
                        filled = !hidden,
                    ) { showMore = false; dismissedMediaUrl = if (hidden) null else sharedUrl }
                }
            }

            ZoomToolButton(
                icon = Icons.Default.MoreHoriz,
                label = tr(StrClassroom.more),
            ) { showShare = false; showMore = !showMore }
        }
    }
    }

    // A dialog cannot be read or tapped inside a PiP window (Android draws it in a separate
    // window the shrunk task never receives touches for), so it waits until the teacher is
    // back on the full screen rather than silently blocking them.
    if (!inPip && askFrameOverlay) {
        ConfirmDialog(
            title = tr(StrClassroom.frameOverlayTitle),
            body = tr(StrClassroom.frameOverlayBody),
            confirmLabel = tr(StrClassroom.frameOverlayEnable),
            onDismiss = { askFrameOverlay = false; activity.markFrameOverlayAsked() },
            onConfirm = { askFrameOverlay = false; activity.requestFrameOverlayPermission() },
        )
    }

    pendingRemove?.let { target ->
        if (!inPip) {
            ConfirmDialog(
                title = tr(StrClassroom.removeStudent),
                body = trf(StrClassroom.removeStudentConfirm, target.profile?.fullName ?: target.userId),
                confirmLabel = tr(StrClassroom.removeStudent),
                destructive = true,
                onDismiss = { pendingRemove = null },
                onConfirm = { pendingRemove = null; vm.removeStudent(target.userId) },
            )
        }
    }

    if (!inPip && confirmEnd) {
        ConfirmDialog(
            title = tr(StrClassroom.endForEveryone),
            body = tr(StrClassroom.endSessionConfirm),
            confirmLabel = tr(StrClassroom.endForEveryone),
            onDismiss = { confirmEnd = false },
            onConfirm = { confirmEnd = false; activity.markCallEndedIntentionally(); vm.endForEveryone { activity.finish() } },
        )
    }
}

@Composable
private fun ClassroomPanel(
    state: CallUiState,
    isModerator: Boolean,
    initialTab: Int = 0,
    onSend: (String, Boolean) -> Unit,
    onMarkAnswered: (Long) -> Unit,
    onLowerHand: (String) -> Unit,
    onSetParticipantMedia: (userId: String, micLocked: Boolean?, cameraLocked: Boolean?) -> Unit,
    onMuteAll: (Boolean) -> Unit = {},
    onRemoveStudent: (com.rork.pro.data.ClassroomParticipant) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tab = initialTab
    var draft by remember { mutableStateOf("") }
    // Each icon (chat, questions, raised hands, participants) opens straight into its own
    // tab with no way to switch to the others from inside this panel — every entry point
    // is a standalone view now, not a shared tabbed sheet. Only the entry point's own title
    // shows, using the same panel chrome (Zm.Bar, same spacing) every tab already shared.
    val panelTitle = when (initialTab) {
        0 -> StrClassroom.chat
        1 -> StrClassroom.questionsTab
        2 -> StrClassroom.raisedHands
        else -> StrClassroom.participantsTab
    }
    val panelIcon = when (initialTab) {
        0 -> Icons.Default.Chat
        1 -> Icons.Default.QuestionAnswer
        2 -> Icons.Default.PanTool
        else -> Icons.Default.Groups
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Zm.Bar)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Zm.Yellow.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(panelIcon, null, tint = Zm.Yellow, modifier = Modifier.size(16.dp))
            }
            Text(
                tr(panelTitle),
                color = Zm.Text,
                style = MaterialTheme.typography.titleSmall,
            )
            Box(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Zm.TextDim) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Zm.Divider))
        Spacer(Modifier.height(10.dp))

        when (tab) {
            0 -> {
                if (state.chat.isEmpty()) {
                    Column(
                        Modifier.height(220.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Chat, null, tint = Zm.TextDim, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrClassroom.noChatMessages), color = Zm.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        Modifier.height(220.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.chat, key = { it.id }) { msg ->
                            ChatBubbleRow(msg, isMine = msg.senderId == Backend.currentUserId)
                        }
                    }
                }
                ChatInputRow(draft, { draft = it }, tr(StrClassroom.typeMessage)) {
                    onSend(draft, false); draft = ""
                }
            }
            1 -> {
                if (state.questions.isEmpty()) {
                    Column(
                        Modifier.height(220.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.QuestionAnswer, null, tint = Zm.TextDim, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrClassroom.noQuestionsYet), color = Zm.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        Modifier.height(220.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.questions, key = { it.id }) { msg ->
                            ChatBubbleRow(
                                msg,
                                isMine = msg.senderId == Backend.currentUserId,
                                trailing = {
                                    if (isModerator && !msg.answered) {
                                        IconButton(
                                            onClick = { onMarkAnswered(msg.id) },
                                            modifier = Modifier.size(28.dp),
                                        ) { Icon(Icons.Default.Badge, null, tint = Zm.Green, modifier = Modifier.size(16.dp)) }
                                    } else if (msg.answered) {
                                        Text(tr(StrClassroom.answered), color = Zm.Green, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                            )
                        }
                    }
                }
                ChatInputRow(draft, { draft = it }, tr(StrClassroom.askQuestion)) {
                    onSend(draft, true); draft = ""
                }
            }
            2 -> {
                if (state.raisedHands.isEmpty()) {
                    Column(
                        Modifier.height(180.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.PanTool, null, tint = Zm.TextDim, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrClassroom.noRaisedHands), color = Zm.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.raisedHands, key = { it.id }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Zm.Divider.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(p.profile?.avatarUrl, p.profile?.fullName, size = 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(p.profile?.fullName ?: p.userId, color = Zm.Text, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onLowerHand(p.userId) }) { Icon(Icons.Default.PanTool, null, tint = Zm.Yellow) }
                            }
                        }
                    }
                }
            }
            3 -> {
                // Every student ever invited stays a row in classroom_participants (their
                // status just moves to LEFT/KICKED/DECLINED) — a plain roleInSession filter
                // with no status check left them all listed here forever, including someone who
                // joined, left, then rejoined: the OLD left-behind-looking row and their current
                // one could momentarily sit side by side before the realtime update replaced it,
                // reading as "this person joined twice". Only someone currently connected
                // belongs in this panel.
                val students = state.participants.filter { it.roleInSession == "STUDENT" && it.status == "JOINED" }
                if (isModerator) {
                    if (students.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Default.Groups, null, tint = Zm.TextDim, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(tr(StrClassroom.noOneElseJoined), color = Zm.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(true to StrClassroom.muteAllStudents, false to StrClassroom.unmuteAllStudents).forEach { (lock, label) ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Zm.Divider.copy(alpha = 0.5f))
                                        .clickable { onMuteAll(lock) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(tr(label), color = if (lock) Zm.Red else Zm.Text, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(students, key = { it.id }) { p ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Zm.Divider.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(p.profile?.avatarUrl, p.profile?.fullName, size = 28.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.profile?.fullName ?: p.userId, color = Zm.Text, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onSetParticipantMedia(p.userId, !p.micLocked, null) }) {
                                        Icon(
                                            if (p.micLocked) Icons.Default.MicOff else Icons.Default.Mic,
                                            tr(if (p.micLocked) StrClassroom.unlockMic else StrClassroom.lockMic),
                                            tint = if (p.micLocked) Zm.Red else Zm.TextDim,
                                        )
                                    }
                                    IconButton(onClick = { onSetParticipantMedia(p.userId, null, !p.cameraLocked) }) {
                                        Icon(
                                            if (p.cameraLocked) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                            tr(if (p.cameraLocked) StrClassroom.unlockCamera else StrClassroom.lockCamera),
                                            tint = if (p.cameraLocked) Zm.Red else Zm.TextDim,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveStudent(p) }) {
                                        Icon(Icons.Default.PersonRemove, tr(StrClassroom.removeStudent), tint = Zm.TextDim)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Read-only for a student: just who is currently in the session, with the
                    // teacher pinned at the top — no mic/camera controls, those stay the
                    // moderator's alone.
                    val teacherRow = state.participants.firstOrNull {
                        it.roleInSession != "STUDENT" && it.status == "JOINED"
                    }
                    Text(
                        trf(StrClassroom.participantsCount, students.size + if (teacherRow != null) 1 else 0),
                        color = Zm.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (teacherRow == null && students.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Default.Groups, null, tint = Zm.TextDim, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(tr(StrClassroom.noOneElseJoined), color = Zm.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            teacherRow?.let { t ->
                                item(key = t.id) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Zm.Yellow.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Avatar(t.profile?.avatarUrl, t.profile?.fullName, size = 28.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(t.profile?.fullName ?: t.userId, color = Zm.Text)
                                    }
                                }
                            }
                            items(students, key = { it.id }) { p ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Zm.Divider.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(p.profile?.avatarUrl, p.profile?.fullName, size = 28.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.profile?.fullName ?: p.userId, color = Zm.Text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One chat/question bubble: sender's avatar + name for someone else's message (skipped for my
 * own, which aligns to the opposite side instead — the usual messaging-app convention so a long
 * thread reads at a glance without re-checking every line's sender), a colored rounded bubble,
 * and a small time label. [trailing] is the Questions tab's "mark answered" control / badge,
 * rendered to the side of the bubble; the Chat tab passes nothing.
 */
@Composable
private fun ChatBubbleRow(
    msg: ClassroomChatMessage,
    isMine: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isMine) {
            Avatar(msg.profile?.avatarUrl, msg.profile?.fullName, size = 26.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 230.dp)) {
            if (!isMine) {
                Text(
                    msg.profile?.fullName ?: "",
                    color = Zm.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMine) trailing?.invoke()
                Box(
                    Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isMine) 14.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 14.dp,
                            ),
                        )
                        .background(if (isMine) Zm.Yellow else Zm.Divider)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        msg.body,
                        color = if (isMine) Zm.Bg else Zm.Text,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!isMine) trailing?.invoke()
            }
            chatTimeLabel(msg.createdAt)?.let { time ->
                Text(
                    time,
                    color = Zm.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
        if (isMine) {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** A rounded, pill-shaped composer shared by the Chat and Questions tabs — the send button only
 *  lights up once there is something to send, instead of always looking active. */
@Composable
private fun ChatInputRow(value: String, onValueChange: (String) -> Unit, placeholder: String, onSend: () -> Unit) {
    val canSend = value.isNotBlank()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)),
            placeholder = { Text(placeholder, color = Zm.TextDim, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Zm.Divider,
                unfocusedContainerColor = Zm.Divider,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { if (canSend) onSend() },
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (canSend) Zm.Yellow else Zm.Divider),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (canSend) Zm.Bg else Zm.TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

private fun chatTimeLabel(createdAt: String?): String? {
    if (createdAt.isNullOrBlank()) return null
    return runCatching {
        DateTimeFormatter.ofPattern("h:mm a").format(Instant.parse(createdAt).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}
