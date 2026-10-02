package com.rork.pro.classroom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rork.pro.data.AppError
import com.rork.pro.data.Backend
import com.rork.pro.data.ClassroomChatMessage
import com.rork.pro.data.ClassroomJoinInfo
import com.rork.pro.data.ClassroomParticipant
import com.rork.pro.data.ClassroomRepository
import com.rork.pro.data.ClassroomStrokeRow
import com.rork.pro.data.ErrorText
import com.rork.pro.data.toAppError
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.data.ClassroomPresenceRow
import com.rork.pro.classroom.translation.LiveVoiceTranslator
import com.rork.pro.classroom.translation.TranslationStatus
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "ClassroomCall"

/**
 * How long the websocket has to stay down, with the device still online, before the screen says
 * anything. Long enough to cover a normal reconnect (including the one that happens right after
 * the app comes back from the background) and short enough that a genuinely stuck sync is still
 * reported while the lesson can be saved.
 */
private const val SOCKET_GRACE_MS = 6_000L

/** Losing the network outright is real and immediate, but still not reported on a stray blip. */
private const val NETWORK_GRACE_MS = 2_500L

/** While the socket is down but the device is online, rebuild the channel this often. */
private const val RECONNECT_RETRY_MS = 8_000L

enum class ConnectionState { LIVE, RECONNECTING, OFFLINE }

data class CallUiState(
    val chat: List<ClassroomChatMessage> = emptyList(),
    /** True once the first transcript snapshot has loaded — before that, arriving messages are
     *  history, not "new", and must not pop up as floating toasts. */
    val chatHistoryLoaded: Boolean = false,
    val questions: List<ClassroomChatMessage> = emptyList(),
    val participants: List<ClassroomParticipant> = emptyList(),
    val raisedHands: List<ClassroomParticipant> = emptyList(),
    val myHandRaised: Boolean = false,
    val whiteboardBoardId: String? = null,
    val whiteboardBackgroundUrl: String? = null,
    val whiteboardBackgroundType: String = "NONE",
    /**
     * Bumped only when a *new* background actually lands — never on a plain "clear the
     * drawing" tap, which reuses the same CLEAR stroke signal but leaves the background
     * unchanged. The call screen watches this to auto-surface newly shared material for
     * everyone the moment it arrives, instead of leaving each participant to notice on their
     * own that there is something to switch over and look at.
     */
    val sharedMediaRevision: Int = 0,
    val strokes: List<ClassroomStrokeRow> = emptyList(),
    /**
     * Who is *really* connected right now, from the server-side heartbeat (see
     * classroom_session_presence) — not merely who has a participant row saying JOINED. A
     * student whose phone died stops appearing here within ~45s instead of looking present
     * for the rest of the class.
     */
    val presence: List<ClassroomPresenceRow> = emptyList(),
    /**
     * A remote participant is presenting their screen right now. Jitsi's own
     * SCREEN_SHARE_TOGGLED broadcast only ever describes *this* device, so a student was never
     * told the teacher had started sharing — and if they had the whiteboard or a shared file
     * open, the presentation was playing behind an overlay they had no reason to close.
     */
    val remoteScreenShareBy: String? = null,
    val connection: ConnectionState = ConnectionState.LIVE,
    val ended: Boolean = false,
    /** True once the teacher removed this student from the class (see [ClassroomCallViewModel.removeStudent]). */
    val removed: Boolean = false,
    // -- media state --------------------------------------------------------
    val micMuted: Boolean = false,
    val cameraMuted: Boolean = false,
    val screenSharing: Boolean = false,
    /** True while my own mic is locked by the teacher — the mic button is disabled while this holds. */
    val myMicLocked: Boolean = false,
    val myCameraLocked: Boolean = false,
)

/**
 * Everything the in-call screen needs beyond the Jitsi view itself.
 *
 * A single Realtime channel is opened for the whole call (see [ClassroomRepository.openChannel])
 * and reused for chat, participant/hand-raise updates, whiteboard strokes and session-level state
 * ("lock all mics"), so the call subscribes exactly once no matter how many panels are
 * open. Attendance's JOIN event is already recorded by the `classroom_join` RPC before this screen
 * even opens (see ClassroomLobbyViewModel); this class is responsible for the matching LEAVE call.
 *
 * Error handling: every server call that can fail surfaces a translated, user-facing [AppError] on
 * [error] (so the in-call banner can show it and offer to dismiss) *and* logs the technical cause
 * via [Log.e] — never one without the other. A bare `runCatching { ... }` with no `onFailure` used
 * to swallow failures here silently (chat never sending, whiteboard strokes never syncing,
 * hand-raise doing nothing) with no visible signal to the user or in logs; that pattern is gone.
 */
/** Statuses after which the call must close on every device still in it. */
internal fun isEndedStatus(status: String?): Boolean =
    status == "ENDED" || status == "CANCELED" || status == "EXPIRED"

class ClassroomCallViewModel(
    private val sessionId: String,
    val info: ClassroomJoinInfo,
) : ViewModel() {

    // Seeded from what classroom_join already told us, so the mic/camera buttons show correctly
    // locked (and disabled) from the very first frame — matching the conference's own initial
    // muted state set in ClassroomCallActivity.startJitsiConference — instead of only catching
    // up once loadParticipants()'s round trip resolves.
    private val _state = MutableStateFlow(
        CallUiState(
            // Mirrors exactly what ClassroomCallActivity.startJitsiConference joins with: a
            // student now arrives muted even when nothing is locked (the noise/feedback
            // default). Seeding the same expression here keeps the mic button truthful from
            // the first frame instead of showing "live" for the fraction of a second until
            // Jitsi's own AUDIO_MUTED_CHANGED broadcast arrives and corrects it.
            micMuted = info.micLocked,
            // Everyone now joins with the camera off (ClassroomCallActivity.startJitsiConference
            // sets setVideoMuted(true) unconditionally), so the camera button must read "off"
            // from the first frame rather than flashing "on" until Jitsi's VIDEO_MUTED_CHANGED
            // broadcast arrives and corrects it.
            cameraMuted = true,
            myMicLocked = info.micLocked,
            myCameraLocked = info.cameraLocked,
        ),
    )
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    /** Set by the Activity once it can actually act on Jitsi (mute/unmute, toggle screen share). */
    var jitsiCommands: JitsiCommands? = null

    /**
     * Surfaces the join-watchdog timeout from ClassroomCallActivity as a retryable banner —
     * previously a stuck "Joining…" screen with a dead XMPP handshake (bad/unreachable domain,
     * very poor connectivity) had no visible failure state at all, so there was nothing for the
     * person to do but force-close the app.
     *
     * [domain] and [roomName] are surfaced as the error's [AppError.detail] — printed right on
     * the banner — so the actual server this device tried to reach is visible on the phone
     * itself. This is a stand-in for a Logcat pull for anyone who can't get one: if `domain`
     * ever shows up blank or clearly wrong here, that alone points at the `classroom_join` RPC
     * response rather than at the device's network.
     */
    fun reportJoinTimeout(domain: String, roomName: String) {
        // Server/room stay in the log for debugging; the person only sees the plain message
        // (the raw "domain: …, room: …" suffix used to be printed into the banner itself).
        Log.w(TAG, "Join timeout — domain: $domain, room: $roomName")
        _error.value = AppError("JOIN_TIMEOUT", StrClassroom.joinTimedOut, retryable = true)
    }

    /** Called when the conference actually joins: a timeout banner shown earlier is now wrong. */
    fun clearJoinTimeoutError() {
        if (_error.value?.code == "JOIN_TIMEOUT") _error.value = null
    }

    private var channel: RealtimeChannel? = null
    private var leftAlready = false
    private var sawSelfRow = false

    private var connectJob: Job? = null

    /** Is the Realtime websocket up? Raw signal — never rendered directly, see [watchConnection]. */
    private val transportUp = MutableStateFlow(true)

    /** Does the device have a network? Raw signal, pushed in by the Activity. */
    private val networkUp = MutableStateFlow(true)

    /**
     * Live Voice Translation (owner-controlled, off unless the owner enabled it — see
     * [LiveVoiceTranslator.init]). While [translationRouting] is true the microphone belongs to
     * the translator: Jitsi's own mic is held muted and the mic button opens/closes the
     * translator's capture instead. When the feature is off none of this runs, so the call
     * behaves exactly as before.
     */
    val translation = LiveVoiceTranslator(sessionId, viewModelScope)
    private var translationRouting = false

    init {
        watchConnection()
        connect()
        watchTransport()
        startHeartbeat()
        startReconcile()
        viewModelScope.launch { translation.init() }
        watchTranslationLink()
    }

    /**
     * If the translation server becomes unreachable while it is carrying my voice, give the
     * microphone straight back to the call instead of leaving me silently muted.
     */
    private fun watchTranslationLink() {
        viewModelScope.launch {
            translation.state.collectLatest { t ->
                if (!translationRouting || t.status == TranslationStatus.LIVE) return@collectLatest
                if (t.status == TranslationStatus.CONNECTING) delay(4_000)
                if (translationRouting) setTranslationSpeaking(false)
            }
        }
    }

    fun setTranslationLanguage(code: String) = translation.setLanguage(code)

    /** Turns "translate my voice" on/off inside the meeting. */
    fun toggleTranslationSpeaking() = setTranslationSpeaking(!translationRouting)

    private fun setTranslationSpeaking(on: Boolean) {
        if (on == translationRouting) return
        if (on && translation.state.value.status != TranslationStatus.LIVE) return
        if (on && _state.value.myMicLocked) {
            _error.value = AppError("MIC_LOCKED_BY_TEACHER", StrClassroom.micLockedByTeacher, retryable = false)
            return
        }
        if (on) {
            // Flag first: the AUDIO_MUTED_CHANGED broadcast our own mute triggers must not be
            // mistaken for the user muting themselves.
            translationRouting = true
            jitsiCommands?.setAudioMuted(true)
            translation.setSpeaking(true)
            translation.setMicOpen(!_state.value.micMuted)
        } else {
            translation.setMicOpen(false)
            translation.setSpeaking(false)
            translationRouting = false
            // Hand the microphone back to the call in whatever state the mic button shows.
            jitsiCommands?.setAudioMuted(_state.value.micMuted)
        }
    }

    /**
     * Turns the two raw signals into the one thing the screen shows — and only says something is
     * wrong when something is actually, persistently wrong.
     *
     * The old behaviour painted a red "weak connection" line the instant either signal dipped.
     * Both dip constantly and harmlessly: the websocket reports DISCONNECTED before its very
     * first connect and for the moment of every routine reconnect, and the OS reports capability
     * changes whenever Wi-Fi and mobile data hand over. So the banner was up during normal
     * lessons, which taught everyone to ignore it — and, worse, nothing was actually done about
     * a socket that stayed down.
     *
     * Now: recovery shows immediately, trouble has to last [SOCKET_GRACE_MS] (or
     * [NETWORK_GRACE_MS] with no network at all) before it is worth interrupting a lesson over,
     * and while it lasts the channel is genuinely rebuilt on a timer instead of just being
     * reported. collectLatest is what makes the grace period free: any recovery cancels the
     * pending delay before it can publish anything.
     */
    private fun watchConnection() {
        viewModelScope.launch {
            combine(transportUp, networkUp) { transport, network -> transport to network }
                .collectLatest { (transport, network) ->
                    if (transport && network) {
                        _state.value = _state.value.copy(connection = ConnectionState.LIVE)
                        return@collectLatest
                    }
                    delay(if (network) SOCKET_GRACE_MS else NETWORK_GRACE_MS)
                    _state.value = _state.value.copy(
                        connection = if (network) ConnectionState.RECONNECTING else ConnectionState.OFFLINE,
                    )
                    // With a network but no socket, rebuilding the channel is the actual repair —
                    // supabase-kt's own retry can stay wedged after a long background stretch.
                    // This loop is cancelled the moment either signal changes.
                    while (network) {
                        delay(RECONNECT_RETRY_MS)
                        connect()
                    }
                }
        }
    }

    /** Called by the Activity's network callback, in both directions. */
    fun setNetworkOnline(online: Boolean) {
        networkUp.value = online
    }

    /**
     * Called by the Activity the moment it actually comes back to the foreground after having
     * been stopped (see [ClassroomCallActivity.onResume]) — not on every onResume, only the ones
     * that follow a real background stretch.
     *
     * Every self-healing path elsewhere in this class is deliberately passive: it waits for
     * [Backend.realtime]'s own status flow to report a drop, or for [SOCKET_GRACE_MS] /
     * [RECONNECT_RETRY_MS] to elapse. That is the right default while the screen is on screen —
     * it avoids rebuilding a channel that is actually fine — but it means a device that sat
     * backgrounded through a Doze window (network frozen without the socket ever cleanly
     * closing, so [Backend.realtime]'s status can still read CONNECTED on a socket that has not
     * carried data in minutes) only recovers once a heartbeat ping finally times out, which can
     * take up to half a minute after the person is already looking at the screen again.
     *
     * Coming back to the app is exactly the moment someone is watching and expects things to be
     * current, so this short-circuits every one of those timers: rebuild the Realtime channel
     * unconditionally (cheap, and connect() already tears the old one down cleanly first) and
     * re-fetch chat/participants/whiteboard/session/presence immediately rather than waiting for
     * the periodic reconcile pass to get around to it.
     */
    fun refreshOnForeground() {
        connect()
        resync()
    }

    /**
     * The safety net under both live paths.
     *
     * Broadcast and Postgres Changes both travel over one websocket; if it is wedged, dropped by
     * a carrier, or the subscription silently failed to bind, a shared image or a message can sit
     * invisible on the other side indefinitely — which is exactly the failure people report, and
     * exactly the failure that is invisible to the sender. So the two things that must never be
     * missed inside a call, the transcript and what is on the shared board, are also re-read on a
     * timer. Slow while the socket is healthy (it should find nothing), fast while it is not.
     *
     * Both reads are small, indexed and idempotent: chat merges by id, the board is a single row.
     */
    private fun startReconcile() {
        viewModelScope.launch {
            while (true) {
                val healthy = _state.value.connection == ConnectionState.LIVE
                delay(if (healthy) 10_000 else 3_000)
                runCatching {
                    loadChatHistory()
                    refreshBoardBackground(bumpRevision = true)
                    // Strokes had no such safety net before: chat and the shared board both
                    // self-heal here if a Realtime binding silently failed, but a stroke drawn
                    // during that same gap had nothing bringing it back — it just never reached
                    // the other side, with nothing on the sender's end to say so either.
                    _state.value.whiteboardBoardId?.let { boardId ->
                        applyStrokeHistory(ClassroomRepository.strokeHistory(boardId))
                    }
                }.onFailure { Log.w(TAG, "reconcile pass failed: ${it.message}") }
            }
        }
    }

    /**
     * Opens the session's Realtime channel and wires every live stream.
     *
     * Order matters and is the whole fix for "Realtime does nothing until I refresh":
     * supabase-kt registers each `postgresChangeFlow` as a binding inside the channel's JOIN
     * payload, so a flow created after `subscribe()` is never registered server-side (3.x
     * throws outright from inside the collector). This previously subscribed first and built
     * the flows afterwards, so chat, whiteboard/shared material and participant updates never
     * arrived in either direction. Now: create the channel, create every flow, start the
     * collectors, subscribe, and only then load the initial snapshot — loading the snapshot
     * last also closes the gap where a message sent between the fetch and the subscription
     * would be lost by both paths.
     */
    private fun connect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            runCatching {
                channel?.let { old -> runCatching { Backend.realtime.removeChannel(old) } }

                val ch = ClassroomRepository.openChannel(sessionId)
                channel = ch

                // 1. Every flow is created BEFORE the channel is joined.
                val chatFlow = ClassroomRepository.chatChangeFlow(ch, sessionId)
                val participantsFlow = ClassroomRepository.participantsChangeFlow(ch, sessionId)
                val strokeFlow = ClassroomRepository.strokeChangeFlow(ch, sessionId)
                val sessionFlow = ClassroomRepository.sessionChangeFlow(ch, sessionId)
                val boardFlow = ClassroomRepository.boardChangeFlow(ch, sessionId)

                // Broadcast: the instant path. Same create-before-subscribe rule applies.
                val liveChat = ClassroomRepository.liveFlow(ch, ClassroomRepository.Live.CHAT)
                val liveShare = ClassroomRepository.liveFlow(ch, ClassroomRepository.Live.SHARE)
                val liveStroke = ClassroomRepository.liveFlow(ch, ClassroomRepository.Live.STROKE)
                val liveScreen = ClassroomRepository.liveFlow(ch, ClassroomRepository.Live.SCREEN_SHARE)

                // 2. Each stream collects in its own guarded launch: one unexpected payload
                //    kills that stream only, never the whole call screen.
                launch {
                    safeCollect("chat") {
                        chatFlow.collect { msg -> mergeChat(listOf(msg)) }
                    }
                }
                launch {
                    safeCollect("participants") {
                        participantsFlow.collect { updated ->
                            val current = _state.value
                            // Realtime payloads contain the changed participant row but not the
                            // profile embed used by the initial SELECT. Keep that profile when
                            // replacing an invited row; otherwise a join event can arrive with
                            // a blank name/avatar and look as if nobody joined.
                            val previous = current.participants.firstOrNull { it.id == updated.id }
                            val enriched = if (updated.profile == null && previous?.profile != null) {
                                updated.copy(profile = previous.profile)
                            } else {
                                updated
                            }
                            val merged = current.participants
                                .filter { it.id != enriched.id && it.userId != enriched.userId } + enriched
                            _state.value = current.copy(participants = merged)
                            clearStaleRemoteShare()
                            refreshHands()
                            applyMyLockState(updated)
                            if (updated.userId == Backend.currentUserId && updated.status == "KICKED") {
                                _state.value = _state.value.copy(removed = true)
                            }
                            refreshPresence()
                        }
                    }
                }
                launch {
                    safeCollect("whiteboard strokes") {
                        strokeFlow.collect { row ->
                            val current = _state.value
                            _state.value = when (row.action) {
                                // A CLEAR row means the drawing is gone and may also mean new
                                // material was just shared; the board stream below carries the
                                // background itself, so this only has to reset the strokes.
                                "CLEAR" -> current.copy(strokes = emptyList())
                                else ->
                                    if (current.strokes.any { it.id == row.id }) current
                                    else current.copy(
                                        // The confirmed row (positive id) has now landed, so any
                                        // temporary local echo of it (negative id, same user —
                                        // see pushStroke's optimistic update) is dropped in favor
                                        // of it, instead of drawing the same stroke twice.
                                        strokes = dropOptimisticEcho(current.strokes, row) + row,
                                    )
                            }
                        }
                    }
                }
                launch {
                    safeCollect("shared material") {
                        // Material shared by ANYONE in the room — teacher or student — arrives
                        // here directly from the board row instead of being inferred from a
                        // side-effect stroke.
                        boardFlow.collect { board -> applyBoard(board, bumpRevision = true) }
                    }
                }
                launch {
                    safeCollect("session state") {
                        sessionFlow.collect { session ->
                            if (isEndedStatus(session.status)) {
                                _state.value = _state.value.copy(ended = true)
                            }
                        }
                    }
                }

                launch {
                    safeCollect("live chat") {
                        liveChat.collect { payload ->
                            runCatching {
                                Backend.json.decodeFromJsonElement(ClassroomChatMessage.serializer(), payload)
                            }.onSuccess { mergeChat(listOf(it)) }
                        }
                    }
                }
                launch {
                    safeCollect("live share") {
                        // The shared file itself is already stored; this only says "look now".
                        // Re-reading the board rather than trusting the payload keeps one source
                        // of truth for what is on screen.
                        liveShare.collect { refreshBoardBackground(bumpRevision = true) }
                    }
                }
                launch {
                    safeCollect("live strokes") {
                        liveStroke.collect { loadWhiteboard() }
                    }
                }
                launch {
                    safeCollect("live screen share") {
                        liveScreen.collect { payload ->
                            val on = payload["on"]?.jsonPrimitive?.booleanOrNull ?: false
                            val by = payload["by"]?.jsonPrimitive?.contentOrNull
                            val me = Backend.currentUserId
                            // Only a *remote* share changes what this device shows; our own is
                            // already reflected by the local Jitsi event.
                            if (by != null && by != me) {
                                _state.value = _state.value.copy(remoteScreenShareBy = if (on) by else null)
                            }
                        }
                    }
                }

                // 3. Join the channel now that every binding exists.
                ClassroomRepository.subscribeChannel(ch)
                transportUp.value = true

                // 4. Snapshot last, so nothing that happens during setup is missed.
                launch { loadChatHistory() }
                launch { loadParticipants() }
                launch { loadWhiteboard() }
                launch { loadInitialSession() }
                launch { refreshPresence() }
            }.onFailure {
                Log.e(TAG, "Failed to open classroom realtime channel for session=$sessionId", it)
                // Reported as a transport signal, not as an error dialog: watchConnection decides
                // whether this is a blip worth nobody's attention or a real outage, and the retry
                // loop there is what fixes it. Throwing an error banner in the teacher's face for
                // every failed channel open was noise on top of a problem the app can repair by
                // itself.
                transportUp.value = false
            }
        }
    }

    /**
     * Mirrors the websocket's own state into [CallUiState.connection] and re-syncs after a
     * reconnect. A dropped socket used to leave the UI claiming LIVE while nothing arrived;
     * worse, the events missed while offline were never refetched, so the room stayed silently
     * stale until the screen was reopened.
     */
    private fun watchTransport() {
        viewModelScope.launch {
            Backend.realtime.status.collect { status ->
                val connected = status.name == "CONNECTED"
                val wasDown = !transportUp.value
                transportUp.value = connected
                // Refetch only on a real down→up edge, so a lesson doesn't re-read its whole
                // transcript every time the flow re-emits the state it was already in.
                if (connected && wasDown) resync()
            }
        }
    }

    /** Refetches everything the live streams would have delivered while the socket was down. */
    private fun resync() {
        viewModelScope.launch {
            loadChatHistory()
            loadParticipants()
            loadWhiteboard()
            loadInitialSession()
            refreshPresence()
        }
    }

    /**
     * Proves to the server that this client is still in the room, every 20 seconds, and pulls
     * back who else is. This is what makes "are the invited students actually connected?"
     * answerable: a row is only reported online while its heartbeat is fresh.
     */
    private fun startHeartbeat() {
        viewModelScope.launch {
            while (!leftAlready) {
                runCatching { ClassroomRepository.heartbeat(sessionId) }
                    .onFailure { Log.w(TAG, "heartbeat failed for session=$sessionId: ${it.message}") }
                if (leftAlready) break
                refreshPresence()
                delay(20_000)
            }
        }
    }

    private suspend fun refreshPresence() {
        runCatching { ClassroomRepository.presence(sessionId) }
            .onSuccess {
                _state.value = _state.value.copy(presence = it)
                // Presence is also the recovery path for joins that happened while the
                // Realtime socket was reconnecting. Refresh the participant snapshot so a
                // newly JOINED row is visible even when its UPDATE event was missed.
                loadParticipants()
            }
            .onFailure { Log.w(TAG, "presence refresh failed for session=$sessionId: ${it.message}") }
    }

    /**
     * Adds messages to the transcript, de-duplicated by id and kept in server order. The sender
     * echoes their own message locally the moment the insert returns *and* receives it again
     * over Realtime; without de-duplication that is the same message twice.
     */
    private fun mergeChat(incoming: List<ClassroomChatMessage>) {
        if (incoming.isEmpty()) return
        val current = _state.value
        val byId = LinkedHashMap<Long, ClassroomChatMessage>()
        (current.chat + incoming).forEach { msg ->
            val existing = byId[msg.id]
            // Realtime payloads carry no embedded profile (the join is a PostgREST feature),
            // so keep whichever copy actually has the sender's name/avatar.
            byId[msg.id] = if (existing?.profile != null && msg.profile == null) existing else msg
        }
        val ordered = byId.values.sortedBy { it.id }
        _state.value = current.copy(
            chat = ordered,
            questions = ordered.filter { it.isQuestion },
        )
    }

    private fun applyBoard(board: com.rork.pro.data.ClassroomWhiteboardBoard, bumpRevision: Boolean) {
        val current = _state.value
        val changed = board.backgroundUrl != current.whiteboardBackgroundUrl ||
            board.backgroundType != current.whiteboardBackgroundType
        _state.value = current.copy(
            whiteboardBoardId = board.id,
            whiteboardBackgroundUrl = board.backgroundUrl,
            whiteboardBackgroundType = board.backgroundType,
            sharedMediaRevision = if (bumpRevision && changed && board.backgroundType != "NONE") {
                current.sharedMediaRevision + 1
            } else {
                current.sharedMediaRevision
            },
        )
    }

    /**
     * Runs a Realtime `.collect` loop and converts any exception it throws — from the flow
     * itself or from the collector body — into a logged, user-visible [AppError] instead of
     * letting it propagate out of this coroutine. A `viewModelScope.launch { flow.collect {} }`
     * with no guard is a live crash risk: one unexpected/malformed payload on any one stream
     * (chat, participants, strokes, session) throws straight past this class's own `init`
     * `runCatching` (which only covers synchronous setup, not exceptions thrown later inside a
     * child coroutine) and would take down the whole call screen. `label` identifies which
     * stream failed, both in the log and in a debounced-free but harmless repeated error banner
     * if it keeps failing.
     */
    private suspend fun safeCollect(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Realtime stream '$label' failed for session=$sessionId", t)
            _error.value = t.toAppError()
        }
    }

    private suspend fun loadChatHistory() {
        runCatching { ClassroomRepository.chatHistory(sessionId) }
            .onSuccess {
                mergeChat(it)
                if (!_state.value.chatHistoryLoaded) _state.value = _state.value.copy(chatHistoryLoaded = true)
            }
            .onFailure {
                Log.e(TAG, "Failed to load chat history for session=$sessionId", it)
                _error.value = it.toAppError()
            }
    }

    private suspend fun loadParticipants() {
        runCatching { ClassroomRepository.participants(sessionId) }
            .onSuccess { list ->
                _state.value = _state.value.copy(participants = list)
                clearStaleRemoteShare()
                refreshHands()
                val mine = list.firstOrNull { it.userId == Backend.currentUserId }
                mine?.let { applyMyLockState(it) }
                if (mine != null) sawSelfRow = true
                // A removed student loses read access to the class, so their own row disappears
                // from a successful fetch (or shows KICKED) — either way the call must close.
                if (!info.isModerator && sawSelfRow && (mine == null || mine.status == "KICKED")) {
                    _state.value = _state.value.copy(removed = true)
                }
            }
            .onFailure {
                Log.e(TAG, "Failed to load participants for session=$sessionId", it)
                _error.value = it.toAppError()
            }
    }

    /**
     * Merges a freshly-fetched stroke history into state without discarding a stroke that was
     * just drawn locally and hasn't round-tripped back yet (negative id — see [pushStroke]'s
     * optimistic update). A plain overwrite here is exactly the same self-erasing symptom for a
     * second reason: this function runs both right after every local push (via the `liveStroke`
     * broadcast triggering [loadWhiteboard]) and on [startReconcile]'s periodic timer, and a
     * fetch that lands before the just-inserted row is visible server-side would otherwise wipe
     * it back out.
     */
    private fun applyStrokeHistory(history: List<ClassroomStrokeRow>) {
        // A pending local stroke whose confirmed row is already in this history is dropped, so it
        // is never drawn twice; the rest stay until their own row arrives.
        val pending = _state.value.strokes.filter { p ->
            p.id < 0 && history.none { h -> h.userId == p.userId && h.stroke == p.stroke }
        }
        _state.value = _state.value.copy(strokes = history + pending)
    }

    /**
     * Removes the ONE temporary local copy that [row] confirms. Dropping every pending stroke of
     * that user (the old rule) made the second and third of several quick strokes vanish for a
     * moment until their own rows landed — a visible flicker while writing on the board.
     */
    private fun dropOptimisticEcho(strokes: List<ClassroomStrokeRow>, row: ClassroomStrokeRow): List<ClassroomStrokeRow> {
        val idx = strokes.indexOfFirst { it.id < 0 && it.userId == row.userId && it.stroke == row.stroke }
            .takeIf { it >= 0 }
            ?: strokes.indexOfFirst { it.id < 0 && it.userId == row.userId }
        if (idx < 0) return strokes
        return strokes.toMutableList().apply { removeAt(idx) }
    }

    private suspend fun loadWhiteboard() {
        runCatching {
            val boardId = ClassroomRepository.ensureWhiteboard(sessionId)
            _state.value = _state.value.copy(whiteboardBoardId = boardId)
            applyStrokeHistory(ClassroomRepository.strokeHistory(boardId))
            refreshBoardBackground()
        }.onFailure {
            Log.e(TAG, "Failed to initialize whiteboard for session=$sessionId", it)
            _error.value = it.toAppError()
        }
    }

    private suspend fun loadInitialSession() {
        runCatching { ClassroomRepository.session(sessionId) }
            .onSuccess { session ->
                // Without this a class that ended while this screen was still opening (or while
                // the socket was down) was never noticed: the Realtime UPDATE had already gone by.
                if (isEndedStatus(session.status)) _state.value = _state.value.copy(ended = true)
            }
            .onFailure {
                // Non-fatal: the session-level Realtime stream above will still keep this in
                // sync going forward, so this is worth logging but not worth interrupting the
                // call with a banner over.
                Log.w(TAG, "Failed to load initial session state for session=$sessionId: ${it.message}")
            }
    }

    /**
     * The "someone is presenting" flag only ever arrives as a live broadcast. If the presenter's
     * app died or they left without their "sharing stopped" notice getting out, everyone else was
     * stuck with the presenting banner and the whiteboard/strokes pushed aside for the rest of the
     * class. Once the presenter is no longer in the room, the flag goes too.
     */
    private fun clearStaleRemoteShare() {
        val by = _state.value.remoteScreenShareBy ?: return
        val stillHere = _state.value.participants.any { it.userId == by && it.status == "JOINED" }
        if (!stillHere) _state.value = _state.value.copy(remoteScreenShareBy = null)
    }

    private fun refreshHands() {
        val current = _state.value
        _state.value = current.copy(
            raisedHands = current.participants.filter { it.handRaised },
            myHandRaised = current.participants.any { it.userId == Backend.currentUserId && it.handRaised },
        )
    }

    /** When *my own* participant row changes, enforce a newly-set lock immediately. */
    private fun applyMyLockState(updated: ClassroomParticipant) {
        if (updated.userId != Backend.currentUserId) return
        val wasMicLocked = _state.value.myMicLocked
        _state.value = _state.value.copy(myMicLocked = updated.micLocked, myCameraLocked = updated.cameraLocked)
        if (updated.micLocked && !wasMicLocked) {
            // Force the mic off the instant a lock lands, regardless of what the student's
            // local toggle currently shows — a lock always wins over a pending unmute tap.
            setMicMuted(true, fromTeacherLock = true)
        }
        if (updated.cameraLocked) {
            setCameraMuted(true, fromTeacherLock = true)
        }
    }

    private suspend fun refreshBoardBackground(bumpRevision: Boolean = false) {
        runCatching { ClassroomRepository.board(sessionId) }
            .onSuccess { board -> applyBoard(board, bumpRevision) }
            .onFailure { Log.e(TAG, "Failed to refresh whiteboard background for session=$sessionId", it) }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Surfaces a failure that happened in the Compose layer — reading/uploading a picked file
     * for the share button, specifically, which needs a `Context` this ViewModel deliberately
     * doesn't hold — through the same dismissible banner every other in-call failure uses,
     * rather than that flow needing its own separate, inconsistent error surface.
     */
    fun reportUploadError(t: Throwable) {
        Log.e(TAG, "Failed to upload/share classroom media for session=$sessionId", t)
        _error.value = t.toAppError()
    }

    fun sendChat(body: String, isQuestion: Boolean) {
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching { ClassroomRepository.sendChat(sessionId, body.trim(), isQuestion) }
                .onSuccess { sent ->
                    // Instant local echo. The Realtime copy of this same row arrives moments
                    // later and is folded in by id, not appended twice.
                    mergeChat(listOf(sent))
                    // ...and pushed straight to everyone else on the channel, so delivery does
                    // not wait on (or depend on) the database change stream.
                    channel?.let { ch ->
                        runCatching {
                            ClassroomRepository.publish(
                                ch,
                                ClassroomRepository.Live.CHAT,
                                Backend.json.encodeToJsonElement(ClassroomChatMessage.serializer(), sent).jsonObject,
                            )
                        }
                    }
                }
                .onFailure {
                    Log.e(TAG, "Failed to send chat message for session=$sessionId", it)
                    _error.value = it.toAppError()
                }
        }
    }

    fun markAnswered(id: Long) {
        viewModelScope.launch {
            runCatching { ClassroomRepository.markAnswered(id) }
                .onFailure {
                    Log.e(TAG, "Failed to mark question answered id=$id", it)
                    _error.value = it.toAppError()
                }
        }
    }

    fun toggleHand() {
        viewModelScope.launch {
            runCatching {
                if (_state.value.myHandRaised) ClassroomRepository.lowerHand(sessionId) else ClassroomRepository.raiseHand(sessionId)
            }.onFailure {
                Log.e(TAG, "Failed to toggle hand-raise for session=$sessionId", it)
                _error.value = it.toAppError()
            }
        }
    }

    fun lowerHandOf(userId: String) {
        viewModelScope.launch {
            runCatching { ClassroomRepository.lowerHand(sessionId, userId) }
                .onFailure {
                    Log.e(TAG, "Failed to lower hand for user=$userId", it)
                    _error.value = it.toAppError()
                }
        }
    }

    // Client-side ids for strokes drawn locally, shown immediately below before the real
    // Postgres row (a positive id) round-trips back — always negative so they can never collide
    // with a real row's id.
    private var nextLocalStrokeId = -1L

    /**
     * Sends a stroke (or a CLEAR) to the room.
     *
     * For a plain stroke this now also renders it locally the instant it's drawn, with a
     * temporary negative id, instead of waiting for [ClassroomRepository.pushStroke]'s insert to
     * round-trip back over Realtime before the person who just drew it can see their own line.
     * That round trip is exactly what [onDragEnd] in [WhiteboardCanvas] finishes right before —
     * `currentPoints` is cleared the moment [onStroke] is called — so without this the stroke
     * had nowhere left to render from until Realtime caught up: it visibly erased itself for the
     * person drawing it, and if that round trip was ever lost (not just slow), it also never
     * reached anyone else in the room. [setWhiteboardBackground] below already uses the same
     * optimistic-update pattern for exactly this reason.
     */
    fun pushStroke(action: String, stroke: JsonObject?) {
        val boardId = _state.value.whiteboardBoardId ?: return
        var optimisticId: Long? = null
        if (action == "CLEAR") {
            _state.value = _state.value.copy(strokes = emptyList())
        } else {
            optimisticId = nextLocalStrokeId
            val optimistic = ClassroomStrokeRow(
                id = nextLocalStrokeId--,
                boardId = boardId,
                sessionId = sessionId,
                userId = Backend.currentUserId.orEmpty(),
                action = action,
                stroke = stroke,
            )
            _state.value = _state.value.copy(strokes = _state.value.strokes + optimistic)
        }
        viewModelScope.launch {
            runCatching { ClassroomRepository.pushStroke(boardId, sessionId, action, stroke) }
                .onSuccess { announce(ClassroomRepository.Live.STROKE, buildJsonObject { put("action", action) }) }
                .onFailure {
                    Log.e(TAG, "Failed to push whiteboard stroke (action=$action) for board=$boardId", it)
                    // The stroke never reached the room — don't leave it drawn on this screen
                    // only, looking as if everyone can see it.
                    optimisticId?.let { id ->
                        _state.value = _state.value.copy(strokes = _state.value.strokes.filterNot { s -> s.id == id })
                    }
                    _error.value = it.toAppError()
                }
        }
    }

    /**
     * Shares a background — image, video, or PDF — with the whole room.
     *
     * The local state updates immediately, before the RPC even starts: without this, the person
     * who just shared something would see their own whiteboard sit empty until their own change
     * round-tripped back through Realtime (the same signal every *other* participant relies on),
     * which is not guaranteed to be instant. If the write genuinely fails, the optimistic value
     * is rolled back to whatever was showing before, so a failed share never leaves the sender
     * looking at material nobody else in the room actually received.
     */
    fun setWhiteboardBackground(url: String, type: String) {
        val previous = _state.value
        _state.value = previous.copy(whiteboardBackgroundUrl = url, whiteboardBackgroundType = type)
        viewModelScope.launch {
            runCatching { ClassroomRepository.setWhiteboardBackground(sessionId, url, type) }
                .onSuccess {
                    // Tells the room to look now. Everyone re-reads the board for themselves,
                    // so what they render is the stored row, not this message's contents.
                    announce(
                        ClassroomRepository.Live.SHARE,
                        buildJsonObject {
                            put("type", type)
                            put("by", Backend.currentUserId ?: "")
                        },
                    )
                }
                .onFailure {
                    Log.e(TAG, "Failed to set whiteboard background for session=$sessionId", it)
                    _state.value = _state.value.copy(
                        whiteboardBackgroundUrl = previous.whiteboardBackgroundUrl,
                        whiteboardBackgroundType = previous.whiteboardBackgroundType,
                    )
                    _error.value = it.toAppError()
                }
        }
    }

    /** Teacher: end the current photo / video / file presentation for everyone. */
    fun stopSharedMedia() {
        val previous = _state.value
        _state.value = previous.copy(whiteboardBackgroundUrl = null, whiteboardBackgroundType = "NONE")
        viewModelScope.launch {
            runCatching { ClassroomRepository.setWhiteboardBackground(sessionId, null, "NONE") }
                .onSuccess {
                    announce(
                        ClassroomRepository.Live.SHARE,
                        buildJsonObject {
                            put("type", "NONE")
                            put("by", Backend.currentUserId ?: "")
                        },
                    )
                }
                .onFailure {
                    Log.e(TAG, "Failed to stop shared media for session=$sessionId", it)
                    _state.value = _state.value.copy(
                        whiteboardBackgroundUrl = previous.whiteboardBackgroundUrl,
                        whiteboardBackgroundType = previous.whiteboardBackgroundType,
                    )
                    _error.value = it.toAppError()
                }
        }
    }

    // ---------------------------------------------------------------- mic / camera / screen share

    /** Self mute/unmute. A no-op (with a surfaced error) if the teacher currently has it locked. */
    fun toggleMic() {
        if (_state.value.myMicLocked) {
            // A plain "you don't have permission" here would leave a student genuinely
            // confused about why the button does nothing — the dedicated string names the
            // actual reason (the teacher locked it) instead of a generic permission error.
            _error.value = AppError("MIC_LOCKED_BY_TEACHER", StrClassroom.micLockedByTeacher, retryable = false)
            return
        }
        setMicMuted(!_state.value.micMuted, fromTeacherLock = false)
    }

    fun toggleCamera() {
        if (_state.value.myCameraLocked) {
            _error.value = AppError("CAMERA_LOCKED_BY_TEACHER", StrClassroom.cameraLockedByTeacher, retryable = false)
            return
        }
        setCameraMuted(!_state.value.cameraMuted, fromTeacherLock = false)
    }

    private fun setMicMuted(muted: Boolean, fromTeacherLock: Boolean) {
        if (translationRouting) {
            // The translator owns the mic: keep Jitsi's muted and open/close the translator's.
            jitsiCommands?.setAudioMuted(true)
            translation.setMicOpen(!muted)
            _state.value = _state.value.copy(micMuted = muted)
            return
        }
        val sent = jitsiCommands?.setAudioMuted(muted) ?: false
        if (!sent && !fromTeacherLock) {
            Log.w(TAG, "setMicMuted($muted) requested but no JitsiCommands attached yet")
            return
        }
        _state.value = _state.value.copy(micMuted = muted)
    }

    private fun setCameraMuted(muted: Boolean, fromTeacherLock: Boolean) {
        val sent = jitsiCommands?.setVideoMuted(muted) ?: false
        if (!sent && !fromTeacherLock) {
            Log.w(TAG, "setCameraMuted($muted) requested but no JitsiCommands attached yet")
            return
        }
        _state.value = _state.value.copy(cameraMuted = muted)
    }

    /** Called by the Activity when Jitsi itself reports a mute state change (e.g. the user muted
     *  via a gesture/hardware control outside our own bottom bar), so our UI never drifts out of
     *  sync with what is actually happening on the call. */
    fun onJitsiAudioMutedChanged(muted: Boolean) {
        if (translationRouting) {
            // Jitsi's mic must stay muted while translation carries the voice.
            if (!muted) jitsiCommands?.setAudioMuted(true)
            return
        }
        _state.value = _state.value.copy(micMuted = muted)
    }

    /**
     * Called once Jitsi reports CONFERENCE_JOINED (first join and every automatic rejoin).
     * Pushes what the mic AND camera buttons show to the live conference so neither can ever
     * disagree with what the other side actually receives after a join. This matters most on
     * an automatic rejoin (see ClassroomCallActivity's CONFERENCE_TERMINATED handling): every
     * join() — including a silent reconnect after a network blip — starts the conference with
     * the camera forced off (ClassroomCallActivity.startJitsiConference's unconditional
     * setVideoMuted(true)), regardless of whether this person had their camera on a moment
     * ago. Without this push, a camera the user had on before the drop would come back off on
     * the live conference while our own button kept showing "on" — the other side seeing no
     * video with no visible reason and no way to know they needed to retoggle it. Mic mute is
     * skipped while translation owns it (Jitsi's must stay muted then); camera has no such
     * routing, so it is always pushed.
     */
    fun onConferenceJoined() {
        if (!translationRouting) {
            val micMuted = _state.value.micMuted || _state.value.myMicLocked
            jitsiCommands?.setAudioMuted(micMuted)
        }
        val cameraMuted = _state.value.cameraMuted || _state.value.myCameraLocked
        jitsiCommands?.setVideoMuted(cameraMuted)
    }

    fun onJitsiVideoMutedChanged(muted: Boolean) {
        _state.value = _state.value.copy(cameraMuted = muted)
    }

    fun onJitsiScreenShareChanged(sharing: Boolean) {
        _state.value = _state.value.copy(screenSharing = sharing)
        // Jitsi tells each device only about its own screen share, so the room is told here.
        // Without this a student had no way to know a presentation had started — and no reason
        // to close whatever overlay was sitting in front of it.
        announce(
            ClassroomRepository.Live.SCREEN_SHARE,
            buildJsonObject {
                put("on", sharing)
                put("by", Backend.currentUserId ?: "")
            },
        )
    }

    /**
     * Sends a live notice to everyone else on the channel. Best-effort by design: the database
     * write it accompanies has already succeeded and is what everyone reconciles against, so a
     * failed announce costs latency, never correctness.
     */
    private fun announce(event: String, payload: JsonObject) {
        val ch = channel ?: return
        viewModelScope.launch {
            runCatching { ClassroomRepository.publish(ch, event, payload) }
                .onFailure { Log.w(TAG, "live announce '$event' failed: ${it.message}") }
        }
    }

    fun toggleScreenShare() {
        val next = !_state.value.screenSharing
        val sent = jitsiCommands?.toggleScreenShare(next) ?: false
        if (!sent) {
            _error.value = AppError("SCREEN_SHARE_UNAVAILABLE", ErrorText.unknown, retryable = true)
            return
        }
        // Starting is NOT flipped optimistically: the system's screen-capture prompt can still be
        // declined, and then no "sharing" event ever arrives — the green "your screen is being
        // shared" banner used to stay up for a share that never started. The real state comes
        // from onJitsiScreenShareChanged. Stopping is safe to show immediately.
        if (!next) _state.value = _state.value.copy(screenSharing = false)
    }

    // ---------------------------------------------------------------- teacher-only controls

    /** Teacher/staff action: locks or unlocks one student's mic and/or camera. */
    fun setParticipantMedia(userId: String, micLocked: Boolean? = null, cameraLocked: Boolean? = null) {
        viewModelScope.launch {
            runCatching { ClassroomRepository.setParticipantMedia(sessionId, userId, micLocked, cameraLocked) }
                .onFailure {
                    Log.e(TAG, "Failed to set participant media for user=$userId", it)
                    _error.value = it.toAppError()
                }
        }
    }

    /** Teacher: mute (lock) or release every student's mic in one tap. */
    fun muteAllStudents(locked: Boolean) {
        viewModelScope.launch {
            val ok = runCatching { ClassroomRepository.setStudentsMicLocked(sessionId, locked) }
                .onFailure {
                    Log.e(TAG, "Failed to ${if (locked) "mute" else "unmute"} all students", it)
                    _error.value = it.toAppError()
                }.isSuccess
            if (ok) loadParticipants()
        }
    }

    /** Teacher: remove a student from the class. Their device closes the call and they cannot rejoin. */
    fun removeStudent(userId: String) {
        viewModelScope.launch {
            val ok = runCatching { ClassroomRepository.removeParticipant(sessionId, userId) }
                .onFailure {
                    Log.e(TAG, "Failed to remove user=$userId", it)
                    _error.value = it.toAppError()
                }.isSuccess
            if (ok) loadParticipants()
        }
    }

    /** Applies the camera state the call will start with, before the first frame is drawn. */
    fun setInitialCameraMuted(muted: Boolean) {
        _state.value = _state.value.copy(cameraMuted = muted)
    }

    /** Teacher/staff action: ends the session for every participant. */
    fun endForEveryone(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { ClassroomRepository.endSession(sessionId) }
                .onFailure { Log.e(TAG, "Failed to end session=$sessionId for everyone", it) }
            // An intentional moderator leave also closes the class server-side, so even if the
            // call above failed the session is still ended by this.
            leave(intentional = true)
            onDone()
        }
    }

    /**
     * @param intentional False when this is an automatic leave — a dropped connection or the
     *   system tearing the screen down in the background — rather than the user actually
     *   choosing to leave. A moderator's automatic leave records their own row as LEFT but must
     *   never end the class for everyone else (see [ClassroomRepository.leave]).
     */
    fun leave(intentional: Boolean = true) {
        translation.release()
        if (leftAlready) return
        leftAlready = true
        // Process scope, not viewModelScope: every caller finishes the Activity right after this,
        // which clears the ViewModel and cancelled the request mid-flight — so the LEAVE was often
        // never recorded (students stayed "JOINED", a teacher's leave didn't close the class).
        Backend.appScope.launch {
            runCatching { ClassroomRepository.leave(sessionId, intentional) }
                .onFailure { Log.e(TAG, "Failed to record LEAVE for session=$sessionId", it) }
        }
    }

    /**
     * `viewModelScope` is already cancelled by the time `onCleared()` runs — androidx's
     * `ViewModel.clear()` closes every tagged Closeable (which cancels the scope's Job) and only
     * *then* calls `onCleared()`. A `viewModelScope.launch { ... }` placed here would therefore
     * build an already-cancelled coroutine whose body never executes: `leave()`'s RPC call and,
     * more importantly, the Realtime channel removal below would silently never happen.
     *
     * In practice `leave()` is almost always already sent by this point (back press, the
     * CONFERENCE_TERMINATED broadcast, and Activity.onDestroy all call it explicitly while the
     * scope is still alive — see ClassroomCallActivity), so `leftAlready` usually short-circuits
     * it here. But nothing else ever removes the Realtime channel, so without routing this
     * through a scope that outlives the ViewModel, every call leaked one open
     * `classroom-<sessionId>` channel for the rest of the process's life — rejoining the same
     * session later would then open a second channel on the same topic, which is a plausible
     * source of duplicated chat/whiteboard events and growing memory/socket usage over a long
     * app session.
     */
    override fun onCleared() {
        translation.release()
        val ch = channel
        Backend.appScope.launch {
            if (!leftAlready) {
                leftAlready = true
                // Reaching onCleared() without leftAlready already set means nobody chose to
                // leave — the system tore this screen down on its own. Never end the class for
                // everyone off the back of that.
                runCatching { ClassroomRepository.leave(sessionId, intentional = false) }
                    .onFailure { Log.e(TAG, "Failed to record LEAVE (onCleared) for session=$sessionId", it) }
            }
            ch?.let {
                runCatching { Backend.realtime.removeChannel(it) }
                    .onFailure { Log.e(TAG, "Failed to remove realtime channel for session=$sessionId", it) }
            }
        }
        super.onCleared()
    }

    class Factory(private val sessionId: String, private val info: ClassroomJoinInfo) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ClassroomCallViewModel(sessionId, info) as T
    }
}

/**
 * The Jitsi-facing side of mic/camera/screen-share, implemented in [ClassroomCallActivity] (it
 * needs a `Context` to send the SDK's broadcast commands). Kept as a narrow interface so the
 * ViewModel — which owns the actual on/off state everyone reasons about — never depends on
 * Android UI classes directly. Each method returns whether the command was actually dispatched;
 * the ViewModel treats `false` as "not ready yet" rather than assuming success.
 */
interface JitsiCommands {
    fun setAudioMuted(muted: Boolean): Boolean
    fun setVideoMuted(muted: Boolean): Boolean
    fun toggleScreenShare(enabled: Boolean): Boolean
}
