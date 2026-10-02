package com.rork.pro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the app has ever been opened on this device.
 *
 * Used to show the full branded splash (with its fixed hold time) only on the very first
 * launch, and to show the onboarding carousel exactly once. Kept in its own preferences file —
 * separate from [SessionCache], which is wiped on sign-out — so signing out never brings the
 * splash or onboarding back.
 */
object AppLaunchState {

    private const val PREFS = "7pro.launch"
    private const val KEY_HAS_LAUNCHED = "has_launched"
    private const val KEY_SEEN_ONBOARDING = "seen_onboarding"

    private var prefs: SharedPreferences? = null

    /** Compose state so finishing onboarding swaps the screen immediately, no restart needed. */
    var hasSeenOnboarding: Boolean by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hasSeenOnboarding = prefs?.getBoolean(KEY_SEEN_ONBOARDING, false) == true
    }

    /** True once [markLaunched] has been called at least once on this device. */
    fun isFirstLaunch(): Boolean = prefs?.getBoolean(KEY_HAS_LAUNCHED, false) != true

    /** Records that the app has now been opened, so future launches skip the splash hold. */
    fun markLaunched() {
        prefs?.edit()?.putBoolean(KEY_HAS_LAUNCHED, true)?.apply()
    }

    /** Records that onboarding finished (or was skipped), so it never shows again on this device. */
    fun markOnboardingSeen() {
        hasSeenOnboarding = true
        prefs?.edit()?.putBoolean(KEY_SEEN_ONBOARDING, true)?.apply()
    }
}
