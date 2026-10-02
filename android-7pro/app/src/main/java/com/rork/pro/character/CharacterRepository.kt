package com.rork.pro.character

import com.rork.pro.data.Backend
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** An owner-uploaded character offered in the AI Tutor call, alongside Mr. Adam and Ms. Sara. */
@Serializable
data class TutorCharacter(
    val id: String = "",
    val name: String,
    @SerialName("image_url") val imageUrl: String,
    val voice: String,
    @SerialName("mouth_x") val mouthX: Float,
    @SerialName("mouth_y") val mouthY: Float,
    @SerialName("mouth_h") val mouthH: Float,
    @SerialName("eye_y") val eyeY: Float,
    // Precise face rig (see migration 20260923140000). Old rows only have eye_y; the defaults keep them working.
    @SerialName("mouth_w") val mouthW: Float = 0.09f,
    @SerialName("eye_l_x") val eyeLX: Float = 0.38f,
    @SerialName("eye_l_y") val eyeLY: Float? = null,
    @SerialName("eye_r_x") val eyeRX: Float = 0.62f,
    @SerialName("eye_r_y") val eyeRY: Float? = null,
    @SerialName("eye_w") val eyeW: Float = 0.06f,
    @SerialName("eye_h") val eyeH: Float = 0.035f,
    @SerialName("rig_source") val rigSource: String = "legacy",
    @SerialName("voice_mode") val voiceMode: String = "system",
    @SerialName("voice_sample_path") val voiceSamplePath: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val active: Boolean = true,
) {
    fun rig() = CharacterRig(
        mouthX = mouthX, mouthY = mouthY, mouthHalfW = mouthW, mouthHalfH = mouthH,
        eyeLX = eyeLX, eyeLY = eyeLY ?: eyeY, eyeRX = eyeRX, eyeRY = eyeRY ?: eyeY,
        eyeHalfW = eyeW, eyeHalfH = eyeH,
    ).clamped()
}

@Serializable
private data class DefaultVisibilityRow(val voice: String, val visible: Boolean)

@Serializable
private data class NewCharacterRow(
    val name: String,
    @SerialName("image_url") val imageUrl: String,
    val voice: String,
    @SerialName("mouth_x") val mouthX: Float,
    @SerialName("mouth_y") val mouthY: Float,
    @SerialName("mouth_h") val mouthH: Float,
    @SerialName("eye_y") val eyeY: Float,
    @SerialName("mouth_w") val mouthW: Float,
    @SerialName("eye_l_x") val eyeLX: Float,
    @SerialName("eye_l_y") val eyeLY: Float,
    @SerialName("eye_r_x") val eyeRX: Float,
    @SerialName("eye_r_y") val eyeRY: Float,
    @SerialName("eye_w") val eyeW: Float,
    @SerialName("eye_h") val eyeH: Float,
    @SerialName("rig_source") val rigSource: String,
    @SerialName("voice_mode") val voiceMode: String,
    @SerialName("voice_sample_path") val voiceSamplePath: String? = null,
)

/**
 * Reads and writes `ai_tutor_characters` / the `tutor-characters` storage bucket. Row-level
 * security already restricts writes to the account with role OWNER, so this object adds no
 * extra permission check of its own — a non-owner call simply gets PostgREST's own error back.
 */
object CharacterRepository {
    private const val TABLE = "ai_tutor_characters"
    private const val BUCKET = "tutor-characters"
    private const val VOICE_BUCKET = "tutor-voice-samples"
    suspend fun list(): List<TutorCharacter> =
        Backend.client.from(TABLE)
            .select { order("sort_order", SortOrder.ASCENDING) }
            .decodeList()

    /** Uploads the selected character image and optional voice sample, then creates its row. */
    suspend fun create(
        name: String,
        voice: String,
        imagePng: ByteArray,
        rig: CharacterRig,
        rigSource: String,
        voiceMode: String = "system",
        voiceSampleWav: ByteArray? = null,
    ): TutorCharacter {
        val bucket = Backend.client.storage.from(BUCKET)
        val path = "${UUID.randomUUID()}.png"
        bucket.upload(path, imagePng) { upsert = false }
        val samplePath = voiceSampleWav?.let { sample ->
            val userId = Backend.currentUserId ?: error("UNAUTHORIZED")
            val voicePath = "$userId/${UUID.randomUUID()}.wav"
            Backend.client.storage.from(VOICE_BUCKET).upload(voicePath, sample) { upsert = false }
            voicePath
        }
        val publicUrl = bucket.publicUrl(path)
        val row = NewCharacterRow(
            name = name, imageUrl = publicUrl, voice = voice,
            mouthX = rig.mouthX, mouthY = rig.mouthY, mouthH = rig.mouthHalfH,
            // eye_y stays filled (average of the two eyes) so older app builds still place the blink.
            eyeY = (rig.eyeLY + rig.eyeRY) / 2f,
            mouthW = rig.mouthHalfW,
            eyeLX = rig.eyeLX, eyeLY = rig.eyeLY, eyeRX = rig.eyeRX, eyeRY = rig.eyeRY,
            eyeW = rig.eyeHalfW, eyeH = rig.eyeHalfH,
            rigSource = rigSource,
            voiceMode = voiceMode, voiceSamplePath = samplePath,
        )
        return try {
            Backend.client.from(TABLE).insert(row) { select() }.decodeSingle()
        } catch (e: Exception) {
            runCatching { bucket.delete(path) }
            samplePath?.let { runCatching { Backend.client.storage.from(VOICE_BUCKET).delete(it) } }
            throw e
        }
    }

    suspend fun delete(id: String) {
        // Look the row up first so the image file can be removed too — otherwise every deleted
        // character leaves its PNG behind in the bucket forever.
        val existing = runCatching {
            Backend.client.from(TABLE).select { filter { eq("id", id) } }.decodeSingleOrNull<TutorCharacter>()
        }.getOrNull()
        Backend.client.from(TABLE).delete { filter { eq("id", id) } }
        val path = existing?.imageUrl?.substringAfterLast("/$BUCKET/", "")?.takeIf { it.isNotBlank() }
        if (path != null) runCatching { Backend.client.storage.from(BUCKET).delete(path) }
        existing?.voiceSamplePath?.let { runCatching { Backend.client.storage.from(VOICE_BUCKET).delete(it) } }
    }

    /** Saves a re-adjusted face rig for an existing character (owner / admin only, enforced by RLS). */
    suspend fun updateRig(id: String, rig: CharacterRig, rigSource: String) {
        val r = rig.clamped()
        Backend.client.from(TABLE).update(buildJsonObject {
            put("mouth_x", r.mouthX)
            put("mouth_y", r.mouthY)
            put("mouth_w", r.mouthHalfW)
            put("mouth_h", r.mouthHalfH)
            put("eye_l_x", r.eyeLX)
            put("eye_l_y", r.eyeLY)
            put("eye_r_x", r.eyeRX)
            put("eye_r_y", r.eyeRY)
            put("eye_y", (r.eyeLY + r.eyeRY) / 2f)
            put("eye_w", r.eyeHalfW)
            put("eye_h", r.eyeHalfH)
            put("rig_source", rigSource)
        }) { filter { eq("id", id) } }
    }

    /** Which built-in tutors students see: keys "male" (Mr. Adam) and "female" (Ms. Sara). Missing = visible. */
    suspend fun defaultsVisible(): Map<String, Boolean> =
        runCatching {
            Backend.client.from("ai_tutor_default_characters").select().decodeList<DefaultVisibilityRow>()
                .associate { it.voice to it.visible }
        }.getOrDefault(emptyMap())

    /** Owner / admin only (enforced by RLS). */
    suspend fun setDefaultVisible(voice: String, visible: Boolean) {
        Backend.client.from("ai_tutor_default_characters").upsert(DefaultVisibilityRow(voice, visible))
    }

    suspend fun setActive(id: String, active: Boolean) {
        Backend.client.from(TABLE).update(buildJsonObject { put("active", active) }) { filter { eq("id", id) } }
    }
}
