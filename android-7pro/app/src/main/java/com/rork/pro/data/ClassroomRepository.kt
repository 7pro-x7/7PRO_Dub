package com.rork.pro.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * All Virtual Classroom data access.
 *
 * Every write that has a permission decision behind it (create a session, invite a student,
 * join, raise/lower a hand, end a session...) goes through a Postgres RPC — never a raw
 * insert/update from here — because the server, not this class, is the one place authorization
 * is actually enforced (RLS + the SECURITY DEFINER functions in the migrations). This object
 * only shapes requests and decodes responses.
 */
object ClassroomRepository {

    private fun me(): String = Backend.currentUserId ?: error("UNAUTHORIZED")

    // ------------------------------------------------------------------ sessions

    /** Only these show up in any of the three list queries below — a session that has ENDED
     *  or been CANCELED drops off the home list entirely rather than lingering with a badge. */
    private val VISIBLE_STATUSES = listOf("SCHEDULED", "LIVE")

    /** Sessions where the signed-in user is teacher, staff, or an invited participant. */
    suspend fun mySessions(): List<ClassroomSession> {
        val userId = me()
        return Backend.client.from("classroom_sessions")
            .select(Columns.raw("*, teacher:profiles!classroom_sessions_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter {
                    eq("teacher_id", userId)
                    isIn("status", VISIBLE_STATUSES)
                }
                order("scheduled_start", Order.DESCENDING)
                limit(200)
            }
            .decodeList()
    }

    /**
     * Sessions a STUDENT has actually been invited to. Uses the `classroom_participants` join
     * so a student never sees a session just because it exists — only because they are on it.
     */
    suspend fun myInvitedSessions(): List<ClassroomSession> {
        val userId = me()
        val participantRows = Backend.client.from("classroom_participants")
            .select(Columns.raw("session_id")) {
                filter {
                    eq("user_id", userId)
                    neq("status", "KICKED")
                }
            }
            .decodeList<Map<String, String>>()
        val sessionIds = participantRows.mapNotNull { it["session_id"] }
        if (sessionIds.isEmpty()) return emptyList()

        return Backend.client.from("classroom_sessions")
            .select(Columns.raw("*, teacher:profiles!classroom_sessions_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter {
                    isIn("id", sessionIds)
                    isIn("status", VISIBLE_STATUSES)
                }
                order("scheduled_start", Order.DESCENDING)
            }
            .decodeList()
    }

    /**
     * The meetings that are running right now for the signed-in user — what Home shows as
     * "Join the meeting now". A teacher gets their own sessions, everyone else the ones they
     * were invited to (never a session they are not on). The filter is
     * [ClassroomSession.isInProgress], so an ended, canceled or expired session — and one that
     * is only scheduled — never comes back.
     */
    suspend fun liveNow(asTeacher: Boolean): List<ClassroomSession> {
        val now = java.time.Instant.now()
        val candidates = if (asTeacher) mySessions() else myInvitedSessions()
        return candidates
            .filter { it.isInProgress(now) }
            .sortedBy { it.startedAt ?: it.scheduledStart }
    }

    /** All sessions across every teacher — only reachable by staff with `classroom.manage`. */
    suspend fun allSessionsForStaff(): List<ClassroomSession> =
        Backend.client.from("classroom_sessions")
            .select(Columns.raw("*, teacher:profiles!classroom_sessions_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter { isIn("status", VISIBLE_STATUSES) }
                order("scheduled_start", Order.DESCENDING)
                limit(300)
            }
            .decodeList()

    suspend fun session(id: String): ClassroomSession =
        Backend.client.from("classroom_sessions")
            .select(Columns.raw("*, teacher:profiles!classroom_sessions_teacher_id_fkey(id, full_name, avatar_url)")) {
                filter { eq("id", id) }
            }
            .decodeSingle()

    suspend fun createSession(
        title: String,
        description: String,
        scheduledStart: String,
        scheduledEnd: String,
        teacherId: String? = null,
        maxParticipants: Int = 30,
        studentIds: List<String> = emptyList(),
    ): String {
        val result = Backend.rpcRaw(
            "classroom_create_session",
            buildJsonObject {
                put("p_title", title)
                put("p_description", description)
                put("p_scheduled_start", scheduledStart)
                put("p_scheduled_end", scheduledEnd)
                teacherId?.let { put("p_teacher_id", it) }
                put("p_max_participants", maxParticipants)
                putJsonArray("p_student_ids") { studentIds.forEach { add(it) } }
            },
        )
        return result.toString().trim('"')
    }

    suspend fun inviteStudents(sessionId: String, studentIds: List<String>) {
        Backend.rpcVoid(
            "classroom_invite_students",
            buildJsonObject {
                put("p_session_id", sessionId)
                putJsonArray("p_student_ids") { studentIds.forEach { add(it) } }
            },
        )
    }

    /** Teacher/staff: mute (lock) or release the mics of every student currently in the class. */
    suspend fun setStudentsMicLocked(sessionId: String, locked: Boolean) {
        Backend.rpcVoid(
            "classroom_set_students_mic_locked",
            buildJsonObject { put("p_session_id", sessionId); put("p_locked", locked) },
        )
    }

    suspend fun removeParticipant(sessionId: String, userId: String) {
        Backend.rpcVoid(
            "classroom_remove_participant",
            buildJsonObject { put("p_session_id", sessionId); put("p_user_id", userId) },
        )
    }

    suspend fun cancelSession(sessionId: String) {
        Backend.rpcVoid("classroom_cancel_session", buildJsonObject { put("p_session_id", sessionId) })
    }

    suspend fun endSession(sessionId: String) {
        Backend.rpcVoid("classroom_end_session", buildJsonObject { put("p_session_id", sessionId) })
    }

    /** The ONLY call that returns room_name/room_password — see the RPC's own comment. */
    suspend fun join(sessionId: String): ClassroomJoinInfo =
        Backend.client.postgrest.rpc(
            "classroom_join",
            buildJsonObject { put("p_session_id", sessionId) },
        ).decodeSingle()

    /**
     * The link that takes someone straight into [sessionId] in the browser — for phones below
     * Android 8, where the in-app video cannot run.
     *
     * The key comes from `classroom_browser_invite_key`, which — unlike [join] — does not mark
     * anyone as joined, so the person is never counted twice (once by account, once as the
     * browser guest). It applies the same "invited and not kicked" rule as joining.
     *
     * A teacher/moderator gets the plain page instead: the invite link would admit them as an
     * ordinary guest, so they sign in there and keep their controls.
     */
    suspend fun browserJoinUrl(sessionId: String): String {
        val key = Backend.client.postgrest.rpc(
            "classroom_browser_invite_key",
            buildJsonObject { put("p_session_id", sessionId) },
        ).decodeSingle<BrowserInviteKey>()
        return if (key.isModerator) ClassroomWeb.teacherUrl(sessionId, runCatching { Backend.client.auth.currentUserOrNull()?.email }.getOrNull()) else ClassroomWeb.inviteUrl(sessionId, key.roomPassword)
    }

    /**
     * @param intentional False for an automatic leave (a dropped connection, the system tearing
     *   the screen down while backgrounded) as opposed to the user actually choosing to leave.
     *   A moderator's automatic leave must never end the session for everyone else — see the
     *   `classroom_leave` migration this flag was added in.
     */
    suspend fun leave(sessionId: String, intentional: Boolean = true) {
        Backend.rpcVoid(
            "classroom_leave",
            buildJsonObject { put("p_session_id", sessionId); put("p_intentional", intentional) },
        )
    }

    suspend fun searchStudents(query: String): List<ClassroomStudentHit> =
        Backend.client.postgrest.rpc(
            "classroom_search_students",
            buildJsonObject { put("p_query", query) },
        ).decodeList()

    /**
     * The signed-in teacher's (or staff member's, for their own account) subscribed students,
     * grouped by class — only those with an account linked via [TeacherRepository.saveSubscription].
     * Backs the "create session" invite picker: a teacher sees their own groups/students instead
     * of searching the whole app, can invite a class in one tap, and can still uncheck individual
     * students as exceptions.
     */
    suspend fun mySubscribedStudentGroups(): List<ClassroomGroupWithStudents> {
        val rows = Backend.client.postgrest.rpc("classroom_my_subscribed_students")
            .decodeList<ClassroomSubscribedStudentRow>()
        return rows
            .groupBy { Triple(it.groupId, it.groupName, it.groupLevel) }
            .map { (key, groupRows) ->
                val (groupId, groupName, groupLevel) = key
                ClassroomGroupWithStudents(
                    groupId = groupId,
                    groupName = groupName,
                    groupLevel = groupLevel,
                    students = groupRows.map { row ->
                        ClassroomStudentHit(
                            id = row.studentId,
                            fullName = row.studentFullName,
                            email = row.studentEmail,
                            avatarUrl = row.studentAvatarUrl,
                        )
                    },
                )
            }
    }

    // ------------------------------------------------------------------ participants

    suspend fun participants(sessionId: String): List<ClassroomParticipant> =
        Backend.client.from("classroom_participants")
            .select(Columns.raw("*, profile:profiles!classroom_participants_user_id_fkey(id, full_name, avatar_url)")) {
                filter { eq("session_id", sessionId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    fun participantsChangeFlow(channel: RealtimeChannel, sessionId: String): Flow<ClassroomParticipant> =
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "classroom_participants"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.map { action ->
            when (action) {
                is PostgresAction.Insert -> action.decodeRecord()
                is PostgresAction.Update -> action.decodeRecord()
                else -> error("unsupported classroom_participants realtime action")
            }
        }

    // ------------------------------------------------------------------ hand raise

    suspend fun raiseHand(sessionId: String) {
        Backend.rpcVoid("classroom_raise_hand", buildJsonObject { put("p_session_id", sessionId) })
    }

    suspend fun lowerHand(sessionId: String, userId: String? = null) {
        Backend.rpcVoid(
            "classroom_lower_hand",
            buildJsonObject { put("p_session_id", sessionId); userId?.let { put("p_user_id", it) } },
        )
    }

    // ------------------------------------------------------------------ chat / Q&A

    suspend fun chatHistory(sessionId: String): List<ClassroomChatMessage> =
        Backend.client.from("classroom_chat_messages")
            .select(Columns.raw("*, profile:profiles!classroom_chat_messages_sender_id_fkey(id, full_name, avatar_url)")) {
                filter { eq("session_id", sessionId) }
                // Newest 500, not oldest 500: in a long, chatty class the ascending query stopped
                // returning anything new past message 500, so the reconcile pass could never
                // recover a missed recent message. The ViewModel re-sorts by id.
                order("id", Order.DESCENDING)
                limit(500)
            }
            .decodeList<ClassroomChatMessage>()
            .reversed()

    /**
     * Inserts a message and returns the stored row. Returning it matters: the sender merges it
     * into their own list immediately (de-duplicated by id against the Realtime echo), so a
     * message appears the instant the server accepts it rather than only once the Realtime
     * round trip completes — and a failed insert is visibly a failure instead of a message that
     * appears locally and exists nowhere else.
     */
    suspend fun sendChat(sessionId: String, body: String, isQuestion: Boolean): ClassroomChatMessage =
        Backend.client.from("classroom_chat_messages").insert(
            buildJsonObject {
                put("session_id", sessionId)
                put("sender_id", me())
                put("body", body)
                put("is_question", isQuestion)
            },
        ) { select() }.decodeSingle()

    suspend fun markAnswered(messageId: Long) {
        Backend.client.from("classroom_chat_messages").update(
            buildJsonObject { put("answered", true) },
        ) { filter { eq("id", messageId) } }
    }

    fun chatChangeFlow(channel: RealtimeChannel, sessionId: String): Flow<ClassroomChatMessage> =
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "classroom_chat_messages"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.map { it.decodeRecord() }

    // ------------------------------------------------------------------ whiteboard

    suspend fun ensureWhiteboard(sessionId: String): String {
        val result = Backend.rpcRaw("classroom_ensure_whiteboard", buildJsonObject { put("p_session_id", sessionId) })
        return result.toString().trim('"')
    }

    suspend fun board(sessionId: String): com.rork.pro.data.ClassroomWhiteboardBoard =
        Backend.client.from("classroom_whiteboard_boards")
            .select { filter { eq("session_id", sessionId) } }
            .decodeSingle()

    suspend fun setWhiteboardBackground(sessionId: String, url: String?, type: String, page: Int = 0) {
        Backend.rpcVoid(
            "classroom_set_whiteboard_background",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_url", url)
                put("p_type", type)
                put("p_page", page)
            },
        )
    }

    /**
     * Full stroke history for a board — but only from the most recent CLEAR forward.
     *
     * Writes here are insert-only (a CLEAR is just another row, never a delete — see the RPC
     * comment on `classroom_set_whiteboard_background`), so a plain "every row for this board"
     * fetch resurrects everything erased before the latest CLEAR for anyone who (re)loads
     * history after it happened: a late joiner, someone reconnecting, or just reopening the
     * call. A continuously-connected client never notices because [ClassroomCallViewModel]
     * resets its local strokes to empty the moment the CLEAR event arrives over Realtime — this
     * makes a fresh load match that same result instead of replaying the erased drawing.
     */
    suspend fun strokeHistory(boardId: String): List<ClassroomStrokeRow> {
        val lastClearId = Backend.client.from("classroom_whiteboard_strokes")
            .select(Columns.raw("id")) {
                filter {
                    eq("board_id", boardId)
                    eq("action", "CLEAR")
                }
                order("id", Order.DESCENDING)
                limit(1)
            }
            .decodeList<Map<String, Long>>()
            .firstOrNull()
            ?.get("id")

        return Backend.client.from("classroom_whiteboard_strokes")
            .select {
                filter {
                    eq("board_id", boardId)
                    lastClearId?.let { gt("id", it) }
                }
                order("id", Order.ASCENDING)
            }
            .decodeList()
    }

    suspend fun pushStroke(boardId: String, sessionId: String, action: String, stroke: JsonObject?) {
        Backend.client.from("classroom_whiteboard_strokes").insert(
            buildJsonObject {
                put("board_id", boardId)
                put("session_id", sessionId)
                put("user_id", me())
                put("action", action)
                put("stroke", stroke ?: JsonNull)
            },
        )
    }

    /**
     * Live strokes as other participants draw them. Backed by Postgres changes (not a bare
     * broadcast) on purpose: a late joiner who subscribes mid-stroke still gets every row in
     * order once they call [strokeHistory], and there is nothing extra to reconcile.
     */
    fun strokeChangeFlow(channel: RealtimeChannel, sessionId: String): Flow<ClassroomStrokeRow> =
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "classroom_whiteboard_strokes"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.map { it.decodeRecord() }

    // ------------------------------------------------------------------ teacher media controls

    /** Locks/unlocks one student's mic and/or camera. Pass null for whichever you're not changing. */
    suspend fun setParticipantMedia(sessionId: String, userId: String, micLocked: Boolean? = null, cameraLocked: Boolean? = null) {
        Backend.rpcVoid(
            "classroom_set_participant_media",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_user_id", userId)
                micLocked?.let { put("p_mic_locked", it) }
                cameraLocked?.let { put("p_camera_locked", it) }
            },
        )
    }

    /** Live updates to the session row itself — currently just "lock all mics" toggling. */
    fun sessionChangeFlow(channel: RealtimeChannel, sessionId: String): Flow<ClassroomSession> =
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "classroom_sessions"
            filter("id", FilterOperator.EQ, sessionId)
        }.map { it.decodeRecord() }

    /**
     * Best-effort: only returns a token when the academy has configured a self-hosted Jitsi
     * deployment with JWT auth (see backend/functions/classroom-jitsi-token). Any failure —
     * including the expected 501 when it's not configured — is treated as "no token", and the
     * call proceeds against the public meet.jit.si fallback instead.
     */
    suspend fun fetchJitsiToken(sessionId: String): String? = runCatching {
        // Bounded: on the public server this function is not configured and only answers 501,
        // and a cold start must never hold the student on a spinner before the call opens.
        kotlinx.coroutines.withTimeoutOrNull(4_000) {
            val result = Backend.invokeFunction("classroom-jitsi-token", buildJsonObject { put("session_id", sessionId) })
            // contentOrNull, not toString(): a JSON null used to become the literal token "null",
            // which a JWT-enabled server rejects outright.
            ((result as? JsonObject)?.get("token") as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    /**
     * Best-effort "ring" push to every invited student's device — see the
     * `classroom-call-push` Edge Function. Deliberately swallows every failure (including the
     * expected 501 when FCM_SERVICE_ACCOUNT_JSON isn't configured for this academy yet): a
     * session is fully usable — students still see it in their list and can join normally —
     * whether or not the call-style push went out.
     */
    suspend fun notifyIncomingCall(sessionId: String) {
        runCatching {
            Backend.invokeFunction("classroom-call-push", buildJsonObject { put("session_id", sessionId) })
        }
    }

    // ------------------------------------------------------------------ realtime channel lifecycle

    /**
     * One Realtime channel per open session screen, reused by chat/participants/whiteboard
     * flows above so opening a call subscribes exactly once.
     *
     * IMPORTANT — this deliberately does NOT subscribe. supabase-kt binds every
     * `postgresChangeFlow` into the channel's JOIN payload, so a flow created *after*
     * `subscribe()` is never registered with the server; supabase-kt 3.x throws
     * ("You cannot call postgresChangeFlow after joining the channel") from inside the
     * collector, which is caught per-stream and looks exactly like "Realtime silently does
     * nothing". That is what broke chat, whiteboard sharing and participant sync in both
     * directions. The correct order is: create the channel → create every flow → call
     * [subscribeChannel]. Callers must still call `Backend.realtime.removeChannel(channel)` on
     * teardown so a reopened session does not pile up channels.
     */
    fun openChannel(sessionId: String): RealtimeChannel =
        Backend.client.channel("classroom-$sessionId")

    /** Joins the channel. Call this only after every postgresChangeFlow has been created. */
    suspend fun subscribeChannel(channel: RealtimeChannel) {
        channel.subscribe(blockUntilSubscribed = true)
    }

    /**
     * Live updates to the shared board row itself (background image/PDF/video). Previously the
     * only signal a shared file produced was the side-effect CLEAR stroke; this is the direct
     * one, so material shared by *anyone* — teacher or student — lands on every other client
     * even if the stroke row is missed.
     */
    fun boardChangeFlow(channel: RealtimeChannel, sessionId: String): Flow<ClassroomWhiteboardBoard> =
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "classroom_whiteboard_boards"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.map { action ->
            when (action) {
                is PostgresAction.Insert -> action.decodeRecord()
                is PostgresAction.Update -> action.decodeRecord()
                else -> error("unsupported classroom_whiteboard_boards realtime action")
            }
        }

    // ------------------------------------------------------------------ presence / heartbeat

    /**
     * Tells the server this client is still really in the room. Called on a short loop by the
     * call screen: a participant row that says JOINED but has not sent a heartbeat in the last
     * 45 seconds is reported as offline by [presence], so a teacher sees who is actually
     * connected rather than who once tapped Join. Also flips a reconnecting client's own row
     * back to JOINED without a second join round trip.
     */
    suspend fun heartbeat(sessionId: String) {
        Backend.rpcVoid("classroom_heartbeat", buildJsonObject { put("p_session_id", sessionId) })
    }

    suspend fun presence(sessionId: String): List<ClassroomPresenceRow> =
        Backend.client.postgrest.rpc(
            "classroom_session_presence",
            buildJsonObject { put("p_session_id", sessionId) },
        ).decodeList()

    // ------------------------------------------------------------------ session list realtime

    /**
     * Invites arriving for *me* (a row inserted into classroom_participants with my user id).
     * Lets a student's session list light up the moment a teacher creates a session and invites
     * them, instead of only after a manual pull-to-refresh.
     */
    fun myInvitesChangeFlow(channel: RealtimeChannel): Flow<Unit> {
        val userId = me()
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "classroom_participants"
            filter("user_id", FilterOperator.EQ, userId)
        }.map { }
    }

    /**
     * Any session row I am allowed to see changing status (SCHEDULED → LIVE → ENDED). RLS is
     * what scopes this: Realtime drops every row `classroom_can_view` would hide, so no filter
     * is needed here and none would be correct.
     */
    fun visibleSessionsChangeFlow(channel: RealtimeChannel): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "classroom_sessions"
        }.map { }

    // ------------------------------------------------------------------ live broadcast
    //
    // Postgres Changes is the *record* of what happened; Broadcast is the *delivery* of it.
    //
    // Everything in a call — a message, a shared image, a stroke, a screen share starting —
    // now goes out over Broadcast the instant the sender's write succeeds, and the Postgres
    // Changes stream stays as the reconciliation path. That matters because Postgres Changes
    // has a long chain of things that must all be true for a row to reach a subscriber: the
    // table in the publication, REPLICA IDENTITY, a SELECT policy that the Realtime process can
    // evaluate for that specific subscriber, and WAL decoding keeping up. Any one of them
    // failing is silent — the sender sees success and the receiver simply never hears. Broadcast
    // depends on none of it: the message goes to the channel and everyone on the channel gets
    // it, identically from the Android client and from the browser.
    //
    // The database write still happens first and remains the source of truth, so a broadcast
    // that is missed (a client that was backgrounded, a reconnect) is recovered by the history
    // fetch and the periodic reconcile — nothing is ever known only as a broadcast.

    object Live {
        const val CHAT = "chat"
        const val SHARE = "share"
        const val STROKE = "stroke"
        const val SCREEN_SHARE = "screen_share"
    }

    suspend fun publish(channel: RealtimeChannel, event: String, payload: JsonObject) {
        channel.broadcast(event, payload)
    }

    fun liveFlow(channel: RealtimeChannel, event: String): Flow<JsonObject> =
        channel.broadcastFlow<JsonObject>(event)

    /** Channel for a list/lobby screen — same create-flows-then-subscribe rule as above. */
    fun openListChannel(): RealtimeChannel =
        Backend.client.channel("classroom-list-${Backend.currentUserId ?: "anon"}")

    /**
     * Channel for the Home "meeting is live" card. Its own name on purpose: the session list
     * screen opens [openListChannel] at the same time, and two owners of one channel would
     * fight over when flows may be added and when it is removed.
     */
    fun openHomeChannel(): RealtimeChannel =
        Backend.client.channel("classroom-home-${Backend.currentUserId ?: "anon"}")
}
