package com.rork.pro.dubbing

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.rork.pro.data.Backend
import com.rork.pro.data.CommerceRepository
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Orchestrates a dubbing job end to end:
 *   1) measure the source duration (local file, or ask the dubbing-server for a YouTube link)
 *   2) create the job row (`dubbing_create_job`, status PENDING_QUOTE)
 *   3) get a price via the EXISTING checkout flow (CommerceRepository.quote, item_type=DUBBING)
 *   4) pay via the EXISTING checkout flow (CommerceRepository.startCheckout / submitManualPayment)
 *   5) once the order is PAID (job is now QUEUED server-side), call the dubbing-server to
 *      actually run the dub, then poll status until DONE/FAILED.
 *
 * Step 3/4 are deliberately NOT reimplemented here — see CommerceRepository, which already
 * supports any item_type generically.
 */
object DubbingRepository {

    private const val UPLOAD_BUCKET = "dubbing_uploads"

    /** Whether dubbing is enabled, its pricing, and where the dubbing-server lives right now. */
    suspend fun settings(): DubbingStatus = Backend.rpc("dubbing_status")

    /** Poll while a job is QUEUED/PROCESSING. */
    suspend fun jobStatus(dubServerBaseUrl: String, jobId: String): DubbingJob =
        DubbingApi.status(dubServerBaseUrl, jobId)

    /** Local video (from the phone's picker): duration first, no network needed. */
    fun probeLocalDurationSeconds(context: Context, uri: Uri): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000.0
        } finally {
            retriever.release()
        }
    }

    /** YouTube link: ask the dubbing-server (yt-dlp --dump-json, no download) for the duration. */
    suspend fun probeYoutubeDurationSeconds(dubServerBaseUrl: String, youtubeUrl: String): Double =
        DubbingApi.probeYoutube(dubServerBaseUrl, youtubeUrl)

    /** Uploads a phone video to private storage; returns the storage key to pass to `dubbing_create_job`. */
    suspend fun uploadLocalVideo(context: Context, uri: Uri, jobHint: String): String = withContext(Dispatchers.IO) {
        val userId = Backend.currentUserId ?: error("UNAUTHENTICATED")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("COULD_NOT_READ_FILE")
        val path = "$userId/$jobHint.mp4"
        Backend.storage.from(UPLOAD_BUCKET).upload(path, bytes) { upsert = true }
        path
    }

    /** Step 2: create the PENDING_QUOTE job row. */
    suspend fun createJob(
        sourceType: DubbingSourceType,
        youtubeUrl: String? = null,
        uploadStoragePath: String? = null,
        direction: DubbingDirection,
        sourceSeconds: Double,
    ): DubbingJob = Backend.rpc(
        "dubbing_create_job",
        buildJsonObject {
            put("p_source_type", sourceType.name)
            put("p_source_url", youtubeUrl)
            put("p_source_storage_path", uploadStoragePath)
            put("p_direction", direction.name)
            put("p_source_seconds", sourceSeconds)
        },
    )

    /**
     * Step 3: price via the same `checkout` edge function every other item type already uses.
     * Reads the raw jsonb here (instead of CommerceRepository's `PriceQuote`, which only models
     * the fields courses/AI-Tutor plans use) so `free_minutes_applied` / `billable_minutes` /
     * `source_minutes` — the dubbing-specific fields `dubbing_quote()` adds — are still visible.
     * `total_amount` and `currency` are the two fields that matter for the actual payment call.
     */
    suspend fun quote(jobId: String): JsonObject {
        val body = buildJsonObject {
            put("action", "quote")
            put("item_type", "DUBBING")
            put("live_plan_id", jobId)
        }
        val result = Backend.invokeFunction("checkout", body).jsonObject
        result["error"]?.let { error(it.toString()) }
        return result.getValue("quote").jsonObject
    }

    /** Step 4a: gateway checkout (card / mobile wallet via Paymob). */
    suspend fun startPaidCheckout(jobId: String, methodId: String?, walletPhone: String?) =
        CommerceRepository.startCheckout(itemType = "DUBBING", planId = jobId, methodId = methodId, walletPhone = walletPhone)

    /** Step 4b: manual transfer, reviewed by the owner/admin — same as course manual payments. */
    suspend fun submitManualPayment(jobId: String, brand: String, senderPhone: String, proofPath: String) =
        CommerceRepository.submitManualPayment(
            itemType = "DUBBING", planId = jobId, brand = brand, senderPhone = senderPhone, proofPath = proofPath,
        )

    /** Step 5: run the actual dub once the job is QUEUED (order PAID). */
    suspend fun start(dubServerBaseUrl: String, jobId: String, youtubeUrl: String?): DubbingJob =
        DubbingApi.start(dubServerBaseUrl, jobId, youtubeUrl)

    suspend fun status(dubServerBaseUrl: String, jobId: String): DubbingJob =
        DubbingApi.status(dubServerBaseUrl, jobId)

    /** A signed URL to play/download the finished dub, once `status().outputStoragePath` is set. */
    suspend fun signedOutputUrl(storagePath: String, expiresInSeconds: Int = 3600): String =
        Backend.storage.from("dubbing").createSignedUrl(storagePath, expiresInSeconds.toDuration(DurationUnit.SECONDS))
}

enum class DubbingSourceType { YOUTUBE, UPLOAD }
enum class DubbingDirection { EN_TO_AR, AR_TO_EN }
