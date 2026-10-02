package com.rork.pro.ui.screens.teacher

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Payments
import com.rork.pro.ui.i18n.StrStudioHub
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.screens.admin.AdminBadgeIcon
import com.rork.pro.ui.screens.admin.AdminEmpty
import com.rork.pro.ui.screens.admin.AdminFormCard
import com.rork.pro.ui.screens.admin.AdminItemCard
import com.rork.pro.ui.screens.admin.AdminNote
import com.rork.pro.ui.screens.admin.AdminStat
import com.rork.pro.ui.screens.admin.AdminSummaryStrip
import com.rork.pro.ui.screens.admin.AdminTabs
import com.rork.pro.ui.screens.admin.AdminTone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.LedgerEntry
import com.rork.pro.data.Payout
import com.rork.pro.data.TeacherBalance
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.StrTeacher
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf

// ---------------------------------------------------------------- Earnings

data class EarningsData(val balance: TeacherBalance, val ledger: List<LedgerEntry>, val payouts: List<Payout>)

class TeacherEarningsViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<EarningsData>>(Async.Loading)
    val state: StateFlow<Async<EarningsData>> = _state.asStateFlow()

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
                EarningsData(
                    TeacherRepository.balance(),
                    TeacherRepository.ledger(),
                    TeacherRepository.payouts(),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun requestPayout(amount: Double, method: String, destination: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { TeacherRepository.requestPayout(amount, method, destination, "") }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }
}

@Composable
fun TeacherEarningsScreen(navController: NavHostController) {
    val vm: TeacherEarningsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(tr(StrTeacher.bankTransfer)) }
    var destination by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("PAYOUTS") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(tr(StrStudioHub.wallet), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val data = s.value
                val canWithdraw = data.balance.available >= data.balance.minimumPayout
                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ── Balance ──
                    item {
                        InkCard(borderColor = Ink.Amber.copy(alpha = 0.3f)) {
                            Text(tr(StrTeacher.available), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatMoney(data.balance.available, data.balance.currency),
                                color = Ink.Amber,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            KeyValueRow(tr(StrTeacher.pendingMaturing), formatMoney(data.balance.pending, data.balance.currency))
                            KeyValueRow(tr(StrTeacher.grossSales), formatMoney(data.balance.gross, data.balance.currency))
                            KeyValueRow(tr(StrTeacher.alreadyWithdrawn), formatMoney(data.balance.paid, data.balance.currency))
                            KeyValueRow(tr(StrTeacher.minimumWithdrawal), formatMoney(data.balance.minimumPayout, data.balance.currency))
                        }
                    }

                    // Course sales and subscription renewals are two income sources; the ledger has
                    // no source column, so the split uses the note subscription rows always carry.
                    val earningEntries = data.ledger.filter { it.kind != "PAYOUT" }
                    val subscriptionEntries = earningEntries.filter { it.note?.startsWith("Subscription") == true }
                    val courseEntries = earningEntries - subscriptionEntries.toSet()
                    if (earningEntries.isNotEmpty()) {
                        item {
                            AdminSummaryStrip(
                                listOf(
                                    AdminStat(formatMoney(courseEntries.sumOf { it.amount }, data.balance.currency), tr(StrTeacher.sourceCourses), Ink.Teal),
                                    AdminStat(formatMoney(subscriptionEntries.sumOf { it.amount }, data.balance.currency), tr(StrTeacher.sourceSubscriptions), Ink.Amber),
                                ),
                            )
                        }
                    }

                    // ── Withdrawal request (the only one in the app) ──
                    item {
                        if (!canWithdraw) {
                            AdminNote(
                                trf(
                                    StrTeacher.needMoreToWithdraw,
                                    formatMoney(data.balance.minimumPayout - data.balance.available, data.balance.currency),
                                ),
                                AdminTone.Primary,
                            )
                        } else {
                            AdminFormCard(tr(StrTeacher.requestWithdrawal), icon = Icons.Default.Payments, startExpanded = false) { close ->
                                InkField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrTeacher.amount), keyboardType = KeyboardType.Decimal)
                                Spacer(Modifier.height(8.dp))
                                InkField(method, { method = it }, tr(StrTeacher.methodField))
                                Spacer(Modifier.height(8.dp))
                                InkField(destination, { destination = it }, tr(StrTeacher.destinationField))
                                error?.let {
                                    Spacer(Modifier.height(8.dp))
                                    AdminNote(it.message, AdminTone.Danger)
                                }
                                Spacer(Modifier.height(14.dp))
                                PrimaryAction(
                                    tr(StrTeacher.requestWithdrawal),
                                    enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
                                    loading = busy,
                                ) {
                                    vm.requestPayout(amount.toDoubleOrNull() ?: 0.0, method, destination)
                                    amount = ""
                                    close()
                                }
                            }
                        }
                    }

                    item {
                        AdminTabs(
                            listOf(
                                "PAYOUTS" to tr(StrStudioHub.withdrawalsTab),
                                "LEDGER" to tr(StrStudioHub.ledgerTab),
                            ),
                            tab,
                            { tab = it },
                            counts = mapOf("PAYOUTS" to data.payouts.count { it.status == "PENDING" || it.status == "APPROVED" }),
                        )
                    }

                    if (tab == "PAYOUTS") {
                        if (data.payouts.isEmpty()) {
                            item { AdminEmpty(tr(StrStudioHub.noWithdrawals), tr(StrStudioHub.noWithdrawalsBody), icon = Icons.Default.Payments) }
                        }
                        items(data.payouts, key = { it.id }) { payout ->
                            AdminItemCard(
                                title = formatMoney(payout.amount, payout.currency),
                                subtitle = listOfNotNull(
                                    formatDate(payout.paidAt ?: payout.requestedAt),
                                    payout.reference?.takeIf { it.isNotBlank() }?.let { trf(StrTeacher.refNumber, it) },
                                ).joinToString(" · "),
                                leading = { AdminBadgeIcon(Icons.Default.Payments, tint = if (payout.status == "PAID") Ink.Teal else Ink.Amber) },
                                trailing = { StatusPill(payout.status) },
                                expandable = false,
                            )
                        }
                    } else {
                        if (data.ledger.isEmpty()) {
                            item { AdminEmpty(tr(StrTeacher.noEntries), tr(StrTeacher.ledgerEmptyBody)) }
                        }
                        items(data.ledger, key = { it.id }) { entry ->
                            val isPayout = entry.kind == "PAYOUT"
                            val isSubscription = entry.note?.startsWith("Subscription") == true
                            AdminItemCard(
                                title = formatMoney(entry.amount, entry.currency),
                                subtitle = listOf(
                                    if (isPayout) codeLabel(entry.kind)
                                    else if (isSubscription) tr(StrTeacher.sourceSubscriptions)
                                    else tr(StrTeacher.sourceCourses),
                                    formatDate(entry.createdAt),
                                ).joinToString(" · "),
                                leading = {
                                    AdminBadgeIcon(
                                        if (entry.amount >= 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        tint = when {
                                            entry.amount < 0 -> Ink.Coral
                                            isSubscription -> Ink.Amber
                                            else -> Ink.Teal
                                        },
                                    )
                                },
                                trailing = { StatusPill(entry.status) },
                                expandable = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
