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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rork.pro.data.Async
import com.rork.pro.data.AppError
import com.rork.pro.data.CourseGrantRepository
import com.rork.pro.data.GrantCourse
import com.rork.pro.data.GrantUser
import com.rork.pro.data.GrantedUser
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Open a paid course for one chosen person, and close it again.
 *
 * Shared by the owner console ([staff] = true: any user, any paid course) and the teacher studio
 * ([staff] = false: only the teacher's own students and only the courses the owner allowed).
 * The server enforces all of it; this screen just shows what it is allowed to.
 *
 * Laid out as two steps — the course, then the person — so the screen is never an empty page with
 * one dropdown: what comes next is always visible, and a finished step collapses into a summary.
 */
@Composable
fun CourseGrantsScreen(navController: NavHostController, staff: Boolean) {
    CourseGrantsPanel(staff = staff, onBack = { navController.popBackStack() })
}

/**
 * The same flow without a screen of its own: the owner opens it from inside the paid-course
 * students screen (with the course already chosen there), the teacher studio shows it as a page.
 * [showOpenedList] is off for the owner because that screen already lists everyone opened by hand,
 * with the switch to close them — a second list here would be the same people twice.
 */
@Composable
fun CourseGrantsPanel(
    staff: Boolean,
    onBack: () -> Unit,
    initialCourse: GrantCourse? = null,
    showOpenedList: Boolean = true,
) {
    val scope = rememberCoroutineScope()

    var courses by remember { mutableStateOf<Async<List<GrantCourse>>>(Async.Loading) }
    var available by remember { mutableStateOf(true) }
    var course by remember { mutableStateOf(initialCourse) }
    var pickerOpen by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GrantUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf<List<GrantedUser>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf<Pair<String, String>?>(null) } // userId to display name
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(staff) {
        courses = runCatching {
            if (staff) {
                CourseGrantRepository.staffCourses()
            } else {
                val mine = CourseGrantRepository.myScope()
                available = mine.enabled && mine.courses.isNotEmpty()
                mine.courses
            }
        }.fold({ Async.Success(it) }, { Async.Failure(it.toAppError()) })
        if (!staff) {
            val list = (courses as? Async.Success)?.value.orEmpty()
            if (list.size == 1) course = list.first()
        }
    }

    // Search as the person types, after a short pause so every letter is not a request.
    LaunchedEffect(course?.id, query, refreshTick) {
        val c = course ?: run { results = emptyList(); return@LaunchedEffect }
        if (staff && query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        searching = true
        delay(280)
        runCatching { CourseGrantRepository.searchUsers(c.id, query.trim()) }
            .onSuccess { results = it; error = null }
            .onFailure { error = it.toAppError() }
        searching = false
    }
    LaunchedEffect(course?.id, refreshTick) {
        val c = course ?: run { granted = emptyList(); return@LaunchedEffect }
        runCatching { CourseGrantRepository.granted(c.id) }
            .onSuccess { granted = it }
            .onFailure { error = it.toAppError() }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; notice = null
            runCatching { block() }.onFailure { error = it.toAppError() }
            busy = false
            refreshTick++
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        DetailHeader(tr(StrAdminX.grantTitle), onBack = onBack)

        when (val c = courses) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(c.error, Modifier.fillMaxSize())
            is Async.Success -> {
                if (!available) {
                    Column(Modifier.padding(Dimens.screenPadding)) {
                        InkCard(contentPadding = PaddingValues(0.dp)) {
                            GrantEmpty(Icons.Default.Lock, tr(StrAdminX.grantNoAccessTitle), tr(StrAdminX.grantNoAccessBody))
                        }
                    }
                } else LazyColumn(
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal)
                                    Text(msg, color = Ink.Teal, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item {
                        Text(tr(StrAdminX.grantIntro), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }

                    // ── step 1: the course ──
                    item { GrantStepTitle(1, tr(StrAdminX.grantPickCourse), done = course != null, enabled = true) }
                    item {
                        GrantCourseSelector(
                            courses = c.value,
                            selected = course,
                            open = pickerOpen,
                            onToggle = { pickerOpen = !pickerOpen },
                            onSelect = { picked ->
                                course = picked; pickerOpen = false
                                query = ""; notice = null; error = null
                            },
                        )
                    }

                    // ── step 2: the person ──
                    item { GrantStepTitle(2, tr(StrAdminX.grantStepPerson), done = false, enabled = course != null) }
                    if (course == null) {
                        item {
                            InkCard(contentPadding = PaddingValues(0.dp)) {
                                GrantEmpty(Icons.Default.School, tr(StrAdminX.grantPickCourseFirst), null)
                            }
                        }
                    } else {
                        item {
                            AdminSearchField(
                                query,
                                { query = it },
                                tr(if (staff) StrAdminX.grantSearchStaff else StrAdminX.grantSearchTeacher),
                            )
                        }
                        item {
                            InkCard(contentPadding = PaddingValues(0.dp)) {
                                when {
                                    staff && query.trim().length < 2 ->
                                        GrantEmpty(Icons.Default.Search, tr(StrAdminX.grantSearchTitle), tr(StrAdminX.grantSearchHintStaff))
                                    searching && results.isEmpty() -> LoadingBlock(Modifier.fillMaxWidth().padding(vertical = 18.dp))
                                    results.isEmpty() -> GrantEmpty(Icons.Default.Search, tr(StrAdminX.grantNoUsers), null)
                                    else -> results.forEachIndexed { i, user ->
                                        if (i > 0) GrantDivider()
                                        val name = user.fullName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty()
                                        GrantRow(
                                            name = name,
                                            email = user.email,
                                            avatarUrl = user.avatarUrl,
                                            extra = if (user.isGranted) tr(StrAdminX.grantOpenedByHand) else null,
                                            busy = busy,
                                            state = when {
                                                user.isGranted -> GrantRowState.CanClose
                                                user.hasAccess -> GrantRowState.HasAccess
                                                else -> GrantRowState.CanOpen
                                            },
                                            onOpen = {
                                                act {
                                                    val r = CourseGrantRepository.grant(user.userId, course!!.id)
                                                    notice = if (r.ok) trf(StrAdminX.grantDoneFor, name) else tr(StrAdminX.grantAlreadyHas)
                                                }
                                            },
                                            onClose = { confirmClose = user.userId to name },
                                        )
                                    }
                                }
                            }
                        }

                        // ── who already has it by hand ──
                        if (showOpenedList) item {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr(StrAdminX.grantOpenedList),
                                    Modifier.weight(1f),
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (granted.isNotEmpty()) Pill(granted.size.toString(), background = Ink.AmberSoft, foreground = Ink.Amber)
                            }
                        }
                        if (showOpenedList) item {
                            InkCard(contentPadding = PaddingValues(0.dp)) {
                                if (granted.isEmpty()) {
                                    GrantEmpty(Icons.Default.LockOpen, tr(StrAdminX.grantNoneYet), tr(StrAdminX.grantNoneYetBody))
                                } else {
                                    granted.forEachIndexed { i, g ->
                                        if (i > 0) GrantDivider()
                                        val name = g.fullName?.takeIf { it.isNotBlank() } ?: g.email.orEmpty()
                                        GrantRow(
                                            name = name,
                                            email = g.email,
                                            avatarUrl = g.avatarUrl,
                                            extra = listOfNotNull(
                                                g.grantedAt?.let { formatDate(it) },
                                                g.grantedByName?.takeIf { it.isNotBlank() }?.let { trf(StrAdminX.grantByLine, it) },
                                            ).joinToString(" · ").ifBlank { null },
                                            busy = busy,
                                            state = GrantRowState.CanClose,
                                            onOpen = {},
                                            onClose = { confirmClose = g.userId to name },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmClose?.let { (userId, name) ->
        ConfirmDialog(
            title = tr(StrAdminX.grantCloseTitle),
            body = trf(StrAdminX.grantCloseBody, name),
            confirmLabel = tr(StrAdminX.grantClose),
            destructive = true,
            onDismiss = { confirmClose = null },
            onConfirm = {
                confirmClose = null
                val id = course?.id ?: return@ConfirmDialog
                act {
                    CourseGrantRepository.revoke(userId, id)
                    notice = tr(StrAdminX.grantClosedDone)
                }
            },
        )
    }
}

private enum class GrantRowState { CanOpen, CanClose, HasAccess }

@Composable
private fun GrantDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Ink.Hairline))
}

/** A numbered step heading: the number turns into a tick once the step is done, and greys out until it applies. */
@Composable
private fun GrantStepTitle(number: Int, title: String, done: Boolean, enabled: Boolean) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val (bg, fg) = when {
            done -> Ink.Teal to Color.White
            enabled -> Ink.Amber to Color.White
            else -> Ink.SurfaceHigh to Ink.TextMuted
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(16.dp))
            } else {
                Text(number.toString(), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = if (enabled) Ink.TextPrimary else Ink.TextMuted,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** A calm placeholder instead of a bare line of text: an icon, what this is, and what to do. */
@Composable
private fun GrantEmpty(icon: ImageVector, title: String, body: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Ink.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink.TextMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

/** One person: avatar, name, email and one clear action or status on the end. */
@Composable
private fun GrantRow(
    name: String,
    email: String?,
    avatarUrl: String?,
    busy: Boolean,
    state: GrantRowState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    extra: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatarUrl, name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (!email.isNullOrBlank()) {
                Text(email, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (!extra.isNullOrBlank()) {
                Text(extra, color = Ink.Amber, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            GrantRowState.CanOpen -> AdminButton(tr(StrAdminX.grantOpen), icon = Icons.Default.LockOpen, tone = AdminTone.Success, enabled = !busy, onClick = onOpen)
            GrantRowState.CanClose -> AdminButton(tr(StrAdminX.grantClose), tone = AdminTone.Danger, enabled = !busy, onClick = onClose)
            GrantRowState.HasAccess -> Pill(tr(StrAdminX.grantHasCourse), background = Ink.TealSoft, foreground = Ink.Teal)
        }
    }
}

/**
 * Step 1. Shows the chosen course as a summary card; tapping it opens the list (with its own search
 * when the list is long). A teacher with a single allowed course has nothing to change, so the card
 * is not tappable for them.
 */
@Composable
private fun GrantCourseSelector(
    courses: List<GrantCourse>,
    selected: GrantCourse?,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (GrantCourse) -> Unit,
) {
    val canChange = courses.size > 1 || selected == null
    var q by remember { mutableStateOf("") }

    InkCard(contentPadding = PaddingValues(0.dp), borderColor = if (selected != null) Ink.Amber else null) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = canChange, onClick = onToggle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ink.AmberSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.School, null, tint = Ink.Amber)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                selected?.displayTitle ?: tr(StrAdminX.grantChooseCourse),
                Modifier.weight(1f),
                color = if (selected == null) Ink.TextMuted else Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            if (canChange) {
                Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted, modifier = Modifier.rotate(if (open) 180f else 0f))
            }
        }
        if (open) {
            GrantDivider()
            if (courses.size > 5) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(horizontal = 14.dp)) { AdminSearchField(q, { q = it }, tr(StrAdminX.couponSearchCourse)) }
                Spacer(Modifier.height(4.dp))
            }
            val shown = remember(courses, q) {
                courses
                    .filter { SearchMatch.matches(q, it.title, it.titleAr, it.titleEn) }
                    .sortedByDescending { SearchMatch.rank(q, it.displayTitle) }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                if (shown.isEmpty()) {
                    Text(tr(StrAdminX.couponNoCourseMatch), Modifier.padding(14.dp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                shown.forEachIndexed { i, c ->
                    if (i > 0) GrantDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(c); q = "" }.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.displayTitle, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        if (c.id == selected?.id) Icon(Icons.Default.Check, null, tint = Ink.Teal)
                    }
                }
            }
        }
    }
}

/**
 * Owner-side control on a teacher's card: switch "can open paid courses for their students" on or
 * off, and choose exactly which courses that covers. Nothing is allowed until at least one course
 * is chosen.
 */
@Composable
internal fun TeacherGrantPermissionEditor(teacherId: String) {
    val scope = rememberCoroutineScope()
    var enabled by remember(teacherId) { mutableStateOf(false) }
    var chosen by remember(teacherId) { mutableStateOf<Set<String>>(emptySet()) }
    var all by remember(teacherId) { mutableStateOf<List<GrantCourse>>(emptyList()) }
    var loaded by remember(teacherId) { mutableStateOf(false) }
    var open by remember(teacherId) { mutableStateOf(false) }
    var q by remember(teacherId) { mutableStateOf("") }
    var saving by remember(teacherId) { mutableStateOf(false) }
    var error by remember(teacherId) { mutableStateOf<AppError?>(null) }

    LaunchedEffect(teacherId) {
        runCatching {
            val perm = CourseGrantRepository.teacherPermission(teacherId)
            enabled = perm.enabled
            chosen = perm.courseIds.toSet()
            all = CourseGrantRepository.staffCourses()
        }.onFailure { error = it.toAppError() }
        loaded = true
    }
    if (!loaded) return

    ToggleRow(tr(StrAdminX.grantTeacherToggle), enabled) { value ->
        scope.launch {
            saving = true; error = null
            runCatching { CourseGrantRepository.setTeacherPermission(teacherId, value, null) }
                .onSuccess { enabled = value }
                .onFailure { error = it.toAppError() }
            saving = false
        }
    }
    error?.let { Text(it.message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall) }

    if (enabled) {
        Spacer(Modifier.height(6.dp))
        Text(tr(StrAdminX.grantTeacherCourses), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.Surface)
                .border(1.dp, if (chosen.isEmpty()) Ink.Coral else Ink.Amber, RoundedCornerShape(16.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (chosen.isEmpty()) tr(StrAdminX.grantTeacherNone) else trf(StrAdminX.grantTeacherCount, chosen.size),
                Modifier.weight(1f),
                color = if (chosen.isEmpty()) Ink.Coral else Ink.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            AdminSearchField(q, { q = it }, tr(StrAdminX.couponSearchCourse))
            Spacer(Modifier.height(6.dp))
            val shown = remember(all, q) {
                all.filter { SearchMatch.matches(q, it.title, it.titleAr, it.titleEn) }
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
                if (shown.isEmpty()) {
                    Text(tr(StrAdminX.couponNoCourseMatch), Modifier.padding(14.dp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                shown.forEach { c ->
                    val on = c.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { chosen = if (on) chosen - c.id else chosen + c.id }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.displayTitle, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        if (on) Icon(Icons.Default.Check, null, tint = Ink.Teal)
                    }
                }
            }
        }
        AdminActions {
            AdminButton(
                tr(StrAdminX.grantTeacherSave),
                Modifier.weight(1f),
                Icons.Default.Check,
                AdminTone.Primary,
                enabled = !saving,
            ) {
                scope.launch {
                    saving = true; error = null
                    runCatching { CourseGrantRepository.setTeacherPermission(teacherId, null, chosen.toList()) }
                        .onSuccess { open = false }
                        .onFailure { error = it.toAppError() }
                    saving = false
                }
            }
        }
    }
}
