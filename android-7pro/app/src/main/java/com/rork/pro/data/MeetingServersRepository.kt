package com.rork.pro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One Jitsi server meetings can run on (Contabo, Hetzner, the free community fallback…).
 * The JWT secret never comes down to the app — only whether one is saved ([hasJwtSecret]).
 */
@Serializable
data class MeetingServer(
    val id: String,
    val label: String,
    val provider: String,
    val domain: String = "",
    @SerialName("jwt_app_id") val jwtAppId: String = "",
    @SerialName("has_jwt_secret") val hasJwtSecret: Boolean = false,
    val notes: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("last_test_ok") val lastTestOk: Boolean? = null,
    @SerialName("last_tested_at") val lastTestedAt: String? = null,
    @SerialName("live_sessions") val liveSessions: Int = 0,
    @SerialName("scheduled_sessions") val scheduledSessions: Int = 0,
) {
    val isReady: Boolean get() = domain.isNotBlank()
    val usesJwt: Boolean get() = jwtAppId.isNotBlank() && hasJwtSecret
}

data class ActivationResult(val movedScheduled: Int, val liveLeft: Int)

object MeetingServersRepository {
    const val PROVIDER_CONTABO = "CONTABO"
    const val PROVIDER_HETZNER = "HETZNER"
    const val PROVIDER_COMMUNITY = "COMMUNITY"
    const val PROVIDER_OTHER = "OTHER"
    val PROVIDERS = listOf(PROVIDER_CONTABO, PROVIDER_HETZNER, PROVIDER_COMMUNITY, PROVIDER_OTHER)

    suspend fun list(): List<MeetingServer> {
        val raw = Backend.rpcRaw("classroom_conference_servers_list")
        return Backend.json.decodeFromJsonElement<List<MeetingServer>>(raw)
    }

    /**
     * @param jwtSecret `null` keeps the saved secret, `""` removes it, anything else replaces it.
     */
    suspend fun save(
        id: String?,
        label: String,
        provider: String,
        domain: String,
        jwtAppId: String,
        jwtSecret: String?,
        notes: String,
    ) {
        Backend.rpcRaw(
            "classroom_conference_server_save",
            buildJsonObject {
                put("p_id", id)
                put("p_label", label.trim())
                put("p_provider", provider)
                put("p_domain", normalizeDomain(domain))
                put("p_jwt_app_id", jwtAppId.trim())
                put("p_jwt_app_secret", jwtSecret)
                put("p_notes", notes.trim())
            },
        )
    }

    suspend fun activate(id: String): ActivationResult {
        val res = Backend.rpcRaw("classroom_conference_server_activate", buildJsonObject { put("p_id", id) }) as JsonObject
        return ActivationResult(
            movedScheduled = res["moved_scheduled"]?.jsonPrimitive?.int ?: 0,
            liveLeft = res["live_left"]?.jsonPrimitive?.int ?: 0,
        )
    }

    suspend fun delete(id: String) {
        Backend.rpcRaw("classroom_conference_server_delete", buildJsonObject { put("p_id", id) })
    }

    /**
     * Checks from this phone that [domain] really is a reachable Jitsi server: it must answer over
     * HTTPS and serve `external_api.js` (what the browser classroom loads) and `config.js`.
     * The result is recorded on the server row so the list shows when it was last checked.
     *
     * @return null when it works, otherwise a short reason.
     */
    suspend fun test(server: MeetingServer): String? {
        val domain = normalizeDomain(server.domain)
        val reason = withContext(Dispatchers.IO) {
            if (domain.isBlank()) return@withContext "NO_DOMAIN"
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            runCatching {
                for (path in listOf("external_api.js", "config.js")) {
                    val req = Request.Builder().url("https://$domain/$path").get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching "HTTP ${resp.code} ($path)"
                        val head = resp.peekBody(65_536).string()
                        val looksRight = if (path == "external_api.js") head.isNotBlank() else head.contains("hosts")
                        if (!looksRight) return@runCatching "UNEXPECTED_CONTENT ($path)"
                    }
                }
                null
            }.getOrElse { it.javaClass.simpleName + ": " + (it.message ?: "") }
        }
        runCatching {
            Backend.rpcRaw(
                "classroom_conference_server_mark_test",
                buildJsonObject {
                    put("p_id", server.id)
                    put("p_ok", reason == null)
                },
            )
        }
        return reason
    }

    fun normalizeDomain(raw: String): String = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .trim()
        .lowercase()
}
