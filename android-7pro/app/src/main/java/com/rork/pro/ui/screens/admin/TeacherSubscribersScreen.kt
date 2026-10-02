package com.rork.pro.ui.screens.admin

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.AppError
import com.rork.pro.data.PaymentSources
import com.rork.pro.data.SubState
import com.rork.pro.data.TeacherSubscriber
import com.rork.pro.data.TeacherSubscribersRepository
import com.rork.pro.data.TeacherSubscribersSummary
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.PaymentSourcePill
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun stateLabel(state: String): String = when (state) {
    SubState.ACTIVE -> tr(StrAdminX.tsStateActive)
    SubState.DUE -> tr(StrAdminX.tsStateDue)
    SubState.OVERDUE -> tr(StrAdminX.tsStateOverdue)
    SubState.PAUSED -> tr(StrAdminX.tsStatePaused)
    SubState.PENDING -> tr(StrAdminX.tsStatePending)
    else -> tr(StrAdminX.tsStateRejected)
}

private fun stateColors(state: String): Pair<Color, Color> = when (state) {
    SubState.ACTIVE -> Ink.TealSoft to Ink.Teal
    SubState.DUE, SubState.PENDING -> Ink.AmberSoft to Ink.Amber
    SubState.OVERDUE, SubState.REJECTED -> Ink.CoralSoft to Ink.Coral
    else -> Ink.SurfaceHigh to Ink.TextMuted
}

/** Same person across teachers and groups: the linked account, else name + parent's phone. */
private fun personKey(s: TeacherSubscriber): String =
    s.studentUserId ?: (s.studentName.trim().lowercase() + "|" + s.parentPhone.orEmpty().trim())

/** Most urgent first: this is the state a person's row shows. */
private val URGENCY = listOf(
    SubState.OVERDUE, SubState.DUE, SubState.PENDING, SubState.ACTIVE, SubState.PAUSED, SubState.REJECTED,
)

/** One person and every subscription they have — a single row in the list. */
private class SubPerson(val key: String, val subs: List<TeacherSubscriber>) {
    val first: TeacherSubscriber get() = subs.first()
    val avatar: String? get() = subs.firstNotNullOfOrNull { it.accountAvatar?.takeIf { a -> a.isNotBlank() } }
    fun has(state: String): Boolean = subs.any { it.state == state }
    val worst: String get() = URGENCY.firstOrNull { has(it) } ?: SubState.ACTIVE
}

/** Rows come ordered by urgency; grouping keeps that order, so late payers lead. */
private fun groupPeople(rows: List<TeacherSubscriber>): List<SubPerson> =
    rows.groupBy { personKey(it) }.map { (k, v) -> SubPerson(k, v) }

/**
 * Teachers' subscribers, one row per person. Tapping a person opens a page with only their
 * subscriptions and the controls for each (renew, pause, approve, dates, delete).
 */
@Composable
fun TeacherSubscribersScreen(navController: NavHostController, session: SessionViewModel) {
    var teachers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var teacher by remember { mutableStateOf<Pair<String, String>?>(null) }
    var state by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    var summary by remember { mutableStateOf<TeacherSubscribersSummary?>(null) }
    var rows by remember { mutableStateOf<List<TeacherSubscriber>?>(null) }
    var rejectedRows by remember { mutableStateOf<List<TeacherSubscriber>>(emptyList()) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var tick by remember { mutableStateOf(0) }

    // Coming back from a person's page: what they changed there must show here.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else tick++
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        teachers = runCatching {
            AdminRepository.teachers().map { it.id to (it.profile?.fullName?.takeIf { n -> n.isNotBlank() } ?: it.id) }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(teacher?.first, tick) {
        runCatching { TeacherSubscribersRepository.summary(teacher?.first) }
            .onSuccess { summary = it }
            .onFailure { error = it.toAppError() }
    }
    LaunchedEffect(teacher?.first, query, tick) {
        if (query.isNotBlank()) delay(280)
        runCatching { TeacherSubscribersRepository.list(teacher?.first, null, query) }
            .onSuccess { rows = it; error = null }
            .onFailure { error = it.toAppError(); if (rows == null) rows = emptyList() }
        rejectedRows = runCatching { TeacherSubscribersRepository.list(teacher?.first, SubState.REJECTED, query) }
            .getOrDefault(emptyList())
    }

    val people = remember(rows) { groupPeople(rows.orEmpty()) }
    val rejectedPeople = remember(rejectedRows) { groupPeople(rejectedRows) }
    val shown = remember(people, rejectedPeople, state) {
        val s = state
        when {
            s == null -> people
            s == SubState.REJECTED -> rejectedPeople
            else -> people.filter { it.has(s) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        DetailHeader(tr(StrAdminX.tsTitle), onBack = { navController.popBackStack() })

        LazyColumn(
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { err ->
                item {
                    InkCard(color = Ink.CoralSoft, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Ink.Coral)
                            Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            summary?.let { s ->
                item {
                    AdminSummaryStrip(
                        listOf(
                            AdminStat(s.students.toString(), tr(StrAdminX.tsStudents), Ink.Teal),
                            AdminStat(formatMoney(s.monthlyValue, s.currency), tr(StrAdminX.tsMonthly), Ink.Amber),
                        ),
                    )
                }
            }

            item { TeacherFilter(teachers, teacher) { teacher = it } }
            item { AdminSearchField(query, { query = it }, tr(StrAdminX.tsSearch)) }
            item {
                // Counted in people, the same unit as the rows below. The rare states only
                // appear when somebody is actually in them.
                val counts = mapOf<String?, Int>(
                    null to people.size,
                    SubState.OVERDUE to people.count { it.has(SubState.OVERDUE) },
                    SubState.DUE to people.count { it.has(SubState.DUE) },
                    SubState.ACTIVE to people.count { it.has(SubState.ACTIVE) },
                    SubState.PAUSED to people.count { it.has(SubState.PAUSED) },
                    SubState.PENDING to people.count { it.has(SubState.PENDING) },
                    SubState.REJECTED to rejectedPeople.size,
                )
                fun show(s: String) = (counts[s] ?: 0) > 0 || state == s
                val chips = buildList<Pair<String?, String>> {
                    add(null to tr(StrAdminX.tsStateAll))
                    add(SubState.OVERDUE to tr(StrAdminX.tsStateOverdue))
                    add(SubState.DUE to tr(StrAdminX.tsStateDue))
                    add(SubState.ACTIVE to tr(StrAdminX.tsStateActive))
                    if (show(SubState.PENDING)) add(SubState.PENDING to tr(StrAdminX.tsStatePending))
                    if (show(SubState.PAUSED)) add(SubState.PAUSED to tr(StrAdminX.tsStatePaused))
                    if (show(SubState.REJECTED)) add(SubState.REJECTED to tr(StrAdminX.tsStateRejected))
                }
                AdminChips(chips, state, { state = it }, counts = counts)
            }

            if (rows == null) {
                item { LoadingBlock() }
            } else if (shown.isEmpty()) {
                item { Text(tr(StrAdminX.tsNone), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(shown, key = { it.key }) { p ->
                    SubPersonRow(p) { navController.navigate("admin/teacher-subscribers/${p.first.subscriptionId}") }
                }
            }
        }
    }
}

@Composable
private fun SubPersonRow(p: SubPerson, onClick: () -> Unit) {
    val (bg, fg) = stateColors(p.worst)
    InkCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatar, p.first.studentName, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.first.studentName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    if (p.subs.size == 1) {
                        listOfNotNull(p.first.groupName, p.first.teacherName?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    } else {
                        trf(StrAdminX.tsSubsCount, p.subs.size)
                    },
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Pill(stateLabel(p.worst), background = bg, foreground = fg)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
        }
    }
}

/** One person's page: who they are, and each of their subscriptions with its own controls. */
@Composable
fun TeacherSubscriberDetailScreen(navController: NavHostController, subId: String) {
    val scope = rememberCoroutineScope()
    var subs by remember { mutableStateOf<List<TeacherSubscriber>?>(null) }
    var sources by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // The person is found once, from the subscription that was tapped, and then kept — so the
    // page still works after that very subscription is deleted.
    var key by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<TeacherSubscriber?>(null) }
    var editing by remember { mutableStateOf<TeacherSubscriber?>(null) }

    LaunchedEffect(subId, tick) {
        runCatching {
            val main = TeacherSubscribersRepository.list(null, null, null)
            val rejected = runCatching { TeacherSubscribersRepository.list(null, SubState.REJECTED, null) }.getOrDefault(emptyList())
            main + rejected
        }.onSuccess { all ->
            if (key == null) key = all.firstOrNull { it.subscriptionId == subId }?.let { personKey(it) }
            val k = key
            val mine = if (k == null) {
                emptyList()
            } else {
                all.filter { personKey(it) == k }
                    .sortedWith(compareBy({ URGENCY.indexOf(it.state) }, { it.nextRenewalDate }))
            }
            subs = mine
            error = null
            sources = PaymentSources.sourcesOf(mine.map { it.subscriptionId })
        }.onFailure { error = it.toAppError(); if (subs == null) subs = emptyList() }
    }

    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; notice = null
            runCatching { block() }
                .onSuccess { notice = done }
                .onFailure { error = it.toAppError() }
            busy = false
            tick++
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrAdminX.tsPersonTitle), onBack = { navController.popBackStack() })

        LazyColumn(
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { err ->
                item {
                    InkCard(color = Ink.CoralSoft, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Ink.Coral)
                            Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            notice?.let { msg ->
                item {
                    InkCard(color = Ink.TealSoft, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(msg, color = Ink.Teal, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val list = subs
            if (list == null) {
                item { LoadingBlock() }
            } else if (list.isEmpty()) {
                item { Text(tr(StrAdminX.tsNone), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                val who = list.first()
                val headerParent = list.firstNotNullOfOrNull { parentLine(it) }
                item {
                    InkCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(list.firstNotNullOfOrNull { it.accountAvatar?.takeIf { a -> a.isNotBlank() } }, who.studentName, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(who.studentName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    list.firstNotNullOfOrNull { it.accountEmail?.takeIf { e -> e.isNotBlank() } } ?: tr(StrAdminX.tsNoAccount),
                                    color = Ink.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                                headerParent?.let {
                                    Text("${tr(StrAdminX.tsParent)}: $it", color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                items(list, key = { it.subscriptionId }) { sub ->
                    SubscriptionCard(
                        sub = sub,
                        source = sources[sub.subscriptionId],
                        // Only repeated when this subscription has a different parent than the header.
                        parent = parentLine(sub)?.takeIf { it != headerParent },
                        busy = busy,
                        onRenew = { act(tr(StrAdminX.tsDone)) { AdminRepository.adminRenewSubscription(sub.subscriptionId) } },
                        onPause = { act(tr(StrAdminX.tsDone)) { TeacherSubscribersRepository.setPaused(sub.subscriptionId, true) } },
                        onResume = { act(tr(StrAdminX.tsDone)) { TeacherSubscribersRepository.setPaused(sub.subscriptionId, false) } },
                        onApprove = { act(tr(StrAdminX.tsDone)) { AdminRepository.activateSubscription(sub.subscriptionId) } },
                        onReject = { act(tr(StrAdminX.tsDone)) { AdminRepository.rejectSubscription(sub.subscriptionId) } },
                        onEditDates = { editing = sub },
                        onTeacherView = {
                            navController.navigate(
                                AdminRoutes.subscriptionGroup(sub.groupName, sub.teacherId, sub.teacherName.orEmpty()),
                            )
                        },
                        onDelete = { confirmDelete = sub },
                    )
                }
            }
        }
    }

    confirmDelete?.let { sub ->
        ConfirmDialog(
            title = trf(StrAdminX.tsDeleteTitle, sub.studentName),
            body = tr(StrAdminX.tsDeleteBody),
            confirmLabel = tr(StrAdminX.tsDelete),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                act(tr(StrAdminX.tsDone)) { AdminRepository.adminDeleteSubscription(sub.subscriptionId) }
            },
        )
    }

    editing?.let { sub ->
        EditDatesDialog(
            sub = sub,
            onDismiss = { editing = null },
            onSave = { start, next ->
                editing = null
                act(tr(StrAdminX.tsDone)) { TeacherSubscribersRepository.setDates(sub.subscriptionId, start, next) }
            },
        )
    }
}

private fun parentLine(s: TeacherSubscriber): String? =
    listOfNotNull(s.parentName?.takeIf { it.isNotBlank() }, s.parentPhone?.takeIf { it.isNotBlank() })
        .joinToString(" · ").takeIf { it.isNotBlank() }

/**
 * One subscription of one person. The main action for its state sits on the card (renew, resume,
 * approve); everything rarer — pause, dates, the teacher's page, delete — is in the ⋮ menu.
 */
@Composable
private fun SubscriptionCard(
    sub: TeacherSubscriber,
    source: String?,
    parent: String?,
    busy: Boolean,
    onRenew: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditDates: () -> Unit,
    onTeacherView: () -> Unit,
    onDelete: () -> Unit,
) {
    val (bg, fg) = stateColors(sub.state)
    val renewColor = when (sub.state) {
        SubState.OVERDUE -> Ink.Coral
        SubState.DUE -> Ink.Amber
        else -> Ink.TextMuted
    }
    val whenText = when {
        sub.daysLeft < 0 -> trf(StrAdminX.tsDaysLate, -sub.daysLeft)
        sub.daysLeft == 0 -> tr(StrAdminX.tsToday)
        else -> trf(StrAdminX.tsDaysLeft, sub.daysLeft)
    }
    var menu by remember { mutableStateOf(false) }
    val canPause = sub.state == SubState.ACTIVE || sub.state == SubState.DUE || sub.state == SubState.OVERDUE

    InkCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(sub.groupName, sub.teacherName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                Modifier.weight(1f),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Spacer(Modifier.width(8.dp))
            Pill(stateLabel(sub.state), background = bg, foreground = fg)
            Box {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = tr(StrAdminX.tsMore),
                    tint = Ink.TextMuted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canPause) {
                        DropdownMenuItem(text = { Text(tr(StrAdminX.tsPause)) }, enabled = !busy, onClick = { menu = false; onPause() })
                    }
                    DropdownMenuItem(text = { Text(tr(StrAdminX.tsEditDates)) }, enabled = !busy, onClick = { menu = false; onEditDates() })
                    DropdownMenuItem(text = { Text(tr(StrAdminX.tsTeacherView)) }, onClick = { menu = false; onTeacherView() })
                    DropdownMenuItem(
                        text = { Text(tr(StrAdminX.tsDelete), color = Ink.Coral) },
                        enabled = !busy,
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PaymentSourcePill(source)
            Text(formatMoney(sub.monthlyAmount, sub.currency), color = Ink.TextPrimary, style = MaterialTheme.typography.labelLarge)
        }
        parent?.let {
            Spacer(Modifier.height(4.dp))
            Text("${tr(StrAdminX.tsParent)}: $it", color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(trf(StrAdminX.tsStarted, formatDate(sub.startDate)), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        if (sub.state != SubState.PENDING && sub.state != SubState.REJECTED) {
            Text(
                trf(StrAdminX.tsRenews, formatDate(sub.nextRenewalDate)) + " · " + whenText,
                color = renewColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // A healthy active subscription needs nothing — so it shows nothing.
        if (sub.state != SubState.ACTIVE) {
            AdminActions {
                when (sub.state) {
                    SubState.PENDING -> {
                        AdminButton(tr(StrAdminX.tsApprove), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success, enabled = !busy, onClick = onApprove)
                        AdminButton(tr(StrAdminX.tsReject), Modifier.weight(1f), Icons.Default.Close, AdminTone.Danger, enabled = !busy, onClick = onReject)
                    }
                    SubState.REJECTED ->
                        AdminButton(tr(StrAdminX.tsApprove), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success, enabled = !busy, onClick = onApprove)
                    SubState.PAUSED ->
                        AdminButton(tr(StrAdminX.tsResume), Modifier.weight(1f), Icons.Default.PlayArrow, AdminTone.Success, enabled = !busy, onClick = onResume)
                    else ->
                        AdminButton(
                            tr(StrAdminX.tsRenew), Modifier.weight(1f), Icons.Default.Autorenew, AdminTone.Success,
                            // Not before the renewal date — the server refuses it as well.
                            enabled = !busy && runCatching { !java.time.LocalDate.parse(sub.nextRenewalDate).isAfter(java.time.LocalDate.now()) }.getOrDefault(true),
                            onClick = onRenew,
                        )
                }
            }
        }
    }
}

@Composable
private fun EditDatesDialog(sub: TeacherSubscriber, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var start by remember(sub.subscriptionId) { mutableStateOf(sub.startDate) }
    var next by remember(sub.subscriptionId) { mutableStateOf(sub.nextRenewalDate) }
    val startDate = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
    val nextDate = runCatching { LocalDate.parse(next.trim()) }.getOrNull()
    val valid = startDate != null && nextDate != null && !nextDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = { Text(sub.studentName, color = Ink.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InkField(start, { start = it }, tr(StrAdminX.tsStartDate))
                InkField(next, { next = it }, tr(StrAdminX.tsNextDate))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip(label = tr(StrAdminX.tsPlusMonth)) {
                        nextDate?.let { next = it.plusMonths(1).toString() }
                    }
                }
                if (!valid) Text(tr(StrAdminX.tsBadDate), color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(start.trim(), next.trim()) }) {
                Text(tr(StrAdminX.tsSave), color = if (valid) Ink.Amber else Ink.TextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(com.rork.pro.ui.i18n.StrAdmin.cancel), color = Ink.TextSecondary) } },
    )
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Ink.Amber,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.AmberSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** "All teachers" or one teacher, with its own search box. */
@Composable
private fun TeacherFilter(teachers: List<Pair<String, String>>, selected: Pair<String, String>?, onSelect: (Pair<String, String>?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    Text(tr(StrAdminX.tsTeacherLabel), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface)
            .border(1.dp, if (selected == null) Ink.Hairline else Ink.Amber, RoundedCornerShape(16.dp))
            .clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            selected?.second ?: tr(StrAdminX.tsAllTeachers),
            Modifier.weight(1f),
            color = if (selected == null) Ink.TextMuted else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted)
    }
    if (open) {
        if (teachers.size > 5) AdminSearchField(q, { q = it }, tr(StrAdminX.tsTeacherLabel))
        val shown = remember(teachers, q) { teachers.filter { SearchMatch.matches(q, it.second) } }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.SurfaceHigh)
                .verticalScroll(rememberScrollState()),
        ) {
            if (q.isBlank()) {
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(null); open = false }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr(StrAdminX.tsAllTeachers), Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    if (selected == null) Icon(Icons.Default.Check, null, tint = Ink.Teal, modifier = Modifier.size(18.dp))
                }
            }
            shown.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(t); open = false; q = "" }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.second, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (t.first == selected?.first) Icon(Icons.Default.Check, null, tint = Ink.Teal, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
