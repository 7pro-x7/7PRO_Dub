package com.rork.pro.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rork.pro.ui.i18n.AppLanguage

/** How 7PRO decides between its night and day palettes. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Holds the chosen appearance for the whole app.
 *
 * [dark] is Compose state, so every `Ink` colour re-reads it and the entire UI repaints the
 * instant the user flips the switch — no restart, no screen rebuild.
 */
object AppAppearance {

    private const val PREFS = "7pro.prefs"
    private const val KEY_THEME = "theme_mode"

    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Resolved appearance. Follows the device until the user picks a side. */
    var dark: Boolean by mutableStateOf(true)
        internal set

    fun init(context: Context) {
        mode = ThemeMode.fromKey(prefs(context).getString(KEY_THEME, null))
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY_THEME, value.key).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * "Atelier" — 7PRO's visual identity, in both appearances.
 *
 * Where the previous palette leaned on a cool navy-black with gold, this one is built around a
 * warm charcoal canvas and a burnt-copper accent — closer to leather and lamplight than
 * screen-glow, which reads as more studious and less "fintech app" for a learning product.
 * Teal shifts a shade greener, error moves from coral to a muted raspberry so it never fights
 * the copper accent, and every neutral (canvas, surface, text) carries the same warm undertone
 * so the whole app feels like one considered object rather than a UI kit default. Only the
 * surfaces and text invert between modes; the accent hue stays put so the brand still reads the
 * same by day and by night. Every screen in the app reads these Ink.* properties rather than
 * hardcoding colors, so this palette is the single place that defines the whole app's look — no
 * screen file needed to change for this redesign.
 */
object Ink {
    private val dark: Boolean get() = AppAppearance.dark

    // Dark canvas lifted from near-black (#131211) to a soft warm charcoal, and light canvas
    // moved off pure white to a warm cream — both cut the raw contrast between background and
    // content, which is what actually drives eye strain on long reading sessions, without
    // touching the accent hues that carry the brand.
    val Canvas: Color get() = HomeInk.Bg

    /**
     * The two other stops of the app's background wash. Dark mode is now a true graded black
     * rather than a flat warm charcoal: near-black at the top, a shade deeper at the bottom,
     * so a full screen has depth instead of reading as one uniform slab. Light mode keeps its
     * cream, with the same top-to-bottom grade added so both appearances share one structure.
     * See AppBackdrop, which paints these along with the accent glows and ornament.
     */
    val CanvasTop: Color get() = if (AppPalette.sky) HomeSky.BgTop else HomeInk.Bg
    val CanvasDeep: Color get() = if (AppPalette.sky) HomeSky.BgBottom else HomeInk.Bg

    // Surfaces climb the same black scale in even steps (0D → 14 → 1C), which is what makes a
    // card sit *above* the page instead of merely differing from it. A single elevation step
    // of pure grey would flatten under the glows below, so each carries a trace of the copper
    // accent's warmth — invisible as colour, present as depth.
    val Surface: Color get() = HomeInk.Card
    val SurfaceHigh: Color get() = HomeInk.Chip

    /** Top stop of the faint sheen on raised surfaces — see InkCard. */
    val SurfaceSheen: Color get() = HomeInk.Card

    val Hairline: Color get() = HomeInk.Line

    /** Hairline for edges that need to read as a real border rather than a whisper. */
    val HairlineStrong: Color get() = if (AppPalette.sky) HomeSky.LineStrong else if (dark) Color(0xFF4A4644) else Color(0xFFD6C8B0)

    // Text softened a step off both extremes (near-white / near-black) so long paragraphs sit
    // at a comfortable contrast instead of glaring. On the darker canvas the primary text cools
    // very slightly — pure warm ivory over near-black reads as a tint rather than as white.
    val TextPrimary: Color get() = HomeInk.Text

    // Dimmed a further step from TextPrimary — this is what long paragraphs (course/lesson
    // descriptions) and secondary labels actually render in, so it's the token that drives
    // reading fatigue on long screens. Kept above the ~4.5:1 AA floor for body text rather than
    // drifting toward TextMuted's low-contrast, label-only range.
    val TextSecondary: Color get() = HomeInk.TextSecondary
    val TextMuted: Color get() = if (AppPalette.sky) HomeSky.TextMuted else if (dark) Color(0xFF8A847D) else Color(0xFF8C7F72)

    val Amber: Color get() = HomeInk.Brand
    val AmberPressed: Color get() = if (AppPalette.sky) HomeSky.BrandDeep else if (dark) Color(0xFFD38C57) else Color(0xFF9E4311)
    val AmberSoft: Color get() = HomeInk.BrandSoft

    val Teal: Color get() = if (dark) Color(0xFF4FC0A0) else Color(0xFF0F7A5A)
    val TealSoft: Color get() = if (dark) Color(0x1F3FAF8E) else Color(0x210F7A5A)

    /**
     * Vivid "go" green reserved for the single highest-intent call to action on a screen
     * (e.g. booking a live group after a placement result). Deliberately more saturated than
     * Teal, which is a status/accent colour used throughout the app — this one exists purely
     * to grab the eye and say "tap me now", so it is not reused for anything ambient.
     */
    val CtaGreen: Color get() = if (dark) Color(0xFF22C55E) else Color(0xFF16A34A)
    val CtaGreenPressed: Color get() = if (dark) Color(0xFF16A34A) else Color(0xFF0E7A37)
    val Coral: Color get() = if (dark) Color(0xFFE2637F) else Color(0xFFA23955)
    val CoralSoft: Color get() = if (dark) Color(0x1FE2637F) else Color(0x21A23955)
    val Sky: Color get() = if (dark) Color(0xFF4FA3FF) else Color(0xFF1868C7)
    val SkySoft: Color get() = if (dark) Color(0x1F4FA3FF) else Color(0x241868C7)
    val Purple: Color get() = Color(0xFF7C4DFF)

    /** Neutral slate for statuses that are neither a success, warning, nor error — draft, archived. */
    val Neutral: Color get() = if (dark) Color(0xFF92A0AC) else Color(0xFF5F6672)
    val NeutralSoft: Color get() = if (dark) Color(0x1F92A0AC) else Color(0x215F6672)

    /**
     * The decorative layer — see AppBackdrop.
     *
     * Two wide, very low-alpha washes of the brand's own copper and teal, plus an ornament
     * stroke for the drawn geometry. They are deliberately weak: at these alphas they read as
     * light in the room rather than as coloured shapes, which is the difference between a
     * background that looks considered and one that looks decorated. Light mode gets the same
     * structure at lower strength, since a cream canvas shows a wash far more readily than
     * black does.
     */
    val GlowWarm: Color get() = Color.Transparent
    val GlowCool: Color get() = Color.Transparent

    /**
     * The drawn line. Light mode's is now materially stronger than a straight port of the dark
     * value: the same alpha that reads as "light in the room" against near-black reads as
     * nothing at all against cream, so the light appearance was getting the geometry without
     * getting the decoration. Here it is ink on paper instead — visible as a line, still far
     * below the weight of any real content.
     */
    val Ornament: Color get() = Color.Transparent

    /**
     * Warm shade drawn into the outer edges in light mode only.
     *
     * A cream page with no edge treatment reads as a flat sheet of colour; darkening the
     * corners a touch is what gives it the sense of being paper lit from above. Dark mode
     * needs none of this — its grade already darkens toward the bottom, and a vignette over
     * near-black would just be a smudge.
     */
    val EdgeShade: Color get() = Color.Transparent

    /**
     * Contrast colour for text and icons drawn on top of the copper accent.
     *
     * Dark chocolate-brown in both modes, not white-in-light like most "on-accent" colours:
     * measured against light-mode copper (#C17A3E) this reaches ~5.2:1, and against dark-mode
     * copper (#E0954F) ~7.3:1 — both clear WCAG AA for normal text. Verified by contrast
     * calculation, not by eye.
     */
    val OnAmber: Color get() = HomeInk.OnBrand

    /** Contrast colour for text and icons drawn on top of a solid teal fill. */
    val OnTeal: Color get() = if (dark) Color(0xFF04211A) else Color(0xFFFFFFFF)

    /** Contrast colour for text and icons drawn on top of a solid sky fill. */
    val OnSky: Color get() = if (dark) Color(0xFF062A4D) else Color(0xFFFFFFFF)

    /** Contrast colour for text and icons drawn on top of CtaGreen — white in both modes. */
    val OnCtaGreen: Color get() = Color(0xFFFFFFFF)
}

private fun sevenProColors(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Ink.Amber,
        onPrimary = Ink.OnAmber,
        primaryContainer = Ink.AmberSoft,
        onPrimaryContainer = Ink.Amber,
        secondary = Ink.Teal,
        onSecondary = Color(0xFF04211A),
        secondaryContainer = Ink.TealSoft,
        onSecondaryContainer = Ink.Teal,
        tertiary = Ink.Sky,
        background = Ink.Canvas,
        onBackground = Ink.TextPrimary,
        surface = Ink.Surface,
        onSurface = Ink.TextPrimary,
        surfaceVariant = Ink.SurfaceHigh,
        onSurfaceVariant = Ink.TextSecondary,
        surfaceContainer = Ink.Surface,
        surfaceContainerHigh = Ink.SurfaceHigh,
        surfaceContainerHighest = Ink.SurfaceHigh,
        surfaceContainerLow = Ink.Surface,
        surfaceContainerLowest = Ink.Canvas,
        outline = Ink.Hairline,
        outlineVariant = Ink.Hairline,
        error = Ink.Coral,
        onError = Color(0xFF2C0A11),
        errorContainer = Ink.CoralSoft,
        onErrorContainer = Ink.Coral,
        scrim = Color(0xD9050506),
    )
} else {
    lightColorScheme(
        primary = Ink.Amber,
        onPrimary = Ink.OnAmber,
        primaryContainer = Ink.AmberSoft,
        onPrimaryContainer = Ink.Amber,
        secondary = Ink.Teal,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Ink.TealSoft,
        onSecondaryContainer = Ink.Teal,
        tertiary = Ink.Sky,
        background = Ink.Canvas,
        onBackground = Ink.TextPrimary,
        surface = Ink.Surface,
        onSurface = Ink.TextPrimary,
        surfaceVariant = Ink.SurfaceHigh,
        onSurfaceVariant = Ink.TextSecondary,
        surfaceContainer = Ink.Surface,
        surfaceContainerHigh = Ink.SurfaceHigh,
        surfaceContainerHighest = Ink.SurfaceHigh,
        surfaceContainerLow = Ink.Surface,
        surfaceContainerLowest = Ink.Canvas,
        outline = Ink.Hairline,
        outlineVariant = Ink.Hairline,
        error = Ink.Coral,
        onError = Color(0xFFFFFFFF),
        errorContainer = Ink.CoralSoft,
        onErrorContainer = Ink.Coral,
        scrim = Color(0x991C1713),
    )
}

/**
 * Letter-spacing is a Latin-typography technique: tightening large headlines makes them read
 * as more refined. Arabic script joins letters together, so any tracking — positive or
 * negative — breaks that connection and looks cramped and unprofessional instead. Callers pass
 * [isRtl] so Arabic (and any other RTL language) gets zero tracking everywhere, while English
 * keeps the deliberately tightened scale.
 */
/**
 * 7PRO's Arabic typefaces: Readex Pro for headings and IBM Plex Sans Arabic for body and labels
 * (both free, SIL Open Font License).
 *
 * They are looked up *by file name at run time*, so the app builds and runs with or without them:
 * until the files are added, [loadFamily] returns null and the platform default is used.
 * To turn them on, drop these files into `app/src/main/res/font/` (lowercase names, as below):
 *
 *   readexpro_regular.ttf, readexpro_medium.ttf, readexpro_bold.ttf
 *   ibmplexarabic_regular.ttf, ibmplexarabic_medium.ttf, ibmplexarabic_semibold.ttf
 *
 * Any subset works — a missing weight falls back to the nearest one that is present. See
 * app/FONTS.md for where to download them.
 */
private class ArabicFonts(val heading: FontFamily?, val body: FontFamily?)

private fun loadFamily(context: Context, vararg files: Pair<String, FontWeight>): FontFamily? {
    val fonts = files.mapNotNull { (name, weight) ->
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        if (id != 0) Font(id, weight) else null
    }
    return if (fonts.isEmpty()) null else FontFamily(fonts)
}

private fun loadArabicFonts(context: Context): ArabicFonts {
    val body = loadFamily(
        context,
        "ibmplexarabic_regular" to FontWeight.Normal,
        "ibmplexarabic_medium" to FontWeight.Medium,
        "ibmplexarabic_semibold" to FontWeight.SemiBold,
    )
    val heading = loadFamily(
        context,
        "readexpro_regular" to FontWeight.Normal,
        "readexpro_medium" to FontWeight.Medium,
        "readexpro_bold" to FontWeight.Bold,
    ) ?: body
    return ArabicFonts(heading = heading, body = body)
}

/**
 * Letter-spacing is a Latin-typography technique: tightening large headlines makes them read
 * as more refined. Arabic script joins letters together, so any tracking — positive or
 * negative — breaks that connection and looks cramped and unprofessional instead. Callers pass
 * [isRtl] so Arabic (and any other RTL language) gets zero tracking everywhere, while English
 * keeps the deliberately tightened scale. The same flag also picks the Arabic-specific
 * typefaces ([ArabicFonts]) so Latin text keeps the platform default it already had.
 */
private fun sevenProTypography(isRtl: Boolean, fonts: ArabicFonts): Typography {
    fun tracking(sp: Double) = if (isRtl) 0.sp else sp.sp
    val heading = if (isRtl) fonts.heading else null
    val family = if (isRtl) fonts.body else null
    return Typography(
        displayLarge = TextStyle(fontFamily = heading, fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(-1.0)),
        displaySmall = TextStyle(fontFamily = heading, fontSize = 33.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(-0.5)),
        headlineMedium = TextStyle(fontFamily = heading, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(-0.4)),
        headlineSmall = TextStyle(fontFamily = heading, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(-0.3)),
        titleLarge = TextStyle(fontFamily = heading, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(-0.2)),
        titleMedium = TextStyle(fontFamily = heading, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = heading, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        labelMedium = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tracking(0.4)),
        labelSmall = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tracking(0.8), textAlign = TextAlign.Start),
    )
}

/**
 * Shape scale, softened from the previous 20dp-everywhere flat radius: cards now round a touch
 * more (24dp) so surfaces feel closer to stacked paper than app chrome, while [chipTight] gives
 * small inline chips and badges a gentler squircle instead of a full pill, which reads calmer
 * next to long Arabic strings than a stadium shape does.
 */
object Dimens {
    val screenPadding = 20.dp
    val cardRadius = 24.dp
    val chipRadius = 999.dp
    val chipTight = 14.dp
    val gap = 12.dp
    val sectionGap = 28.dp
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (AppAppearance.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    AppAppearance.dark = dark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Light appearance needs dark status/navigation icons to stay readable.
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    // Layout direction follows the language, so Arabic mirrors the entire UI.
    val context = LocalContext.current
    val arabicFonts = remember(context) { loadArabicFonts(context) }

    CompositionLocalProvider(LocalLayoutDirection provides AppLanguage.current.layoutDirection) {
        MaterialTheme(
            colorScheme = sevenProColors(dark),
            typography = sevenProTypography(AppLanguage.current.isRtl, arabicFonts),
            content = content,
        )
    }
}
