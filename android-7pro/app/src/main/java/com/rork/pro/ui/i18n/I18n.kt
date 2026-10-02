package com.rork.pro.ui.i18n

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** The two languages 7PRO ships in. */
enum class Lang(val code: String, val nativeLabel: String, val isRtl: Boolean) {
    EN("en", "English", false),
    AR("ar", "العربية", true);

    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        /** Arabic for any Arabic tag (ar, ar-EG, arb...), English for everything else. */
        fun fromTag(tag: String?): Lang =
            if (tag?.lowercase(Locale.US)?.startsWith("ar") == true) AR else EN
    }
}

/** One phrase in both languages, declared side by side so nothing can drift out of sync. */
@Immutable
class Tr(val en: String, val ar: String)

/**
 * Holds the active language for the whole app.
 *
 * [current] is Compose state, so reading it through [tr] — even from a plain, non-composable
 * function — subscribes the calling composition and re-renders instantly when the user switches.
 */
object AppLanguage {

    private const val PREFS = "7pro.prefs"
    private const val KEY_LANG = "language"

    var current: Lang by mutableStateOf(Lang.EN)
        private set

    /** True once the user has explicitly picked a language; their choice then beats auto-detection. */
    var isExplicit: Boolean by mutableStateOf(false)
        private set

    /** Resolves the startup language: saved choice first, otherwise the device language. */
    fun init(context: Context) {
        val saved = prefs(context).getString(KEY_LANG, null)
        isExplicit = saved != null
        current = if (saved != null) Lang.fromTag(saved) else Lang.fromTag(deviceTag(context))
    }

    fun set(context: Context, lang: Lang) {
        current = lang
        isExplicit = true
        prefs(context).edit().putString(KEY_LANG, lang.code).apply()
    }

    /** Formatting locale: Arabic month/day names but Western digits, which Egyptian users expect. */
    fun locale(): Locale =
        if (current == Lang.AR) Locale.forLanguageTag("ar-EG-u-nu-latn") else Locale.US

    private fun deviceTag(context: Context): String? {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) null else locales[0].language
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Resolves a phrase in the active language. Safe to call from composables and plain code alike. */
fun tr(phrase: Tr): String = if (AppLanguage.current == Lang.AR) phrase.ar else phrase.en

/** Resolves a phrase that carries `%s` / `%d` placeholders. */
fun trf(phrase: Tr, vararg args: Any?): String =
    runCatching { String.format(AppLanguage.locale(), tr(phrase), *args) }.getOrElse { tr(phrase) }

/** True when the UI is currently mirrored. */
val isRtl: Boolean get() = AppLanguage.current.isRtl
