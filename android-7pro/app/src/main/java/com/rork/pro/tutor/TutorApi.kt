package com.rork.pro.tutor

import com.rork.pro.data.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Today's allowance and where the Worker / 3D avatar live, from `ai_tutor_status()`. */
@Serializable
data class TutorStatus(
    val enabled: Boolean = false,
    @SerialName("per_user_daily") val perUserDaily: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0,
    @SerialName("global_available") val globalAvailable: Boolean = true,
    @SerialName("worker_url") val workerUrl: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("avatar_version") val avatarVersion: Int = 0,
    @SerialName("resets_at") val resetsAt: String? = null,
    /** The paid plan currently active for this student, if any, and when it ends. */
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("plan_until") val planUntil: String? = null,
    /** Free replies left today (the daily free allowance). */
    @SerialName("free_remaining") val freeRemaining: Int = 0,
    /** Paid replies the student still has, across packs that have not expired. */
    val credits: Int = 0,
    /** When the pack that expires first ends. */
    @SerialName("credits_until") val creditsUntil: String? = null,
)

@Serializable
data class TutorCorrection(
    val wrong: String,
    val right: String,
    @SerialName("explain_ar") val explainAr: String = "",
)

@Serializable
data class TutorTurn(
    val ok: Boolean = false,
    val error: String? = null,
    val transcript: String = "",
    val corrected: String = "",
    val corrections: List<TutorCorrection> = emptyList(),
    val reply: String = "",
    /** Short Egyptian-Arabic gist of [reply], shown on screen only (never spoken). */
    @SerialName("hint_ar") val hintAr: String = "",
    val mood: String = "neutral",
    @SerialName("new_words") val newWords: List<String> = emptyList(),
    /** How sure speech recognition was about [transcript]: "high", "medium" or "low". */
    @SerialName("stt_confidence") val sttConfidence: String = "high",
    /** A second possible reading of what the student said, when recognition was unsure. */
    @SerialName("alt_transcript") val altTranscript: String = "",
    /** True when the tutor asked the student to repeat (he could not make out the words). */
    @SerialName("needs_repeat") val needsRepeat: Boolean = false,
    /** True when this was only the tutor saying "sorry, say that again" — not a real turn. */
    val clarify: Boolean = false,
    @SerialName("audio_mp3") val audioMp3: String = "",
    @SerialName("audio_base64") val audioBase64: String = "",
    @SerialName("audio_format") val audioFormat: String = "mp3",
    @SerialName("sample_rate") val sampleRate: Int = 0,
    val remaining: Int? = null,
    /** "en" or "ar": the tutor answers in the language the student used. */
    val lang: String = "en",
    /** True when the reply has no server voice (Arabic, or the voice service failed): speak it on the phone. */
    @SerialName("speak_locally") val speakLocally: Boolean = false,
    /** The persona's actual gender, as the Worker resolved it for this turn — authoritative for [speakLocally]. */
    @SerialName("voice_gender") val voiceGender: String = "male",
)

@Serializable
data class TutorHistoryLine(val role: String, val content: String)

@Serializable
private data class TurnRequest(
    val mode: String,
    val audio: String? = null,
    @SerialName("audio_format") val audioFormat: String? = null,
    val stream: Boolean = false,
    val text: String? = null,
    val topic: String,
    val level: String,
    val name: String? = null,
    val history: List<TutorHistoryLine> = emptyList(),
    /** "male" (Mr. Adam) or "female" (Ms. Sara). */
    val voice: String = "male",
    @SerialName("voice_mode") val voiceMode: String = "system",
    @SerialName("tutor_name") val tutorName: String? = null,
    /**
     * The gender of whichever persona [tutorName] (or, when that is null, [voice]) actually is — read
     * from that exact same persona, never from a separately-tracked default. The Worker trusts this
     * field first, so a stale value here is what makes the tutor "change gender" after e.g. an app
     * restart with a custom character selected.
     */
    @SerialName("tutor_gender") val tutorGender: String? = null,
    @SerialName("character_id") val characterId: String? = null,
    /** Opt-in behaviours: "soft_errors" = silence is answered in character instead of an error. */
    val features: List<String> = emptyList(),
)

/** Why a turn could not happen — each maps to one clear message on screen. */
enum class TutorFailure { USER_LIMIT, GLOBAL_LIMIT, DISABLED, NO_SPEECH, AUDIO_TOO_LONG, AUDIO_FORMAT, UNAUTHORIZED, NETWORK, AI_FAILED }

/** Streamed pieces of one turn, in the order the Worker sends them. */
sealed interface TutorEvent {
    /** What the tutor heard — arrives before he starts thinking. */
    class Heard(val transcript: String, val confidence: String = "high", val alt: String = "") : TutorEvent
    /** The Arabic translation of the tutor's line, sent as soon as it is complete (usually before he finishes speaking). */
    class Hint(val text: String) : TutorEvent
    /** One spoken sentence of the reply, ready to play while the next one is still being made. */
    class Audio(
        val index: Int,
        val text: String,
        val mp3: String,
        val format: String = "mp3",
        val sampleRate: Int = 0,
    ) : TutorEvent
}

@Serializable
private data class StreamLine(
    val type: String,
    val transcript: String = "",
    @SerialName("stt_confidence") val sttConfidence: String = "high",
    @SerialName("alt_transcript") val altTranscript: String = "",
    val i: Int = 0,
    val text: String = "",
    val mp3: String = "",
    @SerialName("audio_base64") val audioBase64: String = "",
    @SerialName("audio_format") val audioFormat: String = "mp3",
    @SerialName("sample_rate") val sampleRate: Int = 0,
    val error: String? = null,
)

class TutorException(val reason: TutorFailure) : Exception(reason.name)

/**
 * Talks to Supabase (allowance) and to the Cloudflare Worker (one conversational turn).
 * The Worker URL comes from the database, so it can move without shipping a new build.
 */
object TutorApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Hear + think + speak on Workers AI usually lands in 3-6 s; leave room for slow networks.
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun status(): TutorStatus = Backend.rpc("ai_tutor_status")

    /**
     * True only when the Worker reports that its voice-cloning service is configured and answering
     * right now (`/health` → `voice_cloning`). Any failure counts as "not available".
     */
    suspend fun cloningAvailable(workerUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(workerUrl.trimEnd('/') + "/health").get().build()
            http.newCall(request).execute().use { res ->
                res.isSuccessful &&
                    Backend.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject["voice_cloning"]?.jsonPrimitive?.booleanOrNull == true
            }
        }.getOrDefault(false)
    }

    /** The tutor's opening line for [topic] — a greeting plus a first question, with audio. */
    suspend fun open(
        workerUrl: String,
        topic: String,
        level: String,
        name: String?,
        voice: String = "male",
        voiceMode: String = "system",
        tutorName: String? = null,
        characterId: String? = null,
        tutorGender: String? = null,
    ): TutorTurn = post(workerUrl, TurnRequest(
        mode = "open", topic = topic, level = level, name = name, voice = voice,
        voiceMode = voiceMode, tutorName = tutorName, characterId = characterId, tutorGender = tutorGender,
    ))

    /** One student turn: [wavBase64] of what they said (or [typed] text), plus recent history. */
    suspend fun turn(
        workerUrl: String,
        wavBase64: String?,
        typed: String?,
        topic: String,
        level: String,
        name: String?,
        history: List<TutorHistoryLine>,
        voice: String = "male",
        tutorName: String? = null,
        tutorGender: String? = null,
    ): TutorTurn = post(
        workerUrl,
        TurnRequest(
            mode = "turn", audio = wavBase64, text = typed, topic = topic, level = level, name = name,
            history = history.takeLast(8), voice = voice, tutorName = tutorName, tutorGender = tutorGender,
        ),
    )

    /**
     * One student turn, streamed: [onEvent] gets what was heard and then each spoken sentence as
     * soon as it exists, so playback starts ~1-2 s earlier than waiting for the whole reply.
     * Returns the complete turn (corrections, mood, new words) once the Worker is done.
     */
    suspend fun turnStream(
        workerUrl: String,
        audio: AudioPayload?,
        typed: String?,
        topic: String,
        level: String,
        name: String?,
        history: List<TutorHistoryLine>,
        voice: String = "male",
        voiceMode: String = "system",
        tutorName: String? = null,
        characterId: String? = null,
        tutorGender: String? = null,
        onEvent: suspend (TutorEvent) -> Unit,
    ): TutorTurn = withContext(Dispatchers.IO) {
        val body = TurnRequest(
            mode = "turn", audio = audio?.base64, audioFormat = audio?.format, text = typed, stream = true,
            topic = topic, level = level, name = name, history = history.takeLast(8), voice = voice,
            voiceMode = voiceMode, tutorName = tutorName, characterId = characterId, tutorGender = tutorGender,
            features = listOf("soft_errors"),
        )
        try {
            streamOnce(workerUrl, body, onEvent)
        } catch (e: TutorException) {
            if (e.reason != TutorFailure.UNAUTHORIZED) throw e
            runCatching { Backend.auth.refreshCurrentSession() }
            streamOnce(workerUrl, body, onEvent)
        }
    }

    private suspend fun streamOnce(workerUrl: String, body: TurnRequest, onEvent: suspend (TutorEvent) -> Unit): TutorTurn {
        val token = runCatching { Backend.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: throw TutorException(TutorFailure.UNAUTHORIZED)
        val request = Request.Builder()
            .url(workerUrl.trimEnd('/') + "/v1/turn")
            .header("Authorization", "Bearer $token")
            .post(Backend.json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw TutorException(TutorFailure.NETWORK)
        }
        response.use { res ->
            val type = res.header("Content-Type").orEmpty()
            if (!res.isSuccessful || !type.contains("ndjson")) {
                // Errors (and old Workers) answer with one plain JSON object.
                val parsed = runCatching { Backend.json.decodeFromString<TutorTurn>(res.body?.string().orEmpty()) }.getOrNull()
                if (res.isSuccessful && parsed?.ok == true) {
                    onEvent(TutorEvent.Heard(parsed.transcript, parsed.sttConfidence, parsed.altTranscript))
                    if (parsed.audioBase64.isNotBlank()) onEvent(TutorEvent.Audio(0, parsed.reply, parsed.audioBase64, parsed.audioFormat, parsed.sampleRate))
                    else if (parsed.audioMp3.isNotBlank()) onEvent(TutorEvent.Audio(0, parsed.reply, parsed.audioMp3))
                    return parsed
                }
                throw TutorException(failureOf(parsed?.error, res.code, parsed == null))
            }
            val source = res.body?.source() ?: throw TutorException(TutorFailure.NETWORK)
            var heard = ""
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (line.contains("\"type\":\"final\"")) {
                        val final = Backend.json.decodeFromString<TutorTurn>(line)
                        if (final.audioBase64.isNotBlank()) {
                            onEvent(TutorEvent.Audio(-1, final.reply, final.audioBase64, final.audioFormat, final.sampleRate))
                        }
                        return final.copy(ok = true, transcript = final.transcript.ifBlank { heard })
                    }
                    val evt = runCatching { Backend.json.decodeFromString<StreamLine>(line) }.getOrNull() ?: continue
                    when (evt.type) {
                        "heard" -> { heard = evt.transcript; onEvent(TutorEvent.Heard(evt.transcript, evt.sttConfidence, evt.altTranscript)) }
                        "hint" -> if (evt.text.isNotBlank()) onEvent(TutorEvent.Hint(evt.text))
                        "audio" -> onEvent(TutorEvent.Audio(evt.i, evt.text, if (evt.audioBase64.isNotBlank()) evt.audioBase64 else evt.mp3, evt.audioFormat, evt.sampleRate))
                        "error" -> throw TutorException(failureOf(evt.error, 500, false))
                    }
                }
            } catch (e: IOException) {
                throw TutorException(TutorFailure.NETWORK)
            }
            throw TutorException(TutorFailure.AI_FAILED) // stream ended without a final line
        }
    }

    private fun failureOf(error: String?, code: Int, unparsed: Boolean): TutorFailure = when (error) {
        "USER_LIMIT" -> TutorFailure.USER_LIMIT
        "GLOBAL_LIMIT" -> TutorFailure.GLOBAL_LIMIT
        "DISABLED" -> TutorFailure.DISABLED
        "NO_SPEECH" -> TutorFailure.NO_SPEECH
        "AUDIO_TOO_LONG" -> TutorFailure.AUDIO_TOO_LONG
        "AUDIO_FORMAT" -> TutorFailure.AUDIO_FORMAT
        "UNAUTHORIZED" -> TutorFailure.UNAUTHORIZED
        else -> if (code in 500..599 || unparsed) TutorFailure.AI_FAILED else TutorFailure.NETWORK
    }

    private suspend fun post(workerUrl: String, body: TurnRequest): TutorTurn = withContext(Dispatchers.IO) {
        try {
            postOnce(workerUrl, body)
        } catch (e: TutorException) {
            // A token that expired mid-call: refresh once and retry, then give up honestly.
            if (e.reason != TutorFailure.UNAUTHORIZED) throw e
            runCatching { Backend.auth.refreshCurrentSession() }
            postOnce(workerUrl, body)
        }
    }

    private fun postOnce(workerUrl: String, body: TurnRequest): TutorTurn {
        val token = runCatching { Backend.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: throw TutorException(TutorFailure.UNAUTHORIZED)
        val request = Request.Builder()
            .url(workerUrl.trimEnd('/') + "/v1/turn")
            .header("Authorization", "Bearer $token")
            .post(Backend.json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw TutorException(TutorFailure.NETWORK)
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            val parsed = runCatching { Backend.json.decodeFromString<TutorTurn>(text) }.getOrNull()
            if (res.isSuccessful && parsed?.ok == true) return parsed
            throw TutorException(
                when (parsed?.error) {
                    "USER_LIMIT" -> TutorFailure.USER_LIMIT
                    "GLOBAL_LIMIT" -> TutorFailure.GLOBAL_LIMIT
                    "DISABLED" -> TutorFailure.DISABLED
                    "NO_SPEECH" -> TutorFailure.NO_SPEECH
                    "AUDIO_TOO_LONG" -> TutorFailure.AUDIO_TOO_LONG
                    "AUDIO_FORMAT" -> TutorFailure.AUDIO_FORMAT
                    "UNAUTHORIZED" -> TutorFailure.UNAUTHORIZED
                    else -> if (res.code in 500..599 || parsed == null) TutorFailure.AI_FAILED else TutorFailure.NETWORK
                },
            )
        }
    }
}
