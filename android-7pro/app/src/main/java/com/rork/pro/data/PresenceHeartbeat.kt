package com.rork.pro.data

/**
 * The "online now" signal behind the owner console's stat tile.
 *
 * A single call — [ping] — touches `profiles.last_seen_at` for whoever is signed in. It carries
 * no state of its own on purpose: the *cadence* (every 30 seconds, only while the app is in the
 * foreground and someone is signed in) lives in `AppNavigation.kt` as a `repeatOnLifecycle(RESUMED)`
 * loop, so a backgrounded or killed app simply stops calling this and the row goes stale — nothing
 * to explicitly mark "offline". `owner_analytics.online_now` (see the matching migration) then
 * counts rows touched in the last 90 seconds, three missed beats' grace for one dropped ping.
 */
object PresenceHeartbeat {
    const val INTERVAL_MS = 30_000L

    suspend fun ping() {
        if (Backend.currentUserId == null) return
        runCatching { Backend.rpcVoid("ping_presence") }
    }
}
