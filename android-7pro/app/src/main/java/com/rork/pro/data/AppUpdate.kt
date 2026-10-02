package com.rork.pro.data

/** Platform settings that drive the forced-update screen. */
object UpdateKeys {
    const val FORCE: String = "app.force_update"
    const val MIN_BUILD: String = "app.min_build"
    const val MESSAGE: String = "app.update_message"
    const val URL: String = "app.update_url"
}

/**
 * The owner's update rule, as stored in platform settings.
 *
 * Kept as plain data so the same rule can be read before anyone signs in — an outdated build
 * must be stopped at the door, not after it reaches the catalogue.
 */
data class UpdateRule(
    val forced: Boolean = false,
    val minBuild: Int = 0,
    val message: String = "",
    val url: String = "",
) {
    /**
     * True when the owner has switched on a forced update and this build is older than the
     * minimum they set.
     *
     * This was previously hardcoded to `false`: stale PostgREST schema-cache errors (PGRST202) on
     * an unrelated RPC call were once mapped to a misleading "update required" state, so the
     * check was disabled at the source. `settings()` is a plain table read, not an RPC, and
     * PGRST202 is now mapped to its own retriable `SCHEMA_STALE` error instead of being confused
     * with this one — so the real comparison is safe to run again.
     */
    fun blocks(currentBuild: Int): Boolean = forced && currentBuild < minBuild

    companion object {
        fun from(settings: Map<String, String>): UpdateRule = UpdateRule(
            forced = settings[UpdateKeys.FORCE] == "true",
            minBuild = settings[UpdateKeys.MIN_BUILD]?.substringBefore('.')?.toIntOrNull() ?: 0,
            message = settings[UpdateKeys.MESSAGE].orEmpty(),
            url = settings[UpdateKeys.URL].orEmpty(),
        )
    }
}
