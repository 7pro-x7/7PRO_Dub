package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.rork.pro.data.PayWeb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.ClassroomStudentHit
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.Subscription
import com.rork.pro.data.SubscriptionStats
import com.rork.pro.data.TeacherGroup
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.PaymentSourcePill
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.navigation.DetailHeader
import coil3.compose.AsyncImage
import com.rork.pro.ui.screens.admin.AdminRoutes
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import java.net.URLEncoder
import androidx.compose.material.icons.filled.Notifications
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── ViewModel ──────────────────────────────────────────────────────────────

/**
 * A single subscriptions screen serves both roles:
 *  - [Teacher]: the signed-in teacher's own groups/subscriptions (create,
 *    edit, delete, resubmit-after-rejection, self-renew).
 *  - [Staff]: OWNER/ADMIN viewing every teacher's groups/subscriptions
 *    (create/edit with an explicit teacher, approve/reject, renew, delete —
 *    no group create/edit/activate, since groups are teacher-owned).
 *  - [StaffOf]: OWNER/ADMIN working on ONE teacher's behalf — the very same screen the teacher
 *    has (create/edit/activate groups, add/edit/renew/delete members), limited to that teacher.
 *    Activating a group or approving a member is immediate, since staff are the approver.
 */
sealed class SubsScope {
    object Teacher : SubsScope()
    object Staff : SubsScope()
    class StaffOf(val teacherId: String, val teacherName: String = "") : SubsScope()
}

internal val SubsScope.isStaffScope: Boolean get() = this is SubsScope.Staff || this is SubsScope.StaffOf

/** True when the screen should behave exactly like the teacher's own: the teacher, or staff acting as one. */
internal val SubsScope.teacherLike: Boolean get() = this is SubsScope.Teacher || this is SubsScope.StaffOf

internal val SubsScope.scopedTeacherId: String? get() = (this as? SubsScope.StaffOf)?.teacherId

data class SubsData(
    val stats: SubscriptionStats,
    val subscriptions: List<Subscription>,
    val groups: List<TeacherGroup>,
)

class SubscriptionsViewModel(private val scope: SubsScope = SubsScope.Teacher) : ViewModel() {
    private val isStaff get() = scope.isStaffScope
    private val asTeacherId get() = scope.scopedTeacherId

    private val _state = MutableStateFlow<Async<SubsData>>(Async.Loading)
    val state: StateFlow<Async<SubsData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                // Fetch core data. Groups/subscriptions the teacher added are created
                // in place by the server with approval_status PENDING/REJECTED/APPROVED
                // and are shown directly in these lists with a status badge.
                val tid = asTeacherId
                if (tid != null) {
                    // Owner/admin on one teacher's behalf: only that teacher's rows and numbers.
                    val subs = AdminRepository.teacherSubscriptions(tid)
                    SubsData(
                        stats = AdminRepository.statsOf(subs),
                        subscriptions = subs,
                        groups = AdminRepository.teacherGroups(tid),
                    )
                } else if (isStaff) {
                    SubsData(
                        stats = AdminRepository.subscriptionStatsAll(),
                        subscriptions = AdminRepository.allSubscriptions(),
                        groups = AdminRepository.allGroups(),
                    )
                } else {
                    SubsData(
                        stats = TeacherRepository.subscriptionStats(),
                        subscriptions = TeacherRepository.mySubscriptions(),
                        groups = TeacherRepository.myGroups(),
                    )
                }
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure {
                    android.util.Log.e("SubscriptionsVM", "Failed to load subscriptions (quiet=$quiet)", it)
                    if (quiet) {
                        // A pull-to-refresh that fails used to be completely silent — the
                        // spinner just stopped and stale data stayed on screen, so the user
                        // could act on numbers that never actually refreshed. Keep the old
                        // data (blanking the screen on a refresh failure is worse) but say so.
                        _error.value = it.toAppError()
                    } else {
                        _state.value = Async.Failure(it.toAppError())
                    }
                }
            _refreshing.value = false
        }
    }

    /** Teacher, or staff acting as one teacher. Staff on the all-teachers list never create a group. */
    fun createGroup(name: String, level: String) {
        if (isStaff && asTeacherId == null) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                asTeacherId?.let { AdminRepository.createGroupFor(it, name, level) }
                    ?: TeacherRepository.createGroup(name, level)
            }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /** Teacher, or staff acting as one teacher. */
    fun updateGroup(id: String?, name: String, level: String) {
        if ((isStaff && asTeacherId == null) || id == null) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { TeacherRepository.updateGroup(id, name, level, ownerId = asTeacherId) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /**
     * Sets or clears a group's picture. Unlike create/edit/activate, this is available in
     * both scopes: the owning teacher and owner/admin staff both have full control over it.
     */
    fun setGroupPhoto(groupId: String?, photoUrl: String?) {
        if (groupId == null) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                if (isStaff) AdminRepository.setGroupPhoto(groupId, photoUrl)
                else TeacherRepository.setGroupPhoto(groupId, photoUrl)
            }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /** Teacher: sends the group for owner/admin approval. Staff acting as a teacher: approves it at once. */
    fun activateGroup(groupId: String?) {
        if ((isStaff && asTeacherId == null) || groupId == null) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { TeacherRepository.activateGroup(groupId) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /**
     * Staff: approves a PENDING/REJECTED subscription.
     * Teacher: resubmits their own REJECTED subscription back to PENDING.
     */
    fun activateSubscription(subscriptionId: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                if (isStaff) AdminRepository.activateSubscription(subscriptionId)
                else TeacherRepository.activateSubscription(subscriptionId)
            }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /** Staff-only: rejects a PENDING subscription. */
    fun rejectSubscription(subscriptionId: String, note: String = "") {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { AdminRepository.rejectSubscription(subscriptionId, note) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    fun deleteGroup(id: String?, groupName: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                if (isStaff) AdminRepository.adminDeleteGroup(id, groupName)
                else TeacherRepository.deleteGroup(id, groupName)
            }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    fun renew(subscriptionId: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                if (isStaff) AdminRepository.adminRenewSubscription(subscriptionId)
                else TeacherRepository.renewSubscription(subscriptionId)
            }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    /**
     * [teacherIdForStaff] is required (non-blank) when [scope] is [SubsScope.Staff].
     * [studentUserId] is the learner's actual registered account, picked via the add-student
     * form's email/name search — [level] is no longer typed by hand there, it's carried through
     * from the group automatically.
     */
    fun save(
        id: String?, groupName: String, parentName: String, studentName: String,
        startDate: String, amount: Double, currency: String, nextRenewalDate: String,
        status: String, notes: String, level: String = "", parentPhone: String = "",
        studentUserId: String? = null, teacherIdForStaff: String? = null, onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val tid = asTeacherId
                if (tid != null) {
                    AdminRepository.saveSubscriptionFor(
                        id, tid, normalizeGroupName(groupName), parentName, studentName, startDate,
                        amount, currency, nextRenewalDate, status, notes, level, parentPhone, studentUserId,
                    )
                } else if (isStaff) {
                    AdminRepository.adminSaveSubscription(
                        id, teacherIdForStaff.orEmpty(), normalizeGroupName(groupName), parentName, studentName,
                        startDate, amount, currency, nextRenewalDate, status, notes, level, studentUserId,
                    )
                } else {
                    TeacherRepository.saveSubscription(
                        id, normalizeGroupName(groupName), parentName, studentName, startDate,
                        amount, currency, nextRenewalDate, status, notes, level, parentPhone, studentUserId,
                    )
                }
            }.onSuccess { onDone(); load() }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            // This was the one action in this ViewModel with a bare runCatching and no
            // onFailure: a delete that the server refused (FORBIDDEN, or a subscription with
            // earnings already recorded against it) simply reloaded the list with the row
            // still in it and no explanation at all, which reads as "the button is broken".
            runCatching {
                if (isStaff) AdminRepository.adminDeleteSubscription(id)
                else TeacherRepository.deleteSubscription(id)
            }.onFailure {
                android.util.Log.e("SubscriptionsVM", "Failed to delete subscription id=$id", it)
                _error.value = it.toAppError()
            }
            _busy.value = false
            load()
        }
    }

    fun clearError() { _error.value = null }
}

/** Creates a [SubscriptionsViewModel] scoped to [scope] (survives recomposition, not navigation — same lifetime as before). */
@Composable
private fun rememberSubsViewModel(scope: SubsScope): SubscriptionsViewModel {
    return viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SubscriptionsViewModel(scope) as T
        },
    )
}

// ── Group name normalization ───────────────────────────────────────────────

/** Normalizes a group name: trims whitespace and collapses multiple spaces. */
internal fun normalizeGroupName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ")

/** Case-insensitive key used for deduplication in group maps. */
internal fun normalizeKey(name: String): String =
    normalizeGroupName(name).lowercase()

// ── Helper: derive groups from flat subscription list ──────────────────────

data class SubscriptionGroup(
    val name: String,
    val subscriptions: List<Subscription>,
    val groupId: String? = null,
    val levelFromGroup: String? = null,
    /** Approval status for groups created via the approval system. */
    val approvalStatus: String = "APPROVED",
) {
    /** True while the group is created but not yet activated. */
    val isInactive: Boolean get() = approvalStatus == "INACTIVE"
    /** True when this group is awaiting admin approval. */
    val isPending: Boolean get() = approvalStatus == "PENDING"
    val isRejected: Boolean get() = approvalStatus == "REJECTED"
    val isApproved: Boolean get() = approvalStatus == "APPROVED"
    /** All members are shown (PENDING/REJECTED included), but only APPROVED
     *  subscriptions count toward financial totals. */
    val approvedSubscriptions: List<Subscription> get() = subscriptions.filter { it.approvalStatus == "APPROVED" }
    val personCount: Int get() = subscriptions.size
    val totalMonthly: Double get() = approvedSubscriptions.sumOf { it.monthlyAmount }
    val currency: String get() = approvedSubscriptions.firstOrNull()?.currency ?: subscriptions.firstOrNull()?.currency ?: "EGP"
    val nextRenewalDate: String get() = approvedSubscriptions.minOfOrNull { it.nextRenewalDate } ?: ""
    val hasActive: Boolean get() = subscriptions.any {
        it.approvalStatus == "APPROVED" && (it.status == "ACTIVE" || it.status == "DUE" || it.status == "OVERDUE")
    }
    val level: String get() = levelFromGroup
        ?: subscriptions.firstOrNull { !it.level.isNullOrBlank() }?.level
        ?: ""
}

fun List<Subscription>.toGroups(existingGroups: List<TeacherGroup> = emptyList()): List<SubscriptionGroup> {
    // teacher_groups rows are the source of truth for created groups (their
    // approval_status comes straight from the row). Subscriptions are merged in
    // case-insensitively; PENDING/REJECTED members stay visible in the group.
    val subsByKey = groupBy { normalizeKey(it.groupName) }
    val groupMap = mutableMapOf<String, SubscriptionGroup>()
    existingGroups.forEach { g ->
        val key = normalizeKey(g.name)
        groupMap[key] = SubscriptionGroup(
            name = normalizeGroupName(g.name),
            subscriptions = (subsByKey[key] ?: emptyList()).sortedBy { it.studentName },
            groupId = g.id,
            levelFromGroup = g.level,
            approvalStatus = g.approvalStatus,
        )
    }
    subsByKey.forEach { (key, subs) ->
        val existing = groupMap[key]
        groupMap[key] = SubscriptionGroup(
            name = existing?.name ?: normalizeGroupName(subs.first().groupName),
            subscriptions = subs.sortedBy { it.studentName },
            groupId = existing?.groupId,
            levelFromGroup = existing?.levelFromGroup ?: subs.firstOrNull { !it.level.isNullOrBlank() }?.level,
            approvalStatus = existing?.approvalStatus ?: "APPROVED",
        )
    }
    return groupMap.values.sortedBy { it.name.lowercase() }
}

// ── Group color palette ────────────────────────────────────────────────────

private val GroupColors = listOf(
    Color(0xFF7C4DFF), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFF00C853), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFF2196F3), // Blue
    Color(0xFFF44336), // Red
    Color(0xFF9C27B0), // Deep Purple
    Color(0xFF00BCD4), // Cyan
    Color(0xFF8BC34A), // Light Green
    Color(0xFFFF5722), // Deep Orange
)

internal fun groupColor(name: String): Color {
    return GroupColors[Math.floorMod(name.hashCode(), GroupColors.size)]
}

internal fun groupInitials(name: String): String {
    val parts = name.split(" ", limit = 2)
    return if (parts.size >= 2) {
        "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}"
    } else {
        name.take(2)
    }
}

// ── Urgency helper ─────────────────────────────────────────────────────────

private fun daysUntilRenewal(renewalDate: String): Long? {
    return try {
        val target = LocalDate.parse(renewalDate)
        ChronoUnit.DAYS.between(LocalDate.now(), target)
    } catch (_: Exception) {
        null
    }
}

private fun urgencyLabel(days: Long): String = when {
    days < 0 -> "${tr(StrSub.overdue)} ${(-days).toInt()}"
    days == 0L -> tr(StrSub.dueToday)
    days == 1L -> tr(StrSub.dayLeft)
    days <= 7 -> tr(StrSub.within7Days)
    else -> "${days} ${tr(StrSub.daysLeft)}"
}

private fun urgencyColor(days: Long): Color = when {
    days < 0 -> Ink.Coral
    days == 0L -> Color(0xFF00C853) // Green — matches mockup tr(StrAdmin.dueToday)
    days <= 1 -> Color(0xFFFF6B35) // Orange
    days <= 3 -> Color(0xFFFF9800) // Amber-orange
    days <= 7 -> Ink.Teal
    else -> Ink.TextMuted
}

// ── Groups Stats Row ───────────────────────────────────────────────────────

@Composable
internal fun GroupStatsRow(groups: List<SubscriptionGroup>, allSubs: List<Subscription>) {
    // Only count APPROVED subscriptions toward stats
    val approvedSubs = allSubs.filter { it.approvalStatus == "APPROVED" }
    val totalPeople = approvedSubs.size
    // Every approved subscription bills monthlyAmount every month by definition, so this is
    // simply their total — not filtered by whether nextRenewalDate happens to land in the
    // current calendar month. That earlier filter made the figure disagree with the "monthly"
    // total shown on the earnings screen for the exact same active group, since a subscription
    // only has a renewal date inside "this" month for one billing cycle out of every twelve.
    val dueThisMonth = approvedSubs.sumOf { it.monthlyAmount }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupStatCard(
            label = tr(StrSub.totalGroups),
            value = groups.size.toString(),
            modifier = Modifier.weight(1f),
        )
        GroupStatCard(
            label = tr(StrSub.totalPeople),
            value = totalPeople.toString(),
            modifier = Modifier.weight(1f),
        )
        GroupStatCard(
            label = tr(StrSub.dueThisMonth),
            value = formatMoney(dueThisMonth, "EGP"),
            modifier = Modifier.weight(1f),
            valueColor = Ink.Amber,
        )
    }
}

@Composable
private fun GroupStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink.TextPrimary,
) {
    InkCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ── Approval status badge ──────────────────────────────────────────────────
// Clear in-place status for groups the teacher manages:
// ⚪ inactive · 🟡 awaiting approval · 🟢 active · 🔴 rejected

@Composable
internal fun ApprovalStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status.uppercase()) {
        "INACTIVE" -> Triple(Ink.TextSecondary.copy(alpha = 0.12f), Ink.TextSecondary, tr(StrSub.inactiveLabel))
        "PENDING" -> Triple(Ink.Amber.copy(alpha = 0.14f), Ink.Amber, tr(StrSub.awaitingApproval))
        "REJECTED" -> Triple(Ink.Coral.copy(alpha = 0.14f), Ink.Coral, tr(StrSub.rejectedLabel))
        else -> Triple(Ink.Teal.copy(alpha = 0.14f), Ink.Teal, tr(StrSub.approvedLabel))
    }
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

// ── Group Card ─────────────────────────────────────────────────────────────

@Composable
internal fun GroupCard(
    group: SubscriptionGroup,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onActivate: (() -> Unit)? = null,
) {
    val color = groupColor(group.name)
    InkCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored circle badge — clickable to navigate
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    groupInitials(group.name),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Info — clickable to navigate
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(
                    group.name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.level.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${tr(StrSub.level)}: ${group.level}",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${tr(StrSub.groupMembers)}: ${group.personCount} · ${formatMoney(group.totalMonthly, group.currency)}",
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                // Inactive / pending / rejected groups show their status directly
                // on the card; only active groups show the next renewal date.
                if (!group.isApproved) {
                    Spacer(Modifier.height(6.dp))
                    ApprovalStatusBadge(group.approvalStatus)
                } else {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${tr(StrSub.nextRenewalDate)}: ${formatDate(group.nextRenewalDate)}",
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Trailing actions: edit/delete always, activate for inactive or
            // rejected groups, and the active/paused pill for approved groups.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onEdit != null || onDelete != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onEdit != null) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = tr(StrSub.editGroup), tint = Ink.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = tr(Str.delete), tint = Ink.Coral.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (onActivate != null && (group.isInactive || group.isRejected)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.Amber.copy(alpha = 0.15f))
                            .clickable(onClick = onActivate)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            tr(StrSub.activateGroup),
                            color = Ink.Amber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (group.isApproved) {
                    when {
                        group.hasActive -> StatusPill("ACTIVE")
                        else -> StatusPill("PAUSED")
                    }
                }
            }
        }
    }
}

// ── Main Screen: Groups Listing ────────────────────────────────────────────

@Composable
fun SubscriptionsScreen(
    navController: NavHostController,
    session: SessionViewModel,
    scope: SubsScope = SubsScope.Teacher,
    /** Staff only: when set (chosen upstream on the teacher dashboard), only
     * this teacher's groups/subscriptions are shown — no in-screen teacher
     * picker is needed since the choice was already made. */
    teacherIdFilter: String? = null,
) {
    val isStaff = scope.isStaffScope
    val teacherLike = scope.teacherLike
    val asTeacherId = scope.scopedTeacherId
    val asTeacherName = (scope as? SubsScope.StaffOf)?.teacherName.orEmpty()
    val vm: SubscriptionsViewModel = rememberSubsViewModel(scope)
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<Subscription?>(null) }
    var confirmDelete by remember { mutableStateOf<Subscription?>(null) }
    var confirmRenew by remember { mutableStateOf<Subscription?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateGroup by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }
    var confirmActivateGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }
    var presetGroupName by remember { mutableStateOf<String?>(null) }
    var presetGroupLevel by remember { mutableStateOf<String?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }

    confirmDelete?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.deleteSubscription),
            body = tr(StrSub.deleteConfirm),
            confirmLabel = tr(Str.delete),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = { vm.delete(sub.id); confirmDelete = null },
        )
    }

    confirmRenew?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.renew),
            body = tr(StrSub.renewConfirm),
            confirmLabel = tr(StrSub.renew),
            onDismiss = { confirmRenew = null },
            onConfirm = { vm.renew(sub.id); confirmRenew = null },
        )
    }

    confirmDeleteGroup?.let { group ->
        ConfirmDialog(
            title = tr(StrSub.deleteGroup),
            body = tr(StrSub.deleteGroupConfirm),
            confirmLabel = tr(Str.delete),
            destructive = true,
            onDismiss = { confirmDeleteGroup = null },
            onConfirm = { vm.deleteGroup(group.groupId, group.name); confirmDeleteGroup = null },
        )
    }

    confirmActivateGroup?.let { group ->
        ConfirmDialog(
            title = tr(StrSub.activateGroup),
            body = tr(StrSub.activateGroupConfirm),
            confirmLabel = tr(StrSub.activateGroup),
            onDismiss = { confirmActivateGroup = null },
            onConfirm = { vm.activateGroup(group.groupId); confirmActivateGroup = null },
        )
    }

    // Groups are teacher-owned — staff on the all-teachers list never creates/edits one; staff
    // acting as one teacher (StaffOf) get the teacher's own dialogs.
    if (showCreateGroup && teacherLike) {
        CreateGroupDialog(
            onDismiss = { showCreateGroup = false },
            onCreate = { name, level ->
                vm.createGroup(name, level)
                showCreateGroup = false
            },
            busy = busy,
        )
    }

    if (teacherLike) {
        editGroup?.let { group ->
            CreateGroupDialog(
                title = tr(StrSub.editGroup),
                initialName = group.name,
                initialLevel = group.level.orEmpty(),
                onDismiss = { editGroup = null },
                onCreate = { name, level ->
                    vm.updateGroup(group.groupId, name, level)
                    editGroup = null
                },
                busy = busy,
            )
        }
    }

    if (showForm) {
        if (isStaff && !teacherLike) {
            AdminSubscriptionForm(
                subscription = editingSub,
                onDismiss = { showForm = false; editingSub = null; presetGroupName = null },
                onSave = { id, tid, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, suid, onDone ->
                    vm.save(id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, studentUserId = suid, teacherIdForStaff = tid) { onDone() }
                },
                busy = busy,
                error = error,
                presetGroupName = presetGroupName,
            )
        } else {
            SubscriptionForm(
                subscription = editingSub,
                onDismiss = { showForm = false; editingSub = null; presetGroupName = null; presetGroupLevel = null },
                onSave = { id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, pp, suid, onDone ->
                    vm.save(id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, pp, suid) { onDone() }
                },
                busy = busy,
                error = error,
                presetGroupName = presetGroupName,
                presetLevel = presetGroupLevel,
            )
        }
    }

    val allSubs = when (val s = state) {
        is Async.Success -> s.value.subscriptions
        else -> emptyList()
    }.let { subs -> if (isStaff && teacherIdFilter != null) subs.filter { it.teacherId == teacherIdFilter } else subs }
    val approvedSubs = allSubs.filter { it.approvalStatus == "APPROVED" }
    val teacherGroups = when (val s = state) {
        is Async.Success -> s.value.groups
        else -> emptyList()
    }.let { groups -> if (isStaff && teacherIdFilter != null) groups.filter { it.teacherId == teacherIdFilter } else groups }
    val groups = (if (isStaff && !teacherLike) allSubs.toGroups() else allSubs.toGroups(teacherGroups)).let { list ->
        if (searchQuery.isBlank()) list
        else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(
            when {
                asTeacherId != null -> asTeacherName.ifBlank { tr(StrSub.mySubscriptions) }
                isStaff -> tr(StrSub.allSubscriptions)
                else -> tr(StrSub.mySubscriptions)
            },
            onBack = { navController.popBackStack() },
            trailing = {
                Row {
                    IconButton(onClick = {
                        navController.navigate(
                            when {
                                asTeacherId != null -> AdminRoutes.upcomingRenewals(asTeacherId, asTeacherName)
                                isStaff -> AdminRoutes.upcomingRenewals()
                                else -> TeacherRoutes.UPCOMING_RENEWALS
                            },
                        )
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = tr(StrSub.upcomingRenewals), tint = Ink.Amber)
                    }
                    if (teacherLike) {
                        IconButton(onClick = { showCreateGroup = true }) {
                            Icon(Icons.Default.Add, contentDescription = tr(StrSub.createGroup), tint = Ink.Amber)
                        }
                    }
                }
            },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Title
                    item {
                        Text(
                            tr(StrSub.groups),
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    error?.let { err ->
                        item {
                            InkCard(borderColor = Ink.Coral.copy(alpha = 0.4f)) {
                                Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                SecondaryAction(tr(Str.close)) { vm.clearError() }
                            }
                        }
                    }

                    // Search bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(tr(StrSub.searchGroup), color = Ink.TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink.TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Ink.Amber,
                                unfocusedBorderColor = Ink.Hairline,
                                focusedContainerColor = Ink.Surface,
                                unfocusedContainerColor = Ink.Surface,
                            ),
                            singleLine = true,
                        )
                    }

                    // Stats (only APPROVED subscriptions counted)
                    item { GroupStatsRow(groups, approvedSubs) }

                    // Groups list
                    if (groups.isEmpty()) {
                        item {
                            if (isStaff && !teacherLike) {
                                EmptyBlock(tr(StrSub.noGroups), tr(StrSub.noGroupsBody))
                            } else {
                                EmptyBlock(
                                    tr(StrSub.noGroups),
                                    tr(StrSub.noGroupsBody),
                                    actionLabel = tr(StrSub.createFirstPerson),
                                    onAction = { editingSub = null; showForm = true },
                                )
                            }
                        }
                    } else {
                        items(groups, key = { it.name }) { group ->
                            GroupCard(
                                group = group,
                                onClick = {
                                    val encoded = URLEncoder.encode(group.name, "UTF-8")
                                    navController.navigate(
                                        when {
                                            asTeacherId != null -> AdminRoutes.subscriptionGroup(group.name, asTeacherId, asTeacherName)
                                            isStaff -> "admin/subscriptions/group/$encoded"
                                            else -> "teacher/subscriptions/group/$encoded"
                                        },
                                    )
                                },
                                onDelete = { confirmDeleteGroup = group },
                                onEdit = if (!teacherLike) null else ({ editGroup = group }),
                                onActivate = if (!teacherLike) null else ({ confirmActivateGroup = group }),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Group Detail Screen ────────────────────────────────────────────────────

/**
 * The group's circular picture. Shows the photo when one is set, otherwise the color/initials
 * badge used everywhere else. When [editable], a camera badge lets the owning teacher (or
 * owner/admin staff, from the staff-scoped copy of this screen) pick a new photo from the
 * phone and upload it straight away — [onPicked] receives the finished public URL, or null
 * when the photo is removed.
 */
@Composable
private fun GroupPhotoSlot(
    photoUrl: String?,
    fallbackColor: Color,
    fallbackInitials: String,
    editable: Boolean,
    busy: Boolean,
    onPicked: (String?) -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker()
    var uploading by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    fallbackInitials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (uploading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Ink.Amber)
                    .clickable(enabled = !uploading && !busy) {
                        picker.pick("image/*") { uri ->
                            scope.launch {
                                uploading = true
                                runCatching {
                                    val file = MediaRepository.read(context, uri, MediaRepository.MAX_AVATAR_BYTES)
                                    MediaRepository.upload(file, "group-photos")
                                }.onSuccess { onPicked(it) }
                                uploading = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = tr(StrSub.editGroup),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
            // Separate from picking a new one — a one-tap clear straight back to the
            // color/initials badge, no re-upload dialog needed.
            if (!photoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Ink.Coral)
                        .clickable(enabled = !uploading && !busy) { onPicked(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = tr(StrEx.removeFile),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionGroupDetailScreen(
    navController: NavHostController,
    session: SessionViewModel,
    groupName: String,
    scope: SubsScope = SubsScope.Teacher,
) {
    val isStaff = scope.isStaffScope
    val teacherLike = scope.teacherLike
    val vm: SubscriptionsViewModel = rememberSubsViewModel(scope)
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<Subscription?>(null) }
    var confirmDelete by remember { mutableStateOf<Subscription?>(null) }
    var confirmRenew by remember { mutableStateOf<Subscription?>(null) }
    val copyRenewalLink = rememberRenewalLinkCopier()
    var confirmActivateSub by remember { mutableStateOf<Subscription?>(null) }
    var confirmRejectSub by remember { mutableStateOf<Subscription?>(null) }

    confirmDelete?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.deleteSubscription),
            body = tr(StrSub.deleteConfirm),
            confirmLabel = tr(Str.delete),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = { vm.delete(sub.id); confirmDelete = null },
        )
    }

    confirmRenew?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.renew),
            body = tr(StrSub.renewConfirm),
            confirmLabel = tr(StrSub.renew),
            onDismiss = { confirmRenew = null },
            onConfirm = { vm.renew(sub.id); confirmRenew = null },
        )
    }

    confirmActivateSub?.let { sub ->
        if (isStaff) {
            ConfirmDialog(
                title = tr(StrSub.approvedLabel),
                body = "${sub.studentName} - ${sub.groupName}",
                confirmLabel = tr(StrAdmin.approve),
                onDismiss = { confirmActivateSub = null },
                onConfirm = { vm.activateSubscription(sub.id); confirmActivateSub = null },
            )
        } else {
            ConfirmDialog(
                title = tr(StrSub.activateSubscription),
                body = tr(StrSub.activateSubscriptionConfirm),
                confirmLabel = tr(StrSub.activateSubscription),
                onDismiss = { confirmActivateSub = null },
                onConfirm = { vm.activateSubscription(sub.id); confirmActivateSub = null },
            )
        }
    }

    confirmRejectSub?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.rejectedLabel),
            body = "${sub.studentName} - ${sub.groupName}",
            confirmLabel = tr(StrAdmin.reject),
            destructive = true,
            onDismiss = { confirmRejectSub = null },
            onConfirm = { vm.rejectSubscription(sub.id); confirmRejectSub = null },
        )
    }

    if (showForm) {
        if (isStaff && !teacherLike) {
            AdminSubscriptionForm(
                subscription = editingSub,
                onDismiss = { showForm = false; editingSub = null },
                onSave = { id, tid, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, suid, onDone ->
                    vm.save(id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, studentUserId = suid, teacherIdForStaff = tid) { onDone() }
                },
                busy = busy,
                error = error,
                presetGroupName = groupName,
            )
        } else {
            SubscriptionForm(
                subscription = editingSub,
                onDismiss = { showForm = false; editingSub = null },
                onSave = { id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, pp, suid, onDone ->
                    vm.save(id, gn, pn, sn, sd, amt, cur, nrd, st, nt, lv, pp, suid) { onDone() }
                },
                busy = busy,
                error = error,
                presetGroupName = groupName,
            )
        }
    }

    val allSubs = when (val s = state) {
        is Async.Success -> s.value.subscriptions
        else -> emptyList()
    }
    val teacherGroups = when (val s = state) {
        is Async.Success -> s.value.groups
        else -> emptyList()
    }
    // The group's own status drives whether it counts toward earnings. Groups
    // with no teacher_groups row (legacy) are treated as active.
    val groupRow = teacherGroups.firstOrNull { normalizeKey(it.name) == normalizeKey(groupName) }
    val groupStatus = groupRow?.approvalStatus ?: "APPROVED"
    val groupActive = groupStatus == "APPROVED"
    // All members are shown, but only members of an active group count toward
    // group totals (the server marks inactive-group members as PENDING).
    val groupSubs = allSubs.filter { normalizeKey(it.groupName) == normalizeKey(groupName) }.sortedBy { it.studentName }
    val approvedGroupSubs = groupSubs.filter { it.approvalStatus == "APPROVED" }
    val pendingGroupSubs = groupSubs.filter { it.approvalStatus == "PENDING" || it.approvalStatus == "REJECTED" }
    val groupTotal = approvedGroupSubs.sumOf { it.monthlyAmount }
    val currency = approvedGroupSubs.firstOrNull()?.currency ?: "EGP"
    val color = groupColor(groupName)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(
            groupName,
            onBack = { navController.popBackStack() },
            trailing = {
                IconButton(onClick = { editingSub = null; showForm = true }) {
                    Icon(Icons.Default.Add, contentDescription = tr(StrSub.addPerson), tint = Ink.Amber)
                }
            },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { err ->
                        item {
                            InkCard(borderColor = Ink.Coral.copy(alpha = 0.4f)) {
                                Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                SecondaryAction(tr(Str.close)) { vm.clearError() }
                            }
                        }
                    }

                    // Status legend
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(
                                Ink.Amber to tr(StrSub.dueThisWeek),
                                Ink.Coral to tr(StrSub.overdue),
                                Ink.TextMuted to tr(StrSub.activeOnly),
                            ).forEach { (col, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(col))
                                    Spacer(Modifier.width(4.dp))
                                    Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Group summary + status. Inactive/pending/rejected groups show
                    // their status here and do not count toward earnings.
                    item {
                        InkCard(borderColor = color.copy(alpha = 0.25f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The owning teacher and owner/admin staff can both set this —
                                // everyone else just sees the picture (or the color initials).
                                GroupPhotoSlot(
                                    photoUrl = groupRow?.photoUrl,
                                    fallbackColor = color,
                                    fallbackInitials = groupInitials(groupName),
                                    editable = groupRow?.id != null,
                                    busy = busy,
                                    onPicked = { url -> vm.setGroupPhoto(groupRow?.id, url) },
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(groupName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        ApprovalStatusBadge(groupStatus)
                                    }
                                    val groupLevel = groupSubs.firstOrNull { !it.level.isNullOrBlank() }?.level
                                    if (!groupLevel.isNullOrBlank()) {
                                        Text(
                                            "${tr(StrSub.level)}: $groupLevel",
                                            color = Ink.TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        "${groupSubs.size} ${tr(StrSub.totalPeople)} · ${formatMoney(groupTotal, currency)}",
                                        color = Ink.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (!groupActive) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    when (groupStatus) {
                                        "PENDING" -> tr(StrSub.groupPendingHint)
                                        "REJECTED" -> tr(StrSub.groupRejectedHint)
                                        else -> tr(StrSub.groupInactiveHint)
                                    },
                                    color = if (groupStatus == "REJECTED") Ink.Coral else Ink.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                // Group-level activation is the teacher's action (or staff acting as the
                                // teacher, where it takes effect at once); staff on the all-teachers list
                                // approve/reject at the subscription level.
                                if (teacherLike && (groupStatus == "INACTIVE" || groupStatus == "REJECTED")) {
                                    Spacer(Modifier.height(10.dp))
                                    SecondaryAction(tr(StrSub.activateGroup), enabled = !busy) {
                                        vm.activateGroup(groupRow?.id)
                                    }
                                }
                            }
                        }
                    }

                    if (groupSubs.isEmpty()) {
                        item {
                            EmptyBlock(
                                tr(StrSub.noSubscriptions),
                                tr(StrSub.noSubscriptionsBody),
                                actionLabel = tr(StrSub.addPerson),
                                onAction = { editingSub = null; showForm = true },
                            )
                        }
                    } else {
                        items(groupSubs, key = { it.id }) { sub ->
                            PersonCard(
                                sub = sub,
                                onEdit = { editingSub = sub; showForm = true },
                                onDelete = { confirmDelete = sub },
                                onRenew = { confirmRenew = sub },
                                onActivate = if (sub.isPending || sub.isRejected) {{ confirmActivateSub = sub }} else null,
                                groupActive = groupActive,
                                showApprovalStatus = true,
                                onCopyRenewalLink = if (sub.isApproved) {{ copyRenewalLink(sub) }} else null,
                            )
                            // Staff gets an explicit reject action for pending requests
                            // (teacher only ever resubmits their own via onActivate above).
                            if (isStaff && sub.isPending) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    SecondaryAction(
                                        tr(StrAdmin.reject),
                                        tint = Ink.Coral,
                                        enabled = !busy,
                                    ) { confirmRejectSub = sub }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Renewal link ───────────────────────────────────────────────────────────

/**
 * Copies the student's renewal payment link (web-pay page) to the clipboard.
 *
 * The page only lets the student who owns the subscription renew it, so a row that was never
 * linked to a student account cannot use the link at all — that case explains itself instead of
 * silently handing over a link that would lead nowhere.
 */
@Composable
internal fun rememberRenewalLinkCopier(): (Subscription) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { sub ->
            if (sub.studentUserId.isNullOrBlank()) {
                Toast.makeText(context, tr(StrSub.renewalLinkNoAccount), Toast.LENGTH_LONG).show()
            } else {
                clipboard.setText(AnnotatedString(PayWeb.renewalUrl(sub.id)))
                Toast.makeText(context, tr(StrSub.renewalLinkCopied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ── Person Card (inside group) ─────────────────────────────────────────────

@Composable
internal fun PersonCard(
    sub: Subscription,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRenew: () -> Unit,
    onActivate: (() -> Unit)? = null,
    groupActive: Boolean = true,
    showApprovalStatus: Boolean = true,
    onCopyRenewalLink: (() -> Unit)? = null,
) {
    // Members are managed directly now; the approval badge is only meaningful
    // for legacy rows (showApprovalStatus) — the teacher view keeps it hidden.
    val isPending = showApprovalStatus && sub.isPending
    val isRejected = showApprovalStatus && sub.isRejected
    val accent = when {
        isPending -> Ink.Amber
        isRejected -> Ink.Coral
        else -> Ink.Amber
    }
    val borderCol = when {
        isPending -> Ink.Amber.copy(alpha = 0.3f)
        isRejected -> Ink.Coral.copy(alpha = 0.5f)
        sub.status == "OVERDUE" -> Ink.Coral.copy(alpha = 0.6f)
        daysUntilRenewal(sub.nextRenewalDate)?.let { it <= 3 } == true -> Ink.Amber.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    InkCard(contentPadding = PaddingValues(16.dp), borderColor = borderCol) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isPending || isRejected) accent.copy(alpha = 0.15f) else Ink.AmberSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sub.studentName.take(1).uppercase(),
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(sub.parentName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(1.dp))
                Text(sub.studentName, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (!sub.level.isNullOrBlank()) {
                    Text(sub.level, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                // How this subscription was paid for: from the app, or added by hand by the teacher.
                if (!isPending && !isRejected && sub.paymentSource != null) {
                    Spacer(Modifier.height(3.dp))
                    PaymentSourcePill(sub.paymentSource)
                }
                // Approval status badge (legacy PENDING / REJECTED rows only)
                if (isPending || isRejected) {
                    Spacer(Modifier.height(3.dp))
                    ApprovalStatusBadge(sub.approvalStatus)
                }
                Spacer(Modifier.height(2.dp))
                val days = daysUntilRenewal(sub.nextRenewalDate)
                if (days != null && !isPending && !isRejected) {
                    Text(
                        formatDate(sub.nextRenewalDate),
                        color = urgencyColor(days),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(sub.monthlyAmount, sub.currency),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                when {
                    isPending || isRejected -> {
                        // Show activation button for teacher to request approval
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = tr(Str.edit), tint = Ink.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = tr(Str.delete), tint = Ink.Coral.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                            if (onActivate != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Ink.Amber.copy(alpha = 0.15f))
                                        .clickable(onClick = onActivate)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        tr(StrSub.activateSubscription),
                                        color = Ink.Amber,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Edit / delete always; renew only when the group is active.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = tr(Str.edit), tint = Ink.TextMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = tr(Str.delete), tint = Ink.Coral.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                            if (onCopyRenewalLink != null) {
                                IconButton(
                                    onClick = onCopyRenewalLink,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = tr(StrSub.copyRenewalLink), tint = Ink.Amber, modifier = Modifier.size(18.dp))
                                }
                            }
                            val daysLeft = daysUntilRenewal(sub.nextRenewalDate)
                            val renewalOpen = daysLeft == null || daysLeft <= 0
                            val canRenew = groupActive && renewalOpen
                            TextButton(
                                onClick = onRenew,
                                enabled = canRenew,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ink.Amber.copy(alpha = if (canRenew) 0.15f else 0.06f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    when {
                                        !groupActive -> tr(StrSub.groupNotActive)
                                        !renewalOpen -> trf(StrSub.renewsInDays, daysLeft)
                                        else -> tr(StrSub.payNow)
                                    },
                                    color = Ink.Amber.copy(alpha = if (canRenew) 1f else 0.5f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Upcoming Renewals Screen ───────────────────────────────────────────────

@Composable
fun UpcomingRenewalsScreen(
    navController: NavHostController,
    session: SessionViewModel,
    scope: SubsScope = SubsScope.Teacher,
) {
    val renewalsTitle = (scope as? SubsScope.StaffOf)?.teacherName?.takeIf { it.isNotBlank() }
    val vm: SubscriptionsViewModel = rememberSubsViewModel(scope)
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var confirmRenew by remember { mutableStateOf<Subscription?>(null) }
    val copyRenewalLink = rememberRenewalLinkCopier()
    var selectedFilter by remember { mutableStateOf("all") }

    confirmRenew?.let { sub ->
        ConfirmDialog(
            title = tr(StrSub.renew),
            body = tr(StrSub.renewConfirm),
            confirmLabel = tr(StrSub.renew),
            onDismiss = { confirmRenew = null },
            onConfirm = { vm.renew(sub.id); confirmRenew = null },
        )
    }

    // Only count APPROVED subscriptions toward renewal stats
    val allSubs = (when (val s = state) {
        is Async.Success -> s.value.subscriptions
        else -> emptyList()
    }).filter { it.approvalStatus == "APPROVED" }

    val now = LocalDate.now()
    val filteredSubs = when (selectedFilter) {
        "today" -> allSubs.filter { try { LocalDate.parse(it.nextRenewalDate) == now } catch (_: Exception) { false } }
        "tomorrow" -> allSubs.filter { try { LocalDate.parse(it.nextRenewalDate) == now.plusDays(1) } catch (_: Exception) { false } }
        "7days" -> allSubs.filter {
            try {
                val d = LocalDate.parse(it.nextRenewalDate)
                !d.isBefore(now) && !d.isAfter(now.plusDays(7))
            } catch (_: Exception) { false }
        }
        "30days" -> allSubs.filter {
            try {
                val d = LocalDate.parse(it.nextRenewalDate)
                !d.isBefore(now) && !d.isAfter(now.plusDays(30))
            } catch (_: Exception) { false }
        }
        else -> allSubs
    }.sortedBy { it.nextRenewalDate }

    // Stats
    val dueToday = allSubs.filter {
        try { LocalDate.parse(it.nextRenewalDate) == now } catch (_: Exception) { false }
    }
    val due7Days = allSubs.filter {
        try {
            val d = LocalDate.parse(it.nextRenewalDate)
            !d.isBefore(now) && !d.isAfter(now.plusDays(7))
        } catch (_: Exception) { false }
    }
    val due30Days = allSubs.filter {
        try {
            val d = LocalDate.parse(it.nextRenewalDate)
            !d.isBefore(now) && !d.isAfter(now.plusDays(30))
        } catch (_: Exception) { false }
    }

    // Group by date sections
    val dateGroups = filteredSubs.groupBy { sub ->
        try {
            val d = LocalDate.parse(sub.nextRenewalDate)
            val days = ChronoUnit.DAYS.between(now, d)
            when {
                days < 0 -> tr(StrSub.overdue)
                days == 0L -> "${tr(StrSub.dueToday)} - ${formatDate(sub.nextRenewalDate)}"
                days == 1L -> "${tr(StrSub.tomorrow)} - ${formatDate(sub.nextRenewalDate)}"
                days <= 7 -> tr(StrSub.within7Days)
                else -> tr(StrSub.within30Days)
            }
        } catch (_: Exception) { tr(StrSub.upcomingRenewals) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(
            renewalsTitle?.let { "${tr(StrSub.upcomingRenewals)} · $it" } ?: tr(StrSub.upcomingRenewals),
            onBack = { navController.popBackStack() },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Filter tabs
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(
                                "today" to tr(StrSub.today),
                                "tomorrow" to tr(StrSub.tomorrow),
                                "7days" to tr(StrSub.within7Days),
                                "30days" to tr(StrSub.within30Days),
                                "all" to tr(StrSub.allStatuses),
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = selectedFilter == code,
                                    onClick = { selectedFilter = code },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Ink.Amber,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Ink.Surface,
                                        labelColor = Ink.TextSecondary,
                                    ),
                                )
                            }
                        }
                    }

                    // Stats row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RenewalStatCard(
                                label = tr(StrSub.dueToday),
                                count = dueToday.size.toString(),
                                amount = formatMoney(dueToday.sumOf { it.monthlyAmount }, "EGP"),
                                modifier = Modifier.weight(1f),
                                accent = Ink.Amber,
                            )
                            RenewalStatCard(
                                label = tr(StrSub.within7Days),
                                count = due7Days.size.toString(),
                                amount = formatMoney(due7Days.sumOf { it.monthlyAmount }, "EGP"),
                                modifier = Modifier.weight(1f),
                                accent = Ink.Teal,
                            )
                            RenewalStatCard(
                                label = tr(StrSub.within30Days),
                                count = due30Days.size.toString(),
                                amount = formatMoney(due30Days.sumOf { it.monthlyAmount }, "EGP"),
                                modifier = Modifier.weight(1f),
                                accent = Ink.TextPrimary,
                            )
                            RenewalStatCard(
                                label = tr(StrSub.totalExpected),
                                count = due30Days.size.toString(),
                                amount = formatMoney(due30Days.sumOf { it.monthlyAmount }, "EGP"),
                                modifier = Modifier.weight(1f),
                                accent = Ink.Amber,
                                highlight = true,
                            )
                        }
                    }

                    // Date-grouped sections
                    if (filteredSubs.isEmpty()) {
                        item {
                            EmptyBlock(tr(StrSub.noRenewalsDue), tr(StrSub.noRenewalsDueBody))
                        }
                    } else {
                        dateGroups.forEach { (dateLabel, subs) ->
                            // Date section header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        dateLabel,
                                        color = Ink.TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(Icons.Default.CalendarMonth, null, tint = Ink.TextMuted, modifier = Modifier.size(20.dp))
                                }
                            }

                            items(subs, key = { it.id }) { sub ->
                                RenewalPersonCard(sub = sub, onPay = { confirmRenew = sub }, onCopyLink = { copyRenewalLink(sub) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Renewal Stat Card ──────────────────────────────────────────────────────

@Composable
internal fun RenewalStatCard(
    label: String,
    count: String,
    amount: String,
    modifier: Modifier = Modifier,
    accent: Color = Ink.TextPrimary,
    highlight: Boolean = false,
) {
    InkCard(
        modifier,
        contentPadding = PaddingValues(10.dp),
        borderColor = if (highlight) Ink.Amber.copy(alpha = 0.5f) else null,
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            count,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            amount,
            color = Ink.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

// ── Renewal Person Card ────────────────────────────────────────────────────

@Composable
internal fun RenewalPersonCard(
    sub: Subscription,
    onPay: () -> Unit,
    onCopyLink: (() -> Unit)? = null,
) {
    val days = daysUntilRenewal(sub.nextRenewalDate)
    val color = groupColor(sub.groupName)

    InkCard(contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sub.studentName.take(1).uppercase(),
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(sub.studentName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(sub.groupName, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (!sub.level.isNullOrBlank()) {
                    Text(sub.level, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                if (days != null) {
                    // Urgency badge with colored background
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(urgencyColor(days).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            urgencyLabel(days),
                            color = urgencyColor(days),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(sub.monthlyAmount, sub.currency),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onCopyLink != null) {
                        IconButton(onClick = onCopyLink, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Link, contentDescription = tr(StrSub.copyRenewalLink), tint = Ink.Amber, modifier = Modifier.size(18.dp))
                        }
                    }
                    // Renewal opens on the renewal date and not before; the server enforces it too.
                    val open = days == null || days <= 0
                    TextButton(
                        onClick = onPay,
                        enabled = open,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.Amber.copy(alpha = if (open) 0.15f else 0.06f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (open) tr(StrSub.payNow) else trf(StrSub.renewsInDays, days),
                            color = Ink.Amber.copy(alpha = if (open) 1f else 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** Read-only: when the paid month ends, and the renewal rule, shown while editing a subscription. */
@Composable
private fun RenewalRuleNote(nextRenewalDate: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.AmberSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${tr(StrSub.renewsOn)}: ${formatDate(nextRenewalDate)}",
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(tr(StrSub.renewRuleNote), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Create Group Dialog ───────────────────────────────────────────────────

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, level: String) -> Unit,
    busy: Boolean,
    title: String = tr(StrSub.createGroup),
    confirmLabel: String = tr(StrSub.createGroup),
    initialName: String = "",
    initialLevel: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var level by remember { mutableStateOf(initialLevel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InkField(name, { name = it }, tr(StrSub.groupName))
                InkField(level, { level = it }, tr(StrSub.level))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(normalizeGroupName(name), level) },
                enabled = name.isNotBlank() && !busy,
            ) {
                Text(confirmLabel, color = Ink.Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(Str.cancel), color = Ink.TextSecondary) }
        },
    )
}

// ── Student account picker (replaces manual "level" entry) ─────────────────
//
// A teacher/owner/admin now identifies a learner by their actual registered
// app account — searched by email or name — instead of typing a level by
// hand. The level itself still flows through automatically from the group.

@Composable
private fun StudentAccountField(
    selectedId: String?,
    selectedHit: ClassroomStudentHit?,
    onSelect: (ClassroomStudentHit?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ClassroomStudentHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(300)
        runCatching { TeacherRepository.searchStudentAccounts(query) }
            .onSuccess { results = it }
            .onFailure { results = emptyList() }
        searching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(StrSub.studentAccount), color = Ink.TextSecondary, style = MaterialTheme.typography.labelMedium)

        if (selectedHit != null || selectedId != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.TealSoft)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedHit?.fullName ?: selectedHit?.email ?: tr(StrSub.accountLinked),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!selectedHit?.email.isNullOrBlank()) {
                        Text(selectedHit?.email.orEmpty(), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { query = ""; onSelect(null) }) {
                    Text(tr(StrSub.clearLinkedAccount), color = Ink.Coral)
                }
            }
        } else {
            InkField(query, { query = it }, tr(StrSub.searchByEmailOrName))
            when {
                searching -> Text(tr(Str.loading), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                query.isNotBlank() && results.isEmpty() -> Text(
                    tr(StrSub.noAccountsFound), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall,
                )
            }
            results.forEach { hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SurfaceHigh)
                        .clickable { onSelect(hit); results = emptyList() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(hit.fullName ?: hit.email ?: hit.id, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        if (!hit.email.isNullOrBlank()) {
                            Text(hit.email, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (query.isBlank()) {
                Text(tr(StrSub.accountNotLinkedYet), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Form Dialog ────────────────────────────────────────────────────────────

/** The only manual status: a temporary pause. Active / due / overdue are derived by the server
 *  from the renewal date, so the teacher never picks them. */
@Composable
private fun SubscriptionPauseToggle(paused: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(StrSub.statusPaused), style = MaterialTheme.typography.bodyMedium, color = Ink.TextPrimary)
            Switch(
                checked = paused,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Ink.Amber),
            )
        }
        Text(
            tr(StrSub.statusAutoHint),
            style = MaterialTheme.typography.bodySmall,
            color = Ink.TextSecondary,
        )
    }
}

@Composable
private fun SubscriptionForm(
    subscription: Subscription?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, Double, String, String, String, String, String, String, String?, () -> Unit) -> Unit,
    busy: Boolean,
    error: AppError?,
    presetGroupName: String? = null,
    presetLevel: String? = null,
) {
    var groupName by remember(subscription) { mutableStateOf(subscription?.groupName ?: presetGroupName.orEmpty()) }
    var parentName by remember(subscription) { mutableStateOf(subscription?.parentName.orEmpty()) }
    var parentPhone by remember(subscription) { mutableStateOf(subscription?.parentPhone.orEmpty()) }
    var studentName by remember(subscription) { mutableStateOf(subscription?.studentName.orEmpty()) }
    // No longer typed by hand — carried through from the group automatically.
    val level = subscription?.level ?: presetLevel.orEmpty()
    var linkedAccount by remember(subscription) { mutableStateOf<ClassroomStudentHit?>(null) }
    var linkedAccountId by remember(subscription) { mutableStateOf(subscription?.studentUserId) }
    var startDate by remember(subscription) {
        mutableStateOf(subscription?.startDate ?: java.time.LocalDate.now().toString())
    }
    var monthlyAmount by remember(subscription) {
        mutableStateOf(subscription?.monthlyAmount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var currency by remember(subscription) { mutableStateOf(subscription?.currency ?: "EGP") }
    // Renewal is monthly and derived from the start date itself — not typed by hand. Editing an
    // existing subscription keeps its real next_renewal_date (a renewal moves the start and the end
    // together, so it is always start + one month); only a brand-new one gets start date + one month.
    val nextRenewalDate = remember(subscription, startDate) {
        subscription?.nextRenewalDate ?: runCatching {
            java.time.LocalDate.parse(startDate).plusMonths(1).toString()
        }.getOrDefault(startDate)
    }
    // The status (active / due / overdue) is NOT chosen by hand: the server derives it from the
    // renewal date. The only manual state is a temporary pause.
    var paused by remember(subscription) { mutableStateOf(subscription?.status == "PAUSED") }
    var notes by remember(subscription) { mutableStateOf(subscription?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = {
            Text(
                if (subscription != null) tr(StrSub.editSubscription) else tr(StrSub.newSubscription),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InkField(groupName, { groupName = it }, tr(StrSub.groupName))
                StudentAccountField(
                    selectedId = linkedAccountId,
                    selectedHit = linkedAccount,
                    onSelect = { hit -> linkedAccount = hit; linkedAccountId = hit?.id },
                )
                InkField(parentName, { parentName = it }, tr(StrSub.parentName))
                InkField(parentPhone, { parentPhone = it }, tr(StrSub.parentPhone), keyboardType = KeyboardType.Phone)
                InkField(studentName, { studentName = it }, tr(StrSub.studentName))
                InkField(startDate, { startDate = it }, tr(StrSub.startDate))
                if (subscription != null) RenewalRuleNote(subscription.nextRenewalDate)
                InkField(
                    monthlyAmount,
                    { monthlyAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    tr(StrSub.monthlyAmount),
                    keyboardType = KeyboardType.Decimal,
                )
                InkField(currency, { currency = it.uppercase().take(3) }, tr(StrSub.currency))

                SubscriptionPauseToggle(paused) { paused = it }

                InkField(notes, { notes = it }, tr(Str.notes))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = monthlyAmount.toDoubleOrNull() ?: 0.0
                    onSave(
                        subscription?.id, groupName, parentName, studentName,
                        startDate, amt, currency, nextRenewalDate, if (paused) "PAUSED" else "ACTIVE", notes, level, parentPhone, linkedAccountId,
                    ) { onDismiss() }
                },
                enabled = groupName.isNotBlank() && parentName.isNotBlank() && studentName.isNotBlank() &&
                    startDate.isNotBlank() && (monthlyAmount.toDoubleOrNull() ?: 0.0) > 0 && !busy,
            ) {
                Text(
                    if (subscription != null) tr(Str.save) else tr(StrSub.createSubscription),
                    color = Ink.Amber,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(Str.cancel), color = Ink.TextSecondary) }
        },
    )
}

// ── Admin Form Dialog (includes teacherId) ─────────────────────────────────
// Moved here from the now-removed AdminSubscriptionScreens.kt as part of
// unifying the subscriptions screens into one role-aware implementation.

@Composable
private fun AdminSubscriptionForm(
    subscription: Subscription?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String, Double, String, String, String, String, String, String?, () -> Unit) -> Unit,
    busy: Boolean,
    error: AppError?,
    presetGroupName: String? = null,
) {
    var teacherId by remember(subscription) { mutableStateOf(subscription?.teacherId.orEmpty()) }
    var groupName by remember(subscription) { mutableStateOf(subscription?.groupName ?: presetGroupName.orEmpty()) }
    var parentName by remember(subscription) { mutableStateOf(subscription?.parentName.orEmpty()) }
    var studentName by remember(subscription) { mutableStateOf(subscription?.studentName.orEmpty()) }
    // No longer typed by hand — carried through from the group automatically.
    val level = subscription?.level.orEmpty()
    var linkedAccount by remember(subscription) { mutableStateOf<ClassroomStudentHit?>(null) }
    var linkedAccountId by remember(subscription) { mutableStateOf(subscription?.studentUserId) }
    var startDate by remember(subscription) {
        mutableStateOf(subscription?.startDate ?: java.time.LocalDate.now().toString())
    }
    var monthlyAmount by remember(subscription) {
        mutableStateOf(subscription?.monthlyAmount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var currency by remember(subscription) { mutableStateOf(subscription?.currency ?: "EGP") }
    // Renewal is monthly and derived from the start date itself — not typed by hand. Editing an
    // existing subscription keeps its real next_renewal_date (a renewal moves the start and the end
    // together, so it is always start + one month); only a brand-new one gets start date + one month.
    val nextRenewalDate = remember(subscription, startDate) {
        subscription?.nextRenewalDate ?: runCatching {
            java.time.LocalDate.parse(startDate).plusMonths(1).toString()
        }.getOrDefault(startDate)
    }
    // The status (active / due / overdue) is NOT chosen by hand: the server derives it from the
    // renewal date. The only manual state is a temporary pause.
    var paused by remember(subscription) { mutableStateOf(subscription?.status == "PAUSED") }
    var notes by remember(subscription) { mutableStateOf(subscription?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = {
            Text(
                if (subscription != null) tr(StrSub.editSubscription) else tr(StrSub.newSubscription),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InkField(teacherId, { teacherId = it }, tr(StrSub.teacherId))
                InkField(groupName, { groupName = it }, tr(StrSub.groupName))
                StudentAccountField(
                    selectedId = linkedAccountId,
                    selectedHit = linkedAccount,
                    onSelect = { hit -> linkedAccount = hit; linkedAccountId = hit?.id },
                )
                InkField(parentName, { parentName = it }, tr(StrSub.parentName))
                InkField(studentName, { studentName = it }, tr(StrSub.studentName))
                InkField(startDate, { startDate = it }, tr(StrSub.startDate))
                if (subscription != null) RenewalRuleNote(subscription.nextRenewalDate)
                InkField(
                    monthlyAmount,
                    { monthlyAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    tr(StrSub.monthlyAmount),
                    keyboardType = KeyboardType.Decimal,
                )
                InkField(currency, { currency = it.uppercase().take(3) }, tr(StrSub.currency))

                SubscriptionPauseToggle(paused) { paused = it }

                InkField(notes, { notes = it }, tr(Str.notes))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = monthlyAmount.toDoubleOrNull() ?: 0.0
                    onSave(
                        subscription?.id, teacherId, normalizeGroupName(groupName), parentName, studentName,
                        startDate, amt, currency, nextRenewalDate, if (paused) "PAUSED" else "ACTIVE", notes, level, linkedAccountId,
                    ) { onDismiss() }
                },
                enabled = teacherId.isNotBlank() && groupName.isNotBlank() && parentName.isNotBlank() && studentName.isNotBlank() &&
                    startDate.isNotBlank() && (monthlyAmount.toDoubleOrNull() ?: 0.0) > 0 && !busy,
            ) {
                Text(
                    if (subscription != null) tr(Str.save) else tr(StrSub.createSubscription),
                    color = Ink.Amber,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(Str.cancel), color = Ink.TextSecondary) }
        },
    )
}
