package com.rork.pro

import android.app.Application
import org.jitsi.meet.sdk.JitsiMeet
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions

/**
 * 7PRO's Application class.
 *
 * The only thing it does today is register Jitsi Meet SDK defaults once for the whole
 * process, as the SDK's own docs require (`JitsiMeet.setDefaultConferenceOptions`). Every
 * per-session option (room, password/token, display name...) is still set per-call in
 * [com.rork.pro.classroom.ClassroomCallActivity] — this only carries the flags that never
 * change between sessions, so a call can never accidentally start with, say, chat left
 * enabled because one call site forgot to turn it off.
 */
class SevenProApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // First thing, before anything that could crash: remember why the app closed, if it does.
        CrashCatcher.install(this)

        // Jitsi Meet SDK requires Android 8.0 (API 26). The app installs from 7.0, so on older
        // phones Jitsi is never touched at all — live classes are simply unavailable there.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return

        val defaultOptions = JitsiMeetConferenceOptions.Builder()
            // Our own persisted chat/Q&A panel is the one source of truth for a session's
            // transcript, so Jitsi's built-in (ephemeral, unmoderated) chat stays off.
            .setFeatureFlag("chat.enabled", false)
            // Our own raise-hand button (backed by classroom_participants) drives the
            // teacher's live "raised hands" list, so the built-in one stays off too —
            // having both would let a student raise a hand the teacher's list never sees.
            .setFeatureFlag("raise-hand.enabled", false)
            .setFeatureFlag("invite.enabled", false)
            .setFeatureFlag("add-people.enabled", false)
            .setFeatureFlag("calendar.enabled", false)
            .setFeatureFlag("live-streaming.enabled", false)
            .setFeatureFlag("meeting-name.enabled", false)
            .setFeatureFlag("overflow-menu.enabled", true)
            .setFeatureFlag("screen-share.enabled", true)
            // "The class keeps running in a small window" needs three things agreeing, and this
            // is one of them: the SDK flag that permits shrinking instead of tearing the
            // conference down. The other two are the manifest (supportsPictureInPicture +
            // resizeableActivity — without both, Android refuses the shrink no matter what this
            // flag says) and ClassroomCallActivity, which supplies the aspect ratio and the
            // Android 12+ auto-enter behaviour.
            .setFeatureFlag("pip.enabled", true)
            .setFeatureFlag("pip-while-screen-sharing.enabled", true)
            // Noise suppression, where the deployed Jitsi version offers it as a user-facing
            // toggle. An unrecognised flag is ignored by the SDK's JS layer, so this is safe on
            // versions that do not have it; the actual per-call audio cleaning is done by the
            // config overrides in ClassroomCallActivity.applyAudioCleanupConfig, which do not
            // depend on this.
            .setFeatureFlag("noise-suppression.enabled", true)
            .setFeatureFlag("tile-view.enabled", true)
            .setFeatureFlag("kick-out.enabled", true)
            .setFeatureFlag("server-url-change.enabled", false)
            // CRITICAL — this is the fix for a confirmed real crash (IllegalStateException:
            // "In order to use RNScreens components your app's activity need to extend
            // ReactActivity"): the SDK's prejoin/preview page (camera/mic preview shown before
            // actually entering the call) is built with react-native-screens, which requires
            // the host Activity to literally be a ReactActivity. ClassroomCallActivity is a
            // plain ComponentActivity (required for our Compose UI around the Jitsi view), so
            // the instant the SDK tried to render that page, the app crashed — every single
            // time, with zero video ever shown, exactly matching the black-screen-then-close
            // reports. We already have our own equivalent screen (ClassroomLobbyScreen — "Ready
            // to join?") that does the same job (checks access, shows who's joining) before
            // ClassroomCallActivity is ever launched, so Jitsi's own prejoin page is not just
            // safe to disable here, it was always a redundant, incompatible duplicate of a
            // screen we already have.
            .setFeatureFlag("prejoinpage.enabled", false)
            // Keep everything inside our app. Without these, the SDK is willing to hand the
            // user off to an external browser / the standalone Jitsi app — which is exactly
            // what "أنا المضيف" (I am the host) did on a real device: it left 7PRO entirely to
            // do moderator sign-in elsewhere. `security-options` also hides the room
            // lock/password UI that triggers that flow in the first place.
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("security-options.enabled", false)
            .setFeatureFlag("lobby-mode.enabled", false)
            .setFeatureFlag("help.enabled", false)
            .setFeatureFlag("settings.enabled", false)
            .setFeatureFlag("video-share.enabled", false)
            .setFeatureFlag("close-captions.enabled", false)
            .setFeatureFlag("conference-timer.enabled", false)
            // The subject/room banner across the top of the video (the raw room id visible in
            // the reported screenshot) is noise for a student — our own header already shows
            // which class this is.
            .setFeatureFlag("meeting-password.enabled", false)
            // Our own bottom bar (see ClassroomCallActivity) now carries mic/camera/screen-share/
            // whiteboard/chat/end-call as one unified, RTL-aware control strip, driven by real
            // Jitsi SDK broadcast commands rather than Jitsi's own toolbar —
            // having both visible at once would be two divergent controls for the same state.
            .setFeatureFlag("toolbox.enabled", false)
            .build()

        JitsiMeet.setDefaultConferenceOptions(defaultOptions)
    }
}
