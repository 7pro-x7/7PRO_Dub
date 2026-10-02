package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.ErrorText
import com.rork.pro.data.Async
import com.rork.pro.data.ActiveSubscriptionEarnings
import com.rork.pro.data.ActiveSubscriptionGroup
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.SubscriptionEarningsGroup
import com.rork.pro.data.SubscriptionEarningsReport
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.EarningsMatrixCard
import com.rork.pro.ui.components.EarningsStream
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.tr
import androidx.compose.ui.graphics.Color
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.trf

// ── ViewModels ─────────────────────────────────────────────────────────────
//
// Both view models below answer "what are the subscriptions that exist right now billing per
// month, and my/our share of that" — the same question the Teacher Studio home and the owner
// console answer with EarningsMatrixCard. There is deliberately no period filter here any more:
// "this week" used to be a separately-queried calendar window that could disagree with "this
// month" (see the old subscription_earnings-based summary). Now weekly is always monthly ÷ 4,
// so this report screen can never show numbers that contradict the two dashboards that link
// into it. Saved monthly reports (generateReport/loadReports) are untouched — they are a
// point-in-time snapshot for record-keeping, not a live "this week/this month" figure.

class SubscriptionEarningsViewModel : ViewModel() {
    private val _overall = MutableStateFlow<Async<ActiveSubscriptionEarnings>>(Async.Loading)
    val overall: StateFlow<Async<ActiveSubscriptionEarnings>> = _overall.asStateFlow()

    private val _groups = MutableStateFlow<List<ActiveSubscriptionGroup>>(emptyList())
    val groups: StateFlow<List<ActiveSubscriptionGroup>> = _groups.asStateFlow()

    private val _reports = MutableStateFlow<Async<List<SubscriptionEarningsReport>>>(Async.Loading)
    val reports: StateFlow<Async<List<SubscriptionEarningsReport>>> = _reports.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
        loadReports()
    }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _overall.value = Async.Loading
            runCatching {
                TeacherRepository.myActiveSubscriptionEarnings() to TeacherRepository.myActiveSubscriptionGroups()
            }.onSuccess { (o, g) -> _overall.value = Async.Success(o); _groups.value = g }
                .onFailure { if (!quiet) _overall.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun loadReports() {
        viewModelScope.launch {
            _reports.value = Async.Loading
            runCatching { TeacherRepository.mySavedReports() }
                .onSuccess { _reports.value = Async.Success(it) }
                .onFailure { _reports.value = Async.Failure(it.toAppError()) }
        }
    }

    fun clearError() { _error.value = null }
}

class AdminSubscriptionEarningsViewModel : ViewModel() {
    private val _overall = MutableStateFlow<Async<ActiveSubscriptionEarnings>>(Async.Loading)
    val overall: StateFlow<Async<ActiveSubscriptionEarnings>> = _overall.asStateFlow()

    private val _groups = MutableStateFlow<List<ActiveSubscriptionGroup>>(emptyList())
    val groups: StateFlow<List<ActiveSubscriptionGroup>> = _groups.asStateFlow()

    private val _reports = MutableStateFlow<Async<List<SubscriptionEarningsReport>>>(Async.Loading)
    val reports: StateFlow<Async<List<SubscriptionEarningsReport>>> = _reports.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var currentTeacherId: String? = null

    init {
        load()
        loadReports()
    }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _overall.value = Async.Loading
            runCatching {
                AdminRepository.activeSubscriptionEarnings(currentTeacherId) to
                    AdminRepository.activeSubscriptionGroups(currentTeacherId)
            }.onSuccess { (o, g) -> _overall.value = Async.Success(o); _groups.value = g }
                .onFailure { if (!quiet) _overall.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun setTeacherFilter(teacherId: String?) {
        currentTeacherId = teacherId
        load()
    }

    fun loadReports() {
        viewModelScope.launch {
            _reports.value = Async.Loading
            runCatching { AdminRepository.savedReports() }
                .onSuccess { _reports.value = Async.Success(it) }
                .onFailure { _reports.value = Async.Failure(it.toAppError()) }
        }
    }

    fun generateReport() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { AdminRepository.generateMonthlyReport(null, null, null) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            loadReports()
        }
    }

    fun setTeacherRate(teacherId: String, percentage: Double) {
        // A commission rate outside 0..100 is never valid and silently corrupts every future
        // earning split (teacher_earning = amount * rate / 100), so it is rejected here before
        // it can reach the server rather than relying on the input field alone.
        if (percentage.isNaN() || percentage < 0.0 || percentage > 100.0) {
            _error.value = AppError("INVALID_RATE", ErrorText.invalidRate, retryable = false)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { AdminRepository.setTeacherSubscriptionRate(teacherId, percentage) }
                .onSuccess { load(quiet = true) }
                .onFailure {
                    android.util.Log.e("SubsEarnings", "Failed to set rate for teacher=$teacherId", it)
                    _error.value = it.toAppError()
                }
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }
}

// ── Active Earnings Summary ────────────────────────────────────────────────

/**
 * Weekly is always monthly ÷ 4 (same rule as EarningsMatrixCard) — derived on the device, never
 * queried, so a weekly figure can never disagree with the monthly one printed next to it.
 */
private const val WEEKS_PER_MONTH = 4.0

private fun weeklyOf(monthly: Double): Double = monthly / WEEKS_PER_MONTH

/** Small "Weekly  EGP 600" line that sits under a monthly figure. */
@Composable
private fun WeeklyCaption(
    monthly: Double,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink.TextSecondary,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(Str.weeklyLabel), color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            formatMoney(weeklyOf(monthly), "EGP"),
            color = valueColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Like KeyValueRow, but the value is a monthly figure with its weekly (÷ 4) equivalent beneath it. */
@Composable
private fun KeyValueWithWeeklyRow(label: String, monthly: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(monthly, "EGP"),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            WeeklyCaption(monthly)
        }
    }
}

/**
 * The headline card for this screen: the same EarningsMatrixCard shape used on the Teacher
 * Studio home and the owner console, so the three screens are visibly one family. Monthly is
 * what the subscriptions that exist right now bill; weekly is always that ÷ 4 (see
 * EarningsMatrixCard) — never a second, separately-queried "this week".
 *
 * showOwnerShare gates everything platform-side (the owner/admin's cut of the gross amount).
 * On the teacher's own screen this is always false: a teacher should only ever see what THEY
 * earn. Admin keeps the full gross + owner-share breakdown by leaving this at its true default.
 */
@Composable
private fun ActiveEarningsSummary(
    overall: ActiveSubscriptionEarnings,
    ratePercent: Double? = null,
    showOwnerShare: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EarningsMatrixCard(
            title = tr(StrSub.subscriptionEarningsSectionTitle),
            monthlyLabel = tr(Str.monthlyLabel),
            weeklyLabel = tr(Str.weeklyLabel),
            streams = listOf(
                EarningsStream(
                    label = tr(if (showOwnerShare) StrSub.totalEarnings else StrSub.myTotalEarnings),
                    monthly = if (showOwnerShare) overall.total else overall.teacherShare,
                    accent = Ink.Amber,
                ),
            ),
        )
        InkCard {
            ratePercent?.let {
                Text(
                    "${tr(StrSub.teacherShare)} ${it.toInt()}%",
                    color = Ink.Teal,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showOwnerShare) {
                    Column {
                        Text(formatMoney(overall.teacherShare, "EGP"), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(tr(StrSub.teacherShare), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        WeeklyCaption(overall.teacherShare, Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text(formatMoney(overall.ownerShare, "EGP"), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(tr(StrSub.ownerShare), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        WeeklyCaption(overall.ownerShare, Modifier.padding(top = 2.dp))
                    }
                }
                Column {
                    Text(overall.subscriptionCount.toString(), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(tr(StrSub.subscriptions), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── Group Breakdown ────────────────────────────────────────────────────────

@Composable
private fun GroupBreakdown(groups: List<ActiveSubscriptionGroup>, showOwnerShare: Boolean = true) {
    SectionHeader(tr(StrSub.groupBreakdown))
    groups.forEach { group ->
        InkCard {
            val headline = if (showOwnerShare) group.monthlyTotal else group.teacherShare
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(group.groupName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(headline, "EGP"), color = Ink.Amber, style = MaterialTheme.typography.titleSmall)
                    WeeklyCaption(headline, valueColor = Ink.Amber)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (showOwnerShare) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KeyValueWithWeeklyRow(tr(StrSub.teacherShare), group.teacherShare, modifier = Modifier.weight(1f))
                    KeyValueWithWeeklyRow(tr(StrSub.ownerShare), group.ownerShare, modifier = Modifier.weight(1f))
                }
            }
            KeyValueRow(tr(StrSub.subscriptions), group.count.toString())
        }
    }
}

// ── Saved Reports ──────────────────────────────────────────────────────────

@Composable
private fun SavedReportsList(reports: List<SubscriptionEarningsReport>, showOwnerShare: Boolean = true) {
    SectionHeader(tr(StrSub.savedReports))
    reports.forEach { report ->
        InkCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${report.year}/${String.format("%02d", report.month)}", color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal)
            }
            Spacer(Modifier.height(6.dp))
            KeyValueRow(tr(StrSub.totalEarnings), formatMoney(report.totalEarned, report.currency))
            if (showOwnerShare) {
                KeyValueRow(tr(StrSub.ownerShare), formatMoney(report.ownerShare, report.currency))
            }
            KeyValueRow(tr(StrSub.subscriptions), report.subscriptionCount.toString())
        }
    }
}

// ── Teacher Earnings Screen ────────────────────────────────────────────────

@Composable
fun SubscriptionEarningsScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: SubscriptionEarningsViewModel = viewModel()
    val overall by vm.overall.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var myRate by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        myRate = runCatching { TeacherRepository.mySubscriptionRate() }
            .getOrNull()?.percentage?.takeIf { it > 0 }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(tr(StrSub.mySubscriptionEarnings), onBack = { navController.popBackStack() })

        when (val s = overall) {
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
                                PrimaryAction(tr(Str.close)) { vm.clearError() }
                            }
                        }
                    }

                    item { ActiveEarningsSummary(s.value, myRate, showOwnerShare = false) }

                    if (groups.isEmpty() && s.value.subscriptionCount == 0) {
                        item { EmptyBlock(tr(StrSub.noEarningsYet), tr(StrSub.noEarningsYetBody)) }
                    } else if (groups.isNotEmpty()) {
                        item { GroupBreakdown(groups, showOwnerShare = false) }
                    }

                    when (val r = reports) {
                        is Async.Success -> {
                            if (r.value.isNotEmpty()) {
                                item { SavedReportsList(r.value, showOwnerShare = false) }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// ── Admin Earnings Screen ──────────────────────────────────────────────────

@Composable
fun AdminSubscriptionEarningsScreen(
    navController: NavHostController,
    session: SessionViewModel,
    /** When set (chosen upstream on the teacher dashboard), earnings are
     * locked to this teacher and the in-screen teacher-filter chips are
     * hidden — picking a teacher twice would be a duplicate step. */
    initialTeacherId: String? = null,
) {
    val vm: AdminSubscriptionEarningsViewModel = viewModel()
    val overall by vm.overall.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var showRateDialog by remember { mutableStateOf(false) }
    var teachers by remember { mutableStateOf(emptyList<com.rork.pro.data.TeacherProfile>()) }
    var selectedTeacherId by remember { mutableStateOf(initialTeacherId) }
    var selectedRate by remember { mutableStateOf<Double?>(null) }
    val teacherLocked = initialTeacherId != null
    var teacherLoadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // Previously silent: if this failed the teacher dropdown just rendered empty, which
        // looks like "this academy has no teachers" rather than "the list failed to load" —
        // and the owner then cannot set anyone's commission rate with no idea why.
        runCatching { com.rork.pro.data.AdminRepository.teachers() }
            .onSuccess { teachers = it; teacherLoadError = null }
            .onFailure {
                android.util.Log.e("SubsEarnings", "Failed to load teachers for rate picker", it)
                teacherLoadError = it.toAppError().message
            }
        if (initialTeacherId != null) vm.setTeacherFilter(initialTeacherId)
    }
    LaunchedEffect(selectedTeacherId) {
        selectedRate = selectedTeacherId?.let { id ->
            // Same problem, worse consequence: a failed fetch used to be indistinguishable
            // from "no rate configured", so the owner could believe a teacher had no rate set
            // and overwrite a real one. On failure the field is left blank AND flagged.
            runCatching { com.rork.pro.data.AdminRepository.getTeacherSubscriptionRate(id) }
                .onFailure {
                    android.util.Log.e("SubsEarnings", "Failed to load rate for teacher=$id", it)
                    teacherLoadError = it.toAppError().message
                }
                .getOrNull()?.takeIf { it > 0 }
        }
    }
    val lockedTeacherName = if (teacherLocked) {
        teachers.firstOrNull { it.id == initialTeacherId }?.profile?.fullName
    } else null

    // Was previously dead UI state — a rate could be viewed per teacher but
    // never actually edited from this screen (setTeacherRate existed on the
    // view model with no dialog wired to it). Completed here: pick a teacher,
    // then edit their rate right where it's displayed.
    if (showRateDialog && selectedTeacherId != null) {
        TeacherRateDialog(
            currentRate = selectedRate,
            busy = busy,
            onDismiss = { showRateDialog = false },
            onSave = { rate ->
                vm.setTeacherRate(selectedTeacherId!!, rate)
                showRateDialog = false
                selectedRate = rate
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(
            tr(StrSub.allSubscriptionEarnings),
            onBack = { navController.popBackStack() },
            trailing = {
                TextButton(onClick = { vm.generateReport() }, enabled = !busy) {
                    Text(tr(StrSub.generateReport), color = Ink.Amber)
                }
            },
        )

        when (val s = overall) {
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
                                PrimaryAction(tr(Str.close)) { vm.clearError() }
                            }
                        }
                    }

                    teacherLoadError?.let { msg ->
                        item {
                            InkCard(borderColor = Ink.Coral.copy(alpha = 0.4f)) {
                                Text(msg, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                PrimaryAction(tr(Str.close)) { teacherLoadError = null }
                            }
                        }
                    }

                    item { ActiveEarningsSummary(s.value, selectedRate) }

                    // Edit a specific teacher's rate right here, next to where it's shown.
                    if (selectedTeacherId != null) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showRateDialog = true }, enabled = !busy) {
                                    Text(
                                        tr(if (selectedRate != null) StrSub.editTeacherRate else StrSub.setTeacherRate),
                                        color = Ink.Amber,
                                    )
                                }
                            }
                        }
                    }

                    // Teacher filter — a fixed label when a teacher was already
                    // chosen upstream (no duplicate picker), otherwise chips.
                    if (teacherLocked) {
                        item {
                            Text(
                                trf(StrAdmin.teacherEarningsOf, lockedTeacherName ?: "..."),
                                color = Ink.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (teachers.isNotEmpty()) {
                        item {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedTeacherId == null,
                                        onClick = { selectedTeacherId = null; vm.setTeacherFilter(null) },
                                        label = { Text(tr(StrSub.allStatuses), style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Amber, selectedLabelColor = Color.Black, containerColor = Ink.Surface, labelColor = Ink.TextSecondary),
                                    )
                                }
                                items(teachers.size) { idx ->
                                    val t = teachers[idx]
                                    val name = t.profile?.fullName?.take(15) ?: t.headline?.take(15) ?: "..."
                                    FilterChip(
                                        selected = selectedTeacherId == t.id,
                                        onClick = { selectedTeacherId = t.id; vm.setTeacherFilter(t.id) },
                                        label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Amber, selectedLabelColor = Color.Black, containerColor = Ink.Surface, labelColor = Ink.TextSecondary),
                                    )
                                }
                            }
                        }
                    }

                    if (groups.isEmpty() && s.value.subscriptionCount == 0) {
                        item { EmptyBlock(tr(StrSub.noEarningsYet), tr(StrSub.noEarningsYetBody)) }
                    } else if (groups.isNotEmpty()) {
                        item { GroupBreakdown(groups) }
                    }

                    when (val r = reports) {
                        is Async.Success -> {
                            if (r.value.isNotEmpty()) {
                                item { SavedReportsList(r.value) }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// ── Teacher Rate Dialog ────────────────────────────────────────────────────

@Composable
private fun TeacherRateDialog(
    currentRate: Double?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var input by remember { mutableStateOf(currentRate?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(StrAdmin.teacherRatePercent), style = MaterialTheme.typography.titleLarge) },
        text = {
            InkField(
                input,
                { input = it.filter(Char::isDigit).take(3) },
                tr(StrAdmin.percentField),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    input.toIntOrNull()?.coerceIn(0, 100)?.toDouble()?.let(onSave)
                },
                enabled = !busy && input.toIntOrNull() != null,
            ) {
                Text(tr(Str.save), color = Ink.Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(Str.cancel), color = Ink.TextSecondary) }
        },
    )
}
