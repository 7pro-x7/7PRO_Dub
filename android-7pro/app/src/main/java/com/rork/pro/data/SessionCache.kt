package com.rork.pro.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString

/**
 * Last known signed-in state, kept on the device.
 *
 * Supabase already persists the auth tokens, but the profile behind them is fetched over the
 * network. Without a local copy every cold start shows a spinner and, when the connection is
 * slow or missing, drops the user back on the sign-in screen even though their session is valid.
 * Caching the profile lets the app open straight into its last state and keep working offline.
 *
 * Only non-sensitive display data is stored here — never tokens, which stay in Supabase's own
 * encrypted storage — and everything is wiped on sign-out.
 */
object SessionCache {

    private const val PREFS = "7pro.session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PROFILE = "profile"
    private const val KEY_TEACHER = "teacher_profile"
    private const val KEY_PERMISSIONS = "permissions"
    private const val KEY_LAST_TAB = "last_tab"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** The cached profile, or null when nothing was stored or the stored copy is unreadable. */
    fun profile(): Profile? = decode(KEY_PROFILE)

    fun teacherProfile(): TeacherProfile? = decode(KEY_TEACHER)

    fun permissions(): Set<String> =
        prefs?.getString(KEY_PERMISSIONS, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    fun save(profile: Profile?, teacherProfile: TeacherProfile?, permissions: Set<String>) {
        val store = prefs ?: return
        if (profile == null) {
            clear()
            return
        }
        runCatching {
            store.edit()
                .putString(KEY_USER_ID, profile.id)
                .putString(KEY_PROFILE, Backend.json.encodeToString(profile))
                .putString(
                    KEY_TEACHER,
                    teacherProfile?.let { Backend.json.encodeToString(it) },
                )
                .putString(KEY_PERMISSIONS, permissions.joinToString(","))
                .apply()
        }
    }

    /** Remembers the tab the user was last on so reopening the app resumes there. */
    fun lastTab(): String? = prefs?.getString(KEY_LAST_TAB, null)

    fun setLastTab(route: String) {
        prefs?.edit()?.putString(KEY_LAST_TAB, route)?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private inline fun <reified T> decode(key: String): T? {
        val raw = prefs?.getString(key, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Backend.json.decodeFromString<T>(raw) }.getOrNull()
    }
}
