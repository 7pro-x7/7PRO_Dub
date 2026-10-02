package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.GrantCourse
import com.rork.pro.data.CourseGrantRepository
import com.rork.pro.data.PaidSource
import com.rork.pro.data.PaidState
import com.rork.pro.data.PaidStudent
import com.rork.pro.data.PaidStudentsRepository
import com.rork.pro.data.PaidStudentsSummary
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun sourceLabel(source: String): String = when (source) {
    PaidSource.PAID -> tr(StrAdminX.psSourcePaid)
    PaidSource.MONTHLY -> tr(StrAdminX.psSourceMonthly)
    PaidSource.COUPON -> tr(StrAdminX.psSourceCoupon)
    PaidSource.GRANTED -> tr(StrAdminX.psSourceGranted)
    else -> tr(StrAdminX.psSourceFree)
}

private fun stateLabel(state: String): String = when (state) {
    PaidState.ACTIVE -> tr(StrAdminX.psStateActive)
    PaidState.EXPIRED -> tr(StrAdminX.psStateExpired)
    else -> tr(StrAdminX.psStateClosed)
}

/** One person with everything they have on paid courses — a single row in the list. */
private class PaidPerson(val userId: String, val rows: List<PaidStudent>) {
    val first: PaidStudent get() = rows.first()
    val active: Int get() = rows.count { it.state == PaidState.ACTIVE }
    val expired: Int get() = rows.count { it.state == PaidState.EXPIRED }
    fun has(state: String): Boolean = rows.any { it.state == state }
}

/** Rows come ordered open-first; grouping keeps that order, so people with open access lead. */
private fun groupPeople(rows: List<PaidStudent>): List<PaidPerson> =
    rows.groupBy { it.userId }.map { (id, list) -> PaidPerson(id, list) }

/**
 * Paid-course students, one row per person. Tapping a person opens a page with only their
 * courses and the controls for each (open / close, extend a monthly subscription).
 */
@Composable
fun PaidStudentsScreen(navController: NavHostController, session: SessionViewModel, startWithGrant: Boolean = false) {
    val sessionState by session.state.collectAsStateWithLifecycle()
    val canGrant = sessionState.can("courses.grant")
    // "Open a course for someone" lives here, not on a screen of its own: it replaces this page
    // while it is open, and back returns to the list (which then refreshes).
    var granting by remember { mutableStateOf(startWithGrant && canGrant) }

    var courses by remember { mutableStateOf<List<GrantCourse>>(emptyList()) }
    var course by remember { mutableStateOf<GrantCourse?>(null) }
    var state by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    var summary by remember { mutableStateOf<PaidStudentsSummary?>(null) }
    var rows by remember { mutableStateOf<List<PaidStudent>?>(null) }
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
        courses = runCatching { CourseGrantRepository.staffCourses() }.getOrDefault(emptyList())
    }
    LaunchedEffect(course?.id, tick) {
        runCatching { PaidStudentsRepository.summary(course?.id) }
            .onSuccess { summary = it }
            .onFailure { error = it.toAppError() }
    }
    LaunchedEffect(course?.id, query, tick) {
        // A short pause while typing so every letter is not a request.
        if (query.isNotBlank()) delay(280)
        runCatching { PaidStudentsRepository.list(course?.id, null, null, query) }
            .onSuccess { rows = it; error = null }
            .onFailure { error = it.toAppError(); if (rows == null) rows = emptyList() }
    }

    val people = remember(rows) { groupPeople(rows.orEmpty()) }
    val shown = remember(people, state) { state?.let { s -> people.filter { it.has(s) } } ?: people }

    if (granting) {
        androidx.activity.compose.BackHandler { granting = false; tick++ }
        CourseGrantsPanel(
            staff = true,
            onBack = { granting = false; tick++ },
            initialCourse = course,
            showOpenedList = false,
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        DetailHeader(tr(StrAdminX.psTitle), onBack = { navController.popBackStack() })

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
                            AdminStat(s.buyers.toString(), tr(StrAdminX.psBuyers), Ink.Teal),
                            AdminStat(s.byState.active.toString(), tr(StrAdminX.psActive)),
                            AdminStat(formatMoney(s.paidTotal, s.currency), tr(StrAdminX.psPaidTotal), Ink.Amber),
                        ),
                    )
                }
            }

            if (canGrant) {
                item {
                    AdminButton(
                        tr(StrAdminX.psGrantCta),
                        Modifier.fillMaxWidth(),
                        Icons.Default.Add,
                        AdminTone.Success,
                    ) { granting = true }
                }
            }
            item { CourseFilter(courses, course) { course = it } }
            item { AdminSearchField(query, { query = it }, tr(StrAdminX.psSearch)) }
            item {
                AdminChips(
                    listOf<Pair<String?, String>>(
                        null to tr(StrAdminX.psStateAll),
                        PaidState.ACTIVE to tr(StrAdminX.psStateActive),
                        PaidState.EXPIRED to tr(StrAdminX.psStateExpired),
                        PaidState.CLOSED to tr(StrAdminX.psStateClosed),
                    ),
                    state,
                    { state = it },
                    // Counted in people, the same unit as the rows below.
                    counts = mapOf<String?, Int>(null to people.size) + PaidState.ALL.associateWith { s -> people.count { it.has(s) } },
                    tint = Ink.Teal,
                )
            }

            if (rows == null) {
                item { LoadingBlock() }
            } else if (shown.isEmpty()) {
                item { Text(tr(StrAdminX.psNone), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(shown, key = { it.userId }) { p ->
                    PersonRow(p) { navController.navigate("admin/paid-students/${p.userId}") }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(p: PaidPerson, onClick: () -> Unit) {
    val (bg, fg, label) = when {
        p.active > 0 -> Triple(Ink.TealSoft, Ink.Teal, trf(StrAdminX.psOpenCount, p.active))
        p.expired > 0 -> Triple(Ink.AmberSoft, Ink.Amber, tr(StrAdminX.psStateExpired))
        else -> Triple(Ink.CoralSoft, Ink.Coral, tr(StrAdminX.psStateClosed))
    }
    InkCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.first.avatarUrl, p.first.displayName, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.first.displayName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    trf(StrAdminX.psCoursesCount, p.rows.size),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Pill(label, background = bg, foreground = fg)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
        }
    }
}

/** One person's page: who they are, and each of their paid courses with its own controls. */
@Composable
fun PaidStudentDetailScreen(navController: NavHostController, userId: String) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PaidStudent>?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var confirmClose by remember { mutableStateOf<PaidStudent?>(null) }

    LaunchedEffect(userId, tick) {
        runCatching { PaidStudentsRepository.list(null, null, null, null, userId) }
            .onSuccess { rows = it; error = null }
            .onFailure { error = it.toAppError(); if (rows == null) rows = emptyList() }
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
        DetailHeader(tr(StrAdminX.psPersonTitle), onBack = { navController.popBackStack() })

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

            val list = rows
            if (list == null) {
                item { LoadingBlock() }
            } else if (list.isEmpty()) {
                item { Text(tr(StrAdminX.psNone), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                val who = list.first()
                item {
                    InkCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(who.avatarUrl, who.displayName, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(who.displayName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                who.email?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                val total = list.sumOf { it.paidTotal }
                                if (total > 0) {
                                    Text(
                                        tr(StrAdminX.psTotalPaid) + ": " + formatMoney(total, who.currency),
                                        color = Ink.Amber,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                items(list, key = { it.enrollmentId }) { st ->
                    CourseAccessCard(
                        st = st,
                        busy = busy,
                        onClose = { confirmClose = st },
                        onOpen = { act(tr(StrAdminX.psOpenedNote)) { PaidStudentsRepository.open(st.enrollmentId) } },
                        onExtend = { act(tr(StrAdminX.psExtendedNote)) { PaidStudentsRepository.extend(st.enrollmentId, 30) } },
                    )
                }
            }
        }
    }

    confirmClose?.let { st ->
        ConfirmDialog(
            title = trf(StrAdminX.psCloseTitle, st.displayName),
            body = tr(StrAdminX.psCloseBody),
            confirmLabel = tr(StrAdminX.psClose),
            destructive = true,
            onDismiss = { confirmClose = null },
            onConfirm = {
                confirmClose = null
                act(tr(StrAdminX.psClosedNote)) { PaidStudentsRepository.close(st.enrollmentId) }
            },
        )
    }
}

/** True from the day a monthly subscription ends (Cairo date), or when it has already run out. */
private fun monthlyRenewalOpen(expiresAt: String?, state: String): Boolean {
    if (state == PaidState.EXPIRED || expiresAt == null) return true
    return runCatching {
        val cairo = java.time.ZoneId.of("Africa/Cairo")
        val endDay = java.time.OffsetDateTime.parse(expiresAt).toInstant().atZone(cairo).toLocalDate()
        !java.time.LocalDate.now(cairo).isBefore(endDay)
    }.getOrDefault(true)
}

/** One course of one person: how they got in, and the single control for it. */
@Composable
private fun CourseAccessCard(st: PaidStudent, busy: Boolean, onClose: () -> Unit, onOpen: () -> Unit, onExtend: () -> Unit) {
    val (stBg, stFg) = when (st.state) {
        PaidState.ACTIVE -> Ink.TealSoft to Ink.Teal
        PaidState.EXPIRED -> Ink.AmberSoft to Ink.Amber
        else -> Ink.CoralSoft to Ink.Coral
    }
    // Renewal opens on the day the subscription ends and not before; the server enforces the same.
    val renewalOpen = st.isMonthly && monthlyRenewalOpen(st.expiresAt, st.state)
    // A monthly subscriber whose month has ended is controlled only by "renew" (no switch). One that
    // was closed by hand with time still left keeps the switch, so it can simply be opened again.
    val monthlyNeedsExtend = st.isMonthly && st.state != PaidState.ACTIVE && renewalOpen
    InkCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.displayCourse, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            // The switch below already says open / closed; the pill is only for the no-switch case.
            if (monthlyNeedsExtend) {
                Spacer(Modifier.width(8.dp))
                Pill(stateLabel(st.state), background = stBg, foreground = stFg)
            }
        }
        val facts = listOfNotNull(
            sourceLabel(st.source),
            st.paidTotal.takeIf { it > 0 }?.let { trf(StrAdminX.psPaidLine, formatMoney(it, st.currency)) },
            st.expiresAt?.takeIf { st.isMonthly }?.let { trf(StrAdminX.psExpires, formatDate(it)) },
            (st.lastPaidAt ?: st.enrolledAt)?.let { trf(StrAdminX.psEnrolled, formatDate(it)) },
        )
        Spacer(Modifier.height(4.dp))
        Text(facts.joinToString(" · "), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)

        if (!monthlyNeedsExtend) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(if (st.state == PaidState.ACTIVE) StrAdminX.psAccessOpen else StrAdminX.psAccessClosed),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = st.state == PaidState.ACTIVE,
                    enabled = !busy,
                    onCheckedChange = { on -> if (on) onOpen() else onClose() },
                )
            }
        }
        if (st.isMonthly) {
            AdminActions {
                AdminButton(
                    if (renewalOpen) tr(StrAdminX.psExtend)
                    else trf(StrAdminX.psExtendLocked, st.expiresAt?.let { formatDate(it) }.orEmpty()),
                    Modifier.weight(1f),
                    Icons.Default.Update,
                    if (monthlyNeedsExtend) AdminTone.Success else AdminTone.Info,
                    enabled = !busy && renewalOpen,
                    onClick = onExtend,
                )
            }
            if (!renewalOpen) {
                Text(
                    tr(StrAdminX.psExtendRule),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** "All paid courses" or one course, with its own search box. */
@Composable
private fun CourseFilter(courses: List<GrantCourse>, selected: GrantCourse?, onSelect: (GrantCourse?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    Text(tr(StrAdminX.psCourseLabel), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
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
            selected?.displayTitle ?: tr(StrAdminX.psAllCourses),
            Modifier.weight(1f),
            color = if (selected == null) Ink.TextMuted else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted)
    }
    if (open) {
        if (courses.size > 5) AdminSearchField(q, { q = it }, tr(StrAdminX.couponSearchCourse))
        val shown = remember(courses, q) {
            courses.filter { SearchMatch.matches(q, it.title, it.titleAr, it.titleEn) }
                .sortedByDescending { SearchMatch.rank(q, it.displayTitle) }
        }
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
                    Text(tr(StrAdminX.psAllCourses), Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    if (selected == null) Icon(Icons.Default.Check, null, tint = Ink.Teal, modifier = Modifier.size(18.dp))
                }
            }
            shown.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(c); open = false; q = "" }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.displayTitle, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    if (c.id == selected?.id) Icon(Icons.Default.Check, null, tint = Ink.Teal, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
