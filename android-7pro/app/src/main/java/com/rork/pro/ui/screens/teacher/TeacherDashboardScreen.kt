package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.BarChart
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.i18n.StrStudioHub
import com.rork.pro.ui.screens.admin.AdminEmpty
import com.rork.pro.ui.screens.admin.AdminNote
import com.rork.pro.ui.screens.admin.AdminSearchField
import com.rork.pro.ui.screens.admin.AdminShortcutTile
import com.rork.pro.ui.screens.admin.AdminStat
import com.rork.pro.ui.screens.admin.AdminSummaryStrip
import com.rork.pro.ui.screens.admin.AdminTabs
import com.rork.pro.ui.screens.admin.AdminTone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.ApprovalRequest
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.Payout
import com.rork.pro.data.Subscription
import com.rork.pro.data.SubscriptionStats
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatCard
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.PaymentSourcePill
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.TeacherSubscriptionRate
import com.rork.pro.data.TeacherProfile
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.tr

// ── Data Classes ────────────────────────────────────────────────────────────

data class TeacherDashboardData(
    val stats: SubscriptionStats,
    val subscriptions: List<Subscription>,
    val pendingRequests: List<ApprovalRequest>,
)

// ── ViewModel ──────────────────────────────────────────────────────────────

class TeacherDashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<TeacherDashboardData>>(Async.Loading)
    val state: StateFlow<Async<TeacherDashboardData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    /** Pull-to-refresh and after an action: reloads without blanking the board. */
    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                // Balance, earnings and withdrawals are no longer fetched here: they live on the
                // studio home and in the wallet, so this board is only about subscriptions.
                TeacherDashboardData(
                    stats = TeacherRepository.subscriptionStats(),
                    subscriptions = TeacherRepository.mySubscriptions(),
                    pendingRequests = TeacherRepository.myPendingApprovals(),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun renewSubscription(subscriptionId: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { TeacherRepository.renewSubscription(subscriptionId) }
                .onFailure { _error.value = it.toAppError() }
            delay(300)
            _busy.value = false
            refresh()
        }
    }

    fun clearError() { _error.value = null }
}

// ── Screen ──────────────────────────────────────────────────────────────────

/**
 * Subscriptions board — only subscriptions: a summary, three shortcuts, and two views (my
 * subscriptions / my pending requests) in one scrolling list.
 *
 * It used to also carry the balance, an earnings tab and a withdrawals tab with its own payout
 * form, all duplicating the studio home and the wallet; and its fixed header left the list a
 * sliver of the screen on smaller phones.
 */
@Composable
fun TeacherDashboardScreen(
    navController: NavHostController,
    session: SessionViewModel,
) {
    val vm: TeacherDashboardViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf("SUBS") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load(quiet = true) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(title = tr(StrSub.dashboardTitle), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val data = s.value
                val filtered = data.subscriptions.filter {
                    searchQuery.isBlank() ||
                        it.studentName.contains(searchQuery, true) ||
                        it.parentName.contains(searchQuery, true) ||
                        it.groupName.contains(searchQuery, true)
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (busy) {
                        item {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = Ink.Amber,
                                trackColor = Ink.SurfaceHigh,
                            )
                        }
                    }
                    error?.let { err ->
                        item { Box(Modifier.clickable { vm.clearError() }) { AdminNote(err.message, AdminTone.Danger) } }
                    }

                    item {
                        val due = data.stats.dueThisWeek + data.stats.overdue
                        AdminSummaryStrip(
                            listOf(
                                AdminStat(data.stats.totalSubscriptions.toString(), tr(StrStudioHub.activeSubs), Ink.Teal),
                                AdminStat(formatMoney(data.stats.totalMonthlyValue, "EGP"), tr(StrStudioHub.monthlyIncome), Ink.Amber),
                                AdminStat(data.pendingRequests.size.toString(), tr(StrStudioHub.pendingRequests), if (data.pendingRequests.isNotEmpty()) Ink.Coral else null),
                                AdminStat(due.toString(), tr(StrStudioHub.dueOrOverdue), if (data.stats.overdue > 0) Ink.Coral else null),
                            ),
                        )
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AdminShortcutTile(Icons.Default.Groups, tr(StrSub.manageGroups), Modifier.weight(1f), tint = Ink.Amber) {
                                navController.navigate(TeacherRoutes.SUBSCRIPTIONS)
                            }
                            AdminShortcutTile(Icons.Default.CalendarMonth, tr(StrSub.renewalDates), Modifier.weight(1f), tint = Ink.Teal, badge = data.stats.overdue) {
                                navController.navigate(TeacherRoutes.UPCOMING_RENEWALS)
                            }
                            AdminShortcutTile(Icons.Default.BarChart, tr(StrSub.detailedReports), Modifier.weight(1f), tint = Ink.Sky) {
                                navController.navigate(TeacherRoutes.SUBSCRIPTION_EARNINGS)
                            }
                        }
                    }

                    item {
                        AdminTabs(
                            listOf("SUBS" to tr(StrStudioHub.tabSubscriptions), "REQUESTS" to tr(StrStudioHub.tabRequests)),
                            tab,
                            { tab = it },
                            counts = mapOf("REQUESTS" to data.pendingRequests.size),
                        )
                    }

                    if (tab == "SUBS") {
                        item { AdminSearchField(searchQuery, { searchQuery = it }, tr(StrSub.searchStudentOrGroup)) }
                        if (filtered.isEmpty()) {
                            item {
                                DashboardEmptySubscriptions(
                                    hasQuery = searchQuery.isNotBlank(),
                                    onAddSubscription = { navController.navigate(TeacherRoutes.SUBSCRIPTIONS) },
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { sub ->
                                TeacherSubscriptionCard(sub = sub, onRenew = { if (!busy) vm.renewSubscription(sub.id) }, busy = busy)
                            }
                        }
                    } else {
                        if (data.pendingRequests.isEmpty()) {
                            item { AdminEmpty(tr(StrSub.noPendingRequestsTitle), tr(StrSub.noPendingRequestsBody2)) }
                        } else {
                            items(data.pendingRequests, key = { it.id }) { request -> TeacherPendingCard(request = request) }
                        }
                    }
                }
            }
        }
    }
}

// ── Subscriptions Tab ───────────────────────────────────────────────────────

/** Illustrated empty state matching the studio's "no subscriptions" mockup. */
@Composable
private fun DashboardEmptySubscriptions(
    hasQuery: Boolean,
    onAddSubscription: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = Ink.TextMuted,
                    modifier = Modifier.size(40.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Ink.Amber),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Ink.OnAmber,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            tr(StrSub.noSubscriptionsTitle),
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (hasQuery) tr(StrSub.noSubscriptionsForSearch) else tr(StrSub.addFirstSubscriptionBody),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAddSubscription,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink.Amber, contentColor = Ink.OnAmber),
            modifier = Modifier.height(52.dp),
        ) {
            Text(tr(StrSub.addNewSubscription), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TeacherSubscriptionCard(
    sub: Subscription,
    onRenew: () -> Unit,
    busy: Boolean,
) {
    InkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(sub.studentName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${sub.groupName} · ${sub.parentName}", color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(sub.approvalStatus)
                    if (sub.status != "ACTIVE") {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(sub.status)
                    }
                }
                if (sub.paymentSource != null) {
                    Spacer(Modifier.height(4.dp))
                    PaymentSourcePill(sub.paymentSource)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(sub.monthlyAmount, sub.currency),
                    color = Ink.Amber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(formatDate(sub.nextRenewalDate), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (sub.approvalStatus == "APPROVED" && sub.status == "DUE" && !busy) {
                    TextButton(onClick = onRenew) {
                        Text(tr(StrSub.renewAction), color = Ink.Teal)
                    }
                }
            }
        }
    }
}

// ── Pending Tab ─────────────────────────────────────────────────────────────

@Composable
private fun TeacherPendingCard(
    request: ApprovalRequest,
) {
    val actionLabel = when (request.actionType) {
        "ACTIVATE_SUBSCRIPTION" -> tr(StrSub.reqActivateSubscription)
        "ACTIVATE_GROUP" -> tr(StrSub.reqActivateGroup)
        "ADD_SUBSCRIPTION" -> tr(StrSub.reqAddSubscription)
        "ADD_GROUP" -> tr(StrSub.reqAddGroup)
        else -> request.actionType
    }

    InkCard(borderColor = Ink.Amber.copy(alpha = 0.3f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(actionLabel, color = Ink.Amber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    request.teacherName?.let {
                        Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                StatusPill(request.status)
            }

            request.requestData?.let { data ->
                data["student_name"]?.toString()?.trim('\"')?.let { name ->
                    if (name.isNotBlank()) KeyValueRow(tr(StrSub.studentLabel), name)
                }
                data["group_name"]?.toString()?.trim('\"')?.let { group ->
                    KeyValueRow(tr(StrSub.groupLabel), group)
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Ink.TextPrimary, style = MaterialTheme.typography.bodySmall)
    }
}

