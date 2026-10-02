package com.rork.pro.ui.screens.admin

import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import com.rork.pro.ui.i18n.StrAdminX
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.AppError
import com.rork.pro.data.ApprovalRequest
import com.rork.pro.data.Async
import com.rork.pro.data.PendingRequestCount
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PaymentSourcePill
import com.rork.pro.ui.components.paymentBrandLabel
import com.rork.pro.data.PaymentSource
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatDateTime
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrApprovalX
import com.rork.pro.ui.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import com.rork.pro.ui.i18n.trf

// ── ViewModel ──────────────────────────────────────────────────────────────

data class ApprovalsData(
    val counts: List<PendingRequestCount>,
    val requests: List<ApprovalRequest>,
)

class AdminApprovalsViewModel(private val teacherId: String?) : ViewModel() {
    private val _state = MutableStateFlow<Async<ApprovalsData>>(Async.Loading)
    val state: StateFlow<Async<ApprovalsData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Renewals already decided, newest first — loaded the first time the log tab is opened. */
    private val _history = MutableStateFlow<Async<List<ApprovalRequest>>?>(null)
    val history: StateFlow<Async<List<ApprovalRequest>>?> = _history.asStateFlow()

    init { load() }

    fun refresh() {
        _refreshing.value = true
        _error.value = null
        load(quiet = true)
        if (_history.value != null) loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            if (_history.value == null) _history.value = Async.Loading
            runCatching {
                AdminRepository.approvalRequests(
                    status = "DECIDED",
                    teacherId = teacherId,
                    action = "RENEW_SUBSCRIPTION",
                    limit = 100,
                )
            }.onSuccess { _history.value = Async.Success(it) }
                .onFailure { _history.value = Async.Failure(it.toAppError()) }
        }
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                ApprovalsData(
                    AdminRepository.pendingRequestCounts(),
                    // With a teacher (opened from the teacher console) the queue is that
                    // teacher's; with none (opened from the Home shortcut) it is every teacher's —
                    // the server returns all of them to staff when no teacher is given.
                    AdminRepository.approvalRequests(status = "PENDING", teacherId = teacherId),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    /** The card disappears the instant it is decided, instead of after a full-screen reload. */
    private fun hideLocally(requestId: String) {
        val current = (_state.value as? Async.Success)?.value ?: return
        _state.value = Async.Success(current.copy(requests = current.requests.filterNot { it.id == requestId }))
    }

    private fun decide(requestId: String, decision: String, note: String = "") {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            hideLocally(requestId)
            runCatching { AdminRepository.processApprovalRequest(requestId, decision, note) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load(quiet = true)
            if (_history.value != null) loadHistory()
        }
    }

    /** One tap approves — no confirmation step and no text field in the way. */
    fun approve(requestId: String) = decide(requestId, "APPROVED")

    fun reject(requestId: String, note: String) = decide(requestId, "REJECTED", note)

    fun clearError() { _error.value = null }
}

private fun approvalsVmFactory(teacherId: String?) = object : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminApprovalsViewModel(teacherId) as T
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun actionLabel(actionType: String): String = when (actionType) {
    "ADD_SUBSCRIPTION" -> tr(StrAdmin.actAddSubscription)
    "DELETE_SUBSCRIPTION" -> tr(StrAdmin.actDeleteSubscription)
    "UPDATE_SUBSCRIPTION" -> tr(StrAdmin.actEditSubscription)
    "ADD_GROUP" -> tr(StrAdmin.actAddGroup)
    "DELETE_GROUP" -> tr(StrAdmin.actDeleteGroup)
    "RENEW_SUBSCRIPTION" -> tr(StrAdmin.actRenewSubscription)
    "ACTIVATE_GROUP" -> tr(StrAdmin.actActivateGroup)
    "ACTIVATE_SUBSCRIPTION" -> tr(StrAdmin.actActivateSubscription)
    else -> actionType
}

/** Icon and colour per request kind, so a removal never looks like an addition at a glance. */
internal fun approvalVisual(actionType: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> = when {
    actionType.contains("DELETE") -> Icons.Default.Close to Ink.Coral
    actionType.contains("RENEW") -> Icons.Default.Autorenew to Ink.Sky
    actionType.contains("UPDATE") -> Icons.Default.Edit to Ink.Amber
    actionType.contains("ACTIVATE") -> Icons.Default.CheckCircle to Ink.Teal
    actionType.contains("GROUP") -> Icons.Default.Groups to Ink.Teal
    else -> Icons.Default.PersonAdd to Ink.Teal
}

private fun requestDataSummary(requestData: kotlinx.serialization.json.JsonObject?): String {
    if (requestData == null) return ""
    fun field(key: String): String? =
        (requestData[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    return listOfNotNull(
        field("student_name"),
        field("group_name")?.let { trf(StrAdmin.groupPrefix, it) },
        field("monthly_amount")?.let { trf(StrAdmin.amountEgp, it) },
        field("name"),
    ).joinToString(" · ")
}

/** Every fact the owner needs to decide, as label/value rows (payload merged with the subscription). */
private fun requestDetails(request: ApprovalRequest): List<Pair<String, String>> {
    val d = request.requestData ?: return emptyList()
    fun f(key: String): String? =
        (d[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    val currency = f("currency") ?: "EGP"
    val rows = mutableListOf<Pair<String, String>>()
    f("student_name")?.let { rows += tr(StrApprovalX.student) to it }
    f("parent_name")?.let { rows += tr(StrApprovalX.parent) to it }
    f("parent_phone")?.let { rows += tr(StrApprovalX.phone) to it }
    (f("group_name") ?: f("name"))?.let { rows += tr(StrApprovalX.group) to it }
    f("level")?.let { rows += tr(StrApprovalX.level) to it }
    f("member_count")?.let { rows += tr(StrApprovalX.studentsCount) to it }
    (f("amount") ?: f("monthly_amount"))?.let { rows += tr(StrApprovalX.amount) to "$it $currency" }
    f("start_date")?.let { rows += tr(StrApprovalX.startDate) to formatDate(it) }
    f("previous_renewal_date")?.let { rows += tr(StrApprovalX.currentRenewal) to formatDate(it) }
    f("new_renewal_date")?.let { rows += tr(StrApprovalX.newRenewal) to formatDate(it) }
    request.createdAt?.let { rows += tr(StrApprovalX.requestedOn) to formatDateTime(it) }
    return rows
}

private fun flag(d: kotlinx.serialization.json.JsonObject?, key: String): Boolean =
    (d?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"

// ── Main Screen ────────────────────────────────────────────────────────────

@Composable
fun AdminApprovalsScreen(navController: NavHostController, teacherId: String? = null) {
    val vm: AdminApprovalsViewModel = viewModel(factory = approvalsVmFactory(teacherId))
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var rejectFor by remember { mutableStateOf<ApprovalRequest?>(null) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("PENDING") }
    val history by vm.history.collectAsStateWithLifecycle()
    LaunchedEffect(tab) { if (tab == "LOG") vm.loadHistory() }

    // The optional reason lives in this dialog, not on the card: a focused text field on the card
    // used to steal the first tap from the Approve button.
    rejectFor?.let { req ->
        var note by remember(req.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectFor = null },
            title = { Text(tr(StrApprovalX.rejectTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(trf(StrAdmin.confirmRejectRequest, actionLabel(req.actionType)), color = Ink.TextSecondary)
                    InkField(note, { note = it }, tr(StrAdminX.rejectReason))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.reject(req.id, note.trim())
                    rejectFor = null
                }) { Text(tr(StrApprovalX.rejectConfirm), color = Ink.Coral) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { rejectFor = null }) {
                    Text(tr(Str.cancel), color = Ink.TextSecondary)
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Opened from a teacher's console the queue is that teacher's (their name is in the title);
        // opened from the Home shortcut it is every teacher's, and each card names its teacher.
        val teacherName = (state as? Async.Success)?.value?.requests?.firstOrNull()?.teacherName
            ?: (state as? Async.Success)?.value?.counts?.firstOrNull { it.teacherId == teacherId }?.teacherName

        DetailHeader(
            if (teacherId != null) trf(StrAdmin.approvalsForTeacher, teacherName ?: tr(StrAdmin.teacherLabel)) else tr(StrAdmin.approvalRequests),
            onBack = { navController.popBackStack() },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
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
                        item {
                            AdminNote(err.message, AdminTone.Danger)
                        }
                    }

                    val requests = s.value.requests
                    item {
                        AdminTabs(
                            listOf(
                                "PENDING" to tr(StrApprovalX.tabPending),
                                "LOG" to tr(StrApprovalX.tabRenewalsLog),
                            ),
                            tab,
                            { tab = it },
                            counts = mapOf("PENDING" to requests.size),
                        )
                    }

                    if (tab == "LOG") {
                        when (val log = history) {
                            null, is Async.Loading -> item {
                                LoadingBlock(Modifier.fillMaxWidth().height(160.dp))
                            }

                            is Async.Failure -> item { AdminNote(log.error.message, AdminTone.Danger) }

                            is Async.Success -> {
                                if (log.value.isEmpty()) {
                                    item {
                                        AdminEmpty(tr(StrApprovalX.renewalsLogEmpty), tr(StrApprovalX.renewalsLogEmptyBody))
                                    }
                                } else {
                                    item { AdminSectionLabel(tr(StrApprovalX.tabRenewalsLog), log.value.size) }
                                    items(log.value, key = { it.id }) { request ->
                                        ApprovalRequestCard(
                                            request = request,
                                            busy = busy,
                                            onApprove = {},
                                            onReject = {},
                                            showTeacher = teacherId == null,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            val deletions = requests.count { it.actionType.contains("DELETE") }
                            AdminSummaryStrip(
                                listOf(
                                    AdminStat(
                                        requests.size.toString(),
                                        if (teacherId != null) tr(StrAdmin.totalPendingForTeacher) else tr(StrAdmin.totalPendingAll),
                                        if (requests.isNotEmpty()) Ink.Coral else Ink.Teal,
                                    ),
                                    AdminStat(deletions.toString(), tr(StrAdminX.deletionRequests), if (deletions > 0) Ink.Coral else null),
                                ),
                            )
                        }

                        if (requests.isEmpty()) {
                            item {
                                InkCard(color = Ink.TealSoft, contentPadding = PaddingValues(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal)
                                        Text(tr(StrAdminX.nothingToApprove), color = Ink.Teal, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        } else {
                            item { AdminSectionLabel(tr(StrAdminX.needsDecision), requests.size) }
                            items(requests, key = { it.id }) { request ->
                                ApprovalRequestCard(
                                    request = request,
                                    busy = busy,
                                    onApprove = { vm.approve(request.id) },
                                    onReject = { rejectFor = request },
                                    // In the all-teachers queue each card must say whose request it is.
                                    showTeacher = teacherId == null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Request Card ───────────────────────────────────────────────────────────

/**
 * One pending request. The teacher is already fixed by the screen, so the card leads with what
 * is being asked (coloured by kind), lists every detail the owner needs to decide, and puts the
 * decision right underneath: Approve acts on the first tap.
 */
@Composable
internal fun ApprovalRequestCard(
    request: ApprovalRequest,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    showTeacher: Boolean = false,
) {
    val (icon, tint) = approvalVisual(request.actionType)
    val summary = requestDataSummary(request.requestData)
    val details = buildList {
        if (showTeacher) request.teacherName?.takeIf { it.isNotBlank() }?.let { add(tr(StrAdmin.teacherLabel) to it) }
        addAll(requestDetails(request))
    }
    AdminItemCard(
        title = actionLabel(request.actionType),
        subtitle = summary.ifBlank { null },
        leading = { AdminBadgeIcon(icon, tint = tint) },
        trailing = {
            Text(formatDate(request.createdAt), color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
        },
        accent = tint,
        expandable = false,
    ) {
        val selfService = flag(request.requestData, "self_service")
        val source = request.paymentSource
        if (source != null || (request.status == "PENDING" && selfService)) {
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentSourcePill(source)
                if (selfService && request.status == "PENDING") {
                    Pill(tr(StrApprovalX.selfService), background = Ink.SkySoft, foreground = Ink.Sky)
                }
            }
        }
        details.forEach { (label, value) -> KeyValueRow(label, value) }
        // What the student actually sent from the app, when they sent anything.
        if (source == PaymentSource.APP_PAID || source == PaymentSource.AWAITING_REVIEW) {
            val via = paymentBrandLabel(request.paymentBrand)
            val paid = request.paymentAmount?.let { formatMoney(it, request.paymentCurrency ?: "EGP") }
            listOfNotNull(via, paid).takeIf { it.isNotEmpty() }?.let { KeyValueRow(tr(StrApprovalX.paidVia), it.joinToString(" · ")) }
        }
        // The transfer itself: who sent it, when (date and clock time), and the screenshot.
        request.paymentSubmittedAt?.let { KeyValueRow(tr(StrApprovalX.transferredAt), formatDateTime(it)) }
        request.paymentSenderPhone?.takeIf { it.isNotBlank() }?.let { KeyValueRow(tr(StrAdmin.reviewSender), it) }
        if (request.status != "PENDING") {
            (request.reviewedAt ?: request.paymentReviewedAt)?.let {
                KeyValueRow(tr(StrApprovalX.decidedAt), formatDateTime(it))
            }
        }
        request.paymentProofPath?.takeIf { it.isNotBlank() }?.let { proofPath ->
            Spacer(Modifier.height(8.dp))
            PaymentProofPreview(
                proofId = request.id,
                proofPath = proofPath,
                senderPhone = request.paymentSenderPhone.orEmpty(),
                whenText = request.paymentSubmittedAt?.let { formatDateTime(it) },
            )
            Spacer(Modifier.height(8.dp))
        }
        request.reviewNote?.takeIf { it.isNotBlank() }?.let {
            KeyValueRow(tr(StrAdmin.reviewNoteLabel), it)
        }
        if (request.status == "PENDING") {
            AdminActions {
                AdminButton(tr(StrAdmin.approve), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success, enabled = !busy, onClick = onApprove)
                AdminButton(tr(StrAdmin.reject), Modifier.weight(1f), Icons.Default.Close, AdminTone.Danger, enabled = !busy, onClick = onReject)
            }
        } else {
            StatusPill(request.status)
        }
    }
}
