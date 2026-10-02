package com.rork.pro.ui.screens.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rork.pro.data.Async
import com.rork.pro.data.ClassroomGroupWithStudents
import com.rork.pro.data.ClassroomJoinInfo
import com.rork.pro.data.ClassroomRepository
import com.rork.pro.data.ClassroomSession
import com.rork.pro.data.ClassroomStudentHit
import com.rork.pro.data.Backend
import com.rork.pro.data.toAppError
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Who is looking at the session list — decides which repository query runs. */
enum class ClassroomScope { STUDENT, TEACHER, STAFF }

class ClassroomListViewModel : ViewModel() {
    private var scope: ClassroomScope = ClassroomScope.STUDENT
    private val _state = MutableStateFlow<Async<List<ClassroomSession>>>(Async.Loading)
    val state: StateFlow<Async<List<ClassroomSession>>> = _state.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds: StateFlow<Set<String>> = _busyIds.asStateFlow()

    private val _error = MutableStateFlow<com.rork.pro.data.AppError?>(null)
    val error: StateFlow<com.rork.pro.data.AppError?> = _error.asStateFlow()

    private var channel: RealtimeChannel? = null

    /** Called once from a LaunchedEffect(scope) — matches the rest of the app's id-scoped
     *  screens (see TeacherExercisesViewModel.bind), so a plain no-arg viewModel() works here. */
    fun bind(scope: ClassroomScope) {
        this.scope = scope
        refresh()
        watchLive()
    }

    /**
     * Keeps the list live instead of stale-until-pulled. A student sitting on this screen when
     * a teacher creates a session now sees it appear — and sees it flip to LIVE, or disappear
     * when it ends — without touching anything. Two streams feed it: invites addressed to me
     * (a classroom_participants row with my user id) and any session row RLS lets me see.
     *
     * Flows are created before subscribe() on purpose — see ClassroomRepository.openChannel.
     */
    private fun watchLive() {
        if (channel != null) return
        viewModelScope.launch {
            runCatching {
                val ch = ClassroomRepository.openListChannel()
                channel = ch
                val invites = ClassroomRepository.myInvitesChangeFlow(ch)
                val sessions = ClassroomRepository.visibleSessionsChangeFlow(ch)
                launch { runCatching { invites.collect { refresh(showLoading = false) } } }
                launch { runCatching { sessions.collect { refresh(showLoading = false) } } }
                ClassroomRepository.subscribeChannel(ch)
            }.onFailure {
                android.util.Log.w("ClassroomListVM", "Live session list unavailable: ${it.message}")
            }
        }
    }

    override fun onCleared() {
        val ch = channel
        channel = null
        Backend.appScope.launch {
            ch?.let { runCatching { Backend.realtime.removeChannel(it) } }
        }
        super.onCleared()
    }

    /** [showLoading] is false for live-driven refreshes so the list updates in place instead of
     *  blanking to a spinner every time somebody joins or a status changes. */
    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _state.value !is Async.Success) _state.value = Async.Loading
            runCatching {
                when (scope) {
                    ClassroomScope.STUDENT -> ClassroomRepository.myInvitedSessions()
                    ClassroomScope.TEACHER -> ClassroomRepository.mySessions()
                    ClassroomScope.STAFF -> ClassroomRepository.allSessionsForStaff()
                }
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    fun clearError() { _error.value = null }

    fun cancelSession(id: String) = act(id) { ClassroomRepository.cancelSession(id) }
    fun endSession(id: String) = act(id) { ClassroomRepository.endSession(id) }

    private fun act(id: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busyIds.value = _busyIds.value + id
            runCatching { block() }
                .onSuccess { refresh() }
                .onFailure {
                    // Previously silent: canceling/ending a session that failed (FORBIDDEN,
                    // offline, already ended by someone else) left the button just... stop
                    // spinning, with the item unchanged and no explanation.
                    android.util.Log.e("ClassroomListVM", "Action failed for session=$id", it)
                    _error.value = it.toAppError()
                }
            _busyIds.value = _busyIds.value - id
        }
    }
}

data class StudentPick(val hit: ClassroomStudentHit, val selected: Boolean)

data class CreateSessionState(
    val title: String = "",
    val maxParticipants: Int = 30,
    /** Class length the teacher picked (minutes). */
    val durationMinutes: Int = 90,
    /** 0 = start right now; otherwise the class is scheduled this many minutes ahead. */
    val startInMinutes: Int = 0,
    val loadingGroups: Boolean = true,
    val groups: List<ClassroomGroupWithStudents> = emptyList(),
    val selectedStudents: Map<String, ClassroomStudentHit> = emptyMap(),
    val submitting: Boolean = false,
    val error: String? = null,
    val createdSessionId: String? = null,
) {
    /** A group reads as "fully selected" only once every one of its students is picked —
     *  unchecking any single member (the "exception" case) drops it to partial automatically. */
    fun isGroupFullySelected(group: ClassroomGroupWithStudents): Boolean =
        group.students.isNotEmpty() && group.students.all { selectedStudents.containsKey(it.id) }

    fun selectedCountFor(group: ClassroomGroupWithStudents): Int =
        group.students.count { selectedStudents.containsKey(it.id) }
}

/**
 * Default class length applied automatically now that the create-session form no longer asks
 * for a start/end time — sessions start the moment they're created and run for this long.
 */
private const val DEFAULT_SESSION_MINUTES = 90L

/**
 * The name a session gets now that the teacher no longer types one: "Live session • 15 Sep •
 * 7:30 PM" (Arabic month names, Western digits — see AppLanguage.locale()).
 *
 * Date and time, not a lesson topic, on purpose: it is the one label that is always accurate,
 * never duplicated between two sessions in the same list, and readable in the incoming-call
 * notification a student sees before anything else. The teacher's own name is deliberately left
 * out because the session card already prints it directly under the title.
 */
private fun autoSessionTitle(start: java.time.Instant): String {
    val stamp = runCatching {
        java.time.format.DateTimeFormatter
            .ofPattern("d MMM • h:mm a", AppLanguage.locale())
            .format(start.atZone(java.time.ZoneId.systemDefault()))
    }.getOrNull()
    return if (stamp.isNullOrBlank()) tr(StrClassroom.autoSessionTitle) else trf(StrClassroom.autoSessionTitleAt, stamp)
}

class ClassroomCreateViewModel : ViewModel() {
    private val _state = MutableStateFlow(CreateSessionState())
    val state: StateFlow<CreateSessionState> = _state.asStateFlow()

    init {
        loadGroups()
    }

    fun setTitle(v: String) { _state.value = _state.value.copy(title = v) }
    fun setDuration(minutes: Int) { _state.value = _state.value.copy(durationMinutes = minutes.coerceIn(15, 240)) }
    fun setStartIn(minutes: Int) { _state.value = _state.value.copy(startInMinutes = minutes.coerceAtLeast(0)) }
    fun setMaxParticipants(v: Int) { _state.value = _state.value.copy(maxParticipants = v.coerceIn(1, 200)) }

    /**
     * Loads the signed-in teacher's own subscribed, account-linked students grouped by class —
     * replaces the old global "search any student in the app" box, so a teacher only ever
     * invites people already subscribed with them.
     */
    fun loadGroups() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingGroups = true, error = null)
            runCatching { ClassroomRepository.mySubscribedStudentGroups() }
                .onSuccess { groups -> _state.value = _state.value.copy(groups = groups, loadingGroups = false) }
                .onFailure {
                    android.util.Log.e("ClassroomCreateVM", "Failed to load subscribed students", it)
                    _state.value = _state.value.copy(loadingGroups = false, error = it.toAppError().message)
                }
        }
    }

    /** Selecting a group invites every one of its (account-linked) members in one tap. */
    fun toggleGroup(group: ClassroomGroupWithStudents) {
        val current = _state.value.selectedStudents
        val fullySelected = _state.value.isGroupFullySelected(group)
        _state.value = _state.value.copy(
            selectedStudents = if (fullySelected) {
                current - group.students.map { it.id }.toSet()
            } else {
                current + group.students.associateBy { it.id }
            },
        )
    }

    /** Un/re-checking one student inside an already-selected group is how a teacher makes an
     *  exception — the group's checkbox then reads as partially selected rather than resetting. */
    fun toggleStudent(hit: ClassroomStudentHit) {
        val current = _state.value.selectedStudents
        _state.value = _state.value.copy(
            selectedStudents = if (current.containsKey(hit.id)) current - hit.id else current + (hit.id to hit),
        )
    }

    fun submit(onDone: (String) -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(submitting = true, error = null)
            val start = java.time.Instant.now().plusSeconds(s.startInMinutes * 60L)
            val end = start.plusSeconds(s.durationMinutes * 60L)
            runCatching {
                ClassroomRepository.createSession(
                    // The create screen no longer asks for a name, so one is generated. The
                    // title is still what every list, lobby and incoming-call notification
                    // shows, so it can never be blank — it just isn't the teacher's problem
                    // anymore.
                    title = s.title.trim().ifBlank { autoSessionTitle(start) },
                    description = "",
                    scheduledStart = start.toString(),
                    scheduledEnd = end.toString(),
                    maxParticipants = s.maxParticipants,
                    studentIds = s.selectedStudents.keys.toList(),
                )
            }.onSuccess { id ->
                _state.value = _state.value.copy(submitting = false, createdSessionId = id)
                // Rings every invited student's device right away (best-effort — see
                // ClassroomRepository.notifyIncomingCall). Fired after onDone so a slow or
                // unconfigured push never delays landing in the lobby.
                onDone(id)
                // A class scheduled for later must not ring phones now; the invite notification
                // created with the session is enough until it actually starts.
                if (s.selectedStudents.isNotEmpty() && s.startInMinutes == 0) {
                    launch { ClassroomRepository.notifyIncomingCall(id) }
                }
            }.onFailure {
                _state.value = _state.value.copy(submitting = false, error = it.toAppError().message)
            }
        }
    }
}

sealed interface LobbyState {
    data object Loading : LobbyState
    data class Ready(val session: ClassroomSession, val participantCount: Int) : LobbyState
    data class Joining(val session: ClassroomSession) : LobbyState
    data class Joined(val info: ClassroomJoinInfo, val title: String? = null) : LobbyState
    data class Error(val error: com.rork.pro.data.AppError) : LobbyState
}

class ClassroomLobbyViewModel : ViewModel() {
    private var sessionId: String = ""
    private val _state = MutableStateFlow<LobbyState>(LobbyState.Loading)
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    fun bind(sessionId: String) {
        this.sessionId = sessionId
        load()
    }

    fun load() {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            _state.value = LobbyState.Loading
            runCatching {
                val session = ClassroomRepository.session(sessionId)
                val participants = ClassroomRepository.participants(sessionId)
                session to participants.count { it.status == "JOINED" }
            }.onSuccess { (session, count) ->
                _state.value = LobbyState.Ready(session, count)
            }.onFailure {
                _state.value = LobbyState.Error(it.toAppError())
            }
        }
    }

    fun join() {
        val current = _state.value
        val session = (current as? LobbyState.Ready)?.session ?: return
        viewModelScope.launch {
            _state.value = LobbyState.Joining(session)
            runCatching { ClassroomRepository.join(sessionId) }
                .onSuccess { info ->
                    _state.value = LobbyState.Joined(info, session.title)
                    // Ring the invited students again at the moment the teacher actually
                    // enters. Creating the session already sent one push, but a teacher who
                    // creates ahead of time and starts later used to reach nobody: the
                    // session went LIVE with no signal to anyone. Best-effort, exactly like
                    // the create-time ring — a failed or unconfigured push never blocks the
                    // call.
                    if (info.isModerator) {
                        launch { ClassroomRepository.notifyIncomingCall(sessionId) }
                    }
                }
                .onFailure { _state.value = LobbyState.Error(it.toAppError()) }
        }
    }
}
