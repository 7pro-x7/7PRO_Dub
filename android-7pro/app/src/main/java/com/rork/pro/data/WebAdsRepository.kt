package com.rork.pro.data

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One ad slot on a 7PRO web page.
 *
 * These are separate from the app's AdMob placements on purpose: an AdMob unit id cannot serve on
 * a web page, so web pages carry their own kinds — an image with a link, an AdSense unit, or a
 * snippet of HTML that the page shows inside a sandboxed frame.
 */
@Serializable
data class WebAdSlot(
    val id: String? = null,
    val page: String = WebAdsRepository.PAGE,
    val slot: String,
    val kind: String,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    val title: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("adsense_client") val adsenseClient: String? = null,
    @SerialName("adsense_slot") val adsenseSlot: String? = null,
    @SerialName("html_code") val htmlCode: String? = null,
    @SerialName("height_px") val heightPx: Int = 120,
)

object WebAdsRepository {
    /** The web page these slots belong to (the teacher exercises page). */
    const val PAGE = "EXERCISES"

    /** Slot names the page knows how to place. */
    val SLOTS = listOf("TOP", "QUESTION", "RESULT", "BOTTOM")

    private const val GLOBAL_KEY = "web_ads.enabled"

    suspend fun slots(): List<WebAdSlot> =
        Backend.client.from("web_ad_slots")
            .select { filter { eq("page", PAGE) } }
            .decodeList()

    /** Master switch for every web page; a missing setting means on, same as the server. */
    suspend fun globallyEnabled(): Boolean {
        val setting = runCatching { AdminRepository.settings() }.getOrDefault(emptyList())
            .firstOrNull { it.key == GLOBAL_KEY }
        return setting?.value?.toString()?.trim('"')?.equals("false", ignoreCase = true)?.not() ?: true
    }

    suspend fun setGloballyEnabled(enabled: Boolean) =
        AdminRepository.saveSetting(GLOBAL_KEY, enabled.toString())

    /** Inserts the slot, or updates it when [WebAdSlot.id] is set. The database re-validates everything. */
    suspend fun save(slot: WebAdSlot) {
        val payload = buildJsonObject {
            put("page", PAGE)
            put("slot", slot.slot)
            put("kind", slot.kind)
            put("is_enabled", slot.isEnabled)
            put("title", slot.title?.takeIf { it.isNotBlank() })
            put("image_url", slot.imageUrl?.takeIf { it.isNotBlank() && slot.kind == "IMAGE" })
            put("link_url", slot.linkUrl?.takeIf { it.isNotBlank() && slot.kind == "IMAGE" })
            put("adsense_client", slot.adsenseClient?.takeIf { it.isNotBlank() && slot.kind == "ADSENSE" })
            put("adsense_slot", slot.adsenseSlot?.takeIf { it.isNotBlank() && slot.kind == "ADSENSE" })
            put("html_code", slot.htmlCode?.takeIf { it.isNotBlank() && slot.kind == "HTML" })
            put("height_px", slot.heightPx)
        }
        if (slot.id == null) {
            Backend.client.from("web_ad_slots").insert(payload)
        } else {
            Backend.client.from("web_ad_slots").update(payload) { filter { eq("id", slot.id) } }
        }
    }

    suspend fun delete(id: String) {
        Backend.client.from("web_ad_slots").delete { filter { eq("id", id) } }
    }
}
