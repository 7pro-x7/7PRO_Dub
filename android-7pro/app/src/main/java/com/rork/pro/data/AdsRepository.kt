package com.rork.pro.data

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One ad slot the owner switched on for a screen. */
@Serializable
data class AdPlacement(
    val id: String? = null,
    val section: String? = null,
    val format: String = "BANNER",
    @SerialName("ad_unit_id") val adUnitId: String = "",
    @SerialName("max_impressions") val maxImpressions: Int = 0,
    @SerialName("display_interval_seconds") val displayIntervalSeconds: Int = 90,
)

/**
 * Whether this user may see ads on this screen, and which units to request.
 *
 * Eligibility is decided entirely on the server: ads are switched off globally by the owner,
 * hidden on paid content, and never shown to someone with paid access.
 */
@Serializable
data class AdConfig(
    val enabled: Boolean = false,
    val reason: String? = null,
    val placements: List<AdPlacement> = emptyList(),
) {
    fun banner(): AdPlacement? = of("BANNER")

    fun interstitial(): AdPlacement? = of("INTERSTITIAL")

    fun rewarded(): AdPlacement? = of("REWARDED")

    fun native(): AdPlacement? = of("NATIVE")

    /**
     * The placement for one named slot on this screen, so a screen can carry several banners.
     *
     * A null [section] asks for the screen's default slot — the row whose own section is null.
     * That keeps every existing single-banner screen working exactly as before, since those rows
     * were all written without a section.
     */
    fun banner(section: String?): AdPlacement? =
        placements.firstOrNull {
            it.format == "BANNER" && it.adUnitId.isNotBlank() && it.section == section
        }.takeIf { enabled }

    private fun of(format: String): AdPlacement? =
        placements.firstOrNull { it.format == format && it.adUnitId.isNotBlank() }
            .takeIf { enabled }
}

object AdsRepository {

    private const val TAG = "AdsRepo"

    /**
     * Last-resort units, used only when Supabase cannot be reached at all (no RPC, no direct
     * query). This guarantees a banner/interstitial is always requested even during an outage,
     * instead of silently going blank because the owner's placement rows could not be read.
     */
    private const val FALLBACK_BANNER_UNIT = "ca-app-pub-2143545712755970/5531457415"
    private const val FALLBACK_INTERSTITIAL_UNIT = "ca-app-pub-2143545712755970/9675928002"

    /** @param courseId set when the screen belongs to a course, so paid content stays ad-free. */
    suspend fun config(screen: String, courseId: String? = null): AdConfig {
        val raw = Backend.rpcRaw(
            "ad_config",
            buildJsonObject {
                put("p_screen", screen)
                put("p_course_id", courseId)
            },
        )
        // PostgREST returns the jsonb value directly (no wrapping).
        return try {
            Backend.json.decodeFromJsonElement(AdConfig.serializer(), raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ad_config: ${e.message}, raw=$raw")
            AdConfig()
        }
    }

    /**
     * Direct query for placements, used ONLY when the ad_config RPC could not be reached.
     *
     * This deliberately does NOT second-guess the RPC. It used to be consulted whenever the RPC
     * returned no placement — including when the server had decided, correctly, that this screen
     * or this course should not carry an ad. That turned every "no" into a "yes" and made the
     * owner's free-content-only setting unenforceable. A refusal is now respected; only an
     * outage falls through to here.
     */
    suspend fun directPlacements(screen: String): List<AdPlacement> {
        val rows: List<AdmobPlacement> = Backend.client.from("admob_placements")
            .select {
                filter { eq("screen", screen); eq("is_enabled", true) }
                order("sort_order", SortOrder.ASCENDING)
            }
            .decodeList()
        return rows.map { row ->
            AdPlacement(
                id = row.id,
                section = row.section,
                format = row.format,
                adUnitId = row.adUnitId,
                maxImpressions = row.maxImpressions,
                displayIntervalSeconds = row.displayIntervalSeconds,
            )
        }
    }

    /**
     * Combined: try the RPC first, then fall back to direct table query.
     * Returns a placement if either source has one for the requested format.
     *
     * @param section names one slot on a screen that carries more than one banner; null asks for
     *   the screen's default slot, which is what every single-banner screen uses.
     */
    suspend fun banner(screen: String, courseId: String? = null, section: String? = null): AdPlacement? {
        // 1. Try the RPC
        val config = runCatching { config(screen, courseId) }
            .onFailure { Log.w(TAG, "ad_config RPC failed for $screen: ${it.message}") }
            .getOrNull()
        val rpcPlacement = config?.banner(section)
        if (rpcPlacement != null) return rpcPlacement

        // The server answered. Whatever it said — including "no ad here" — is final.
        if (config != null) {
            Log.i(TAG, "No banner for $screen/${section ?: "default"} (server said ${config.reason ?: "none"})")
            return null
        }

        // 2. The RPC was unreachable. Only now is the table read directly.
        val direct = runCatching { directPlacements(screen) }
            .onFailure { Log.w(TAG, "Direct placements failed for $screen: ${it.message}") }
            .getOrNull()
        val directPlacement = direct?.firstOrNull {
            it.format == "BANNER" && it.adUnitId.isNotBlank() && it.section == section
        }
        if (directPlacement != null) return directPlacement

        // 3. Both Supabase paths failed outright — Supabase is unreachable, not merely configured
        // without a placement. Keep the banner alive with the hardcoded unit. Only the default
        // slot gets this treatment: inventing extra banners for named slots the owner may never
        // have configured would put ads on screens they did not ask for.
        if (direct == null && section == null) {
            Log.w(TAG, "Supabase unreachable for $screen banner — using hardcoded fallback unit")
            return AdPlacement(format = "BANNER", adUnitId = FALLBACK_BANNER_UNIT)
        }

        Log.i(
            TAG,
            "No banner for $screen/${section ?: "default"} " +
                "(rpc_enabled=${config?.enabled}, direct_count=${direct?.size ?: 0})",
        )
        return null
    }

    /** Same pattern for interstitials. */
    suspend fun interstitial(screen: String, courseId: String? = null): AdPlacement? {
        val config = runCatching { config(screen, courseId) }
            .onFailure { Log.w(TAG, "ad_config RPC failed for $screen: ${it.message}") }
            .getOrNull()
        val rpcPlacement = config?.interstitial()
        if (rpcPlacement != null) return rpcPlacement

        if (config != null) {
            Log.i(TAG, "No interstitial for $screen (server said ${config.reason ?: "none"})")
            return null
        }

        val direct = runCatching { directPlacements(screen) }
            .onFailure { Log.w(TAG, "Direct placements failed for $screen: ${it.message}") }
            .getOrNull()
        val directPlacement = direct?.firstOrNull { it.format == "INTERSTITIAL" && it.adUnitId.isNotBlank() }
        if (directPlacement != null) return directPlacement

        if (direct == null) {
            Log.w(TAG, "Supabase unreachable for $screen interstitial — using hardcoded fallback unit")
            return AdPlacement(format = "INTERSTITIAL", adUnitId = FALLBACK_INTERSTITIAL_UNIT)
        }

        Log.i(TAG, "No interstitial for $screen (rpc_enabled=${config?.enabled}, direct_count=${direct?.size ?: 0})")
        return null
    }

    /** Same pattern for rewarded. */
    suspend fun rewarded(screen: String, courseId: String? = null): AdPlacement? {
        val config = runCatching { config(screen, courseId) }
            .onFailure { Log.w(TAG, "ad_config RPC failed for $screen: ${it.message}") }
            .getOrNull()
        val rpcPlacement = config?.rewarded()
        if (rpcPlacement != null) return rpcPlacement
        if (config != null) {
            Log.i(TAG, "No rewarded for $screen (server said ${config.reason ?: "none"})")
            return null
        }
        val direct = runCatching { directPlacements(screen) }
            .onFailure { Log.w(TAG, "Direct placements failed for $screen: ${it.message}") }
            .getOrNull()
        val directPlacement = direct?.firstOrNull { it.format == "REWARDED" && it.adUnitId.isNotBlank() }
        if (directPlacement != null) return directPlacement

        Log.i(TAG, "No rewarded for $screen (rpc_enabled=${config?.enabled}, direct_count=${direct?.size ?: 0})")
        return null
    }

    /**
     * Same pattern for native ads.
     *
     * There is deliberately no hardcoded fallback unit here: a native ad is drawn inline among
     * real content, so inventing one during a Supabase outage would put an unconfigured ad in
     * the middle of a lesson list. No configuration means no native ad.
     */
    suspend fun native(screen: String, courseId: String? = null, section: String? = null): AdPlacement? {
        val config = runCatching { config(screen, courseId) }
            .onFailure { Log.w(TAG, "ad_config RPC failed for $screen: ${it.message}") }
            .getOrNull()
        val rpcPlacement = config?.native()
        if (rpcPlacement != null && (section == null || rpcPlacement.section == section)) return rpcPlacement
        if (config != null) {
            Log.i(TAG, "No native for $screen (server said ${config.reason ?: "none"})")
            return null
        }

        val direct = runCatching { directPlacements(screen) }
            .onFailure { Log.w(TAG, "Direct placements failed for $screen: ${it.message}") }
            .getOrNull()
        val directPlacement = direct?.firstOrNull {
            it.format == "NATIVE" && it.adUnitId.isNotBlank() && (section == null || it.section == section)
        }
        if (directPlacement != null) return directPlacement

        Log.i(
            TAG,
            "No native for $screen/${section ?: "default"} " +
                "(rpc_enabled=${config?.enabled}, direct_count=${direct?.size ?: 0})",
        )
        return null
    }
}
