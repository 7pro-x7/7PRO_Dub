package com.rork.pro.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * How a lesson media URL should be presented.
 *
 * [Embedded] is played through an in-app web view (YouTube, Vimeo, Google Drive…),
 * [Streamed] through the native player, and [Link] can only be handed to another app.
 */
sealed interface LessonMedia {
    /**
     * A YouTube video, played through the IFrame Player API with YouTube's own on-screen
     * controls switched off entirely (`controls=0`) and 7PRO's controls drawn over it instead.
     *
     * Separate from [Embedded] because YouTube's default embed chrome is the one thing this app
     * must never show: its paused overlay carries a share/copy-link button, a clickable video
     * title, a channel avatar and a watermark, every one of which hands the student the raw
     * video URL. Those live inside a cross-origin iframe, so they cannot be hidden with CSS,
     * removed from the DOM, or reliably covered up — the only real fix is to never ask YouTube
     * to draw them. See [YouTubeVideo].
     *
     * [portrait] records that the source is a tall 9:16 video (a YouTube Short, for example).
     */
    data class YouTube(val videoId: String, val portrait: Boolean = false) : LessonMedia

    /**
     * Provider page that exposes an iframe embed.
     *
     * [portrait] records that the source is a tall 9:16 video (a YouTube Short, for example),
     * which decides how the screen is turned when the player goes fullscreen.
     */
    data class Embedded(val embedUrl: String, val portrait: Boolean = false) : LessonMedia

    /** Direct media file or stream playable by ExoPlayer. */
    data class Streamed(val url: String) : LessonMedia

    /** Anything else (documents, unknown pages) — opened outside the app. */
    data object Link : LessonMedia
}

private val streamableExtensions = setOf(
    "mp4", "m4v", "mov", "webm", "mkv", "3gp", "m3u8", "mpd",
    "mp3", "m4a", "aac", "wav", "ogg", "opus",
)

/** YouTube Shorts are always shot and published 9:16. */
private fun isYouTubeShort(host: String, uri: Uri): Boolean =
    (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) &&
        uri.pathSegments.orEmpty().firstOrNull() == "shorts"

/**
 * Confirms a YouTube video's real orientation via the public oEmbed endpoint.
 *
 * The `/shorts/{id}` URL form is used here on purpose, for every video regardless of whether it
 * actually is a Short: oEmbed silently locks its reported width/height to a fixed 16:9 shape
 * whenever it is queried with the ordinary `/watch?v=` URL, no matter the source video's real
 * proportions — that quirk was the actual bug here, since it meant this "confirmation" was
 * unconditionally overwriting a correct portrait guess (from the `/shorts/` URL check above)
 * with an incorrect landscape one. Querying the same video id through the `/shorts/` URL form
 * instead returns its true width/height either way, which is what lets this genuinely correct a
 * wrong first guess (a vertical video pasted as a normal `/watch?v=` link) rather than just
 * rubber-stamping "landscape" on everything. Returns null on any failure (offline, blocked,
 * unexpected response) so the caller can keep using its existing guess instead of stalling or
 * crashing playback.
 */
private suspend fun fetchYouTubeAspect(videoId: String): Float? = withContext(Dispatchers.IO) {
    runCatching {
        val endpoint = "https://www.youtube.com/oembed?format=json&url=" +
            Uri.encode("https://www.youtube.com/shorts/$videoId")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.requestMethod = "GET"
        try {
            require(connection.responseCode in 200..299)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val width = json.optDouble("width", -1.0)
            val height = json.optDouble("height", -1.0)
            require(width > 0 && height > 0)
            (width / height).toFloat()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun youTubeVideoId(host: String, uri: Uri): String? {
    val segments = uri.pathSegments.orEmpty()
    val id = when {
        host == "youtu.be" -> segments.firstOrNull()
        host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> when (segments.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")
            "embed", "shorts", "live", "v" -> segments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
        else -> null
    }
    return id?.takeIf { it.isNotBlank() }
}

/** Maps a teacher-supplied lesson URL to the best in-app playback strategy. */
fun resolveLessonMedia(raw: String): LessonMedia {
    val url = raw.trim()
    if (url.isEmpty()) return LessonMedia.Link

    val uri = runCatching { url.toUri() }.getOrNull() ?: return LessonMedia.Link
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return LessonMedia.Link

    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return LessonMedia.Link
    val segments = uri.pathSegments.orEmpty()

    youTubeVideoId(host, uri)?.let { id ->
        // Sanitised because this id is pasted straight into a JavaScript string literal in
        // [youTubeHtml]. YouTube ids are only ever [A-Za-z0-9_-], so anything else is either a
        // parsing mistake or an injection attempt, and both are safest treated as "not a video".
        val safeId = id.takeIf { it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
        if (safeId != null) return LessonMedia.YouTube(safeId, portrait = isYouTubeShort(host, uri))
        return LessonMedia.Link
    }

    if (host == "player.vimeo.com") return LessonMedia.Embedded(url)
    if (host == "vimeo.com") {
        val id = segments.lastOrNull { it.all { c -> c.isDigit() } }
        if (!id.isNullOrBlank()) return LessonMedia.Embedded("https://player.vimeo.com/video/$id")
    }

    if (host == "drive.google.com") {
        val id = if (segments.getOrNull(0) == "file" && segments.getOrNull(1) == "d") {
            segments.getOrNull(2)
        } else {
            uri.getQueryParameter("id")
        }
        if (!id.isNullOrBlank()) return LessonMedia.Embedded("https://drive.google.com/file/d/$id/preview")
    }

    if (host == "dailymotion.com" && segments.getOrNull(0) == "video") {
        segments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
            return LessonMedia.Embedded("https://www.dailymotion.com/embed/video/$it")
        }
    }

    val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
    if (extension in streamableExtensions) return LessonMedia.Streamed(url)

    return LessonMedia.Link
}

/** Inline player used inside the lesson card. Renders nothing for [LessonMedia.Link]. */
@Composable
fun LessonVideo(media: LessonMedia, modifier: Modifier = Modifier) {
    // Course video must never be capturable off-device — screenshots, screen recording, and
    // casting are all blocked for as long as an actual video is on screen. Link isn't playback
    // inside this app (it just hands the URL to another app), so it's excluded.
    if (media != LessonMedia.Link) SecureScreen()

    when (media) {
        is LessonMedia.YouTube -> YouTubeVideo(media.videoId, media.portrait, modifier)
        is LessonMedia.Embedded -> EmbeddedVideo(media.embedUrl, media.portrait, modifier)
        is LessonMedia.Streamed -> StreamedVideo(media.url, modifier)
        LessonMedia.Link -> Unit
    }
}

// ---------------------------------------------------------------- fullscreen plumbing

/** Walks the context chain to the hosting activity, which owns orientation and system bars. */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Locks the device to whichever orientation matches [aspect] for as long as a video is shown
 * fullscreen — landscape for a wide (16:9-style) video, portrait for a tall (9:16-style) one —
 * and returns whatever the activity was set to before, so the caller can restore it on exit.
 * A `null` or exactly-square aspect is left alone: there is no "correct" spin for it, so
 * fighting the user's current orientation would only be annoying.
 */
private fun Activity.lockOrientationForFullscreenVideo(aspect: Float?): Int {
    val previous = requestedOrientation
    if (aspect != null && aspect != 1f) {
        requestedOrientation = if (aspect < 1f) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    return previous
}

/** Hides or restores the status and navigation bars of a window. */
private fun Window.setImmersive(enabled: Boolean, anchor: View) {
    val controller = WindowInsetsControllerCompat(this, anchor)
    if (enabled) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

// ---------------------------------------------------------------- YouTube (no YouTube chrome)

/**
 * The page that hosts a YouTube video with **none of YouTube's own on-screen controls**.
 *
 * This is the whole anti-link-copying mechanism, and it is worth being precise about why it is
 * built this way rather than as an overlay.
 *
 * A normal YouTube embed draws, inside a cross-origin iframe: a share/copy-link button, the
 * video title, the channel name and avatar, a "Watch on YouTube" affordance and a corner
 * watermark. Every one of those either copies or navigates to the raw video URL. Because the
 * iframe is cross-origin, the host page cannot restyle it, cannot remove those nodes, and
 * cannot listen for clicks on them — so the only previous defence was an invisible box drawn
 * over one corner. That can never be airtight: the icons move between YouTube's paused, playing
 * and ended states, their positions change with player size and A/B tests, and a guard sized for
 * one of those states is simply not over the icon in another.
 *
 * `controls=0` removes the problem at the source instead of hiding it: YouTube is never asked to
 * draw a control bar at all, so there is no share button, no title bar and no channel link to
 * cover. 7PRO draws its own play/pause, seek bar and fullscreen button over the video, and every
 * touch is consumed by the app before it can reach the page (see the touch listener in
 * [YouTubeVideo]), so even a watermark that YouTube still paints cannot be tapped.
 *
 * The video itself is still genuinely streamed and counted by YouTube — this is YouTube's own
 * IFrame Player API, the officially supported way to embed with custom controls, not a scraper
 * or a proxy.
 *
 * FALLBACK: if the IFrame API fails to load or initialise (a captive-portal network, an
 * ad-blocking DNS, a video that refuses API embedding), the page swaps itself back to a plain
 * iframe embed after 8 seconds and tells the app via `onFallback`. A lesson that plays with an
 * imperfectly hidden link is much better than a lesson that does not play at all; the app then
 * re-enables the old corner guards for that case.
 */
private fun youTubeHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
  html,body{margin:0;padding:0;background:#000;height:100%;width:100%;overflow:hidden}
  #stage{position:absolute;top:0;left:0;width:100%;height:100%}
  #stage iframe{width:100%;height:100%;border:0;display:block}
  *{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent}
</style>
</head>
<body>
<div id="stage"><div id="player"></div></div>
<script>
  var player = null, ready = false, timer = null, fellBack = false;
  function app(){ return window.AndroidPlayer; }
  function onYouTubeIframeAPIReady(){
    try{
      player = new YT.Player('player', {
        videoId: '__VIDEO_ID__',
        host: 'https://www.youtube-nocookie.com',
        playerVars: {
          controls: 0, rel: 0, playsinline: 1, fs: 0, disablekb: 1,
          iv_load_policy: 3, modestbranding: 1, cc_load_policy: 0,
          origin: location.origin
        },
        events: {
          'onReady': function(){
            ready = true;
            try{ app().onReady(player.getDuration() || 0); }catch(e){}
            tick();
            if(timer) clearInterval(timer);
            timer = setInterval(tick, 400);
          },
          'onStateChange': function(e){ try{ app().onState(e.data); }catch(x){} },
          'onError': function(){ fallback(); }
        }
      });
    }catch(e){ fallback(); }
  }
  function tick(){
    try{ app().onTime(player.getCurrentTime() || 0, player.getDuration() || 0); }catch(e){}
  }
  function cmd(action, value){
    try{
      if(!player) return;
      if(action === 'play') player.playVideo();
      else if(action === 'pause') player.pauseVideo();
      else if(action === 'seek'){ player.seekTo(value, true); tick(); }
    }catch(e){}
  }
  function fallback(){
    if(ready || fellBack) return;
    fellBack = true;
    try{
      document.getElementById('stage').innerHTML =
        '<iframe src="https://www.youtube-nocookie.com/embed/__VIDEO_ID__?rel=0&playsinline=1&modestbranding=1&fs=1&iv_load_policy=3&disablekb=1" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe>';
    }catch(e){}
    try{ app().onFallback(); }catch(e){}
  }
  setTimeout(fallback, 8000);
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>
""".replace("__VIDEO_ID__", videoId)

/** mm:ss, the only time format a lesson of this length ever needs. */
private fun formatPlaybackTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeVideo(videoId: String, portrait: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }
    var inlineHost by remember(videoId) { mutableStateOf<FrameLayout?>(null) }

    var duration by remember(videoId) { mutableStateOf(0f) }
    var position by remember(videoId) { mutableStateOf(0f) }
    var playing by remember(videoId) { mutableStateOf(false) }
    var ended by remember(videoId) { mutableStateOf(false) }
    var controlsVisible by remember(videoId) { mutableStateOf(true) }
    var scrubbing by remember(videoId) { mutableStateOf(false) }
    var scrubValue by remember(videoId) { mutableStateOf(0f) }
    var isFullscreen by remember(videoId) { mutableStateOf(false) }

    // A normal /watch?v= URL does not tell us whether the source is portrait. Never treat
    // that unknown state as landscape, because doing so can rotate a real vertical video
    // sideways if the student taps fullscreen before oEmbed responds. Shorts are known
    // immediately; every other YouTube URL stays unknown until the real dimensions arrive.
    var effectivePortrait by remember(videoId, portrait) {
        mutableStateOf<Boolean?>(if (portrait) true else null)
    }
    LaunchedEffect(videoId, portrait) {
        if (!portrait) {
            effectivePortrait = fetchYouTubeAspect(videoId)?.let { aspect -> aspect < 1f }
        }
    }

    // Held as a MutableState rather than a plain `var` because the WebView's touch listener is
    // built once, inside the factory, and must keep reading the *current* value: touches are
    // swallowed while our own controls are in charge, and let through only if the page had to
    // fall back to a stock embed (which has no other controls at all).
    val fellBackToPlainEmbed = remember(videoId) { mutableStateOf(false) }

    val html = remember(videoId) { youTubeHtml(videoId) }

    fun send(action: String, value: Float = 0f) {
        webView?.evaluateJavascript("cmd('$action',$value);", null)
    }

    /** Moves the single live WebView between the inline card and the fullscreen dialog. */
    fun attachTo(target: FrameLayout?) {
        val view = webView ?: return
        if (target == null || view.parent === target) return
        (view.parent as? ViewGroup)?.removeView(view)
        target.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    // Controls fade out on their own while the video is running, the way every video player
    // behaves — otherwise the bar sits permanently over the bottom of the lesson.
    LaunchedEffect(controlsVisible, playing, videoId) {
        if (controlsVisible && playing) {
            delay(3500)
            controlsVisible = false
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.evaluateJavascript("cmd('pause',0);", null)
                    webView?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (effectivePortrait == true) 9f / 16f else 16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        key(videoId) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { viewContext ->
                    val host = FrameLayout(viewContext)
                    inlineHost = host
                    val view = WebView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        // No long-press menu ("copy link address", "open in browser", "share")
                        // and no text selection anywhere in the page.
                        isLongClickable = false
                        setOnLongClickListener { true }
                        // THE touch barrier. Returning true consumes the event here, so nothing
                        // the page draws — watermark, an unexpected overlay, a future YouTube
                        // redesign — can ever be tapped. All real control comes from the Compose
                        // chrome below via the JS bridge. The one exception is the fallback
                        // embed, which has no other controls, so it must receive touches.
                        setOnTouchListener { _, _ -> !fellBackToPlainEmbed.value }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url ?: return false
                                val scheme = target.scheme?.lowercase()
                                // intent://, vnd.youtube:, market:// exist only to hand the video
                                // to the standalone YouTube app. Never content, always blocked.
                                if (scheme != "http" && scheme != "https") return true
                                val host = target.host?.lowercase()?.removePrefix("www.") ?: return false
                                val path = target.path.orEmpty()
                                // Any attempt to leave the embed for the full site is refused;
                                // the player's own internal/CDN requests are untouched so
                                // playback is never at risk.
                                return (host == "youtube.com" || host == "m.youtube.com") &&
                                    (path == "/" || path.startsWith("/watch") || path.startsWith("/channel") ||
                                        path.startsWith("/c/") || path.startsWith("/@") || path.startsWith("/results"))
                            }
                        }

                        webChromeClient = WebChromeClient()

                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun onReady(seconds: Double) = mainHandler.post {
                                    if (seconds > 0) duration = seconds.toFloat()
                                }

                                @android.webkit.JavascriptInterface
                                fun onTime(current: Double, total: Double) = mainHandler.post {
                                    if (!scrubbing) position = current.toFloat().coerceAtLeast(0f)
                                    if (total > 0) duration = total.toFloat()
                                }

                                /** YT.PlayerState: 0 ended, 1 playing, 2 paused, 3 buffering. */
                                @android.webkit.JavascriptInterface
                                fun onState(state: Int) = mainHandler.post {
                                    playing = state == 1
                                    ended = state == 0
                                    if (state == 1) ended = false
                                    if (state != 1) controlsVisible = true
                                }

                                @android.webkit.JavascriptInterface
                                fun onFallback() = mainHandler.post {
                                    fellBackToPlainEmbed.value = true
                                }
                            },
                            "AndroidPlayer",
                        )

                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        // A real https base URL is required: the IFrame API refuses to talk to a
                        // page whose origin is "null", which is what a plain loadData gives.
                        loadDataWithBaseURL(
                            "https://www.youtube-nocookie.com",
                            html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    webView = view
                    host.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    host
                },
                onRelease = {
                    val view = webView
                    webView = null
                    inlineHost = null
                    view?.let {
                        (it.parent as? ViewGroup)?.removeView(it)
                        it.loadUrl("about:blank")
                        it.destroy()
                    }
                },
            )
        }

        if (fellBackToPlainEmbed.value) {
            // Degraded path only: YouTube's own chrome is back, so the old corner guard returns
            // with it. Not as good as controls=0 — which is exactly why it is a last resort.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(0.25f)
                        .fillMaxHeight(0.25f)
                        .pointerInput(videoId) { detectTapGestures { } },
                )
            }
        } else if (!isFullscreen) {
            YouTubeChrome(
                visible = controlsVisible,
                playing = playing,
                ended = ended,
                position = if (scrubbing) scrubValue else position,
                duration = duration,
                isFullscreen = false,
                onToggleVisible = { controlsVisible = !controlsVisible },
                onPlayPause = {
                    controlsVisible = true
                    if (playing) send("pause") else send("play")
                },
                onScrub = { scrubbing = true; scrubValue = it; controlsVisible = true },
                onScrubEnd = { send("seek", scrubValue); position = scrubValue; scrubbing = false },
                onToggleFullscreen = {
                    // Do not allow a fullscreen rotation decision while the source orientation
                    // is still unknown. This prevents /watch?v= portrait videos from ever being
                    // forced into landscape because the metadata request is still in flight.
                    if (effectivePortrait != null) isFullscreen = true
                },
            )
        }
    }

    if (isFullscreen && !fellBackToPlainEmbed.value) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val dialogView = LocalView.current
            SecureScreen() // fullscreen dialog opens its own Window — needs FLAG_SECURE too
            DisposableEffect(dialogView) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window
                window?.let {
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    it.setImmersive(true, dialogView)
                }
                val previousOrientation =
                    activity?.lockOrientationForFullscreenVideo(
                        effectivePortrait?.let { if (it) 9f / 16f else 16f / 9f }
                    )
                onDispose {
                    window?.setImmersive(false, dialogView)
                    previousOrientation?.let { activity?.requestedOrientation = it }
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // The SAME WebView is moved in here rather than a second one being created, so
                // going fullscreen never restarts the video or costs a second stream.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext -> FrameLayout(viewContext).also { attachTo(it) } },
                    onRelease = { attachTo(inlineHost) },
                )
                YouTubeChrome(
                    visible = controlsVisible,
                    playing = playing,
                    ended = ended,
                    position = if (scrubbing) scrubValue else position,
                    duration = duration,
                    isFullscreen = true,
                    onToggleVisible = { controlsVisible = !controlsVisible },
                    onPlayPause = {
                        controlsVisible = true
                        if (playing) send("pause") else send("play")
                    },
                    onScrub = { scrubbing = true; scrubValue = it; controlsVisible = true },
                    onScrubEnd = { send("seek", scrubValue); position = scrubValue; scrubbing = false },
                    onToggleFullscreen = { isFullscreen = false },
                )
            }
        }
    }
}

/**
 * 7PRO's own player controls, drawn over the video in place of YouTube's.
 *
 * Forced to left-to-right: a seek bar that runs right-to-left because the app is in Arabic
 * would be actively misleading, since the video frame underneath it is never mirrored.
 */
@Composable
private fun YouTubeChrome(
    visible: Boolean,
    playing: Boolean,
    ended: Boolean,
    position: Float,
    duration: Float,
    isFullscreen: Boolean,
    onToggleVisible: () -> Unit,
    onPlayPause: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onToggleVisible() } },
        ) {
            // `controls=0` removes YouTube's control bar, but YouTube still paints a thin
            // overlay pinned to the FRAME's corners — video title, channel avatar, and on some
            // builds a share/link affordance — during the paused and not-yet-started states.
            // (`showinfo`, the parameter that used to turn that off, was deprecated in 2018 and
            // is ignored today; there is no replacement.) It is already harmless, because the
            // touch listener in [YouTubeVideo] swallows every touch before it reaches the page
            // — but it should not be *visible* either, so these two opaque bands sit exactly
            // over the strips it occupies. They are drawn only while paused, which is the only
            // time that overlay exists, so a playing video is never covered.
            if (!playing) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.22f)
                        .background(Color.Black),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.22f)
                        .background(Color.Black),
                )
            }

            // Tapping anywhere brings the controls back, so they are free to hide themselves.
            if (visible) {
            // A light scrim so white controls stay readable over a bright frame.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { onPlayPause() } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        ended -> Icons.Default.Replay
                        playing -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    formatPlaybackTime(position),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = position.coerceIn(0f, duration.coerceAtLeast(0.1f)),
                    onValueChange = onScrub,
                    onValueChangeFinished = onScrubEnd,
                    valueRange = 0f..duration.coerceAtLeast(0.1f),
                    enabled = duration > 0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPlaybackTime(duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures { onToggleFullscreen() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            }
        }
    }
}

// ---------------------------------------------------------------- embedded (WebView)

private fun embedHtml(embedUrl: String): String =
    "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">" +
        "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}" +
        "iframe{border:0;display:block;width:100%;height:100%}</style>" +
        "</head><body>" +
        "<iframe src=\"" + embedUrl + "\" " +
        "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen\" " +
        "allowfullscreen></iframe>" +
        "<script>" +
        "window.addEventListener('message',function(e){" +
        "  try{var d=typeof e.data==='string'?JSON.parse(e.data):e.data;" +
        "  if(d&&d.type==='videoOrientation'&&window.Android)" +
        "    window.Android.onVideoOrientation(d.aspect||1.0,d.orientation||'landscape');" +
        "  }catch(x){}});" +
        "</script></body></html>"

/**
 * Hosts the video view that a web player hands over when its fullscreen button is pressed.
 *
 * The provider (YouTube, Vimeo…) does not resize itself: it calls `onShowCustomView` and
 * expects the app to display that view fullscreen. Without this the button does nothing.
 */
private class WebFullscreenHost(private val activity: Activity?) {
    // Mutable rather than passed once at construction: this host is created a single time
    // and handed to the WebView's WebChromeClient inside AndroidView's `factory`, which only
    // ever runs once. If [aspect] were a constructor value instead, updating it would mean
    // building a *new* WebFullscreenHost — but the WebChromeClient closure would keep calling
    // the old, stale instance forever, so the corrected orientation (confirmed after the page
    // loads) would never actually reach show(). Updating this field in place is what lets the
    // one-time-created closure see the real, later-confirmed aspect.
    var aspect: Float? = null

    // The video view and its corner guard travel together as one unit, so hide() can tear
    // down both with a single removeView call on the root.
    private var container: FrameLayout? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var previousOrientation: Int? = null

    var isActive: Boolean by mutableStateOf(false)
        private set

    fun show(view: View, viewCallback: WebChromeClient.CustomViewCallback) {
        val root = activity?.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            viewCallback.onCustomViewHidden()
            return
        }
        if (container != null) hide()
        callback = viewCallback

        // Same corner-icon blackout used inline (see EmbeddedVideo's playGate/share-icon
        // boxes), reapplied here because YouTube hands this view to a native Android
        // ViewGroup added straight to the activity's content — completely outside the
        // Compose tree that hosts the inline overlays. Without a fullscreen-specific
        // guard, entering fullscreen would remove every protection at once.
        disableLongClick(view)
        val guard = View(view.context).apply {
            setOnTouchListener { _, _ -> true }
            isLongClickable = true
            setOnLongClickListener { true }
        }

        val frame = FrameLayout(view.context)
        frame.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(
            guard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                // Bottom-right corner, forced regardless of the app's Arabic (RTL) layout —
                // the video chrome itself is never mirrored, so the icon always sits in the
                // player's true bottom-right.
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT
                width = FrameLayout.LayoutParams.MATCH_PARENT
                height = FrameLayout.LayoutParams.MATCH_PARENT
            },
        )
        // Resize the guard to the same relative 25%x25% corner box once the frame has real
        // dimensions (fullscreen size isn't known until layout).
        frame.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = frame.width
                val h = frame.height
                if (w > 0 && h > 0) {
                    guard.layoutParams = FrameLayout.LayoutParams((w * 0.25f).toInt(), (h * 0.25f).toInt()).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT
                    }
                    guard.requestLayout()
                }
            }
        })

        container = frame
        root.addView(
            frame,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.window.setImmersive(true, view)
        previousOrientation = activity.lockOrientationForFullscreenVideo(aspect)
        isActive = true
    }

    private fun disableLongClick(view: View) {
        view.isLongClickable = false
        view.setOnLongClickListener { true }
    }

    fun hide() {
        val frame = container ?: return
        (frame.parent as? ViewGroup)?.removeView(frame)
        container = null
        runCatching { callback?.onCustomViewHidden() }
        callback = null
        activity?.let {
            it.window.setImmersive(false, it.window.decorView)
            previousOrientation?.let { orientation -> it.requestedOrientation = orientation }
        }
        previousOrientation = null
        isActive = false
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedVideo(embedUrl: String, portrait: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val html = remember(embedUrl) { embedHtml(embedUrl) }
    val baseUrl = remember(embedUrl) {
        val uri = runCatching { embedUrl.toUri() }.getOrNull()
        val host = uri?.host
        if (host.isNullOrBlank()) null else "https://$host"
    }

    // Detected aspect ratio from JavaScript — starts with a URL-based heuristic (which for
    // every non-YouTube provider here is just "assume landscape", since none of their URLs
    // reveal orientation the way a YouTube Short's does), then updates to the actual video
    // dimensions once the page loads. [aspectConfirmed] tracks whether that real measurement
    // has actually landed yet, so the still-unverified guess is never used to force-rotate the
    // screen — see its use below.
    var detectedAspect by remember(embedUrl) {
        mutableStateOf(if (portrait) 9f / 16f else 16f / 9f)
    }
    var aspectConfirmed by remember(embedUrl) { mutableStateOf(false) }

    // Created once and kept for the composable's whole lifetime — see the comment on
    // WebFullscreenHost.aspect for why this must NOT be recreated when the aspect changes.
    val fullscreen = remember(activity) { WebFullscreenHost(activity) }

    // Until the real dimensions are confirmed, keep aspect as null rather than the unverified
    // guess: lockOrientationForFullscreenVideo() treats null as "leave the current orientation
    // alone", which is far safer than force-rotating a portrait video to landscape on a wrong
    // guess. Once JS confirms the real orientation, update the field in place so the
    // already-created WebChromeClient (see the AndroidView factory below) picks it up on its
    // next call to fullscreen.show().
    LaunchedEffect(detectedAspect, aspectConfirmed) {
        fullscreen.aspect = if (aspectConfirmed) detectedAspect else null
    }
    var webView by remember(embedUrl) { mutableStateOf<WebView?>(null) }

    // Leaving fullscreen with the system back gesture, exactly like the browser does.
    BackHandler(enabled = fullscreen.isActive) { fullscreen.hide() }

    DisposableEffect(fullscreen) {
        onDispose { fullscreen.hide() }
    }

    // Stop playback (and its audio) when the lesson leaves the screen.
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(detectedAspect)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        // Keyed on the URL: AndroidView's `factory` only ever runs once per node and this
        // WebView has no `update` callback, so without this key switching to another lesson
        // would leave the old page loaded forever — the sidebar would highlight the new
        // lesson while the video area silently kept showing the previous one. Keying forces
        // the whole node (and the WebView inside it) to be torn down and rebuilt fresh
        // whenever the embed URL changes.
        key(embedUrl) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // Blocks the system's long-press-on-link menu (copy link address,
                    // open in browser, share link) so the raw video URL can never be
                    // extracted or opened from inside the in-app player.
                    isLongClickable = false
                    setOnLongClickListener { true }
                    webViewClient = object : WebViewClient() {
                        // YouTube's own iframe chrome (its logo, the video title, the
                        // channel name, and the "watch on YouTube" overlay) all try to
                        // navigate to youtube.com when tapped. Since youtube.com is a
                        // verified Android app link, letting that navigation through can
                        // hand the whole screen to the standalone YouTube app — exactly
                        // the "opens outside the app" behavior this player must never
                        // allow. Only that escape (and any non-http app-handoff scheme)
                        // is blocked below; the player's own internal embed/CDN requests
                        // are left alone so playback is never at risk of breaking.
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): Boolean {
                            val target = request?.url ?: return false
                            val scheme = target.scheme?.lowercase()
                            // Non-http(s) schemes (intent://, vnd.youtube:, market://…) exist
                            // only to hand the video off to the standalone app — never used to
                            // deliver actual video content, so always safe to block outright.
                            if (scheme != "http" && scheme != "https") return true
                            val host = target.host?.lowercase()?.removePrefix("www.") ?: return false
                            val path = target.path.orEmpty()
                            // Tapping the video title, channel name, or the corner logo tries to
                            // leave the embed for the full YouTube site — block only that, so the
                            // player's own internal resource/redirect requests (embed pages, CDN
                            // domains) keep working exactly as before and playback never breaks.
                            val leavesForFullSite = (host == "youtube.com" || host == "m.youtube.com") &&
                                (path == "/" || path.startsWith("/watch") || path.startsWith("/channel") ||
                                    path.startsWith("/c/") || path.startsWith("/@") || path.startsWith("/results"))
                            return leavesForFullSite
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Wait a moment for iframes (YouTube, Vimeo…) to finish loading,
                            // then inject JavaScript to detect the actual video orientation.
                            view?.postDelayed({
                                view.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            var vw = 0, vh = 0;
                                            // Try to find a video element anywhere in the page
                                            var videos = document.querySelectorAll('video');
                                            if (videos.length > 0) {
                                                var best = null;
                                                for (var i = 0; i < videos.length; i++) {
                                                    if (videos[i].videoWidth > 0 && videos[i].videoHeight > 0) {
                                                        if (!best || videos[i].videoWidth * videos[i].videoHeight > best.videoWidth * best.videoHeight) {
                                                            best = videos[i];
                                                        }
                                                    }
                                                }
                                                if (best) { vw = best.videoWidth; vh = best.videoHeight; }
                                            }
                                            // Fallback: try the main iframe's content
                                            if (vw === 0 || vh === 0) {
                                                var iframe = document.querySelector('iframe');
                                                if (iframe && iframe.contentDocument) {
                                                    var iv = iframe.contentDocument.querySelector('video');
                                                    if (iv && iv.videoWidth > 0 && iv.videoHeight > 0) {
                                                        vw = iv.videoWidth; vh = iv.videoHeight;
                                                    }
                                                }
                                            }
                                            // Fallback: document dimensions (works when iframe fills the page)
                                            if (vw === 0 || vh === 0) {
                                                vw = document.documentElement.clientWidth || document.body.clientWidth;
                                                vh = document.documentElement.clientHeight || document.body.clientHeight;
                                            }
                                            if (vw > 0 && vh > 0) {
                                                var ratio = vw / vh;
                                                var orient = ratio >= 1.0 ? 'landscape' : 'portrait';
                                                window.parent.postMessage(JSON.stringify({type:'videoOrientation', aspect:ratio, orientation:orient}), '*');
                                            }
                                        } catch(e) {}
                                    })();
                                    """.trimIndent(),
                                    null,
                                )
                            }, 800L)
                        }
                    }
                    // JS bridge: the HTML page's message listener calls this when
                    // the embedded player reports its actual video orientation.
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onVideoOrientation(aspect: Double, orientation: String) {
                            activity?.runOnUiThread {
                                val a = aspect.toFloat()
                                if (a.isFinite() && a > 0f) {
                                    detectedAspect = a
                                    aspectConfirmed = true
                                }
                            }
                        }
                    }, "Android")
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            fullscreen.show(view, callback)
                        }

                        override fun onHideCustomView() {
                            fullscreen.hide()
                        }
                    }
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                    webView = this
                }
            },
            onRelease = { view ->
                fullscreen.hide()
                webView = null
                view.loadUrl("about:blank")
                view.destroy()
            },
        )
        }

        // YouTube's own pre-play card carries its branding bar (logo, title, "watch on
        // YouTube") and a share/copy-link icon — content from a cross-origin iframe our
        // page cannot restyle or remove. This overlay blocks every tap outside a center
        // "play" hit-zone so none of those hotspots ever do anything; a tap inside the
        // zone removes the overlay for good, handing normal control (including the real
        // fullscreen button) straight to the player from then on.
        var playGateOpen by remember(embedUrl) { mutableStateOf(true) }
        if (playGateOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(embedUrl) {
                        detectTapGestures { offset ->
                            val withinCenter =
                                offset.x in (size.width * 0.3f)..(size.width * 0.7f) &&
                                    offset.y in (size.height * 0.25f)..(size.height * 0.75f)
                            if (withinCenter) playGateOpen = false
                        }
                    },
            )
        }

        // The pre-play overlay above only exists before the first tap. It's also visible from
        // the very start (paused, before playback), sitting in the bottom-right corner of the
        // control bar right next to the YouTube wordmark — confirmed against a screenshot of
        // this exact player. It lives inside the cross-origin iframe, so it can't be removed
        // or restyled from here — the only way to disable it is a small always-on overlay
        // sitting exactly on top of it that swallows the tap before it ever reaches the
        // iframe. Sized to cover the icon with a comfortable touch-target margin while staying
        // clear of the seek bar above it and the YouTube logo to its left. Forced to LTR so
        // the corner stays the video's actual bottom-right regardless of the app's Arabic
        // (RTL) layout; the video itself is never mirrored.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.25f)
                    .pointerInput(embedUrl) { detectTapGestures { } },
            )
        }
    }
}

// ---------------------------------------------------------------- streamed (ExoPlayer)

@OptIn(UnstableApi::class)
@Composable
private fun StreamedVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // The frame starts 16:9 and reshapes to the video's real proportions once known,
    // so portrait (9:16) and square lessons are shown at full size instead of letterboxed.
    var videoAspect by remember(url) { mutableStateOf<Float?>(null) }
    var isFullscreen by remember(url) { mutableStateOf(false) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) {
                        videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }
            })
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(videoAspect ?: (16f / 9f))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // Setting a listener is what makes the fullscreen button appear.
                    setFullscreenButtonClickListener { isFullscreen = true }
                }
            },
            update = { view ->
                // Only one surface may hold the player, so the inline view lets go while expanded.
                view.player = if (isFullscreen) null else player
                view.setFullscreenButtonState(false)
            },
            onRelease = { view -> view.player = null },
        )
    }

    if (isFullscreen) {
        FullscreenPlayer(
            player = player,
            aspect = videoAspect,
            activity = activity,
            onExit = { isFullscreen = false },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FullscreenPlayer(
    player: ExoPlayer,
    aspect: Float?,
    activity: Activity?,
    onExit: () -> Unit,
) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SecureScreen() // fullscreen dialog opens its own Window — needs FLAG_SECURE too
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setImmersive(true, dialogView)
            }
            val previousOrientation = activity?.lockOrientationForFullscreenVideo(aspect)
            onDispose {
                window?.setImmersive(false, dialogView)
                previousOrientation?.let { activity?.requestedOrientation = it }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setFullscreenButtonClickListener { onExit() }
                    }
                },
                update = { view ->
                    view.player = player
                    view.setFullscreenButtonState(true)
                },
                onRelease = { view -> view.player = null },
            )
        }
    }
}
