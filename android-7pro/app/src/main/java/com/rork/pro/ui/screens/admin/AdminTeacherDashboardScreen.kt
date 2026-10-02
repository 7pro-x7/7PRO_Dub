package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import com.rork.pro.ui.components.ConfirmDialog
import androidx.compose.runtime.produceState
import com.rork.pro.data.TeacherEarningsOverview
import com.rork.pro.ui.components.EarningsMatrixCard
import com.rork.pro.ui.components.EarningsStream
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrStudioHub
import com.rork.pro.ui.i18n.StrTeacher
import com.rork.pro.ui.i18n.StrAdminKit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.rork.pro.ui.i18n.StrAdminX
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.Async
import com.rork.pro.data.PendingRequestCount
import com.rork.pro.data.TeacherProfile
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAdminHub
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.teacher.StudioRow
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.trf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.sp

/**
 * The two revenue figures this screen used to carry were fetched on every load and rendered
 * nowhere — two of the heaviest queries in the app, run for nothing. Earnings live on the owner
 * console and in the teacher studio; this screen is the picker and the approval queue.
 */
data class TeacherPickerData(
    val teachers: List<TeacherProfile>,
    val pendingCounts: List<PendingRequestCount>,
)

class AdminTeacherDashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<TeacherPickerData>>(Async.Loading)
    val state: StateFlow<Async<TeacherPickerData>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                TeacherPickerData(
                    teachers = AdminRepository.teachers(),
                    pendingCounts = AdminRepository.pendingRequestCounts(),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }
}

/**
 * The one place for teachers.
 *
 * Merges what used to be three separate screens — the teachers list (create, flags, shares,
 * suspend / delete), this picker with its per-teacher tools, and a "full profile" page whose
 * subscription and request lists duplicated the Approvals and Subscriptions screens.
 *
 *  • No teacher picked: summary, "new teacher", search, filters, the list (pending first).
 *  • Teacher picked: header, the four tools (approvals / subscriptions / earnings / payouts),
 *    then the account settings for that teacher.
 */
@Composable
fun AdminTeacherDashboardScreen(
    navController: NavHostController,
    session: com.rork.pro.ui.SessionViewModel,
    initialTeacherId: String? = null,
) {
    val vm: AdminTeacherDashboardViewModel = viewModel()
    val manageVm: TeachersVm = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val manageState by manageVm.state.collectAsStateWithLifecycle()
    val manageBusy by manageVm.busy.collectAsStateWithLifecycle()
    val manageError by manageVm.error.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    // Saveable, and hoisted above `when (state)`: the selection has to survive both a reload and
    // a round trip to one of the teacher's tools.
    var selectedTeacherId by rememberSaveable { mutableStateOf(initialTeacherId) }
    var confirmDelete by remember { mutableStateOf<TeacherProfile?>(null) }
    val canManage = sessionState.can("teachers.manage")

    // Counts change when a request is approved on the next screen; refresh quietly on return.
    LaunchedEffect(Unit) { vm.load(quiet = true) }
    // An account change (status, delete, new teacher) reloads the manage list — keep the picker in step.
    LaunchedEffect((manageState as? Async.Success)?.value) { vm.load(quiet = true) }

    // With a teacher picked, Back returns to the picker instead of leaving the console.
    androidx.activity.compose.BackHandler(enabled = selectedTeacherId != null && initialTeacherId == null) {
        selectedTeacherId = null
    }

    confirmDelete?.let { teacher ->
        ConfirmDialog(
            title = tr(StrAdmin.deleteTeacher),
            body = tr(StrAdmin.deleteTeacherConfirm),
            confirmLabel = tr(StrAdmin.deleteTeacher),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                manageVm.act { AdminRepository.manageTeacher(teacher.id, "DELETE") }
                confirmDelete = null
                selectedTeacherId = null
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        DetailHeader(
            tr(StrAdmin.teacherDashboardTitle),
            onBack = {
                if (selectedTeacherId != null && initialTeacherId == null) selectedTeacherId = null
                else navController.popBackStack()
            },
        )

        when (val current = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(current.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh(); manageVm.refresh() }) {
                val countByTeacher = current.value.pendingCounts.associateBy { it.teacherId }
                val pendingOf = { id: String -> (countByTeacher[id]?.pendingCount ?: 0L).toInt() }
                val allTeachers = current.value.teachers
                val suspendedCount = allTeachers.count { it.status == "SUSPENDED" }
                val teachers = allTeachers
                    .filter { teacher ->
                        search.isBlank() ||
                            teacher.profile?.fullName?.contains(search.trim(), ignoreCase = true) == true ||
                            teacher.headline?.contains(search.trim(), ignoreCase = true) == true
                    }
                    .filter {
                        when (filter) {
                            "PENDING" -> pendingOf(it.id) > 0
                            "SUSPENDED" -> it.status == "SUSPENDED"
                            else -> true
                        }
                    }
                    // Teachers with something waiting float to the top.
                    .sortedWith(compareByDescending<TeacherProfile> { pendingOf(it.id) }.thenBy { it.profile?.fullName.orEmpty() })
                val selectedTeacher = allTeachers.firstOrNull { it.id == selectedTeacherId }

                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (manageBusy) {
                        item(key = "busy") {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = Ink.Amber,
                                trackColor = Ink.SurfaceHigh,
                            )
                        }
                    }
                    manageError?.let { err ->
                        item(key = "error") {
                            Box(Modifier.clickable { manageVm.clearError() }) { AdminNote(err.message, AdminTone.Danger) }
                        }
                    }

                    if (selectedTeacher != null) {
                        val pending = pendingOf(selectedTeacher.id)
                        item(key = "selected") {
                            InkCard(borderColor = Ink.Amber.copy(alpha = 0.4f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Avatar(selectedTeacher.photoUrl ?: selectedTeacher.profile?.avatarUrl, selectedTeacher.profile?.fullName, 52.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            selectedTeacher.profile?.fullName ?: tr(StrAdmin.teacherLabel),
                                            color = Ink.TextPrimary,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        selectedTeacher.headline?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        StatusPill(selectedTeacher.status)
                                    }
                                }
                                AdminMetaRow {
                                    AdminMeta(trf(StrAdmin.studentsCount, selectedTeacher.studentsCount))
                                    selectedTeacher.commissionRate?.let {
                                        AdminMeta(trf(StrAdminKit.teacherShareShort, (it * 100).toInt()), Ink.Teal)
                                    }
                                    if (pending > 0) AdminMeta(trf(StrAdmin.pendingCount, pending), Ink.Coral)
                                }
                                if (initialTeacherId == null) {
                                    AdminActions {
                                        AdminButton(tr(StrAdminX.change), Modifier.weight(1f), Icons.Default.SwapHoriz, AdminTone.Primary) {
                                            selectedTeacherId = null
                                        }
                                    }
                                }
                            }
                        }

                        // The teacher's earnings, exactly as the teacher sees them in their studio: both
                        // read the same server RPC (teacher_earnings_overview), so they always match.
                        item(key = "earnings-${selectedTeacher.id}") {
                            val overviewState by produceState<Async<TeacherEarningsOverview>>(Async.Loading, selectedTeacher.id) {
                                value = runCatching { AdminRepository.teacherEarningsOverview(selectedTeacher.id) }
                                    .fold({ Async.Success(it) }, { Async.Failure(it.toAppError()) })
                            }
                            val overview = (overviewState as? Async.Success)?.value
                            val wallet = overview?.balance
                            val currency = wallet?.currency ?: "EGP"
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (wallet != null) {
                                    InkCard(borderColor = Ink.Amber.copy(alpha = 0.3f)) {
                                        Text(tr(StrTeacher.availableBalance), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            formatMoney(wallet.available, currency),
                                            color = Ink.Amber,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                        AdminMetaRow {
                                            AdminMeta(trf(StrStudioHub.pendingShort, formatMoney(wallet.pending, currency)))
                                            AdminMeta(trf(StrStudioHub.withdrawnShort, formatMoney(wallet.paid, currency)))
                                        }
                                    }
                                }
                                EarningsMatrixCard(
                                    title = tr(Str.earningsTitle),
                                    monthlyLabel = tr(Str.monthlyLabel),
                                    weeklyLabel = tr(Str.weeklyLabel),
                                    currency = currency,
                                    streams = listOf(
                                        EarningsStream(tr(Str.coursesStream), overview?.course?.teacherShare, Ink.Teal),
                                        EarningsStream(tr(Str.subscriptionsStream), overview?.subscriptions?.teacherShare, Ink.Amber),
                                    ),
                                )
                            }
                        }

                        item(key = "tools-title") { AdminSectionLabel(tr(StrAdminX.toolsForTeacher)) }
                        item(key = "tools") {
                            val tools = buildList {
                                add(Triple(Icons.Default.FactCheck, tr(StrAdminX.tApprovals), AdminRoutes.approvals(selectedTeacher.id)))
                                add(Triple(Icons.Default.Groups, tr(StrAdminX.tSubs), AdminRoutes.subscriptions(selectedTeacher.id, selectedTeacher.profile?.fullName.orEmpty())))
                                // This teacher's renewals only (not everyone's).
                                add(
                                    Triple(
                                        Icons.Default.Event,
                                        tr(com.rork.pro.ui.i18n.StrSub.upcomingRenewals),
                                        AdminRoutes.upcomingRenewals(selectedTeacher.id, selectedTeacher.profile?.fullName.orEmpty()),
                                    ),
                                )
                                add(Triple(Icons.Default.DateRange, tr(StrAdminX.tEarnings), AdminRoutes.subscriptionEarnings(selectedTeacher.id)))
                                if (sessionState.can("payouts.manage")) {
                                    add(Triple(Icons.Default.Payments, tr(StrAdminX.tPayouts), AdminRoutes.payouts(selectedTeacher.id)))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                tools.chunked(2).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (icon, label, route) ->
                                            val isApprovals = route.startsWith("admin/approvals")
                                            AdminShortcutTile(
                                                icon = icon,
                                                label = label,
                                                modifier = Modifier.weight(1f),
                                                tint = if (isApprovals) Ink.Coral else Ink.Amber,
                                                badge = if (isApprovals) pending else 0,
                                            ) { navController.navigate(route) }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Account settings — what the separate teachers screen used to hold.
                        if (canManage) {
                            val managed = (manageState as? Async.Success)?.value?.firstOrNull { it.id == selectedTeacher.id }
                            item(key = "account-title") { AdminSectionLabel(tr(StrAdminX.accountSettings)) }
                            item(key = "account-${selectedTeacher.id}") {
                                if (managed == null) {
                                    LoadingBlock()
                                } else {
                                    TeacherManageCard(
                                        teacher = managed,
                                        canManage = true,
                                        vm = manageVm,
                                        onDelete = { confirmDelete = managed },
                                        startExpanded = true,
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "summary") {
                            val waitingRequests = current.value.pendingCounts.sumOf { it.pendingCount }
                            AdminSummaryStrip(
                                listOf(
                                    AdminStat(allTeachers.size.toString(), tr(StrAdminX.teachersTotal)),
                                    AdminStat(waitingRequests.toString(), tr(StrAdminX.pendingRequests), if (waitingRequests > 0) Ink.Coral else null),
                                    AdminStat((allTeachers.size - suspendedCount).toString(), tr(StrAdminX.activeNow), Ink.Teal),
                                    AdminStat(suspendedCount.toString(), tr(StrAdminX.suspendedNow), if (suspendedCount > 0) Ink.Coral else null),
                                ),
                            )
                        }
                        if (canManage) {
                            item(key = "new") {
                                NewTeacherCard { userId, headline, share ->
                                    manageVm.act { AdminRepository.createTeacher(userId, headline, share) }
                                }
                            }
                        }
                        item(key = "hint") { AdminNote(tr(StrAdminX.chooseTeacherHint)) }
                        item(key = "search") { AdminSearchField(search, { search = it }, tr(StrAdmin.searchTeacher)) }
                        item(key = "filter") {
                            AdminChips(
                                listOf(
                                    "ALL" to tr(StrAdminX.allTeachers),
                                    "PENDING" to tr(StrAdminX.withPending),
                                    "SUSPENDED" to tr(StrAdminKit.suspended),
                                ),
                                filter,
                                { filter = it },
                                counts = mapOf(
                                    "ALL" to allTeachers.size,
                                    "PENDING" to allTeachers.count { pendingOf(it.id) > 0 },
                                    "SUSPENDED" to suspendedCount,
                                ),
                            )
                        }
                        if (allTeachers.isEmpty()) {
                            item { AdminEmpty(tr(StrAdmin.noTeachers), tr(StrAdmin.noTeachersBody), icon = Icons.Default.Groups) }
                        } else if (teachers.isEmpty()) {
                            item { AdminNoResults() }
                        } else {
                            items(teachers, key = { it.id }) { teacher ->
                                val pending = pendingOf(teacher.id)
                                AdminItemCard(
                                    title = teacher.profile?.fullName ?: tr(StrAdmin.teacherLabel),
                                    subtitle = listOfNotNull(
                                        teacher.headline?.takeIf { it.isNotBlank() },
                                        trf(StrAdmin.studentsCount, teacher.studentsCount),
                                    ).joinToString(" · "),
                                    leading = { Avatar(teacher.photoUrl ?: teacher.profile?.avatarUrl, teacher.profile?.fullName, 44.dp) },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (pending > 0) {
                                                Pill(trf(StrAdmin.pendingCount, pending), background = Ink.CoralSoft, foreground = Ink.Coral)
                                            } else if (teacher.status == "SUSPENDED") {
                                                StatusPill(teacher.status)
                                            }
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink.TextMuted)
                                        }
                                    },
                                    accent = when {
                                        pending > 0 -> Ink.Coral
                                        teacher.status == "SUSPENDED" -> Ink.Neutral
                                        else -> null
                                    },
                                    expandable = false,
                                    onClick = { selectedTeacherId = teacher.id },
                                )
                            }
                        }

                        // General tool, not tied to a teacher.
                        if (sessionState.can("tests.manage")) {
                            item(key = "general-title") { AdminSectionLabel(tr(StrAdmin.generalTools)) }
                            item(key = "general") {
                                AdminItemCard(
                                    title = tr(StrEx.allExercises),
                                    subtitle = tr(StrEx.allExercisesSub),
                                    leading = { AdminBadgeIcon(Icons.Default.FitnessCenter, tint = Ink.Teal) },
                                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted) },
                                    expandable = false,
                                    onClick = { navController.navigate(AdminRoutes.EXERCISES) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One half of the revenue-split row: a quiet caption above a coloured figure.
 *
 * The split used to be a single run-on sentence ("Teachers X · Platform Y") where the two
 * amounts sat side by side in the same muted body style, so neither could be read at a glance
 * and it was impossible to tell which number belonged to which side without parsing the text.
 * Splitting it into two labelled, colour-coded columns makes the comparison the point of the
 * row, which is what an owner actually looks at here.
 */
@Composable
internal fun SplitStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            color = Ink.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
