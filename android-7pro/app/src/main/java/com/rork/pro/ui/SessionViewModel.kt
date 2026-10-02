package com.rork.pro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.pro.data.AppError
import com.rork.pro.data.AppNotification
import com.rork.pro.data.ErrorText
import com.rork.pro.data.Backend
import com.rork.pro.data.NotificationSync
import com.rork.pro.data.Profile
import com.rork.pro.data.PushTokenRepository
import com.rork.pro.data.SessionCache
import com.rork.pro.data.SessionRepository
import com.rork.pro.data.TeacherProfile
import com.rork.pro.data.UpdateRule
import com.rork.pro.data.toAppError
import io.github.jan.supabase.auth.status.SessionStatus
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private object AuthNotice {
    val resetLinkSent = Tr(
        "We sent a password reset link to %s.",
        "أرسلنا رابط إعادة تعيين كلمة المرور إلى %s.",
    )
}

data class SessionState(
    val booting: Boolean = true,
    val signedIn: Boolean = false,
    val profile: Profile? = null,
    val teacherProfile: TeacherProfile? = null,
    val permissions: Set<String> = emptySet(),
    val notifications: List<AppNotification> = emptyList(),
    val notificationPreferences: Map<String, Boolean> = emptyMap(),
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
    val error: AppError? = null,
    /** True while the app runs on locally cached data because the server was unreachable. */
    val offline: Boolean = false,
    /** The owner's forced-update rule, read before sign-in so old builds are stopped at the door. */
    val update: UpdateRule = UpdateRule(),
) {
    val unreadCount: Int get() = notifications.count { it.readAt == null }
    val isStaff: Boolean get() = profile?.isStaff == true
    val isOwner: Boolean get() = profile?.isOwner == true
    val isTeacher: Boolean get() = teacherProfile != null && teacherProfile.status == "APPROVED"

    fun can(permission: String): Boolean = isOwner || (profile?.role == "ADMIN" && permission in permissions)
}

/** Owns authentication state, the signed-in profile and platform-wide settings. */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _authError = MutableStateFlow<AppError?>(null)
    val authError: StateFlow<AppError?> = _authError.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authNotice = MutableStateFlow<String?>(null)
    val authNotice: StateFlow<String?> = _authNotice.asStateFlow()

    init {
        restoreFromCache()
        readUpdateRule()
        observeSession()
    }

    /**
     * Reads the update rule straight away, without waiting for a sign-in.
     *
     * Platform settings are world-readable exactly so a retired build can be stopped before it
     * tries to talk to anything else.
     */
    private fun readUpdateRule() {
        if (!Backend.isConfigured) return
        viewModelScope.launch {
            runCatching { com.rork.pro.data.CatalogRepository.settings() }
                .onSuccess { settings ->
                    _state.value = _state.value.copy(update = UpdateRule.from(settings))
                }
        }
    }

    /** Re-reads the update rule, used by the "I've updated" button on the update screen. */
    fun refreshUpdateRule() = readUpdateRule()

    /**
     * Shows the last known signed-in state right away.
     *
     * Supabase restores its tokens asynchronously, so without this the app would flash a boot
     * spinner on every launch — and, with no connection, the sign-in screen — even though the
     * stored session is still perfectly valid.
     */
    private fun restoreFromCache() {
        val cached = SessionCache.profile() ?: return
        _state.value = SessionState(
            booting = false,
            signedIn = true,
            profile = cached,
            teacherProfile = SessionCache.teacherProfile(),
            permissions = SessionCache.permissions(),
        )
    }

    private fun observeSession() {
        viewModelScope.launch {
            if (!Backend.isConfigured) {
                _state.value = SessionState(
                    booting = false,
                    error = AppError("NOT_CONFIGURED", ErrorText.notConfigured, false),
                )
                return@launch
            }
            Backend.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> loadEverything()

                    is SessionStatus.NotAuthenticated -> {
                        // Genuinely signed out (or nothing stored): drop the cached copy too.
                        SessionCache.clear()
                        _state.value = SessionState(booting = false, signedIn = false)
                    }

                    // The token could not be refreshed — almost always a connectivity problem.
                    // Supabase keeps retrying with the stored refresh token, so a user who was
                    // signed in stays signed in on cached data instead of being kicked to login.
                    is SessionStatus.RefreshFailure -> {
                        val cached = SessionCache.profile()
                        _state.value = if (cached != null) {
                            _state.value.copy(
                                booting = false,
                                signedIn = true,
                                profile = _state.value.profile ?: cached,
                                teacherProfile = _state.value.teacherProfile ?: SessionCache.teacherProfile(),
                                permissions = _state.value.permissions.ifEmpty { SessionCache.permissions() },
                                offline = true,
                                error = AppError("OFFLINE", ErrorText.offlineKeepWorking, true),
                            )
                        } else {
                            SessionState(
                                booting = false,
                                signedIn = false,
                                error = AppError("SESSION_EXPIRED", ErrorText.sessionExpired, false),
                            )
                        }
                    }

                    // Still initialising: keep whatever is already on screen rather than
                    // throwing the restored state away behind a spinner.
                    else -> _state.value = _state.value.copy(booting = _state.value.profile == null)
                }
            }
        }
    }

    fun loadEverything() {
        viewModelScope.launch {
            runCatching {
                val profile = SessionRepository.myProfile()
                val permissions = if (profile?.role == "ADMIN") SessionRepository.myPermissions() else emptySet()
                val teacher = if (profile?.role == "TEACHER" || profile?.isStaff == true) {
                    SessionRepository.myTeacherProfile()
                } else {
                    null
                }
                val notifications = SessionRepository.notifications()
                val notificationPreferences = SessionRepository.notificationPreferences()
                    .associate { it.kind.uppercase() to it.enabled }
                val settings = runCatching { com.rork.pro.data.CatalogRepository.settings() }.getOrElse { emptyMap() }
                SessionState(
                    booting = false,
                    signedIn = profile != null,
                    profile = profile,
                    teacherProfile = teacher,
                    permissions = permissions,
                    notifications = notifications,
                    notificationPreferences = notificationPreferences,
                    maintenance = settings["maintenance.enabled"] == "true",
                    maintenanceMessage = settings["maintenance.message"].orEmpty(),
                    update = UpdateRule.from(settings),
                )
            }.onSuccess { loaded ->
                _state.value = loaded
                SessionCache.save(loaded.profile, loaded.teacherProfile, loaded.permissions)
                // Refresh subscription statuses (DUE / OVERDUE) in the background.
                if (loaded.profile?.role == "TEACHER" || loaded.profile?.isStaff == true || loaded.isOwner) {
                    runCatching { com.rork.pro.data.TeacherRepository.refreshSubscriptionStatuses() }
                }
                // Important alerts keep arriving while the app is in the background or closed.
                NotificationSync.start(getApplication())
                // Lets classroom-call-push find this device the next time a teacher starts a
                // session this account is invited to. Best-effort — see PushTokenRepository.
                launch { PushTokenRepository.syncToken() }
            }.onFailure { throwable ->
                // The session is valid but the profile could not be fetched. Fall back to the
                // cached copy so the app opens normally instead of showing an empty shell.
                val cached = SessionCache.profile()
                val signedIn = Backend.currentUserId != null
                _state.value = _state.value.copy(
                    booting = false,
                    signedIn = signedIn && (_state.value.profile != null || cached != null),
                    profile = _state.value.profile ?: cached,
                    teacherProfile = _state.value.teacherProfile ?: SessionCache.teacherProfile(),
                    permissions = _state.value.permissions.ifEmpty { SessionCache.permissions() },
                    offline = true,
                    error = throwable.toAppError(),
                )
            }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            runCatching { SessionRepository.notifications() }
                .onSuccess { _state.value = _state.value.copy(notifications = it) }
        }
    }

    fun setNotificationPreference(kind: String, enabled: Boolean) {
        val key = kind.uppercase()
        val previous = _state.value.notificationPreferences
        _state.value = _state.value.copy(notificationPreferences = previous + (key to enabled))
        viewModelScope.launch {
            runCatching { SessionRepository.setNotificationPreference(key, enabled) }
                .onFailure { _state.value = _state.value.copy(notificationPreferences = previous) }
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            runCatching { SessionRepository.markNotificationRead(id) }
            refreshNotifications()
        }
    }

    /** Removes one notification. Updates the list immediately, then confirms with the server. */
    fun deleteNotification(id: Long) {
        val previous = _state.value.notifications
        _state.value = _state.value.copy(notifications = previous.filterNot { it.id == id })
        viewModelScope.launch {
            runCatching { SessionRepository.deleteNotification(id) }
                .onFailure { _state.value = _state.value.copy(notifications = previous) }
        }
    }

    /** Clears every notification for the signed-in user. */
    fun clearNotifications() {
        val previous = _state.value.notifications
        _state.value = _state.value.copy(notifications = emptyList())
        viewModelScope.launch {
            runCatching { SessionRepository.clearNotifications() }
                .onFailure { _state.value = _state.value.copy(notifications = previous) }
        }
    }

    fun signIn(email: String, password: String) {
        runAuth { SessionRepository.signIn(email, password) }
    }

    fun signUp(email: String, password: String, name: String, phone: String) {
        runAuth { SessionRepository.signUp(email, password, name, phone) }
    }

    fun signInWithGoogle() {
        runAuth { SessionRepository.signInWithGoogle() }
    }

    fun resetPassword(email: String) {
        runAuth {
            SessionRepository.sendPasswordReset(email)
            _authNotice.value = trf(AuthNotice.resetLinkSent, email)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { PushTokenRepository.clearToken() }
            runCatching { SessionRepository.signOut() }
            NotificationSync.stop(getApplication())
            SessionCache.clear()
            _state.value = SessionState(booting = false, signedIn = false)
        }
    }

    fun clearAuthFeedback() {
        _authError.value = null
        _authNotice.value = null
    }

    private fun runAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            _authNotice.value = null
            runCatching { block() }
                .onFailure { _authError.value = it.toAppError() }
            _authBusy.value = false
        }
    }
}
