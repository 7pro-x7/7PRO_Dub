package com.rork.pro.ui.screens.classroom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.classroom.ClassroomCallActivity
import com.rork.pro.data.Async
import com.rork.pro.data.ClassroomGroupWithStudents
import com.rork.pro.data.ClassroomSession
import com.rork.pro.data.ClassroomStudentHit
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- Session list

@Composable
fun ClassroomHomeScreen(navController: NavHostController, session: SessionViewModel, scope: ClassroomScope) {
    val vm: ClassroomListViewModel = viewModel()
    LaunchedEffect(scope) { vm.bind(scope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busyIds by vm.busyIds.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val canManage = scope == ClassroomScope.TEACHER || scope == ClassroomScope.STAFF
    // True only once the list has loaded and really has nothing in it.
    val listIsEmpty = when (val s = state) {
        is Async.Success -> s.value.isEmpty()
        else -> false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPaddingSafe, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink.TextPrimary)
                }
                Text(
                    if (scope == ClassroomScope.STAFF) tr(StrClassroom.adminAllSessions) else tr(StrClassroom.title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                // Balances the back arrow so the title stays visually centered now that
                // "add" moved out of the top bar and into the floating action button below.
                Spacer(Modifier.width(48.dp))
            }

            // Surfaces a failed cancel/end action — previously that failure had zero visible
            // effect: the busy spinner just stopped and the session stayed exactly as it was.
            error?.let { err ->
                InkCard(
                    modifier = Modifier.padding(horizontal = Dimens.screenPaddingSafe, vertical = 4.dp),
                    borderColor = Ink.Coral.copy(alpha = 0.4f),
                ) {
                    Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(tr(Str.close)) { vm.clearError() }
                }
            }

            when (val s = state) {
                is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
                is Async.Failure -> ErrorBlock(s.error, Modifier.padding(20.dp)) { vm.refresh() }
                is Async.Success -> {
                    val all = s.value
                    // Local filtering only — the list is already fully loaded, so searching it
                    // needs no extra round-trip and stays instant offline. Matches the class
                    // title and the teacher's name, which is how both a teacher hunting for
                    // "which of my classes was that" and a student looking for a specific
                    // teacher actually search.
                    val sessions = remember(all, query) {
                        val q = query.trim()
                        if (q.isBlank()) all
                        else all.filter { session ->
                            session.title.contains(q, ignoreCase = true) ||
                                session.teacher?.fullName.orEmpty().contains(q, ignoreCase = true)
                        }
                    }
                    // The search box only earns its space once there are enough classes for
                    // scanning to be a chore.
                    if (all.size >= 6) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(tr(StrClassroom.searchSessions)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink.TextMuted) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.screenPaddingSafe, vertical = 4.dp),
                            colors = fieldColors(),
                        )
                    }
                    if (all.isEmpty()) {
                        // The teacher's big create button lives inside this screen, so the FAB
                        // below steps aside while the list is empty (one primary action, not two).
                        ClassroomEmptyState(
                            canManage = canManage,
                            onCreate = { navController.navigate(ClassroomRoutes.CREATE) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (sessions.isEmpty()) {
                        EmptyBlock(tr(StrClassroom.noResults), "", modifier = Modifier.padding(20.dp))
                    } else {
                        LazyColumn(
                            // Extra bottom room so the last card never sits under the FAB.
                            contentPadding = PaddingValues(
                                start = Dimens.screenPaddingSafe,
                                end = Dimens.screenPaddingSafe,
                                top = 8.dp,
                                bottom = if (canManage) 104.dp else 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(sessions, key = { it.id }) { item ->
                                SessionCard(
                                    item,
                                    canManage = canManage,
                                    busy = item.id in busyIds,
                                    onOpen = { navController.navigate(ClassroomRoutes.lobby(item.id)) },
                                    onCancel = { vm.cancelSession(item.id) },
                                    onEnd = { vm.endSession(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Centered "add session" FAB — the professional, thumb-friendly spot for the
        // primary create action, replacing the small top-bar icon. Amber-on-ink matches
        // the app's one accent color so it reads as the single obvious next action.
        if (canManage && !listIsEmpty) {
            FloatingActionButton(
                onClick = { navController.navigate(ClassroomRoutes.CREATE) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .size(64.dp),
                shape = CircleShape,
                containerColor = Ink.Amber,
                contentColor = Ink.OnAmber,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 3.dp,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = tr(StrClassroom.newSession), modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(
    item: ClassroomSession,
    canManage: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM • h:mm a") }
    val startLabel = remember(item.scheduledStart) {
        runCatching { formatter.format(Instant.parse(item.scheduledStart).atZone(ZoneId.systemDefault())) }
            .getOrDefault(item.scheduledStart)
    }

    InkCard(onClick = onOpen, borderColor = if (item.isLive) Ink.CtaGreen.copy(alpha = 0.5f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Ink.TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Videocam, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    item.teacher?.fullName ?: startLabel,
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            when {
                item.isLive -> LivePill()
                item.status == "CANCELED" -> Pill(tr(StrClassroom.canceled), background = Ink.SurfaceHigh, foreground = Ink.TextMuted)
                item.status == "ENDED" -> Pill(tr(StrClassroom.ended), background = Ink.SurfaceHigh, foreground = Ink.TextMuted)
                item.status == "EXPIRED" -> Pill(tr(StrClassroom.expired), background = Ink.SurfaceHigh, foreground = Ink.TextMuted)
            }
        }

        if (!item.isPast) {
            Spacer(Modifier.height(10.dp))
            Text(startLabel, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }

        if (canManage && item.status == "SCHEDULED") {
            Spacer(Modifier.height(12.dp))
            SecondaryAction(tr(StrClassroom.cancelSession), enabled = !busy) { confirmCancel = true }
        }
        if (canManage && item.isLive) {
            Spacer(Modifier.height(12.dp))
            SecondaryAction(tr(StrClassroom.endForEveryone), enabled = !busy, tint = Ink.Coral) { onEnd() }
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = tr(StrClassroom.cancelSession),
            body = tr(StrClassroom.cancelSessionConfirm),
            confirmLabel = tr(StrClassroom.cancelSession),
            onDismiss = { confirmCancel = false },
            onConfirm = { confirmCancel = false; onCancel() },
        )
    }
}

// ---------------------------------------------------------------- Create session

@Composable
private fun CreateOptionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) Ink.TextPrimary else Ink.Surface)
            .border(1.dp, if (selected) Ink.TextPrimary else Ink.Hairline, RoundedCornerShape(21.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Ink.Canvas else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun ClassroomCreateScreen(navController: NavHostController) {
    val vm: ClassroomCreateViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Dimens.screenPaddingSafe),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink.TextPrimary)
                }
                Text(tr(StrClassroom.createSession), style = MaterialTheme.typography.titleLarge, color = Ink.TextPrimary)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(tr(StrClassroom.inviteStudents), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
                // No title, description or start/end time here anymore — the session goes live
                // the moment the teacher taps Create (see DEFAULT_SESSION_MINUTES in the
                // ViewModel) and names itself (see autoSessionTitle). Asking a teacher to type a
                // lesson name every single time was pure friction on the one screen that must be
                // instant: the students are already listed below, and the name the teacher would
                // have typed added nothing the card doesn't already show (their own name and the
                // start time, right under the title).
                // Instead of a global name/email search, a teacher picks from the people already
                // subscribed with them: a whole group in one tap, or hand-picked students, with
                // individual members uncheckable as exceptions once a group is selected.
                if (state.loadingGroups) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink.Amber)
                            Spacer(Modifier.width(8.dp))
                            Text(tr(Str.loading), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (state.groups.isEmpty()) {
                    item { Text(tr(StrClassroom.noLinkedStudents), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall) }
                } else {
                    state.groups.forEach { group ->
                        item(key = "group-${group.groupId}") {
                            GroupSelectCard(
                                group = group,
                                fullySelected = state.isGroupFullySelected(group),
                                selectedCount = state.selectedCountFor(group),
                                selectedStudents = state.selectedStudents,
                                onToggleGroup = { vm.toggleGroup(group) },
                                onToggleStudent = vm::toggleStudent,
                            )
                        }
                    }
                }
                if (state.selectedStudents.isNotEmpty()) {
                    item { Text(trf(StrClassroom.studentsInvited, state.selectedStudents.size), color = Ink.Teal) }
                }
                item {
                    Text(tr(StrClassroom.startTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to StrClassroom.startNow, 60 to StrClassroom.startIn1h, 24 * 60 to StrClassroom.startTomorrow).forEach { (mins, label) ->
                            CreateOptionChip(tr(label), state.startInMinutes == mins, Modifier.weight(1f)) { vm.setStartIn(mins) }
                        }
                    }
                    if (state.startInMinutes > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(tr(StrClassroom.startLaterHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text(tr(StrClassroom.durationTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(45, 60, 90, 120).forEach { mins ->
                            CreateOptionChip(trf(StrClassroom.minutesShort, mins), state.durationMinutes == mins, Modifier.weight(1f)) { vm.setDuration(mins) }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    PrimaryAction(tr(StrClassroom.create), loading = state.submitting) {
                        val startsLater = state.startInMinutes > 0
                        vm.submit { id ->
                            if (startsLater) {
                                navController.popBackStack()
                            } else {
                                // Straight into the class — the teacher used to land on a lobby and
                                // had to tap "Join" again while students were already being rung.
                                navController.navigate(ClassroomRoutes.lobby(id, autoJoin = true)) {
                                    popUpTo(ClassroomRoutes.HOME) { inclusive = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRow(hit: ClassroomStudentHit, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink.TealSoft else Ink.SurfaceHigh)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(hit.avatarUrl, hit.fullName, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.fullName ?: hit.email ?: hit.id, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            if (!hit.email.isNullOrBlank()) {
                Text(hit.email, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) Icon(Icons.Default.Groups, null, tint = Ink.Teal)
    }
}

/**
 * One subscribed group in the create-session picker: a header row selects/deselects every
 * (account-linked) member in one tap, and each member below it can be individually unchecked —
 * that per-student toggle is how a teacher carves out an exception without leaving the group.
 */
@Composable
private fun GroupSelectCard(
    group: ClassroomGroupWithStudents,
    fullySelected: Boolean,
    selectedCount: Int,
    selectedStudents: Map<String, ClassroomStudentHit>,
    onToggleGroup: () -> Unit,
    onToggleStudent: (ClassroomStudentHit) -> Unit,
) {
    // Collapsed by default — the header row (checkbox, name, count) is all a teacher sees
    // at first. Tapping the "+" on the right expands the card to reveal the member list;
    // tapping it again (now shown as "×") collapses it back. Selecting/deselecting the whole
    // group via its checkbox never requires expanding it.
    var expanded by remember(group.groupId) { mutableStateOf(false) }

    InkCard(color = Ink.SurfaceHigh) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleGroup)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = fullySelected, onCheckedChange = { onToggleGroup() })
                Column(Modifier.weight(1f)) {
                    Text(group.groupName, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        trf(StrClassroom.groupSelectedCount, selectedCount, group.students.size),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (group.students.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            tint = Ink.TextMuted,
                        )
                    }
                }
            }
            if (expanded && group.students.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.students.forEach { hit ->
                        StudentRow(hit, selected = selectedStudents.containsKey(hit.id)) { onToggleStudent(hit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Ink.SurfaceHigh,
    unfocusedContainerColor = Ink.SurfaceHigh,
    focusedIndicatorColor = Ink.Amber,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
)

// ---------------------------------------------------------------- Lobby (pre-join)

/**
 * Opens the hosted web classroom (web/classroom.html) in the phone's browser. This is how a
 * phone below Android 8, which cannot run the in-app video, still gets into a live class: the
 * person signs in there with their own account and joins from their session list — as
 * themselves, not as an anonymous guest, so nothing depends on guest access being switched on.
 */
suspend fun openClassroomInBrowser(context: android.content.Context, sessionId: String? = null) {
    // With a session, go straight into it (the web page lets guests in by invite link). If that
    // cannot be built — offline, not signed in, session already over — fall back to the plain
    // page, where the person can still sign in and pick the class themselves.
    val url = sessionId
        ?.let { runCatching { com.rork.pro.data.ClassroomRepository.browserJoinUrl(it) }.getOrNull() }
        ?: com.rork.pro.data.ClassroomWeb.BASE_URL
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun ClassroomLobbyScreen(
    navController: NavHostController,
    session: SessionViewModel,
    sessionId: String,
    autoJoin: Boolean = false,
) {
    // The video SDK cannot run below Android 8.0. Stop here — before anything is loaded or the
    // server is told this person "joined" a call they could never open.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = Dimens.screenPaddingSafe)) {
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink.TextPrimary)
                }
                EmptyBlock(
                    tr(StrClassroom.unsupportedTitle),
                    tr(StrClassroom.unsupportedBody),
                    modifier = Modifier.padding(20.dp),
                )
                val ctx = LocalContext.current
                val browserScope = androidx.compose.runtime.rememberCoroutineScope()
                PrimaryAction(tr(StrClassroom.openInBrowser)) {
                    browserScope.launch { openClassroomInBrowser(ctx, sessionId) }
                }
            }
        }
        return
    }

    val vm: ClassroomLobbyViewModel = viewModel()
    LaunchedEffect(sessionId) { vm.bind(sessionId) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Coming from Home's "Join the meeting now": enter as soon as the session has loaded. Fires
    // once only — if the join fails the person lands on the normal error/retry state instead of
    // being re-joined in a loop.
    var autoJoinDone by remember { mutableStateOf(false) }
    LaunchedEffect(state, autoJoin) {
        if (autoJoin && !autoJoinDone && state is LobbyState.Ready) {
            autoJoinDone = true
            vm.join()
        }
    }
    val context = LocalContext.current
    val sessionState by session.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Dimens.screenPaddingSafe)) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink.TextPrimary)
            }

            when (val s = state) {
                is LobbyState.Loading -> LoadingBlock(Modifier.fillMaxSize(), tr(StrClassroom.checkingAccess))
                is LobbyState.Error -> ErrorBlock(s.error, Modifier.padding(20.dp)) { vm.load() }
                is LobbyState.Ready -> LobbyReady(s.session, s.participantCount) { vm.join() }
                is LobbyState.Joining -> LobbyReady(s.session, null, joining = true) {}
                is LobbyState.Joined -> {
                    val info = s.info
                    LaunchedEffect(info) {
                        val jwt = com.rork.pro.data.ClassroomRepository.fetchJitsiToken(sessionId)
                        ClassroomCallActivity.launch(
                            context = context,
                            sessionId = sessionId,
                            info = info,
                            title = s.title,
                            jwt = jwt,
                        )
                        navController.popBackStack()
                    }
                    LoadingBlock(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun LobbyReady(
    item: ClassroomSession,
    participantCount: Int?,
    joining: Boolean = false,
    onJoin: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.9f))

        // The hook: a live, pulsing hero instead of a static parked icon. Teacher photo when
        // there is one, so the person is looking at *who* they're about to see, not a generic
        // video glyph.
        PulsingClassAvatar(avatarUrl = item.teacher?.avatarUrl, name = item.teacher?.fullName ?: item.title)

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.CtaGreen.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PulsingDot(color = Ink.CtaGreen)
            Text(
                tr(StrClassroom.lobbyBadge),
                color = Ink.CtaGreen,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            item.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        val teacherName = item.teacher?.fullName
        if (!teacherName.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                trf(StrClassroom.lobbyWithTeacher, teacherName),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            tr(StrClassroom.lobbyHook),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(Modifier.height(22.dp))

        // A quick-glance readiness strip — what Zoom-style pre-join screens use to make joining
        // feel like a sure thing rather than a leap into the unknown.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadinessChip(Icons.Default.Videocam, tr(StrClassroom.lobbyVideoReady))
            ReadinessChip(Icons.Default.Mic, tr(StrClassroom.lobbyAudioReady))
        }

        if (participantCount != null && participantCount > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Groups, null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
                Text(
                    trf(StrClassroom.participantsCount, participantCount),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.weight(1.1f))

        PrimaryAction(
            tr(StrClassroom.joinNow),
            loading = joining,
            containerColor = Ink.CtaGreen,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp),
        ) { onJoin() }
    }
}

/**
 * Teacher (or session) avatar ringed by two soft rings that pulse outward on a loop, offset
 * half a cycle apart so a fresh ring is always starting as the other fades — the same visual
 * grammar as a live-call indicator, which is what turns "here's an icon" into "this is live and
 * waiting for you". Falls back to a plain video glyph when there's no teacher photo to show.
 */
@Composable
private fun PulsingClassAvatar(avatarUrl: String?, name: String?) {
    val transition = rememberInfiniteTransition(label = "lobbyPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "lobbyPulsePhase",
    )
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        listOf(phase, (phase + 0.5f) % 1f).forEach { ringPhase ->
            Box(
                Modifier
                    .size(96.dp + 46.dp * ringPhase)
                    .clip(CircleShape)
                    .background(Ink.CtaGreen.copy(alpha = (1f - ringPhase) * 0.20f)),
            )
        }
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Ink.TealSoft)
                .border(2.5.dp, Ink.CtaGreen.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.Videocam, null, tint = Ink.Teal, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/** Small breathing dot used to mark something as live right now — see [LivePill] and the lobby badge. */
@Composable
private fun PulsingDot(color: androidx.compose.ui.graphics.Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/** The session list's "Live" badge — a pulsing dot instead of a flat pill so a live class reads as live at a glance. */
@Composable
internal fun LivePill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Ink.CtaGreen.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PulsingDot(color = Ink.CtaGreen, size = 6.dp)
        Text(
            tr(StrClassroom.liveNow),
            color = Ink.CtaGreen,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** One "everything's fine" chip in the lobby's readiness strip. */
@Composable
private fun ReadinessChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Ink.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Ink.Teal, modifier = Modifier.size(15.dp))
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private object Dimens {
    val screenPaddingSafe = 20.dp
}
