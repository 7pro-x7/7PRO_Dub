package com.rork.pro.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** A file the user picked, already read into memory and ready to upload. */
data class PickedFile(val bytes: ByteArray, val name: String) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Uploads course covers, lesson videos, images and documents to the public `course-media`
 * bucket. Storage policies keep every teacher inside their own folder, while course staff
 * may write anywhere in the bucket.
 */
object MediaRepository {

    private const val BUCKET = "course-media"

    /** Every signed-in person owns a folder here, which is what lets students change their photo. */
    private const val AVATAR_BUCKET = "avatars"

    /** Private bucket holding wallet-transfer screenshots awaiting review. */
    private const val PROOF_BUCKET = "payment-proofs"

    /** The proofs bucket refuses anything larger, so a photo is checked before it is sent. */
    const val MAX_PROOF_BYTES: Long = 5L * 1024L * 1024L

    /** Matches the bucket's server-side limit so oversized files fail fast with a clear message. */
    const val MAX_BYTES: Long = 50L * 1024L * 1024L

    /** Photos are small by nature, and the avatars bucket refuses anything larger. */
    const val MAX_AVATAR_BYTES: Long = 5L * 1024L * 1024L

    /**
     * Reads a picked document into memory, rejecting anything the bucket would refuse.
     *
     * An image, video or audio clip is compressed first — see [ImageCompressor], [VideoCompressor] and [AudioCompressor] — so
     * every upload in the app (course covers, lesson videos, question media, conversation
     * lines) shrinks automatically without each screen doing its own work. Compression only
     * ever replaces the bytes when it actually produced something smaller; any failure, or a
     * result that isn't smaller, falls back to the original file untouched.
     *
     * [compressVideo] defaults to true everywhere except live-call sharing. [VideoCompressor]
     * drives the device's *hardware* MediaCodec encoder and decoder directly, and most phones
     * only expose a single concurrent hardware video encoder session. A Virtual Classroom call
     * already holds that encoder for the outgoing camera stream, so compressing a shared video
     * at the same time can fail to acquire the codec, stall, or crash mid-call. Callers sharing
     * into an active call pass false and upload the picked file as-is instead.
     */
    suspend fun read(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_BYTES,
        compressVideo: Boolean = true,
    ): PickedFile =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = displayName(context, uri)
            val rawBytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: error("FILE_UNREADABLE")

            val type = resolver.getType(uri).orEmpty()
            val (bytes, finalName) = when {
                type.startsWith("image/") -> {
                    val compressed = ImageCompressor.compress(rawBytes)
                    if (compressed != null && compressed.size < rawBytes.size) {
                        compressed to swapExtension(name, "jpg")
                    } else {
                        rawBytes to name
                    }
                }
                type.startsWith("video/") && compressVideo -> compressVideo(context, rawBytes, name)
                type.startsWith("audio/") -> compressAudio(context, rawBytes, name)
                else -> rawBytes to name
            }

            if (bytes.size > maxBytes) error("FILE_TOO_LARGE")
            PickedFile(bytes, finalName)
        }

    /** Runs [VideoCompressor] through temp files and cleans them up either way. */
    private fun compressVideo(context: Context, rawBytes: ByteArray, name: String): Pair<ByteArray, String> {
        val input = File.createTempFile("upload-in", ".mp4", context.cacheDir)
        val output = File.createTempFile("upload-out", ".mp4", context.cacheDir)
        return try {
            input.writeBytes(rawBytes)
            val compressed = VideoCompressor.compress(input.absolutePath, output)
            if (compressed != null && compressed.length() in 1 until rawBytes.size.toLong()) {
                compressed.readBytes() to swapExtension(name, "mp4")
            } else {
                rawBytes to name
            }
        } catch (t: Throwable) {
            Log.w("MediaRepository", "Video compression failed, uploading original", t)
            rawBytes to name
        } finally {
            input.delete()
            output.delete()
        }
    }

    /** Runs [AudioCompressor] through temp files; falls back to the original on any problem. */
    private fun compressAudio(context: Context, rawBytes: ByteArray, name: String): Pair<ByteArray, String> {
        val input = File.createTempFile("upload-in", ".audio", context.cacheDir)
        val output = File.createTempFile("upload-out", ".m4a", context.cacheDir)
        return try {
            input.writeBytes(rawBytes)
            val compressed = AudioCompressor.compress(input.absolutePath, output)
            if (compressed != null && compressed.length() in 1 until rawBytes.size.toLong()) {
                compressed.readBytes() to swapExtension(name, "m4a")
            } else {
                rawBytes to name
            }
        } catch (t: Throwable) {
            Log.w("MediaRepository", "Audio compression failed, uploading original", t)
            rawBytes to name
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun swapExtension(name: String, ext: String): String =
        name.substringBeforeLast('.', name) + "." + ext

    /** Uploads the file and returns the public URL to store on the course or lesson. */
    suspend fun upload(file: PickedFile, folder: String): String = withContext(Dispatchers.IO) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        val path = "$userId/$folder/${System.currentTimeMillis()}-${sanitize(file.name)}"
        val bucket = Backend.client.storage.from(BUCKET)
        bucket.upload(path, file.bytes) { upsert = false }
        bucket.publicUrl(path)
    }

    /**
     * Uploads a profile picture to the avatars bucket and returns its permanent public URL.
     *
     * Anyone signed in may write inside their own folder there, so students and teachers use
     * the same path without touching the teacher-scoped course bucket.
     */
    suspend fun uploadAvatar(file: PickedFile): String = withContext(Dispatchers.IO) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        val path = "$userId/${System.currentTimeMillis()}-${sanitize(file.name)}"
        val bucket = Backend.client.storage.from(AVATAR_BUCKET)
        bucket.upload(path, file.bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    /**
     * Uploads a transfer screenshot and returns its storage key — deliberately not a URL.
     *
     * The bucket is private, because the picture carries a wallet number and a balance. Only the
     * learner who uploaded it and the staff reviewing the payment can open it, and they do so
     * through a short-lived signed link rather than a permanent public address.
     */
    suspend fun uploadPaymentProof(file: PickedFile): String = withContext(Dispatchers.IO) {
        val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
        // The first folder is the uploader's own id: storage policy and the server-side check on
        // the payment request both refuse anything filed outside it.
        val path = "$userId/${System.currentTimeMillis()}-${sanitize(file.name)}"
        Backend.client.storage.from(PROOF_BUCKET).upload(path, file.bytes) { upsert = false }
        path
    }

    /**
     * A temporary link to a stored transfer screenshot, for the reviewer to look at.
     *
     * Signed rather than public, and short lived, so a screenshot cannot be passed around after
     * the review is over.
     */
    suspend fun paymentProofUrl(path: String, minutes: Int = 30): String = withContext(Dispatchers.IO) {
        Backend.client.storage.from(PROOF_BUCKET)
            .createSignedUrl(path, minutes.toDuration(DurationUnit.MINUTES))
    }

    private fun displayName(context: Context, uri: Uri): String {
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull()
        cursor?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
    }

    /** Storage keys allow a narrow character set, so anything else is folded to an underscore. */
    private fun sanitize(name: String): String {
        val cleaned = name.lowercase(Locale.US).replace(Regex("[^a-z0-9._-]"), "_")
        return cleaned.takeLast(64).ifBlank { "upload" }
    }
}
