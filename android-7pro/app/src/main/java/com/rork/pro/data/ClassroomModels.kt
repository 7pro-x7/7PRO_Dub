package com.rork.pro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A scheduled or live classroom session.
 *
 * [roomName] and [roomPassword] are intentionally absent here — they are never selected in a
 * plain list/detail query (see [ClassroomRepository]) and only ever come back from the
 * `classroom_join` RPC for someone already authorized, decoded separately as [ClassroomJoinInfo].
 */
@Serializable
data class ClassroomSession(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val title: String,
    val description: String? = null,
    @SerialName("scheduled_start") val scheduledStart: String,
    @SerialName("scheduled_end") val scheduledEnd: String,
    val status: String = "SCHEDULED",
    @SerialName("max_participants") val maxParticipants: Int = 30,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    /** Joined in via the `teacher:profiles!classroom_sessions_teacher_id_fkey(...)` embed. */
    val teacher: TeacherRef? = null,
) {
    val isLive: Boolean get() = status == "LIVE"
    val isUpcoming: Boolean get() = status == "SCHEDULED"
    val isPast: Boolean get() = status == "ENDED" || status == "CANCELED" || status == "EXPIRED"

    /**
     * True only while the meeting is actually running — this is what drives the "Join the
     * meeting now" card on Home, which must appear when the meeting starts and vanish when it
     * ends.
     *
     * "Running" means the server has flipped the session to LIVE (someone with permission
     * actually entered it — see `classroom_join`). A merely SCHEDULED session is not shown: the
     * teacher may not be in the room yet, and a slot nobody opened is not a meeting.
     *
     * ENDED / CANCELED / EXPIRED are never in progress. A LIVE row can in theory outlive its
     * meeting (everyone's app was killed and nobody pressed "end"), so a LIVE session is also
     * treated as over once it runs [LIVE_OVERRUN_GRACE] past its planned end — the same 30
     * minutes of slack the server allows around a slot — instead of leaving a stale button on
     * Home forever. It stays reachable from the classroom list either way.
     */
    fun isInProgress(now: java.time.Instant = java.time.Instant.now()): Boolean {
        if (!isLive) return false
        val end = parseInstantOrNull(scheduledEnd) ?: return true
        return now.isBefore(end.plus(LIVE_OVERRUN_GRACE))
    }
}

// Two hours: classes now run up to 3 h and can be started late, so the "join now" card must not vanish mid-lesson.
private val LIVE_OVERRUN_GRACE: java.time.Duration = java.time.Duration.ofMinutes(120)

/** PostgREST returns `+00:00` offsets, which plain `Instant.parse` rejects on older runtimes. */
private fun parseInstantOrNull(value: String): java.time.Instant? =
    runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(value) }.getOrNull()

/**
 * Where the 7PRO web classroom (web/classroom.html) is hosted. Used for browser invite links and
 * as the way into a live class on phones too old for the in-app video (Android 7 and below).
 * Change this single constant if the hostname ever changes.
 */
object ClassroomWeb {
    const val BASE_URL = "https://7pro-x7.7pro7777.workers.dev/"
    const val DEFAULT_JITSI_DOMAIN = "meet.ffmuc.net"

    /**
     * Older sessions can still contain the retired public meet.jit.si value (or the
     * non-conferencing riot.im host). Keep both clients on the same working server until the
     * session row is migrated, otherwise teacher and student end up in different rooms.
     */
    fun normalizeJitsiDomain(raw: String?): String {
        val domain = raw.orEmpty().trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .substringBefore('/')
            .lowercase()
        return if (domain.isBlank() || domain == "meet.jit.si" || domain == "jitsi.riot.im") {
            DEFAULT_JITSI_DOMAIN
        } else {
            domain
        }
    }

    /** Teacher/moderator link: straight into the session after their own sign-in (email pre-filled). */
    fun teacherUrl(sessionId: String, email: String?): String {
        val s = java.net.URLEncoder.encode(sessionId, "UTF-8")
        val e = email?.takeIf { it.isNotBlank() }?.let { "&email=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return "${BASE_URL}?session=$s$e"
    }

    /** A link that opens straight into one session: the room password doubles as the invite key
     *  the page's `classroom_guest_join` checks. Same format the "copy invite link" button makes. */
    fun inviteUrl(sessionId: String, key: String): String {
        val s = java.net.URLEncoder.encode(sessionId, "UTF-8")
        val k = java.net.URLEncoder.encode(key, "UTF-8")
        return "${BASE_URL}?session=$s&key=$k"
    }
}

@Serializable
data class ClassroomParticipant(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("role_in_session") val roleInSession: String = "STUDENT",
    val status: String = "INVITED",
    @SerialName("hand_raised") val handRaised: Boolean = false,
    @SerialName("hand_raised_at") val handRaisedAt: String? = null,
    @SerialName("mic_locked") val micLocked: Boolean = false,
    @SerialName("camera_locked") val cameraLocked: Boolean = false,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("left_at") val leftAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    /** Joined in via `profile:profiles!classroom_participants_user_id_fkey(...)`. */
    val profile: TeacherRef? = null,
)

@Serializable
data class ClassroomChatMessage(
    val id: Long = 0,
    @SerialName("session_id") val sessionId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("is_question") val isQuestion: Boolean = false,
    val answered: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val profile: TeacherRef? = null,
)

@Serializable
data class ClassroomWhiteboardBoard(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("background_url") val backgroundUrl: String? = null,
    @SerialName("background_type") val backgroundType: String = "NONE",
    @SerialName("background_page") val backgroundPage: Int = 0,
    @SerialName("shared_by") val sharedBy: String? = null,
    @SerialName("shared_at") val sharedAt: String? = null,
)

/**
 * One row of `classroom_session_presence` — who is in the room and whether they are *really*
 * connected right now ([online] is derived server-side from the heartbeat, not from the
 * participant status alone, so a student whose phone died no longer counts as present).
 */
@Serializable
data class ClassroomPresenceRow(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("role_in_session") val roleInSession: String = "STUDENT",
    val status: String = "INVITED",
    @SerialName("hand_raised") val handRaised: Boolean = false,
    @SerialName("mic_locked") val micLocked: Boolean = false,
    @SerialName("camera_locked") val cameraLocked: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val online: Boolean = false,
)

@Serializable
data class ClassroomStrokeRow(
    val id: Long = 0,
    @SerialName("board_id") val boardId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    val action: String = "STROKE",
    val stroke: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
data class ClassroomStudentHit(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * One (group, subscribed student) row from `classroom_my_subscribed_students` — flat because
 * Postgres has no nested-table return, grouped client-side in [ClassroomRepository] into
 * [ClassroomGroupWithStudents]. Only subscriptions already linked to a registered account
 * ([student_user_id][com.rork.pro.data.Subscription.studentUserId] not null) come back, since an
 * unlinked (manual-text) student has no account to invite into a Virtual Classroom session.
 */
@Serializable
data class ClassroomSubscribedStudentRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("group_level") val groupLevel: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("student_full_name") val studentFullName: String? = null,
    @SerialName("student_email") val studentEmail: String? = null,
    @SerialName("student_avatar_url") val studentAvatarUrl: String? = null,
)

/** [ClassroomSubscribedStudentRow] rows grouped by class, for the "invite my students" picker. */
data class ClassroomGroupWithStudents(
    val groupId: String,
    val groupName: String,
    val groupLevel: String?,
    val students: List<ClassroomStudentHit>,
)

/** Decoded result of `classroom_browser_invite_key` — the invite key, with no join side effects. */
@Serializable
data class BrowserInviteKey(
    @SerialName("room_password") val roomPassword: String,
    @SerialName("is_moderator") val isModerator: Boolean,
)

/** Decoded result of the `classroom_join` RPC — the only place credentials ever appear. */
@Serializable
data class ClassroomJoinInfo(
    @SerialName("room_name") val roomName: String,
    @SerialName("room_password") val roomPassword: String,
    @SerialName("jitsi_domain") val jitsiDomain: String,
    @SerialName("role_in_session") val roleInSession: String,
    @SerialName("is_moderator") val isModerator: Boolean,
    @SerialName("display_name") val displayName: String,
    /**
     * Whether the teacher already had this person's mic/camera locked — from a standing "lock
     * all mics" or an earlier per-student lock — at the moment they joined. Read before the
     * Jitsi conference is even opened, so a locked participant can start already muted instead
     * of joining live for a moment and being muted after the fact.
     */
    @SerialName("mic_locked") val micLocked: Boolean = false,
    @SerialName("camera_locked") val cameraLocked: Boolean = false,
)
