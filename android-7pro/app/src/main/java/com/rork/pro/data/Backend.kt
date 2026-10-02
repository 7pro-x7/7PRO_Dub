package com.rork.pro.data

import com.rork.pro.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Single Supabase client for the whole app. */
object Backend {
    /**
     * Deep link the Google sign-in browser hands control back through.
     *
     * Must match the redirect URL allowed on the Supabase project and the intent filter in the
     * manifest, otherwise the browser finishes the sign-in but never returns to the app.
     */
    const val AUTH_SCHEME: String = "sevenpro"
    const val AUTH_HOST: String = "auth"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                scheme = AUTH_SCHEME
                host = AUTH_HOST
            }
            install(Postgrest)
            install(Functions)
            install(Storage)
            install(Realtime)
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(json)
            // Every database / function / storage request waits for a usable sign-in first.
            // See [SessionGate] for why this exists.
            httpConfig { install(SessionGate) }
        }
    }

    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The access token a request should carry *right now*, or null for a signed-out visitor.
     *
     * Found in the server logs: right after the app comes back from the background (or the
     * process is restarted by Android) the saved sign-in is still loading or its 1-hour token
     * has just expired. Requests fired in that moment went out with no user token at all, so the
     * database treated them as an anonymous visitor and refused them ("permission denied for
     * function …") — classroom chat/whiteboard, approvals, subscription state, push token, all
     * failing with an error screen until the next refresh. This waits (bounded) for the saved
     * session to finish loading and refreshes a token that is about to expire before the
     * request leaves.
     */
    suspend fun freshAccessTokenOrNull(): String? = runCatching {
        val auth = client.auth
        kotlinx.coroutines.withTimeoutOrNull(8_000) {
            auth.sessionStatus.first {
                it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated || it is SessionStatus.RefreshFailure
            }
        }
        val session = auth.currentSessionOrNull() ?: return@runCatching null
        if (secondsLeft(session.expiresAt.epochSeconds) < 60) {
            refreshLock.withLock {
                val current = auth.currentSessionOrNull()
                if (current != null && secondsLeft(current.expiresAt.epochSeconds) < 60) {
                    kotlinx.coroutines.withTimeoutOrNull(6_000) { runCatching { auth.refreshCurrentSession() } }
                }
            }
        }
        auth.currentAccessTokenOrNull()
    }.getOrNull()

    private fun secondsLeft(epochSeconds: Long): Long = epochSeconds - System.currentTimeMillis() / 1000

    /**
     * Ktor plugin on the Supabase HTTP client: before a request to the database (`/rest/v1`),
     * Edge Functions (`/functions/v1`) or Storage (`/storage/v1`) leaves, make sure it carries a
     * live user token (see [freshAccessTokenOrNull]). Sign-in requests themselves (`/auth/v1`)
     * pass straight through, so the refresh can never wait on itself.
     */
    private val SessionGate = io.ktor.client.plugins.api.createClientPlugin("SessionGate") {
        on(io.ktor.client.plugins.api.Send) { request ->
            val path = request.url.encodedPath
            val gated = path.contains("/rest/v1/") || path.contains("/functions/v1/") || path.contains("/storage/v1/")
            if (gated) {
                val token = freshAccessTokenOrNull()
                if (token != null) {
                    request.headers[io.ktor.http.HttpHeaders.Authorization] = "Bearer $token"
                }
            }
            proceed(request)
        }
    }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /**
     * Process-lifetime scope for cleanup work that must survive the caller's own scope being
     * cancelled — e.g. a ViewModel's `onCleared()`, which androidx already cancels
     * `viewModelScope` *before* invoking (see ViewModel.clear() in the lifecycle-viewmodel
     * source: it closes every tagged Closeable, including the CoroutineScope's Job, then calls
     * onCleared()). Launching on `viewModelScope` from inside `onCleared()` silently does
     * nothing — the coroutine is created already-cancelled and its body never runs. Anything
     * that truly must fire on teardown (e.g. ClassroomCallViewModel removing its Realtime
     * channel) has to run on a scope like this one instead.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val auth get() = client.auth
    val db get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime

    val currentUserId: String?
        get() = runCatching { client.auth.currentUserOrNull()?.id }.getOrNull()

    /** Calls a Postgres function and decodes the JSON result. */
    suspend inline fun <reified T> rpc(name: String, params: JsonObject = buildJsonObject { }): T {
        return client.postgrest.rpc(name, params).decodeAs()
    }

    suspend fun rpcRaw(name: String, params: JsonObject = buildJsonObject { }): JsonElement {
        val raw = client.postgrest.rpc(name, params).data
        return if (raw.isBlank()) JsonNull else json.parseToJsonElement(raw)
    }

    suspend fun rpcVoid(name: String, params: JsonObject = buildJsonObject { }) {
        try {
            val result = client.postgrest.rpc(name, params)
            // For RETURNS VOID functions PostgREST may return a 200 with an error payload
            // inside the body. Inspect the raw data so server-side RAISE EXCEPTION messages
            // (e.g. FORBIDDEN, COURSE_NOT_FOUND) propagate as real exceptions.
            val body = result.data
            if (body.isNotBlank() && body != "null" && body != "") {
                val trimmed = body.trim()
                if (trimmed.startsWith("{") && trimmed.contains("\"message\"")) {
                    val parsed = json.parseToJsonElement(trimmed) as? JsonObject
                    val msg = parsed?.get("message")?.toString()?.trim('"') ?: trimmed
                    error(msg)
                }
            }
        } catch (e: Exception) {
            // The Supabase SDK may throw a RestException whose message contains the
            // PostgREST JSON error body. Extract the "message" field so the app-level
            // error mapper can match the known error codes (FORBIDDEN, COURSE_HAS_STUDENTS, etc).
            val raw = e.message ?: throw e
            val extracted = extractPostgrestMessage(raw)
            if (extracted != null) error(extracted)
            throw e
        }
    }

    /** Pulls the "message" value out of a PostgREST JSON error embedded in an exception message. */
    private fun extractPostgrestMessage(raw: String): String? {
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null
        return runCatching {
            val fragment = raw.substring(jsonStart, jsonEnd + 1)
            val obj = json.parseToJsonElement(fragment) as? JsonObject
            obj?.get("message")?.toString()?.trim('"')
        }.getOrNull()
    }

    /** Invokes an edge function and returns the parsed JSON body. */
    suspend fun invokeFunction(slug: String, body: JsonObject): JsonElement {
        val response = client.functions.invoke(slug) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        return if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }
}
