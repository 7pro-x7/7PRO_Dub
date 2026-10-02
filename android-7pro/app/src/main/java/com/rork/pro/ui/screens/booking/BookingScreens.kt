package com.rork.pro.ui.screens.booking

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Close
import com.rork.pro.data.BookingGroupInfo
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.data.AppError
import com.rork.pro.data.BookingGroup
import com.rork.pro.data.BookingTeacher
import com.rork.pro.data.GroupBookingRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LevelBadge
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.StrNav
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.launch

/**
 * Turns a server error code into copy a student can act on.
 *
 * The server is the only thing that decides whether a booking is allowed, so its codes are
 * mapped here rather than re-checked in the app — an app-side guess would eventually
 * disagree with the real rule and confuse the student.
 */
@Composable
private fun bookingMessage(error: Throwable): String {
    val raw = error.message.orEmpty()
    val known = when {
        raw.contains("ALREADY_SUBSCRIBED") -> StrBooking.errAlreadySubscribed
        raw.contains("GROUP_NOT_AVAILABLE") -> StrBooking.errGroupUnavailable
        raw.contains("TEACHER_NOT_AVAILABLE") -> StrBooking.errTeacherUnavailable
        raw.contains("GROUP_FULL") -> StrBooking.errGroupFull
        raw.contains("PRICE_NOT_SET") -> StrBooking.errPriceNotSet
        raw.contains("NOTHING_TO_PAY") -> StrBooking.errNothingToPay
        raw.contains("NOT_DUE_YET") -> StrBooking.errNotDueYet
        raw.contains("PARENT_PHONE_INVALID") -> StrBooking.phoneInvalid
        raw.contains("DUPLICATE_STUDENT") -> StrBooking.errDuplicateStudent
        raw.contains("TOO_MANY_STUDENTS") -> StrBooking.errTooMany
        raw.contains("STUDENT_NAME_REQUIRED") -> StrBooking.fieldRequired
        else -> null
    }
    return known?.let { tr(it) } ?: error.toAppError().message
}

// ─────────────────────────────────────────────────────────────────────────
// Step 1 — the teacher
// ─────────────────────────────────────────────────────────────────────────

/**
 * Who the student can book with.
 *
 * Only teachers the owner has marked available, and only ones with an open group, are ever
 * listed: a teacher shown here can always be booked, so the student never picks their way
 * into a dead end.
 */
@Composable
fun BookingTeachersScreen(navController: NavHostController) {
    var teachers by remember { mutableStateOf<List<BookingTeacher>>(emptyList()) }
    var recommendedId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<AppError?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runCatching { GroupBookingRepository.teachers() }
            .onSuccess { teachers = it }
            .onFailure { error = it.toAppError() }
        recommendedId = runCatching { GroupBookingRepository.nearestGroup()?.teacherId }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) { load() }
    val scope = rememberCoroutineScope()

    BookingScaffold(
        title = tr(StrBooking.title),
        step = 1,
        onBack = { BookingHistory.back(navController, Routes.BOOKING_TEACHERS) },
        onForward = if (BookingHistory.canGoForward()) {
            { BookingHistory.goForward(navController) }
        } else null,
    ) {
        when {
            loading -> LoadingBlock(Modifier.fillMaxSize())
            error != null -> ErrorBlock(error!!, Modifier.fillMaxSize()) { scope.launch { load() } }
            teachers.isEmpty() -> EmptyBlock(
                tr(StrBooking.noTeachers),
                tr(StrBooking.noTeachersBody),
                Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AdBanner("BOOKING") }
                item {
                    Text(
                        tr(StrBooking.stepTeacher),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(teachers, key = { it.teacherId }) { teacher ->
                    TeacherCard(
                        teacher = teacher,
                        recommended = teacher.teacherId == recommendedId,
                    ) {
                        BookingHistory.clear()
                        navController.navigate(Routes.bookingGroups(teacher.teacherId))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherCard(teacher: BookingTeacher, recommended: Boolean, onClick: () -> Unit) {
    // A premium teacher keeps the amber treatment even when they are not the recommended one,
    // since that badge is the owner's deliberate promotion rather than a per-student match.
    val accent = if (recommended || teacher.isPremium) Ink.Amber else Ink.Teal
    InkCard(
        onClick = onClick,
        borderColor = accent.copy(alpha = if (recommended || teacher.isPremium) 0.55f else 0.18f),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (recommended || teacher.isPremium) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (teacher.isPremium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            null,
                            tint = Ink.Amber,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        // The owner's own wording when they set one, otherwise the plain badge.
                        Pill(teacher.badge?.takeIf { it.isNotBlank() } ?: tr(StrBooking.premiumBadge))
                    }
                    if (recommended) Spacer(Modifier.width(8.dp))
                }
                if (recommended) {
                    Pill(
                        tr(StrBooking.recommendedForYou),
                        background = Ink.TealSoft,
                        foreground = Ink.Teal,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(teacher.avatarUrl, teacher.fullName, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    teacher.fullName,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                teacher.headline?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (teacher.rating > 0) {
                        Icon(Icons.Default.Star, null, tint = Ink.Amber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            String.format("%.1f", teacher.rating),
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(Icons.Default.Groups, null, tint = Ink.TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        trf(StrBooking.groupsCount, teacher.groupsCount),
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    tr(StrBooking.startsFrom),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    formatMoney(teacher.minPrice, teacher.currency),
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Step 2 — the group
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun BookingGroupsScreen(navController: NavHostController, teacherId: String) {
    var groups by remember { mutableStateOf<List<BookingGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<AppError?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        error = null
        runCatching { GroupBookingRepository.groups(teacherId) }
            .onSuccess { groups = it }
            .onFailure { error = it.toAppError() }
        loading = false
    }

    LaunchedEffect(teacherId) { load() }

    BookingScaffold(
        title = tr(StrBooking.title),
        step = 2,
        onBack = { BookingHistory.back(navController, Routes.bookingGroups(teacherId)) },
        onForward = if (BookingHistory.canGoForward()) {
            { BookingHistory.goForward(navController) }
        } else null,
    ) {
        when {
            loading -> LoadingBlock(Modifier.fillMaxSize())
            error != null -> ErrorBlock(error!!, Modifier.fillMaxSize()) { scope.launch { load() } }
            groups.isEmpty() -> EmptyBlock(
                tr(StrBooking.noGroups),
                tr(StrBooking.noGroupsBody),
                Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AdBanner("BOOKING_GROUPS") }
                item {
                    Text(
                        tr(StrBooking.stepGroup),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(groups, key = { it.groupId }) { group ->
                    GroupCard(group) {
                        if (!group.isFull) {
                            BookingHistory.clear()
                            navController.navigate(Routes.bookingDetails(group.groupId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: BookingGroup, onClick: () -> Unit) {
    val accent = if (group.isFull) Ink.Neutral else Ink.Teal
    InkCard(
        onClick = if (group.isFull) null else onClick,
        borderColor = accent.copy(alpha = 0.2f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!group.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Seats are only mentioned for a capped group; on an uncapped one a
                    // "seats left" line would be an invented number.
                    when {
                        group.isFull -> tr(StrBooking.groupFull)
                        group.seatsLeft != null -> trf(StrBooking.seatsLeft, group.seatsLeft)
                        else -> trf(StrBooking.members, group.members)
                    },
                    color = if (group.isFull) Ink.Coral else Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                group.schedule?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = Ink.TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                group.level?.let { LevelBadge(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    trf(StrBooking.perMonth, formatMoney(group.price, group.currency)),
                    color = Ink.Amber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Step 3 — parent and student details
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun BookingDetailsScreen(navController: NavHostController, groupId: String) {
    var parentName by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }
    // One text box per child. The first is always there; more can be added up to what the group
    // has room for (never more than MAX_STUDENTS).
    val students = remember { mutableStateListOf("") }
    var note by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<BookingGroupInfo?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) { info = GroupBookingRepository.groupInfo(groupId) }

    val maxStudents = (info?.seatsLeft?.coerceAtLeast(1) ?: MAX_STUDENTS).coerceAtMost(MAX_STUDENTS)
    val phoneDigits = parentPhone.filter(Char::isDigit)
    val phoneOk = Regex("^01[0125]\\d{8}$").matches(phoneDigits)
    val names = students.map { it.trim() }
    val duplicate = names.filter { it.isNotEmpty() }.let { list -> list.size != list.map { it.lowercase() }.toSet().size }
    val canSubmit = parentName.isNotBlank() && names.all { it.isNotEmpty() } && phoneOk && !duplicate

    BookingScaffold(
        title = tr(StrBooking.title),
        step = 3,
        onBack = { BookingHistory.back(navController, Routes.bookingDetails(groupId)) },
        onForward = if (BookingHistory.canGoForward()) {
            { BookingHistory.goForward(navController) }
        } else null,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.screenPadding)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(4.dp))
                DetailsHeroCard()
                Spacer(Modifier.height(14.dp))
                TrustRow()
                Spacer(Modifier.height(22.dp))

                Text(tr(StrBooking.stepDetails), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(tr(StrBooking.detailsIntro), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                BookingField(
                    value = parentName,
                    onChange = { parentName = it },
                    label = tr(StrBooking.parentName),
                    icon = Icons.Default.Person,
                    error = touched && parentName.isBlank(),
                    errorText = tr(StrBooking.fieldRequired),
                )
                Spacer(Modifier.height(12.dp))
                BookingField(
                    value = parentPhone,
                    onChange = { parentPhone = it.filter(Char::isDigit).take(11) },
                    label = tr(StrBooking.parentPhone),
                    icon = Icons.Default.Phone,
                    keyboard = KeyboardType.Phone,
                    placeholder = tr(StrBooking.phoneHint),
                    error = touched && !phoneOk,
                    errorText = tr(StrBooking.phoneInvalid),
                )
                Spacer(Modifier.height(12.dp))
                students.forEachIndexed { index, name ->
                    BookingField(
                        value = name,
                        onChange = { students[index] = it },
                        label = if (students.size == 1) tr(StrBooking.studentName) else trf(StrBooking.studentNameN, index + 1),
                        icon = Icons.Default.Person,
                        error = touched && (name.isBlank() || (duplicate && names.count { it.equals(name.trim(), ignoreCase = true) } > 1)),
                        errorText = tr(if (name.isBlank()) StrBooking.fieldRequired else StrBooking.errDuplicateStudent),
                        trailing = if (index > 0) {
                            {
                                IconButton(onClick = { students.removeAt(index) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = tr(StrBooking.removeStudent),
                                        tint = Ink.TextMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else null,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (students.size < maxStudents) {
                    AddStudentButton { students.add("") }
                    Spacer(Modifier.height(12.dp))
                } else if (info?.seatsLeft != null && students.size >= (info?.seatsLeft ?: 0)) {
                    Text(
                        trf(StrBooking.seatsLimit, info?.seatsLeft),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (students.size > 1 && info != null && (info?.price ?: 0.0) > 0) {
                    val i = info!!
                    StudentsTotalRow(
                        count = students.size,
                        each = formatMoney(i.price, i.currency),
                        total = formatMoney(i.price * students.size, i.currency),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                BookingField(
                    value = note,
                    onChange = { note = it },
                    label = tr(StrBooking.noteOptional),
                    icon = null,
                    imeAction = ImeAction.Done,
                    singleLine = false,
                )

                failure?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(bookingMessage(it), color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }

            Column(Modifier.navigationBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, null, tint = Ink.TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(tr(StrBooking.payNextHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                PrimaryAction(
                    tr(StrBooking.continueToPayment),
                    loading = submitting,
                    enabled = canSubmit,
                    containerColor = Ink.CtaGreen,
                    contentColor = Ink.OnCtaGreen,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    touched = true
                    if (!canSubmit) return@PrimaryAction
                    submitting = true
                    failure = null
                    scope.launch {
                        runCatching {
                            GroupBookingRepository.bookBatch(groupId, parentName, parentPhone, names, note)
                        }
                            .onSuccess { booking ->
                                // The seat exists but is not paid for, so the payment screen
                                // replaces this one: backing up here would only offer to book a
                                // second seat the server would refuse.
                                BookingHistory.clear()
                                navController.navigate(Routes.bookingPay(booking.subscriptionIds)) {
                                    popUpTo(Routes.BOOKING_TEACHERS) { inclusive = true }
                                }
                            }
                            .onFailure { failure = it }
                        submitting = false
                    }
                }
            }
        }
    }
}

/**
 * Warm reassurance panel at the top of the details step — sets the expectation that this is
 * the last bit of typing before a seat is requested, so the form below reads as short rather
 * than as a wall of fields.
 */
@Composable
private fun DetailsHeroCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Brush.horizontalGradient(listOf(Ink.TealSoft, Ink.AmberSoft)))
            .border(1.dp, Ink.Teal.copy(alpha = 0.22f), RoundedCornerShape(Dimens.cardRadius))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Ink.Teal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Groups, null, tint = Ink.OnTeal, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(StrBooking.detailsHeroTitle),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr(StrBooking.detailsHeroBody),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Three short reassurances that lower the friction of handing over a phone number. */
@Composable
private fun TrustRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrustChip(Icons.Default.Bolt, tr(StrBooking.trustFast), Ink.Amber, Modifier.weight(1f))
        TrustChip(Icons.Default.Lock, tr(StrBooking.trustSecure), Ink.Teal, Modifier.weight(1f))
        TrustChip(Icons.Default.VerifiedUser, tr(StrBooking.trustQualified), Ink.Sky, Modifier.weight(1f))
    }
}

@Composable
private fun TrustChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** The most children one booking can hold (the server enforces the same limit). */
private const val MAX_STUDENTS = 5

/** "Add another student": a soft outlined tile in the same teal as the field icons. */
@Composable
private fun AddStudentButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Teal.copy(alpha = 0.08f))
            .border(1.dp, Ink.Teal.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.PersonAdd, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr(StrBooking.addStudent), color = Ink.Teal, style = MaterialTheme.typography.titleSmall)
    }
}

/** "2 students × 400 EGP  ·  Total 800 EGP" — shown once there is more than one child. */
@Composable
private fun StudentsTotalRow(count: Int, each: String, total: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Ink.AmberSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            trf(StrBooking.studentsSummary, count, each),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text("${tr(StrBooking.studentsTotal)}: $total", color = Ink.Amber, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BookingField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    placeholder: String? = null,
    singleLine: Boolean = true,
    error: Boolean = false,
    errorText: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, color = Ink.TextMuted) },
            placeholder = placeholder?.let { { Text(it, color = Ink.TextMuted) } },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            isError = error,
            leadingIcon = icon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (error) Ink.Coral else Ink.Teal,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Teal,
                unfocusedBorderColor = Ink.Hairline,
                errorBorderColor = Ink.Coral,
                cursorColor = Ink.Teal,
                focusedTextColor = Ink.TextPrimary,
                unfocusedTextColor = Ink.TextPrimary,
            ),
        )
        if (error && errorText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(errorText, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Shared chrome
// ─────────────────────────────────────────────────────────────────────────

/**
 * A browser-style forward stack for the group-booking funnel (teacher → group → details).
 *
 * Stepping back with the header's back arrow remembers the exact step you left, so the
 * forward arrow can return you to it without re-fetching or re-picking anything. Moving
 * ahead any other way — tapping a card — invalidates whatever was ahead, exactly like a
 * browser tab: there is no meaningful "forward" once you have chosen a different path.
 */
internal object BookingHistory {
    private val forward = mutableListOf<String>()

    fun canGoForward(): Boolean = forward.isNotEmpty()

    /** Use instead of navController.popBackStack() from a booking step's back arrow. */
    fun back(navController: NavHostController, currentRoute: String) {
        forward.add(currentRoute)
        navController.popBackStack()
    }

    /** Use from the header's forward arrow. Restores the step the user backed out of. */
    fun goForward(navController: NavHostController) {
        val route = forward.removeLastOrNull() ?: return
        navController.navigate(route)
    }

    /** Call whenever the funnel moves ahead through a normal tap, not the forward arrow. */
    fun clear() {
        forward.clear()
    }
}

/** Header, progress rail and background, shared by every step so the funnel reads as one flow. */
@Composable
internal fun BookingScaffold(
    title: String,
    step: Int,
    onBack: () -> Unit,
    onForward: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .appBackdrop(),
    ) {
        DetailHeader(
            title,
            onBack = onBack,
            trailing = onForward?.let { forward ->
                {
                    androidx.compose.material3.IconButton(onClick = forward) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = tr(StrNav.forward),
                            tint = Ink.TextPrimary,
                        )
                    }
                }
            },
        )
        StepRail(step)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun StepRail(current: Int) {
    val labels = listOf(
        tr(StrBooking.stepTeacher),
        tr(StrBooking.stepGroup),
        tr(StrBooking.stepDetails),
        tr(StrBooking.stepPay),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val position = index + 1
            val done = position < current
            val active = position == current
            val color = when {
                done -> Ink.Teal
                active -> Ink.Amber
                else -> Ink.Hairline
            }
            val weight by animateFloatAsState(if (active) 1.6f else 1f, tween(220), label = "step")
            Column(Modifier.weight(weight), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(color),
                )
                if (active) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        color = Ink.Amber,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The confirmation a student lands on after sending a transfer.
 *
 * It promises nothing beyond "someone will look at this": the seat is only real once the
 * owner approves, and saying otherwise here would be the one place the app could lie about
 * a state the backend owns.
 */
@Composable
internal fun BookingSubmittedPanel(
    teacherName: String,
    groupName: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Ink.TealSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            tr(StrBooking.sentTitle),
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            String.format(tr(StrBooking.sentBody), groupName, teacherName),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryAction(tr(StrBooking.backHome), onClick = onDone)
    }
}

/** A soft accent panel used for the "what you're paying for" note above the wallets. */
@Composable
internal fun BookingNote(text: String, accent: Color = Ink.Teal, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(text, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Used by the payment step when nothing at all is payable — a dead end worth explaining. */
@Composable
internal fun BookingUnavailable(message: String, onBack: () -> Unit) {
    EmptyBlock(
        message,
        tr(Tr("Please try again from the home screen.", "حاول مرة أخرى من الصفحة الرئيسية.")),
        Modifier.fillMaxSize(),
        actionLabel = tr(Str.back),
        onAction = onBack,
    )
}
