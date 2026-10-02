package com.rork.pro.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.rork.pro.ui.i18n.StrAds
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Ink
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.rork.pro.BuildConfig
import com.rork.pro.data.AdsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Ads"

/**
 * After a failed load we retry with increasing back-off: 10 s, 20 s, 40 s … capped at 60 s.
 * The banner keeps retrying as long as it is visible — no permanent give-up.
 */
private const val INITIAL_RETRY_MS = 10_000L
private const val MAX_RETRY_MS = 60_000L

/** How long an ad slot waits for the SDK to finish starting before it requests anyway. */
private const val READY_TIMEOUT_MS = 15_000L

/**
 * Starts the ads SDK once per process, and only when a real application id was built in.
 *
 * Without an id the SDK is never touched at all, so a build with no AdMob account behaves
 * exactly as if ads did not exist.
 */
private object Ads {
    /**
     * Google's own units, used on development builds and on emulators.
     *
     * A live unit is never filled on an emulator and tapping a live ad while testing puts the
     * AdMob account at risk, so testing devices always request Google's demo ads while real
     * phones always request the units the owner configured in the database.
     */
    private const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE_UNIT = "ca-app-pub-3940256099942544/2247696110"

    @Volatile
    private var started = false

    /** Completes once the SDK reports it is ready, so no ad is ever requested too early. */
    private val ready = CompletableDeferred<Unit>()

    val configured: Boolean get() = BuildConfig.ADMOB_APP_ID.isNotBlank()

    /** Emulators never receive live inventory, so ads would silently stay blank there. */
    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }

    private val useTestAds: Boolean get() = isEmulator

    fun start(context: Context) {
        if (!configured || started) return
        started = true
        runCatching {
            if (useTestAds) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                        .build(),
                )
            }
            MobileAds.initialize(context.applicationContext) {
                Log.i(TAG, "Mobile Ads SDK ready (test ads = $useTestAds)")
                ready.complete(Unit)
            }
        }.onFailure {
            Log.w(TAG, "Ads SDK could not start: ${it.message}")
            ready.complete(Unit)
        }
    }

    /**
     * The SDK drops requests made before initialisation finishes, which is exactly what happens
     * on a cold start: the first screen asks for its banner while the SDK is still warming up and
     * no ad — and no failure callback — ever arrives. Waiting here makes every request count.
     */
    suspend fun awaitReady(context: Context) {
        if (!configured) return
        start(context)
        if (ready.isCompleted) return
        withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() }
    }

    fun bannerUnit(placementUnitId: String): String =
        if (useTestAds) TEST_BANNER_UNIT else placementUnitId

    fun interstitialUnit(placementUnitId: String): String =
        if (useTestAds) TEST_INTERSTITIAL_UNIT else placementUnitId

    fun rewardedUnit(placementUnitId: String): String =
        if (useTestAds) TEST_REWARDED_UNIT else placementUnitId

    fun nativeUnit(placementUnitId: String): String =
        if (useTestAds) TEST_NATIVE_UNIT else placementUnitId
}

/**
 * Keeps full-screen ads civil: they are capped per screen for the life of the session and
 * never follow each other back to back while the learner moves around.
 */
private object InterstitialSession {
    private var minGapMs = 90_000L

    private val shown = mutableMapOf<String, Int>()
    private var lastShownAt = 0L

    @Synchronized
    fun configure(intervalSeconds: Int) {
        if (intervalSeconds > 0) minGapMs = intervalSeconds * 1000L
    }

    @Synchronized
    fun mayShow(screen: String, maxImpressions: Int): Boolean {
        val count = shown[screen] ?: 0
        if (maxImpressions > 0 && count >= maxImpressions) return false
        val elapsed = SystemClock.elapsedRealtime() - lastShownAt
        return lastShownAt == 0L || elapsed >= minGapMs
    }

    @Synchronized
    fun record(screen: String) {
        shown[screen] = (shown[screen] ?: 0) + 1
        lastShownAt = SystemClock.elapsedRealtime()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * A stretch of time, earned by watching a rewarded ad, during which no other ad is shown.
 *
 * This is what a rewarded ad actually pays out. It is intentionally in memory only and lasts a
 * single session: nothing is persisted, so it can never be confused with a purchased ad-free
 * entitlement, and closing the app simply ends it.
 */
object AdFreeSession {
    private const val DEFAULT_MINUTES = 30L

    /** Wall clock, via elapsedRealtime, so changing the device clock cannot extend it. */
    private var activeUntil by mutableLongStateOf(0L)

    val isActive: Boolean get() = SystemClock.elapsedRealtime() < activeUntil

    /** Whole minutes still remaining, for a countdown the learner can see. */
    val minutesLeft: Long
        get() = ((activeUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 59_999L) / 60_000L

    /** Extends rather than replaces, so a second ad adds to whatever is left. */
    fun grant(minutes: Long = DEFAULT_MINUTES) {
        val base = if (isActive) activeUntil else SystemClock.elapsedRealtime()
        activeUntil = base + minutes * 60_000L
        Log.i(TAG, "Ad-free session granted: ${minutes}m (total ${minutesLeft}m)")
    }
}

/**
 * Starts the ads SDK as the app opens, so the first banner does not have to wait for
 * initialisation before it can request anything.
 */
fun initAds(context: Context) = Ads.start(context)

/**
 * Banner slot driven by the database. The server decides whether this learner may see an
 * ad here, but the owner's explicit placement configuration on the admob_placements table
 * also serves as a fallback so ads can appear on paid content when the owner wants them.
 *
 * @param section names this slot when a screen carries more than one banner, matching the
 *   section the owner set on the placement. Leave it null for a screen's single/default banner.
 */
@Composable
fun AdBanner(
    screen: String,
    modifier: Modifier = Modifier,
    courseId: String? = null,
    section: String? = null,
) {
    if (!Ads.configured) return
    // The reward a learner earned by watching a rewarded ad is honoured here, not just promised.
    if (AdFreeSession.isActive) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    // Every piece of per-slot state is keyed by section too, so two banners on one screen keep
    // completely separate views, retry timers and failure flags instead of fighting over one.
    var unitId by remember(screen, courseId, section) { mutableStateOf<String?>(null) }
    var failed by remember(screen, courseId, section) { mutableStateOf(false) }
    var retryDelayMs by remember(screen, courseId, section) { mutableStateOf(INITIAL_RETRY_MS) }
    var viewVersion by remember(screen, courseId, section) { mutableIntStateOf(0) }

    LaunchedEffect(screen, courseId, section) {
        val placement = runCatching { AdsRepository.banner(screen, courseId, section) }
            .onFailure { Log.w(TAG, "Ad banner lookup for $screen/${section ?: "default"} failed: ${it.message}") }
            .getOrNull()

        if (placement == null) {
            Log.i(TAG, "No banner for $screen/${section ?: "default"}")
            return@LaunchedEffect
        }
        // Wait for the SDK: a request fired before it is ready is silently dropped.
        Ads.awaitReady(context)
        unitId = Ads.bannerUnit(placement.adUnitId.trim())
    }

    // Retry a FAILED load with exponential back-off. A loaded banner never enters here.
    LaunchedEffect(failed, viewVersion) {
        if (!failed || unitId == null) return@LaunchedEffect
        delay(retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        failed = false
        viewVersion += 1
    }

    val id = unitId ?: return

    // The view is built here but NOT loaded here: requesting an ad during composition fires
    // again on every recomposition and leaks half-built views. The request is made once, from
    // the effect below, for each view this remember produces.
    // Identifies this exact slot in logcat, so two banners on one screen can be told apart.
    val slot = "$screen/${section ?: "default"}"

    val adView = remember(id, viewVersion) {
        AdView(context).apply {
            adUnitId = id
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    widthDp.coerceAtLeast(320),
                ),
            )
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner $slot failed: ${error.code} ${error.message}")
                    failed = true
                }

                override fun onAdLoaded() {
                    Log.i(TAG, "Banner $slot loaded")
                    failed = false
                    retryDelayMs = INITIAL_RETRY_MS
                }
            }
        }
    }

    LaunchedEffect(adView) {
        runCatching { adView.loadAd(AdRequest.Builder().build()) }
            .onFailure { Log.w(TAG, "Banner $slot request failed: ${it.message}") }
    }

    // Pause and resume with the screen so no traffic is spent while it is not visible.
    //
    // Crucially, coming back does NOT rebuild the banner. The old code destroyed the loaded
    // AdView on every resume and immediately asked for a new one; AdMob answers a burst of
    // repeat requests for the same unit with no-fill, so the banner that had just been showing
    // went blank and stayed blank — the "appears then disappears" report. The SDK already
    // refreshes a live banner on its own schedule, so resuming it is all that is needed.
    DisposableEffect(adView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { adView.destroy() }
        }
    }

    Box(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        key(id, viewVersion) {
            AndroidView(factory = { adView })
        }
    }
}

/**
 * Full-screen ad for a screen the owner configured as an interstitial.
 *
 * Nothing is drawn here: the ad is requested quietly after the screen has settled, and only
 * shown when the same server rules that gate banners allow it. Session caps keep it from
 * appearing every single time the screen is opened.
 */
@Composable
fun AdInterstitial(screen: String, courseId: String? = null) {
    if (!Ads.configured) return
    if (AdFreeSession.isActive) return

    val context = LocalContext.current

    LaunchedEffect(screen, courseId) {
        val placement = runCatching { AdsRepository.interstitial(screen, courseId) }
            .onFailure { Log.w(TAG, "Ad interstitial lookup for $screen failed: ${it.message}") }
            .getOrNull()
        if (placement == null) return@LaunchedEffect

        InterstitialSession.configure(placement.displayIntervalSeconds)
        if (!InterstitialSession.mayShow(screen, placement.maxImpressions)) return@LaunchedEffect

        // Let the screen finish loading its own content before anything covers it.
        delay(4_000L)

        // Re-acquire the activity at show time to avoid stale references.
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return@LaunchedEffect

        Ads.awaitReady(context)

        runCatching {
            InterstitialAd.load(
                context,
                Ads.interstitialUnit(placement.adUnitId.trim()),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Interstitial $screen failed: ${error.code} ${error.message}")
                    }

                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (!InterstitialSession.mayShow(screen, placement.maxImpressions)) return

                        // Re-acquire the activity inside the callback: the original may have
                        // been destroyed while the ad was loading.
                        val current = context.findActivity()
                        if (current == null || current.isFinishing || current.isDestroyed) return

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() = InterstitialSession.record(screen)

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                Log.w(TAG, "Interstitial $screen not shown: ${error.message}")
                            }
                        }
                        runCatching { ad.show(current) }
                            .onFailure { Log.w(TAG, "Interstitial $screen show failed: ${it.message}") }
                    }
                },
            )
        }.onFailure { Log.w(TAG, "Interstitial $screen request failed: ${it.message}") }
    }
}

/** What a rewarded ad slot is doing right now, so the caller can show honest UI. */
enum class RewardedState { UNAVAILABLE, LOADING, READY, SHOWING, EARNED }

/**
 * A rewarded ad the learner chooses to watch.
 *
 * The previous version showed itself automatically as soon as the screen composed, which is both
 * hostile and against AdMob policy — a rewarded ad has to be an offer the user accepts. This
 * loads quietly and then waits: nothing appears until [show] is called from a button the learner
 * actually pressed, and [RewardedState] lets that button describe itself truthfully instead of
 * pretending an ad is ready when none loaded.
 */
@Stable
class RewardedAdController internal constructor(
    private val screen: String,
    private val courseId: String?,
) {
    internal var ad: RewardedAd? = null
    var state by mutableStateOf(RewardedState.UNAVAILABLE)
        internal set

    /** Set once the placement is known; null means the owner configured no rewarded unit here. */
    internal var unitId: String? = null

    internal suspend fun prepare(context: Context) {
        if (state == RewardedState.SHOWING || state == RewardedState.READY) return
        state = RewardedState.LOADING

        val placement = runCatching { AdsRepository.rewarded(screen, courseId) }
            .onFailure { Log.w(TAG, "Rewarded lookup for $screen failed: ${it.message}") }
            .getOrNull()
        if (placement == null) {
            state = RewardedState.UNAVAILABLE
            return
        }
        unitId = Ads.rewardedUnit(placement.adUnitId.trim())
        Ads.awaitReady(context)

        runCatching {
            RewardedAd.load(
                context,
                unitId!!,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Rewarded $screen failed: ${error.code} ${error.message}")
                        ad = null
                        state = RewardedState.UNAVAILABLE
                    }

                    override fun onAdLoaded(loaded: RewardedAd) {
                        Log.i(TAG, "Rewarded $screen ready")
                        ad = loaded
                        state = RewardedState.READY
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "Rewarded $screen request failed: ${it.message}")
            state = RewardedState.UNAVAILABLE
        }
    }

    /**
     * Plays the ad, if one is loaded, and grants the reward only on the SDK's own earned callback.
     *
     * Dismissing early never pays out: [onReward] runs from onUserEarnedReward and nowhere else.
     */
    fun show(context: Context, onReward: () -> Unit) {
        val loaded = ad ?: return
        val activity = context.findActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        var earned = false
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                state = RewardedState.SHOWING
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded $screen not shown: ${error.message}")
                ad = null
                state = RewardedState.UNAVAILABLE
            }

            override fun onAdDismissedFullScreenContent() {
                // A rewarded ad is single-use: the old instance is spent either way.
                ad = null
                state = if (earned) RewardedState.EARNED else RewardedState.UNAVAILABLE
            }
        }

        state = RewardedState.SHOWING
        runCatching {
            loaded.show(activity) { reward: RewardItem ->
                earned = true
                Log.i(TAG, "Rewarded $screen earned: ${reward.amount} ${reward.type}")
                onReward()
            }
        }.onFailure {
            Log.w(TAG, "Rewarded $screen show failed: ${it.message}")
            ad = null
            state = RewardedState.UNAVAILABLE
        }
    }
}

/**
 * Prepares a rewarded ad for this screen and returns the handle used to offer and play it.
 *
 * Returns a controller whose state is UNAVAILABLE when ads are switched off or the owner
 * configured no rewarded placement, so callers can simply hide their offer.
 */
@Composable
fun rememberRewardedAd(screen: String, courseId: String? = null): RewardedAdController {
    val context = LocalContext.current
    val controller = remember(screen, courseId) { RewardedAdController(screen, courseId) }

    LaunchedEffect(screen, courseId) {
        if (!Ads.configured) return@LaunchedEffect
        controller.prepare(context)
    }

    // A spent ad is replaced so a second offer can be made later in the same session.
    LaunchedEffect(controller.state) {
        if (controller.state == RewardedState.EARNED) {
            delay(1_000)
            controller.prepare(context)
        }
    }

    return controller
}

/**
 * A native ad rendered with the app's own typography and colours.
 *
 * Native ads have no SDK-drawn surface: every asset is bound to views the app supplies, which is
 * why this format previously existed as an option in the admin panel with nothing behind it. The
 * view tree is built here in Kotlin rather than XML so it can read the live [Ink] theme and sit
 * among real content without looking pasted in.
 *
 * The "Ad" label is mandatory and deliberately not configurable.
 */
@Composable
fun AdNative(screen: String, modifier: Modifier = Modifier, courseId: String? = null, section: String? = null) {
    if (!Ads.configured) return
    if (AdFreeSession.isActive) return

    val context = LocalContext.current
    var nativeAd by remember(screen, courseId, section) { mutableStateOf<NativeAd?>(null) }

    val surface = Ink.Surface.toArgb()
    val hairline = Ink.Hairline.toArgb()
    val textPrimary = Ink.TextPrimary.toArgb()
    val textMuted = Ink.TextMuted.toArgb()
    val amber = Ink.Amber.toArgb()
    val onAmber = Ink.OnAmber.toArgb()

    DisposableEffect(screen, courseId, section) {
        onDispose { nativeAd?.destroy() }
    }

    LaunchedEffect(screen, courseId, section) {
        val placement = runCatching { AdsRepository.native(screen, courseId, section) }
            .onFailure { Log.w(TAG, "Native lookup for $screen failed: ${it.message}") }
            .getOrNull() ?: return@LaunchedEffect

        Ads.awaitReady(context)

        runCatching {
            AdLoader.Builder(context, Ads.nativeUnit(placement.adUnitId.trim()))
                .forNativeAd { loaded ->
                    // The composable may already be gone by the time the network answers.
                    nativeAd?.destroy()
                    nativeAd = loaded
                    Log.i(TAG, "Native $screen loaded")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Native $screen failed: ${error.code} ${error.message}")
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build(),
                )
                .build()
                .loadAd(AdRequest.Builder().build())
        }.onFailure { Log.w(TAG, "Native $screen request failed: ${it.message}") }
    }

    val ad = nativeAd ?: return

    Box(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val density = ctx.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()

                val headline = TextView(ctx).apply {
                    setTextColor(textPrimary)
                    textSize = 15f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTypeface(typeface, Typeface.BOLD)
                }
                val body = TextView(ctx).apply {
                    setTextColor(textMuted)
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val adLabel = TextView(ctx).apply {
                    text = "Ad"
                    setTextColor(onAmber)
                    textSize = 10f
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(4).toFloat()
                        setColor(amber)
                    }
                }
                val icon = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val media = MediaView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(140),
                    ).apply { topMargin = dp(10) }
                }
                val cta = Button(ctx).apply {
                    isAllCaps = false
                    setTextColor(onAmber)
                    textSize = 14f
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(amber)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(44),
                    ).apply { topMargin = dp(12) }
                }

                val headerText = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = dp(10) }
                    addView(headline)
                    addView(body)
                }
                val header = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(icon)
                    addView(headerText)
                    addView(adLabel)
                }

                val content = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(surface)
                        setStroke(dp(1).coerceAtLeast(1), hairline)
                    }
                    addView(header)
                    addView(media)
                    addView(cta)
                }

                NativeAdView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    addView(content)
                    headlineView = headline
                    bodyView = body
                    iconView = icon
                    mediaView = media
                    callToActionView = cta
                }
            },
            update = { view ->
                val current = view.headlineView as? TextView
                current?.text = ad.headline

                (view.bodyView as? TextView)?.apply {
                    text = ad.body
                    visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                (view.iconView as? ImageView)?.apply {
                    val drawable = ad.icon?.drawable
                    setImageDrawable(drawable)
                    visibility = if (drawable == null) View.GONE else View.VISIBLE
                }
                (view.callToActionView as? Button)?.apply {
                    text = ad.callToAction
                    visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                view.mediaView?.apply {
                    val content = ad.mediaContent
                    if (content == null) {
                        visibility = View.GONE
                    } else {
                        visibility = View.VISIBLE
                        mediaContent = content
                    }
                }

                // Must be last: it binds every assigned asset view to the ad.
                view.setNativeAd(ad)
            },
        )
    }
}

/**
 * The learner-facing offer for a rewarded ad: watch one, and every other ad goes away for a while.
 *
 * Shows nothing at all unless an ad genuinely loaded, so the app never advertises a reward it
 * cannot deliver. While the reward is running it becomes a plain countdown instead of an offer.
 */
@Composable
fun AdFreeOffer(modifier: Modifier = Modifier, screen: String = "PROFILE") {
    if (!Ads.configured) return

    val context = LocalContext.current
    val controller = rememberRewardedAd(screen)

    if (AdFreeSession.isActive) {
        InkCard(modifier = modifier) {
            Text(
                trf(StrAds.adFreeActive, AdFreeSession.minutesLeft.toString()),
                color = Ink.Teal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    if (controller.state != RewardedState.READY) return

    InkCard(modifier = modifier) {
        Text(tr(StrAds.rewardTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(tr(StrAds.rewardBody), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        SecondaryAction(tr(StrAds.rewardAction), Modifier.fillMaxWidth(), tint = Ink.Amber) {
            controller.show(context) { AdFreeSession.grant() }
        }
    }
}
