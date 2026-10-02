package com.rork.pro.classroom.translation

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.rork.pro.data.Backend
import com.rork.pro.data.CatalogRepository
import com.rork.pro.ui.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "LiveVoiceTranslator"

/** Owner-controlled keys in app_settings (see migration 20260916120000_classroom_live_voice_translation). */
object LiveTranslationKeys {
    const val ENABLED = "classroom.live_translation_enabled"
    const val SERVER_URL = "classroom.live_translation_server_url"
}

enum class TranslationStatus { OFF, CONNECTING, LIVE, ERROR }

data class TranslationCaption(
    val speakerName: String,
    val text: String,
    val original: String,
    val at: Long = System.currentTimeMillis(),
)

data class TranslationUiState(
    /** The owner switched the feature on and gave a server address. Nothing is shown otherwise. */
    val available: Boolean = false,
    val status: TranslationStatus = TranslationStatus.OFF,
    /** "Translate my voice" — my mic goes through the translator instead of straight into Jitsi. */
    val speaking: Boolean = false,
    /** The language I speak AND want to hear everyone else in. */
    val language: String = "ar",
    val languages: List<String> = listOf("ar", "en"),
    val cloning: Boolean = false,
    val caption: TranslationCaption? = null,
    val errorCode: String? = null,
)

/**
 * Client side of the in-meeting Live Voice Translation.
 *
 * One WebSocket to the self-hosted open-source translation server (translation-server/):
 *  - while [TranslationUiState.speaking] and the mic is open, the phone's microphone is streamed
 *    as 16 kHz PCM16 and the call's own (Jitsi) microphone stays muted, so nobody hears the voice
 *    twice;
 *  - everything the server sends back is played on the call's audio route: the original voice of
 *    anyone speaking my language, and the translated sentence — re-voiced with the speaker's own
 *    timbre — for anyone speaking another language.
 *
 * Every participant connects as a listener automatically once the owner has enabled the feature,
 * so a speaker who turns translation on is still heard by the whole room.
 */
class LiveVoiceTranslator(private val sessionId: String, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(TranslationUiState(language = AppLanguage.current.code))
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var serverUrl: String = ""
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var released = false
    private var micOpen = false

    @Volatile private var capturing = false
    private var captureThread: Thread? = null

    // One ordered lane per kind: live pass-through audio is never held up behind a translated
    // sentence that is still playing, and translated sentences never overlap each other.
    private val origLane: ExecutorService = Executors.newSingleThreadExecutor()
    private val trLane: ExecutorService = Executors.newSingleThreadExecutor()
    private val tracks = HashMap<String, AudioTrack>()

    // ------------------------------------------------------------------ lifecycle

    /** Reads the owner's switch; connects only if the feature is on. A no-op otherwise. */
    suspend fun init() {
        val settings = runCatching { CatalogRepository.settings() }
            .onFailure { Log.w(TAG, "Could not read live translation settings", it) }
            .getOrNull() ?: return
        val enabled = settings[LiveTranslationKeys.ENABLED].equals("true", ignoreCase = true)
        val url = settings[LiveTranslationKeys.SERVER_URL].orEmpty().trim()
        if (!enabled || url.isBlank() || released) return
        serverUrl = url.trimEnd('/').let { if (it.endsWith("/ws")) it else "$it/ws" }
        _state.update { it.copy(available = true) }
        connect()
    }

    fun release() {
        if (released) return
        released = true
        reconnectJob?.cancel()
        stopCapture()
        runCatching { socket?.close(1000, "leave") }
        socket = null
        origLane.shutdownNow()
        trLane.shutdownNow()
        synchronized(tracks) {
            tracks.values.forEach { runCatching { it.stop(); it.release() } }
            tracks.clear()
        }
        http.dispatcher.executorService.shutdown()
        _state.update { it.copy(status = TranslationStatus.OFF, speaking = false) }
    }

    // ------------------------------------------------------------------ controls

    fun setLanguage(code: String) {
        if (code == _state.value.language) return
        _state.update { it.copy(language = code, caption = null) }
        send(buildJsonObject { put("type", "lang"); put("lang", code) }.toString())
    }

    fun setSpeaking(on: Boolean) {
        _state.update { it.copy(speaking = on, errorCode = null) }
        send(buildJsonObject { put("type", "speak"); put("on", on) }.toString())
        refreshCapture()
    }

    /** Mirrors the call's own mic button while translation owns the microphone. */
    fun setMicOpen(open: Boolean) {
        micOpen = open
        refreshCapture()
    }

    fun clearError() = _state.update { it.copy(errorCode = null) }

    // ------------------------------------------------------------------ socket

    private fun connect() {
        if (released || serverUrl.isBlank()) return
        val token = runCatching { Backend.auth.currentSessionOrNull()?.accessToken }.getOrNull()
        if (token.isNullOrBlank()) {
            _state.update { it.copy(status = TranslationStatus.ERROR, errorCode = "UNAUTHORIZED") }
            return
        }
        _state.update { it.copy(status = TranslationStatus.CONNECTING) }
        val request = runCatching { Request.Builder().url(serverUrl).build() }.getOrElse {
            Log.e(TAG, "Invalid translation server url: $serverUrl", it)
            _state.update { s -> s.copy(status = TranslationStatus.ERROR, errorCode = "BAD_URL") }
            return
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val s = _state.value
                webSocket.send(
                    buildJsonObject {
                        put("type", "hello")
                        put("token", token)
                        put("session_id", sessionId)
                        put("lang", s.language)
                        put("speak", s.speaking)
                    }.toString(),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onAudio(bytes.toByteArray())

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onDropped(webSocket, code)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Translation socket failed", t)
                onDropped(webSocket, -1)
            }
        })
    }

    private fun onDropped(webSocket: WebSocket, code: Int) {
        if (socket !== webSocket) return
        socket = null
        stopCapture()
        if (released) return
        // 4403 = not allowed (feature turned off, removed from the class): stop retrying.
        if (code == 4403) {
            _state.update { it.copy(status = TranslationStatus.ERROR, speaking = false) }
            return
        }
        _state.update { it.copy(status = TranslationStatus.CONNECTING) }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3_000)
            connect()
        }
    }

    private fun send(text: String) {
        runCatching { socket?.send(text) }
    }

    private fun onText(text: String) {
        val obj = runCatching { Backend.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (obj.str("type")) {
            "ready" -> {
                val langs = runCatching { obj["languages"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
                    .getOrNull().orEmpty()
                _state.update { s ->
                    val language = if (langs.isNotEmpty() && s.language !in langs) langs.first() else s.language
                    s.copy(
                        status = TranslationStatus.LIVE,
                        languages = langs.ifEmpty { s.languages },
                        language = language,
                        cloning = obj["cloning"]?.jsonPrimitive?.booleanOrNull == true,
                    )
                }
                // The hello carried the language from before the list was known; resend if it changed.
                send(buildJsonObject { put("type", "lang"); put("lang", _state.value.language) }.toString())
                refreshCapture()
            }
            "caption" -> _state.update {
                it.copy(
                    caption = TranslationCaption(
                        speakerName = obj.str("name").orEmpty(),
                        text = obj.str("text").orEmpty(),
                        original = obj.str("original").orEmpty(),
                    ),
                )
            }
            "error" -> {
                val code = obj.str("code") ?: "ERROR"
                if (code == "MIC_LOCKED") {
                    _state.update { it.copy(errorCode = code) }
                } else {
                    _state.update { it.copy(errorCode = code, status = TranslationStatus.ERROR) }
                }
            }
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    // ------------------------------------------------------------------ playback

    private fun onAudio(packet: ByteArray) {
        if (packet.size < 4) return
        val headerLen = ByteBuffer.wrap(packet, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        if (headerLen <= 0 || 4 + headerLen > packet.size) return
        val header = runCatching {
            Backend.json.parseToJsonElement(String(packet, 4, headerLen, Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: return
        val kind = header.str("kind") ?: return
        val rate = header["sr"]?.jsonPrimitive?.intOrNull ?: 16_000
        val pcm = packet.copyOfRange(4 + headerLen, packet.size)
        val lane = if (kind == "tr") trLane else origLane
        runCatching {
            lane.execute {
                val track = trackFor("$kind-$rate", rate) ?: return@execute
                track.write(pcm, 0, pcm.size)
            }
        }
    }

    private fun trackFor(key: String, rate: Int): AudioTrack? = synchronized(tracks) {
        if (released) return null
        tracks[key]?.let { return it }
        runCatching {
            val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Same route as the call itself (earpiece / speaker / Bluetooth).
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, rate / 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play(); tracks[key] = it }
        }.onFailure { Log.e(TAG, "Could not open playback for $key", it) }.getOrNull()
    }

    // ------------------------------------------------------------------ capture

    private fun refreshCapture() {
        val s = _state.value
        val shouldCapture = s.speaking && micOpen && s.status == TranslationStatus.LIVE && !released
        if (shouldCapture) startCapture() else stopCapture()
    }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        if (capturing) return
        // Let a just-stopped recorder release the mic before opening a new one.
        captureThread?.let { old -> if (old !== Thread.currentThread()) runCatching { old.join(500) } }
        capturing = true
        captureThread = Thread({
            val rate = 16_000
            val frame = ShortArray(rate / 10) // 100 ms
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = try {
                AudioRecord(
                    // Same input profile Jitsi uses, so the platform's echo cancellation and
                    // noise suppression stay on and the microphone is shared within the app.
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, rate),
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            } catch (t: Throwable) {
                Log.e(TAG, "Microphone unavailable for translation", t)
                null
            }
            if (recorder == null) {
                capturing = false
                _state.update { it.copy(errorCode = "MIC_UNAVAILABLE") }
                return@Thread
            }
            var silentMs = 0
            try {
                recorder.startRecording()
                val bytes = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                while (capturing) {
                    val n = recorder.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    bytes.clear()
                    var peak = 0
                    for (i in 0 until n) {
                        bytes.putShort(frame[i])
                        val v = kotlin.math.abs(frame[i].toInt())
                        if (v > peak) peak = v
                    }
                    // A device that refuses to share the mic with the call hands back pure zeros.
                    silentMs = if (peak == 0) silentMs + 100 else 0
                    if (silentMs == 4_000) _state.update { it.copy(errorCode = "MIC_UNAVAILABLE") }
                    socket?.send(bytes.array().toByteString(0, n * 2))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Translation capture stopped", t)
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }, "live-translation-mic").apply { start() }
    }

    private fun stopCapture() {
        capturing = false
    }
}
