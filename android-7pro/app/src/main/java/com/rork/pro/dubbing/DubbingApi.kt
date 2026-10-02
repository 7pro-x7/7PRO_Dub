package com.rork.pro.dubbing

import com.rork.pro.data.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class YoutubeProbe(
    @SerialName("source_seconds") val sourceSeconds: Double = 0.0,
    val title: String = "",
)

@Serializable
data class DubbingJob(
    val id: String = "",
    val status: String = "PENDING_QUOTE",
    @SerialName("output_storage_path") val outputStoragePath: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

/** Owner-controlled settings + where the dubbing-server lives, from `dubbing_status()`. */
@Serializable
data class DubbingStatus(
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("price_per_minute") val pricePerMinute: Double = 0.0,
    val currency: String = "",
    @SerialName("min_billable_minutes") val minBillableMinutes: Double = 1.0,
    @SerialName("free_trial_minutes_total") val freeTrialMinutesTotal: Double = 0.0,
    @SerialName("max_source_minutes") val maxSourceMinutes: Int = 60,
    @SerialName("dub_server_url") val dubServerUrl: String = "",
)

private class DubbingException(val reason: String) : IOException(reason)

/**
 * Talks to the dubbing-server (an extension of translation-server). The base URL is read
 * from BuildConfig/remote config the same way `workerUrl` is for the AI tutor — do not
 * hardcode it here.
 */
object DubbingApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)   // uploading a phone video can take a while
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private suspend fun token(): String =
        runCatching { Backend.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: throw DubbingException("UNAUTHORIZED")

    /** Duration of a YouTube video without downloading it, so a quote can be shown pre-payment. */
    suspend fun probeYoutube(baseUrl: String, youtubeUrl: String): Double = withContext(Dispatchers.IO) {
        val body = Backend.json.encodeToString(mapOf("youtube_url" to youtubeUrl))
        post<YoutubeProbe>(baseUrl, "/v1/dub/probe-youtube", body).sourceSeconds
    }

    /** Starts processing a job row that already exists and is paid (status = QUEUED). */
    suspend fun start(baseUrl: String, jobId: String, youtubeUrl: String?): DubbingJob = withContext(Dispatchers.IO) {
        val payload = buildMap<String, String> {
            put("job_id", jobId)
            youtubeUrl?.let { put("youtube_url", it) }
        }
        val body = Backend.json.encodeToString(payload)
        post(baseUrl, "/v1/dub/start", body)
    }

    /** Poll this while status is QUEUED/PROCESSING. */
    suspend fun status(baseUrl: String, jobId: String): DubbingJob = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/dub/status/$jobId")
            .header("Authorization", "Bearer ${token()}")
            .get()
            .build()
        execute(request)
    }

    private suspend inline fun <reified T> post(baseUrl: String, path: String, jsonBody: String): T {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${token()}")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        return execute(request)
    }

    private inline fun <reified T> execute(request: Request): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw DubbingException("NETWORK")
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw DubbingException("HTTP_${res.code}: $text")
            return Backend.json.decodeFromString(text)
        }
    }
}
