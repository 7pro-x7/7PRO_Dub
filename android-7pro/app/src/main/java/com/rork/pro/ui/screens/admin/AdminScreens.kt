package com.rork.pro.ui.screens.admin

import com.rork.pro.ui.components.coursePriceLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.i18n.StrAdminKit
import com.rork.pro.ui.i18n.levelLabel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.BuildConfig
import com.rork.pro.data.AdmobPlacement
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.AppError
import com.rork.pro.data.AppSetting
import com.rork.pro.classroom.translation.LiveTranslationKeys
import com.rork.pro.ui.i18n.StrLiveTranslation
import com.rork.pro.data.Async
import com.rork.pro.data.AuditLog
import com.rork.pro.data.Banner
import com.rork.pro.data.Coupon
import com.rork.pro.data.Course
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDropDown
import com.rork.pro.data.CountryPricing
import com.rork.pro.data.Order
import com.rork.pro.data.Payout
import com.rork.pro.data.PlacementTest
import com.rork.pro.data.Profile
import com.rork.pro.data.RefundRow
import com.rork.pro.data.Review
import com.rork.pro.data.SupportTicket
import com.rork.pro.data.TeacherProfile
import com.rork.pro.data.UpdateKeys
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.MediaSlot
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.screens.studio.IconAction
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrDubbing
import com.rork.pro.ui.i18n.StrAiPlans
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.StrStudio
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.statusLabel
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.settingTitle
import com.rork.pro.ui.i18n.settingHint
import com.rork.pro.ui.i18n.settingGroupTitle
import com.rork.pro.ui.i18n.auditActionLabel
import com.rork.pro.ui.i18n.auditTargetLabel
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.font.FontWeight

/** Generic list + action view model used by the admin screens. */
open class AdminListViewModel<T>(private val loader: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Async<T>>(Async.Loading)
    val state: StateFlow<Async<T>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    /** Pull-to-refresh: fetches again while leaving the current list on screen. */
    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching { loader() }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }

    fun clearError() { _error.value = null }
}

/**
 * The frame every admin screen shares: header, loading and error states, pull to refresh.
 *
 * Internal rather than file-private so a screen living in its own file — the payments console,
 * for one — looks and behaves exactly like the screens declared here.
 */
@Composable
internal fun <T> AdminScaffold(
    title: String,
    navController: NavHostController,
    vm: AdminListViewModel<T>,
    content: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(title, onBack = { navController.popBackStack() })

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
                            InkCard(color = Ink.CoralSoft, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.ErrorOutline, null, tint = Ink.Coral)
                                    Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.IconButton(onClick = { vm.clearError() }) {
                                        Icon(Icons.Default.Close, tr(StrAdmin.dismiss), tint = Ink.Coral)
                                    }
                                }
                            }
                        }
                    }
                    content(s.value)
                }
            }
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Canvas,
                checkedTrackColor = Ink.Amber,
                uncheckedThumbColor = Ink.TextMuted,
                uncheckedTrackColor = Ink.SurfaceHigh,
            ),
        )
    }
}

// ---------------------------------------------------------------- Teachers

class TeachersVm : AdminListViewModel<List<TeacherProfile>>({ AdminRepository.teachers() })

class MemberSearchVm : ViewModel() {
    private val _results = MutableStateFlow<List<Profile>>(emptyList())
    val results: StateFlow<List<Profile>> = _results.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _results.value = emptyList()
                return@launch
            }
            runCatching { AdminRepository.users(search = query) }
                .onSuccess { found -> _results.value = found.filter { it.role == "STUDENT" }.take(6) }
        }
    }
}

/**
 * The only door into a teaching account.
 *
 * Members sign up as learners; the owner (or an admin with teacher rights) picks one, sets the
 * share they keep, and the server promotes the account in a single audited step.
 */
@Composable
internal fun NewTeacherCard(onCreate: (String, String, Double?) -> Unit) {
    val search: MemberSearchVm = viewModel()
    val results by search.results.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Profile?>(null) }
    var headline by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }

    AdminFormCard(tr(StrAdmin.newTeacher), tr(StrAdmin.newTeacherPolicy), icon = Icons.Default.PersonAdd) { close ->
        AdminSearchField(
            query,
            {
                query = it
                picked = null
                search.search(it)
            },
            tr(StrAdmin.searchMember),
        )

        if (picked == null && query.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text(tr(StrAdmin.noMatches), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                results.forEach { member ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                picked = member
                                query = member.displayName
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(member.avatarUrl, member.displayName, 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(member.email.orEmpty(), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        picked?.let { member ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.TealSoft).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(member.avatarUrl, member.displayName, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(member.displayName, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(member.email.orEmpty(), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            InkField(headline, { headline = it }, tr(StrAdmin.headline))
            Spacer(Modifier.height(8.dp))
            InkField(
                share,
                { share = it.filter(Char::isDigit).take(3) },
                tr(StrAdmin.teacherSharePct),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryAction(tr(StrAdmin.makeTeacher)) {
                val keptByTeacher = share.toIntOrNull()?.coerceIn(0, 100)
                onCreate(member.id, headline, keptByTeacher?.let { it / 100.0 })
                query = ""; headline = ""; share = ""; picked = null
                close()
            }
        }
    }
}

// The standalone teachers list was merged into the teacher console
// (AdminTeacherDashboardScreen): one list, one place to create, configure, suspend or delete.

/**
 * One teacher's account settings: booking/test flags, headline, both profit shares and the
 * suspend / delete actions. Shown inside the teacher console once a teacher is picked.
 */
@Composable
internal fun TeacherManageCard(
    teacher: TeacherProfile,
    canManage: Boolean,
    vm: TeachersVm,
    onDelete: () -> Unit,
    startExpanded: Boolean = false,
) {
    val suspended = teacher.status == "SUSPENDED"
    var commission by remember(teacher.id, teacher.commissionRate) {
        mutableStateOf(teacher.commissionRate?.let { (it * 100).toInt().toString() } ?: "")
    }
    AdminItemCard(
        title = teacher.profile?.fullName ?: tr(StrAdmin.teacherLabel),
        subtitle = buildString {
            append(trf(StrAdmin.studentsCount, teacher.studentsCount))
            teacher.commissionRate?.let { append(" · "); append(trf(StrAdminKit.teacherShareShort, (it * 100).toInt())) }
        },
        leading = { Avatar(teacher.photoUrl ?: teacher.profile?.avatarUrl, teacher.profile?.fullName, 44.dp) },
        trailing = { StatusPill(teacher.status) },
        accent = if (suspended) Ink.Coral else null,
        startExpanded = startExpanded,
        stateKey = teacher.id,
    ) {
        var headline by remember(teacher.id, teacher.headline) { mutableStateOf(teacher.headline.orEmpty()) }
        // Loaded only once the card is opened — a long list no longer fires one request per row.
        var subRateInput by remember(teacher.id) { mutableStateOf("") }
        LaunchedEffect(teacher.id) {
            subRateInput = runCatching { AdminRepository.getTeacherSubscriptionRate(teacher.id) }
                .getOrNull()
                ?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                ?: ""
        }

        ToggleRow(tr(StrAdmin.openForNewStudents), teacher.acceptingNewStudents) { value ->
            vm.act { AdminRepository.setTeacherFlags(teacher.id, value, null, null, null) }
        }
        ToggleRow(tr(StrAdmin.canManageTests), teacher.canManageTests) { value ->
            vm.act { AdminRepository.setTeacherFlags(teacher.id, null, null, value, null) }
        }
        ToggleRow(tr(StrAdmin.canManageCourseExercises), teacher.canManageCourseExercises) { value ->
            vm.act { AdminRepository.setTeacherFlags(teacher.id, null, null, null, null, value) }
        }
        if (canManage) TeacherGrantPermissionEditor(teacher.id)

        // The line shown under the teacher's name on booking / showcase cards.
        Spacer(Modifier.height(10.dp))
        AdminFieldWithSave(
            value = headline,
            onValueChange = { headline = it },
            label = tr(StrAdmin.headline),
            keyboardType = KeyboardType.Text,
        ) {
            vm.act { AdminRepository.setTeacherHeadline(teacher.id, headline.trim()) }
        }
        Spacer(Modifier.height(8.dp))
        AdminFieldWithSave(
            value = commission,
            onValueChange = { commission = it.filter(Char::isDigit).take(3) },
            label = tr(StrAdmin.teacherSharePct),
        ) {
            val value = commission.toIntOrNull()?.coerceIn(0, 100)?.div(100.0)
            vm.act { AdminRepository.setTeacherFlags(teacher.id, null, null, null, value) }
        }
        // Subscription profit share — applied to every new earning; existing ones keep their split.
        Spacer(Modifier.height(8.dp))
        AdminFieldWithSave(
            value = subRateInput,
            onValueChange = { subRateInput = it.filter(Char::isDigit).take(3) },
            label = tr(StrAdmin.subscriptionRatePercent),
        ) {
            subRateInput.toIntOrNull()?.coerceIn(0, 100)?.toDouble()?.let { value ->
                vm.act { AdminRepository.setTeacherSubscriptionRate(teacher.id, value) }
            }
        }

        if (canManage) {
            if (suspended) {
                Spacer(Modifier.height(10.dp))
                AdminNote(tr(StrAdmin.suspendedTeacherNote), AdminTone.Danger)
            }
            AdminActions {
                if (suspended) {
                    AdminButton(tr(StrAdmin.reactivateTeacher), Modifier.weight(1f), Icons.Default.Restore, AdminTone.Success) {
                        vm.act { AdminRepository.manageTeacher(teacher.id, "REACTIVATE") }
                    }
                } else {
                    AdminButton(tr(StrAdmin.suspend), Modifier.weight(1f), Icons.Default.Block, AdminTone.Neutral) {
                        vm.act { AdminRepository.manageTeacher(teacher.id, "SUSPEND") }
                    }
                }
                AdminButton(tr(StrAdmin.deleteTeacher), Modifier.weight(1f), Icons.Outlined.Delete, AdminTone.Danger, onClick = onDelete)
            }
        }
    }
}

/** A numeric field with its own compact save button on the same line. */
@Composable
internal fun AdminFieldWithSave(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    onSave: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            InkField(value, onValueChange, label, keyboardType = keyboardType)
        }
        AdminButton(tr(StrAdmin.save), tone = AdminTone.Primary, icon = Icons.Default.Check, modifier = Modifier.padding(top = 6.dp), onClick = onSave)
    }
}

// ---------------------------------------------------------------- Content review

class ReviewVm : AdminListViewModel<List<Course>>({ AdminRepository.coursesForReview(null) })

@Composable
fun AdminReviewScreen(navController: NavHostController) {
    val vm: ReviewVm = viewModel()
    var filter by rememberSaveable { mutableStateOf("PENDING") }
    var query by rememberSaveable { mutableStateOf("") }

    AdminScaffold(tr(StrAdmin.contentReview), navController, vm) { courses ->
        val pending = courses.filter { it.status == "PENDING_REVIEW" || it.status == "APPROVED" }
        val groups = mapOf(
            "PENDING" to pending,
            "PUBLISHED" to courses.filter { it.status == "PUBLISHED" },
            "SUSPENDED" to courses.filter { it.status == "SUSPENDED" || it.status == "REJECTED" },
            "ALL" to courses,
        )
        item { AdminSearchField(query, { query = it }) }
        item {
            AdminChips(
                listOf(
                    "PENDING" to tr(StrAdminKit.pending),
                    "PUBLISHED" to tr(StrAdminKit.published),
                    "SUSPENDED" to tr(StrAdminKit.suspended),
                    "ALL" to tr(StrAdminKit.all),
                ),
                filter,
                { filter = it },
                counts = groups.mapValues { it.value.size },
            )
        }
        val shown = groups[filter].orEmpty().filter {
            query.isBlank() || it.displayTitle.contains(query.trim(), true) ||
                it.teacher?.fullName.orEmpty().contains(query.trim(), true)
        }
        if (courses.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noCourses), tr(StrAdmin.noCoursesBody)) }
        } else if (shown.isEmpty()) {
            item { AdminNoResults() }
        }
        items(shown, key = { "c-${it.id}" }) { course ->
            // Each course keeps its own note — typing in one card no longer fills every card.
            var note by remember(course.id) { mutableStateOf("") }
            val needsDecision = course.status == "PENDING_REVIEW" || course.status == "APPROVED"
            AdminItemCard(
                title = course.displayTitle,
                subtitle = "${course.teacher?.fullName ?: tr(StrAdmin.teacherLabel)} · " +
                    if (course.isFreeCourse) tr(Str.free) else coursePriceLabel(course),
                leading = { AdminBadgeIcon(Icons.AutoMirrored.Filled.MenuBook, tint = if (needsDecision) Ink.Amber else Ink.Teal) },
                trailing = { StatusPill(course.status) },
                accent = if (needsDecision) Ink.Amber else null,
                startExpanded = needsDecision,
                stateKey = course.id,
            ) {
                ToggleRow(tr(StrAdmin.featuredOnHome), course.isFeatured) { value ->
                    vm.act { AdminRepository.setCourseFeatured(course.id, value) }
                }
                if (needsDecision) {
                    Spacer(Modifier.height(6.dp))
                    InkField(note, { note = it }, tr(StrAdmin.reviewNote))
                }
                AdminActions {
                    when (course.status) {
                        "PENDING_REVIEW", "APPROVED" -> {
                            AdminButton(tr(StrAdmin.publish), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success) {
                                vm.act { AdminRepository.reviewContent("COURSE", course.id, "PUBLISH", note) }
                            }
                            AdminButton(tr(StrAdmin.reject), Modifier.weight(1f), Icons.Default.Close, AdminTone.Danger) {
                                vm.act { AdminRepository.reviewContent("COURSE", course.id, "REJECT", note) }
                            }
                        }
                        "PUBLISHED" -> AdminButton(tr(StrAdmin.suspend), Modifier.weight(1f), Icons.Default.Block) {
                            vm.act { AdminRepository.reviewContent("COURSE", course.id, "SUSPEND", note) }
                        }
                        "SUSPENDED", "REJECTED" -> AdminButton(tr(StrAdmin.republish), Modifier.weight(1f), Icons.Default.Restore, AdminTone.Success) {
                            vm.act { AdminRepository.reviewContent("COURSE", course.id, "PUBLISH", note) }
                        }
                        else -> Unit
                    }
                    AdminDeleteIcon(tr(StrAdmin.deleteCourse), tr(StrAdmin.deleteCourseConfirm)) {
                        vm.act { AdminRepository.deleteCourse(course.id) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pricing

class PricingVm : AdminListViewModel<List<CountryPricing>>({ AdminRepository.countryPricing() })

@Composable
fun AdminPricingScreen(navController: NavHostController) {
    val vm: PricingVm = viewModel()
    var code by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf("1") }
    var roundTo by remember { mutableStateOf("5") }
    var discount by remember { mutableStateOf("0") }

    AdminScaffold(tr(StrAdmin.countryPricing), navController, vm) { rows ->
        item {
            AdminFormCard(tr(StrAdmin.addUpdateCountry), tr(StrAdmin.pricingPolicy), icon = Icons.Default.Public) { close ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkField(code, { code = it.uppercase().filter(Char::isLetter).take(2) }, tr(StrAdmin.country))
                    }
                    Box(Modifier.weight(1f)) {
                        InkField(currency, { currency = it.uppercase().filter(Char::isLetter).take(3) }, tr(StrAdmin.currency))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkField(multiplier, { multiplier = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.rateField), keyboardType = KeyboardType.Decimal)
                    }
                    Box(Modifier.weight(1f)) {
                        InkField(roundTo, { roundTo = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.roundTo), keyboardType = KeyboardType.Decimal)
                    }
                }
                Spacer(Modifier.height(8.dp))
                InkField(discount, { discount = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.discPct), keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(14.dp))
                PrimaryAction(tr(StrAdmin.saveCountryPricing), enabled = code.length == 2 && currency.length == 3) {
                    vm.act {
                        AdminRepository.saveCountryPricing(
                            CountryPricing(
                                countryCode = code,
                                currency = currency,
                                fxMultiplier = multiplier.toDoubleOrNull() ?: 1.0,
                                roundTo = roundTo.toDoubleOrNull() ?: 1.0,
                                discountPercent = discount.toDoubleOrNull() ?: 0.0,
                            ),
                        )
                    }
                    code = ""; currency = ""; multiplier = "1"; roundTo = "5"; discount = "0"
                    close()
                }
            }
        }

        if (rows.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noCountryRules), tr(StrAdmin.noCountryRulesBody)) }
        } else {
            item { AdminSectionLabel(tr(StrAdmin.countryPricing), rows.size) }
        }
        items(rows, key = { it.countryCode }) { row ->
            AdminItemCard(
                title = "${row.countryCode} · ${row.currency}",
                leading = { AdminBadgeIcon(text = row.countryCode, tint = Ink.Sky) },
                trailing = {
                    AdminDeleteIcon(tr(StrAdmin.remove), tr(StrAdminKit.deletePricingConfirm)) {
                        vm.act { AdminRepository.deleteCountryPricing(row.countryCode) }
                    }
                },
                expandable = false,
            ) {
                AdminMetaRow {
                    AdminMeta("${tr(StrAdmin.multiplier)} × ${row.fxMultiplier.clean()}")
                    AdminMeta("${tr(StrAdmin.roundedTo)} ${row.roundTo.clean()}")
                    AdminMeta("${tr(StrAdmin.discount)} ${row.discountPercent.clean()}%", if (row.discountPercent > 0) Ink.Teal else Ink.TextSecondary)
                }
            }
        }
    }
}

/** 5.0 → "5", 1.25 → "1.25". */
internal fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

// ---------------------------------------------------------------- Coupons

data class CouponsData(val coupons: List<Coupon>, val courses: List<Course>)

class CouponsVm : AdminListViewModel<CouponsData>({
    CouponsData(
        AdminRepository.coupons(),
        // Only used to name / pick courses: a failure here must not hide the coupons themselves.
        runCatching { AdminRepository.couponCourses() }.getOrDefault(emptyList()),
    )
})

/** Text a search should be able to hit for the courses a coupon is linked to. */
private fun linkedCourseTitles(coupon: Coupon, byId: Map<String, Course>): List<String?> =
    coupon.courseIds.flatMap { id ->
        byId[id]?.let { listOf(it.title, it.titleAr, it.titleEn) } ?: emptyList()
    }

private fun couponCourseLabel(coupon: Coupon, byId: Map<String, Course>): String? = when (coupon.courseIds.size) {
    0 -> null
    1 -> byId[coupon.courseIds.first()]?.displayTitle ?: tr(StrAdminX.couponUnknownCourse)
    else -> trf(StrAdminX.couponManyCourses, coupon.courseIds.size)
}

/**
 * Picks the course a coupon is tied to, with its own search box. "All courses" (empty selection)
 * keeps the coupon open to every course.
 */
@Composable
private fun CouponCourseLink(courses: List<Course>, selected: List<String>, onSelect: (List<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val byId = remember(courses) { courses.associateBy { it.id } }
    val label = when (selected.size) {
        0 -> tr(StrAdminX.couponAllCourses)
        1 -> byId[selected.first()]?.displayTitle ?: tr(StrAdminX.couponUnknownCourse)
        else -> trf(StrAdminX.couponManyCourses, selected.size)
    }

    Text(tr(StrAdminX.couponCourseTitle), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface)
            .border(1.dp, if (selected.isEmpty()) Ink.Hairline else Ink.Amber, RoundedCornerShape(16.dp))
            .clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = if (selected.isEmpty()) Ink.TextMuted else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted)
    }
    if (open) {
        Spacer(Modifier.height(8.dp))
        AdminSearchField(query, { query = it }, tr(StrAdminX.couponSearchCourse))
        Spacer(Modifier.height(6.dp))
        val matches = remember(courses, query) {
            courses
                .filter { SearchMatch.matches(query, it.title, it.titleAr, it.titleEn, it.teacher?.fullName) }
                .sortedByDescending { SearchMatch.rank(query, it.displayTitle) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.SurfaceHigh)
                .verticalScroll(rememberScrollState()),
        ) {
            if (query.isBlank()) {
                CourseChoiceRow(tr(StrAdminX.couponAllCourses), null, selected.isEmpty()) {
                    onSelect(emptyList()); open = false
                }
            }
            if (matches.isEmpty()) {
                Text(
                    tr(StrAdminX.couponNoCourseMatch),
                    Modifier.padding(14.dp),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            matches.forEach { course ->
                CourseChoiceRow(course.displayTitle, course.teacher?.fullName, course.id in selected) {
                    onSelect(listOf(course.id)); open = false; query = ""
                }
            }
        }
    }
}

@Composable
private fun CourseChoiceRow(title: String, sub: String?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (!sub.isNullOrBlank()) {
                Text(sub, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        if (chosen) Icon(Icons.Default.Check, null, tint = Ink.Teal)
    }
}

/** Which products a coupon can be used on: courses only, courses and the AI Tutor, or the AI Tutor only. */
@Composable
private fun CouponScopeChips(scope: String, onSelect: (String) -> Unit) {
    Text(tr(StrAiPlans.scopeTitle), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    AdminChips(
        listOf("NONE" to tr(StrAiPlans.scopeNone), "ALSO" to tr(StrAiPlans.scopeAlso), "ONLY" to tr(StrAiPlans.scopeOnly)),
        scope,
        onSelect,
    )
}

private fun scopeLabel(scope: String): String? = when (scope) {
    "ALSO" -> tr(StrAiPlans.scopeAlso)
    "ONLY" -> tr(StrAiPlans.scopeOnly)
    else -> null
}

@Composable
private fun CouponKindChips(kind: String, onSelect: (String) -> Unit) {
    AdminChips(
        listOf("PERCENT" to codeLabel("PERCENT"), "FIXED" to codeLabel("FIXED")),
        kind,
        onSelect,
    )
}

@Composable
fun AdminCouponsScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: CouponsVm = viewModel()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("PERCENT") }
    var amount by remember { mutableStateOf("") }
    var maxUses by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("NONE") }
    var courseIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }

    var editingId by remember { mutableStateOf<String?>(null) }

    AdminScaffold(tr(StrAdmin.coupons), navController, vm) { data ->
        val coupons = data.coupons
        val courseById = data.courses.associateBy { it.id }
        item {
            AdminFormCard(tr(StrAdmin.newCoupon), icon = Icons.Default.Discount) { close ->
                InkField(code, { code = it.uppercase().replace(" ", "") }, tr(StrAdmin.code))
                Spacer(Modifier.height(10.dp))
                CouponKindChips(kind) { kind = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.amount), keyboardType = KeyboardType.Decimal)
                    }
                    Box(Modifier.weight(1f)) {
                        InkField(maxUses, { maxUses = it.filter(Char::isDigit) }, tr(StrAdmin.maxUses), keyboardType = KeyboardType.Number)
                    }
                }
                Spacer(Modifier.height(10.dp))
                CouponCourseLink(data.courses, courseIds) { courseIds = it }
                Spacer(Modifier.height(10.dp))
                if (courseIds.isEmpty()) {
                    CouponScopeChips(scope) { scope = it }
                } else {
                    Text(tr(StrAdminX.couponLinkedNoTutor), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryAction(tr(StrAdmin.createCoupon), enabled = code.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0) {
                    vm.act {
                        AdminRepository.saveCoupon(
                            code, kind, amount.toDoubleOrNull() ?: 0.0, maxUses.toIntOrNull(), null,
                            aiTutorScope = scope, courseIds = courseIds,
                        )
                    }
                    code = ""; amount = ""; maxUses = ""; scope = "NONE"; courseIds = emptyList()
                    close()
                }
            }
        }

        if (coupons.isNotEmpty()) {
            item {
                AdminSummaryStrip(
                    listOf(
                        AdminStat(coupons.count { it.isActive }.toString(), tr(StrAdminX.activeCoupons), Ink.Teal),
                        AdminStat(coupons.sumOf { it.usedCount }.toString(), tr(StrAdminX.totalUses)),
                    ),
                )
            }
            item { AdminSearchField(query, { query = it }, tr(StrAdminX.couponSearchCoupons)) }
            item {
                AdminChips(
                    listOf("ALL" to tr(StrAdminKit.all), "ON" to tr(StrAdminKit.active), "OFF" to tr(StrAdminKit.inactive)),
                    filter,
                    { filter = it },
                    counts = mapOf("ALL" to coupons.size, "ON" to coupons.count { it.isActive }, "OFF" to coupons.count { !it.isActive }),
                )
            }
        }
        val shown = coupons
            .filter { c ->
                (filter == "ALL" || (filter == "ON") == c.isActive) &&
                    SearchMatch.matches(
                        query,
                        c.code,
                        *linkedCourseTitles(c, courseById).toTypedArray(),
                        c.amount.clean(),
                        if (c.kind == "PERCENT") "${c.amount.clean()}%" else null,
                    )
            }
            .let { list -> if (query.isBlank()) list else list.sortedByDescending { SearchMatch.rank(query, it.code) } }
        if (coupons.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noCoupons), tr(StrAdmin.noCouponsBody), icon = Icons.Default.Discount) }
        } else if (shown.isEmpty()) {
            item { AdminNoResults() }
        }
        items(shown, key = { it.id ?: it.code }) { coupon ->
            val value = if (coupon.kind == "PERCENT") "${coupon.amount.clean()}%" else coupon.amount.clean()
            AdminItemCard(
                title = coupon.code,
                subtitle = (coupon.maxUses?.let { trf(StrAdminKit.usedOf, coupon.usedCount.toString(), it.toString()) }
                    ?: trf(StrAdminKit.usedCount, coupon.usedCount.toString())) +
                    (scopeLabel(coupon.aiTutorScope)?.let { " · $it" } ?: "") +
                    (couponCourseLabel(coupon, courseById)?.let { " · $it" } ?: ""),
                leading = { AdminBadgeIcon(Icons.Default.Discount, tint = if (coupon.isActive) Ink.Teal else Ink.Neutral) },
                trailing = { Pill(value, background = if (coupon.isActive) Ink.TealSoft else Ink.SurfaceHigh, foreground = if (coupon.isActive) Ink.Teal else Ink.TextMuted) },
                stateKey = coupon.id,
            ) {
                coupon.maxUses?.takeIf { it > 0 }?.let { max ->
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (coupon.usedCount.toFloat() / max).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (coupon.usedCount >= max) Ink.Coral else Ink.Teal,
                        trackColor = Ink.SurfaceHigh,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                KeyValueRow(tr(StrAdminX.couponCourseTitle), couponCourseLabel(coupon, courseById) ?: tr(StrAdminX.couponAllCourses))
                coupon.expiresAt?.let { KeyValueRow(tr(StrAdmin.expires), formatDate(it)) }
                ToggleRow(tr(StrAdmin.activeLabel), coupon.isActive) { on ->
                    coupon.id?.let { id -> vm.act { AdminRepository.setCouponActive(id, on) } }
                }

                if (coupon.id != null && sessionState.can("coupons.manage")) {
                    if (editingId == coupon.id) {
                        CouponEditor(
                            coupon = coupon,
                            courses = data.courses,
                            onCancel = { editingId = null },
                            onSave = { newCode, newKind, newAmount, newMax, newScope, newCourseIds ->
                                vm.act {
                                    AdminRepository.updateCoupon(coupon.id, newCode, newKind, newAmount, newMax, newScope, newCourseIds)
                                }
                                editingId = null
                            },
                        )
                    } else {
                        AdminActions {
                            AdminButton(tr(StrAdmin.editCoupon), Modifier.weight(1f), Icons.Default.Edit, AdminTone.Primary) {
                                editingId = coupon.id
                            }
                            AdminDeleteIcon("${tr(StrAdmin.deleteCoupon)} · ${coupon.code}", tr(StrAdmin.deleteCouponConfirm)) {
                                vm.act { AdminRepository.deleteCoupon(coupon.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Inline edit form for one coupon; the code itself stays editable so typos can be fixed. */
@Composable
private fun CouponEditor(
    coupon: Coupon,
    courses: List<Course>,
    onCancel: () -> Unit,
    onSave: (String, String, Double, Int?, String, List<String>) -> Unit,
) {
    var code by remember(coupon.id) { mutableStateOf(coupon.code) }
    var kind by remember(coupon.id) { mutableStateOf(coupon.kind) }
    var amount by remember(coupon.id) { mutableStateOf(coupon.amount.clean()) }
    var maxUses by remember(coupon.id) { mutableStateOf(coupon.maxUses?.toString().orEmpty()) }
    var scope by remember(coupon.id) { mutableStateOf(coupon.aiTutorScope) }
    var courseIds by remember(coupon.id) { mutableStateOf(coupon.courseIds) }

    Spacer(Modifier.height(12.dp))
    Divider()
    Spacer(Modifier.height(12.dp))
    InkField(code, { code = it.uppercase().replace(" ", "") }, tr(StrAdmin.code))
    Spacer(Modifier.height(10.dp))
    CouponKindChips(kind) { kind = it }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            InkField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.amount), keyboardType = KeyboardType.Decimal)
        }
        Box(Modifier.weight(1f)) {
            InkField(maxUses, { maxUses = it.filter(Char::isDigit) }, tr(StrAdmin.maxUses), keyboardType = KeyboardType.Number)
        }
    }
    Spacer(Modifier.height(10.dp))
    CouponCourseLink(courses, courseIds) { courseIds = it }
    Spacer(Modifier.height(10.dp))
    if (courseIds.isEmpty()) {
        CouponScopeChips(scope) { scope = it }
    } else {
        Text(tr(StrAdminX.couponLinkedNoTutor), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    AdminActions {
        AdminButton(
            tr(StrAdmin.saveCoupon),
            Modifier.weight(1f),
            Icons.Default.Check,
            AdminTone.Primary,
            enabled = code.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
        ) { onSave(code.trim(), kind, amount.toDoubleOrNull() ?: 0.0, maxUses.toIntOrNull(), scope, courseIds) }
        AdminButton(tr(StrAdmin.cancel), Modifier.weight(1f), onClick = onCancel)
    }
}

// ---------------------------------------------------------------- Finance

data class FinanceData(val orders: List<Order>, val refunds: List<RefundRow>)

class FinanceVm : AdminListViewModel<FinanceData>({
    FinanceData(AdminRepository.orders(), AdminRepository.refunds())
})

@Composable
fun AdminFinanceScreen(navController: NavHostController) {
    val vm: FinanceVm = viewModel()
    var refundFor by remember { mutableStateOf<String?>(null) }
    var refundAmount by remember { mutableStateOf("") }
    var refundReason by remember { mutableStateOf("") }
    var refundKind by remember { mutableStateOf("REFUND") }
    var tab by rememberSaveable { mutableStateOf("ORDERS") }
    var filter by rememberSaveable { mutableStateOf("ALL") }

    AdminScaffold(tr(StrAdmin.ordersRefunds), navController, vm) { data ->
        // Totals are shown in the currency most orders use; mixing currencies in one sum
        // would produce a number that means nothing.
        val mainCurrency = data.orders.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: "EGP"
        val paidInMain = data.orders.filter { it.currency == mainCurrency && it.status != "PENDING" && it.status != "FAILED" && it.status != "CANCELLED" }
        item {
            AdminSummaryStrip(
                listOf(
                    AdminStat(formatMoney(paidInMain.sumOf { it.totalAmount }, mainCurrency), tr(StrAdminX.paidRevenue)),
                    AdminStat(formatMoney(paidInMain.sumOf { it.platformAmount }, mainCurrency), tr(StrAdminX.platformNet), Ink.Teal),
                    AdminStat(data.orders.count { it.status == "PAID" }.toString(), tr(StrAdminX.ordersPaid)),
                    AdminStat(
                        formatMoney(data.orders.filter { it.currency == mainCurrency }.sumOf { it.refundedAmount }, mainCurrency),
                        tr(StrAdminX.refundedTotal),
                        Ink.Coral,
                    ),
                ),
            )
        }
        item {
            AdminTabs(
                listOf("ORDERS" to tr(StrAdmin.orders), "REFUNDS" to tr(StrAdminKit.refundHistoryShort)),
                tab,
                { tab = it },
            )
        }

        if (tab == "ORDERS") {
            val isRefunded = { o: Order -> o.status == "REFUNDED" || o.status == "PARTIALLY_REFUNDED" || o.status == "CHARGEBACK" }
            item {
                AdminChips(
                    listOf("ALL" to tr(StrAdminKit.all), "PAID" to tr(StrAdminKit.paid), "REFUNDED" to tr(StrAdminKit.refunded), "OTHER" to tr(StrAdminKit.pending)),
                    filter,
                    { filter = it },
                    counts = mapOf(
                        "ALL" to data.orders.size,
                        "PAID" to data.orders.count { it.status == "PAID" },
                        "REFUNDED" to data.orders.count(isRefunded),
                        "OTHER" to data.orders.count { it.status != "PAID" && !isRefunded(it) },
                    ),
                )
            }
            val shown = data.orders.filter {
                when (filter) {
                    "PAID" -> it.status == "PAID"
                    "REFUNDED" -> isRefunded(it)
                    "OTHER" -> it.status != "PAID" && !isRefunded(it)
                    else -> true
                }
            }
            if (shown.isEmpty()) {
                item { AdminEmpty(tr(StrAdmin.noOrders), tr(StrAdmin.noOrdersBody)) }
            }
            items(shown, key = { it.id }) { order ->
                AdminItemCard(
                    title = formatMoney(order.totalAmount, order.currency),
                    subtitle = "${codeLabel(order.itemType)} · ${formatDate(order.paidAt ?: order.createdAt)}",
                    leading = { AdminBadgeIcon(Icons.AutoMirrored.Filled.ReceiptLong, tint = if (order.status == "PAID") Ink.Teal else Ink.Sky) },
                    trailing = { StatusPill(order.status) },
                    stateKey = order.id,
                ) {
                    KeyValueRow(tr(StrAdmin.country), order.countryCode)
                    KeyValueRow(tr(StrAdmin.teacherShare), formatMoney(order.teacherAmount, order.currency))
                    KeyValueRow(tr(StrAdmin.platformShare), formatMoney(order.platformAmount, order.currency), valueColor = Ink.Teal)
                    KeyValueRow(tr(StrAdmin.commissionUsed), "${(order.commissionRate * 100).toInt()}%")
                    if (order.refundedAmount > 0) {
                        KeyValueRow(tr(StrAdmin.refunded), formatMoney(order.refundedAmount, order.currency), valueColor = Ink.Coral)
                    }

                    if (order.status == "PAID" || order.status == "PARTIALLY_REFUNDED") {
                        if (refundFor == order.id) {
                            Spacer(Modifier.height(10.dp))
                            AdminChips(
                                listOf("REFUND" to codeLabel("REFUND"), "CHARGEBACK" to codeLabel("CHARGEBACK")),
                                refundKind,
                                { refundKind = it },
                                tint = Ink.Coral,
                            )
                            Spacer(Modifier.height(8.dp))
                            InkField(refundAmount, { refundAmount = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.amount), keyboardType = KeyboardType.Decimal)
                            Spacer(Modifier.height(8.dp))
                            InkField(refundReason, { refundReason = it }, tr(StrAdmin.reason))
                            AdminActions {
                                AdminButton(
                                    trf(StrAdmin.processKind, codeLabel(refundKind)),
                                    Modifier.weight(1f),
                                    Icons.Default.Check,
                                    AdminTone.Danger,
                                    enabled = (refundAmount.toDoubleOrNull() ?: 0.0) > 0,
                                ) {
                                    vm.act {
                                        AdminRepository.refund(order.id, refundAmount.toDoubleOrNull() ?: 0.0, refundKind, refundReason)
                                    }
                                    refundFor = null; refundAmount = ""; refundReason = ""
                                }
                                AdminButton(tr(StrAdmin.cancel), Modifier.weight(1f)) { refundFor = null }
                            }
                        } else {
                            AdminActions {
                                AdminButton(tr(StrAdmin.refundChargeback), Modifier.weight(1f), Icons.AutoMirrored.Filled.Undo, AdminTone.Danger) {
                                    refundFor = order.id
                                    refundAmount = (order.totalAmount - order.refundedAmount).clean()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (data.refunds.isEmpty()) {
                item { AdminEmpty(tr(StrAdminX.noRefunds), tr(StrAdminX.noRefundsBody), icon = Icons.AutoMirrored.Filled.Undo) }
            }
            items(data.refunds, key = { it.id }) { refund ->
                AdminItemCard(
                    title = formatMoney(refund.amount, refund.currency),
                    subtitle = listOfNotNull(formatDate(refund.createdAt), refund.reason?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    leading = { AdminBadgeIcon(Icons.AutoMirrored.Filled.Undo, tint = Ink.Coral) },
                    trailing = { Pill(codeLabel(refund.kind), background = Ink.CoralSoft, foreground = Ink.Coral) },
                    expandable = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Payouts

class PayoutsVm(teacherId: String? = null) : AdminListViewModel<List<Payout>>({ AdminRepository.payouts(teacherId = teacherId) })

@Composable
fun AdminPayoutsScreen(navController: NavHostController, teacherId: String? = null) {
    val vm: PayoutsVm = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PayoutsVm(teacherId) as T
        },
    )
    var filter by rememberSaveable { mutableStateOf("PENDING") }

    // A teacher is chosen upstream on the teacher dashboard, so this list is
    // already scoped to their withdrawals — no separate teacher picker here.
    AdminScaffold(tr(StrAdmin.withdrawals), navController, vm) { payouts ->
        val groups = mapOf(
            "PENDING" to payouts.filter { it.status == "PENDING" },
            "APPROVED" to payouts.filter { it.status == "APPROVED" },
            "DONE" to payouts.filter { it.status != "PENDING" && it.status != "APPROVED" },
            "ALL" to payouts,
        )
        item {
            val cur = payouts.firstOrNull()?.currency ?: "EGP"
            AdminSummaryStrip(
                listOf(
                    AdminStat(formatMoney(groups["PENDING"].orEmpty().sumOf { it.amount }, cur), tr(StrAdminX.awaitingApproval), Ink.Amber),
                    AdminStat(formatMoney(groups["APPROVED"].orEmpty().sumOf { it.amount }, cur), tr(StrAdminX.awaitingPayment), Ink.Sky),
                ),
            )
        }
        item {
            AdminChips(
                listOf(
                    "PENDING" to tr(StrAdminKit.pending),
                    "APPROVED" to tr(StrAdminKit.approved),
                    "DONE" to tr(StrAdminKit.resolved),
                    "ALL" to tr(StrAdminKit.all),
                ),
                filter,
                { filter = it },
                counts = groups.mapValues { it.value.size },
            )
        }
        val shown = groups[filter].orEmpty()
        if (shown.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noWithdrawals), tr(StrAdmin.noWithdrawalsBody)) }
        }
        items(shown, key = { it.id }) { payout ->
            // Per-payout inputs: a note typed for one request no longer leaks into the next.
            var reference by remember(payout.id) { mutableStateOf("") }
            var note by remember(payout.id) { mutableStateOf("") }
            val actionable = payout.status == "PENDING" || payout.status == "APPROVED"
            AdminItemCard(
                title = formatMoney(payout.amount, payout.currency),
                subtitle = "${payout.teacher?.fullName ?: tr(StrAdmin.teacherLabel)} · ${formatDate(payout.requestedAt)}",
                leading = { Avatar(payout.teacher?.avatarUrl, payout.teacher?.fullName, 44.dp) },
                trailing = { StatusPill(payout.status) },
                accent = if (actionable) Ink.Amber else null,
                startExpanded = actionable,
                stateKey = payout.id,
            ) {
                payout.method?.let { KeyValueRow(tr(StrAdmin.method), codeLabel(it)) }
                payout.destination?.let { KeyValueRow(tr(StrAdmin.destination), it) }
                when (payout.status) {
                    "PENDING" -> {
                        Spacer(Modifier.height(6.dp))
                        InkField(note, { note = it }, tr(StrAdmin.decisionNote))
                        AdminActions {
                            AdminButton(tr(StrAdmin.approve), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success) {
                                vm.act { AdminRepository.reviewPayout(payout.id, "APPROVE", "", note) }
                            }
                            AdminButton(tr(StrAdmin.reject), Modifier.weight(1f), Icons.Default.Close, AdminTone.Danger) {
                                vm.act { AdminRepository.reviewPayout(payout.id, "REJECT", "", note) }
                            }
                        }
                    }
                    "APPROVED" -> {
                        Spacer(Modifier.height(6.dp))
                        InkField(reference, { reference = it }, tr(StrAdmin.transferReference))
                        AdminActions {
                            AdminButton(tr(StrAdmin.markAsPaid), Modifier.weight(1f), Icons.Default.Payments, AdminTone.Success, enabled = reference.isNotBlank()) {
                                vm.act { AdminRepository.reviewPayout(payout.id, "PAID", reference, "") }
                            }
                        }
                    }
                    else -> payout.reference?.let { KeyValueRow(tr(StrAdmin.reference), it) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Users & admins

/**
 * Backs the admin users list, on top of the shared load/refresh/error plumbing in
 * [AdminListViewModel]. A blank query is the browsable newest-100 page (unchanged); search text
 * re-queries the server (see [AdminRepository.users]) instead of filtering that fixed page in
 * memory — debounced in [search] so each keystroke doesn't fire its own request.
 */
class UsersVm(
    @Volatile private var pendingQuery: String? = null,
) : AdminListViewModel<List<Profile>>(loader = { AdminRepository.users(search = pendingQuery) }) {
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(300)
            pendingQuery = query.trim().takeIf { it.isNotBlank() }
            load(quiet = true)
        }
    }
}

@Composable
fun AdminUsersScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: UsersVm = viewModel()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf("ALL") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // (user, editingPassword): which account the credentials dialog is open for.
    var credentialEdit by remember { mutableStateOf<Pair<Profile, Boolean>?>(null) }

    LaunchedEffect(query) { vm.search(query) }

    credentialEdit?.let { (target, editingPassword) ->
        AccountCredentialDialog(
            userName = target.displayName,
            currentEmail = target.email.orEmpty(),
            editingPassword = editingPassword,
            onDismiss = { credentialEdit = null },
            onSave = { value ->
                credentialEdit = null
                vm.act {
                    if (editingPassword) AdminRepository.setUserPassword(target.id, value)
                    else AdminRepository.setUserEmail(target.id, value)
                }
            },
        )
    }

    AdminScaffold(tr(StrAdmin.users), navController, vm) { users ->
        if (sessionState.isOwner) {
            item { AdminNote(tr(StrAdmin.adminsPolicy)) }
        }
        item { AdminSearchField(query, { query = it }) }
        item {
            val roles = listOf("ALL", "ADMIN", "TEACHER", "STUDENT")
            AdminChips(
                roles.map { it to if (it == "ALL") tr(StrAdminKit.all) else roleLabel(it) },
                roleFilter,
                { roleFilter = it },
                counts = roles.associateWith { r -> if (r == "ALL") users.size else users.count { it.role == r } },
            )
        }
        // The server already matched `query` against name and email (AdminRepository.users);
        // the phone check stays local since phone search isn't part of that server query.
        val q = query.trim()
        val shown = users
            .filter { roleFilter == "ALL" || it.role == roleFilter }
            .filter { q.isBlank() || it.displayName.contains(q, true) || it.email.orEmpty().contains(q, true) || it.phone.orEmpty().contains(q) }
            .sortedWith(compareBy({ listOf("OWNER", "ADMIN", "TEACHER", "STUDENT").indexOf(it.role).let { i -> if (i < 0) 9 else i } }, { it.displayName.lowercase() }))
        if (users.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noUsers), tr(StrAdmin.noUsersBody)) }
        } else if (shown.isEmpty()) {
            item { AdminNoResults() }
        }
        items(shown, key = { it.id }) { user ->
            val isSelf = user.id == sessionState.profile?.id
            val manageable = user.role != "OWNER" && !isSelf
            AdminItemCard(
                title = user.displayName,
                subtitle = listOfNotNull(user.email, user.currentLevel?.let { levelLabel(it) }).joinToString(" · "),
                leading = { Avatar(user.avatarUrl, user.displayName, 44.dp) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Pill(roleLabel(user.role), background = roleTint(user.role).copy(alpha = 0.14f), foreground = roleTint(user.role))
                        if (user.status != "ACTIVE") StatusPill(user.status)
                    }
                },
                accent = if (user.status == "SUSPENDED") Ink.Coral else null,
                expandable = manageable,
                stateKey = user.id,
                showBody = manageable,
            ) {
                AdminActions {
                    AdminButton(
                        if (user.status == "ACTIVE") tr(StrAdmin.suspend) else tr(StrAdmin.reactivate),
                        Modifier.weight(1f),
                        if (user.status == "ACTIVE") Icons.Default.Block else Icons.Default.Restore,
                        if (user.status == "ACTIVE") AdminTone.Neutral else AdminTone.Success,
                    ) {
                        vm.act {
                            AdminRepository.setUserStatus(
                                user.id,
                                if (user.status == "ACTIVE") "SUSPENDED" else "ACTIVE",
                                tr(StrAdmin.changedFromConsole),
                            )
                        }
                    }
                    if (sessionState.isOwner && user.role == "ADMIN") {
                        AdminButton(tr(StrAdmin.permissions), Modifier.weight(1f), Icons.Default.Key, AdminTone.Info) {
                            expanded = if (expanded == user.id) null else user.id
                            if (expanded == user.id) {
                                scope.launch { runCatching { AdminRepository.permissionsOf(user.id) }.onSuccess { granted = it }.onFailure { expanded = null } }
                            }
                        }
                    }
                }

                // Account recovery is the owner's alone: it is equivalent to signing in as that user.
                if (sessionState.isOwner) {
                    AdminActions {
                        AdminButton(tr(StrAdmin.changeEmail), Modifier.weight(1f), Icons.Default.Email, AdminTone.Info) {
                            credentialEdit = user to false
                        }
                        AdminButton(tr(StrAdmin.changePassword), Modifier.weight(1f), Icons.Default.Lock, AdminTone.Info) {
                            credentialEdit = user to true
                        }
                    }
                }

                // Appointing and standing down admins is the owner's alone.
                if (sessionState.isOwner) {
                    Spacer(Modifier.height(8.dp))
                    if (user.role == "ADMIN") {
                        AdminButton(tr(StrAdmin.removeAdmin), Modifier.fillMaxWidth(), Icons.Default.PersonRemove, AdminTone.Danger) {
                            vm.act { AdminRepository.setUserRole(user.id, "STUDENT", tr(StrAdmin.changedFromConsole)) }
                            expanded = null
                        }
                    } else {
                        AdminButton(tr(StrAdmin.makeAdmin), Modifier.fillMaxWidth(), Icons.Default.AdminPanelSettings, AdminTone.Primary) {
                            vm.act { AdminRepository.setUserRole(user.id, "ADMIN", tr(StrAdmin.changedFromConsole)) }
                            expanded = user.id
                            scope.launch { runCatching { AdminRepository.permissionsOf(user.id) }.onSuccess { granted = it }.onFailure { expanded = null } }
                        }
                    }
                }

                if (expanded == user.id && sessionState.isOwner) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.SurfaceHigh).padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(StrAdmin.adminAccess), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Pill(trf(StrAdmin.permissionsCount, granted.size))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(tr(StrAdmin.permissionsPolicy), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminButton(tr(StrAdmin.selectAll), Modifier.weight(1f), Icons.Default.DoneAll) {
                                granted = AdminRepository.ALL_PERMISSIONS.map { it.first }.toSet()
                            }
                            AdminButton(tr(StrAdmin.clearAll), Modifier.weight(1f), Icons.Default.Close) { granted = emptySet() }
                        }
                        Spacer(Modifier.height(6.dp))
                        AdminRepository.ALL_PERMISSIONS.forEach { (key, label) ->
                            ToggleRow(tr(label), key in granted) { value ->
                                granted = if (value) granted + key else granted - key
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        PrimaryAction(tr(StrAdmin.savePermissions)) {
                            vm.act { AdminRepository.setPermissions(user.id, granted) }
                            expanded = null
                        }
                    }
                }
            }
        }
    }
}

/** Owner-only dialog: set a new password, or a new sign-in email, for another account. */
@Composable
private fun AccountCredentialDialog(
    userName: String,
    currentEmail: String,
    editingPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(if (editingPassword) "" else currentEmail) }
    val valid = if (editingPassword) value.length >= 8 else Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = { Text(tr(if (editingPassword) StrAdmin.changePassword else StrAdmin.changeEmail) + " — " + userName, color = Ink.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(if (editingPassword) StrAdmin.changePasswordHint else StrAdmin.changeEmailHint),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                InkField(
                    value = value,
                    onValueChange = { value = it },
                    label = tr(if (editingPassword) StrAdmin.newPassword else StrAdmin.newEmail),
                    keyboardType = if (editingPassword) KeyboardType.Password else KeyboardType.Email,
                    visualTransformation = if (editingPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim().takeIf { !editingPassword } ?: value) }, enabled = valid) {
                Text(tr(StrAdmin.save), color = Ink.Amber)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(StrAdmin.cancel), color = Ink.TextSecondary) } },
    )
}

// ---------------------------------------------------------------- CMS

data class CmsData(
    val banners: List<Banner>,
    val categories: List<com.rork.pro.data.Category>,
    val shortcuts: List<com.rork.pro.data.HomeShortcut>,
)

class CmsVm : AdminListViewModel<CmsData>({
    CmsData(AdminRepository.banners(), AdminRepository.categories(), AdminRepository.shortcuts())
})

private data class BannerDraft(
    val id: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val image: String = "",
    val cta: String = "",
    val target: String = "",
    val active: Boolean = true,
)

private data class ShortcutDraft(
    val id: String? = null,
    val title: String = "",
    val icon: String = "⭐",
    val target: String = "",
    val active: Boolean = true,
    val sortOrder: Int = 0,
)

@Composable
private fun CmsFormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    InkCard(borderColor = Ink.Amber.copy(alpha = 0.45f)) {
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun CmsFormActions(saveLabel: String, canSave: Boolean, onSave: () -> Unit, onCancel: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    PrimaryAction(saveLabel, modifier = Modifier.fillMaxWidth(), enabled = canSave) { onSave() }
    Spacer(Modifier.height(8.dp))
    com.rork.pro.ui.components.SecondaryAction(tr(StrAdminX.cmsCancel), modifier = Modifier.fillMaxWidth()) { onCancel() }
}

@Composable
private fun CmsEditIcon(onClick: () -> Unit) {
    Icon(
        Icons.Default.Edit,
        contentDescription = tr(StrAdminX.cmsEdit),
        tint = Ink.TextSecondary,
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick).padding(8.dp),
    )
}

/**
 * Home content: banners, categories and shortcuts. Each tab opens with one clear "add" button, the
 * form only appears when wanted, and every existing item can be edited with the same form. Where an
 * item leads, its icon and its picture are all picked, never typed.
 */
@Composable
fun AdminCmsScreen(navController: NavHostController) {
    val vm: CmsVm = viewModel()
    var tab by rememberSaveable { mutableStateOf("BANNERS") }
    var banner by remember { mutableStateOf<BannerDraft?>(null) }
    var shortcut by remember { mutableStateOf<ShortcutDraft?>(null) }
    var categoryForm by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var categoryIcon by remember { mutableStateOf("") }
    // Published courses only: a banner or shortcut that points at a draft would lead nowhere.
    var courses by remember { mutableStateOf<List<com.rork.pro.data.GrantCourse>>(emptyList()) }
    LaunchedEffect(Unit) {
        courses = runCatching {
            com.rork.pro.data.CourseStudioRepository.courses(com.rork.pro.data.CourseScope.ALL)
                .filter { it.status == "PUBLISHED" }
                .map { com.rork.pro.data.GrantCourse(it.id, it.title, it.titleAr, it.titleEn) }
        }.getOrDefault(emptyList())
    }

    AdminScaffold(tr(StrAdmin.homeContent), navController, vm) { data ->
        item {
            AdminTabs(
                listOf(
                    "BANNERS" to "${tr(StrAdminKit.banners)} · ${data.banners.size}",
                    "CATEGORIES" to "${tr(StrAdmin.categories)} · ${data.categories.size}",
                    "SHORTCUTS" to "${tr(StrAdmin.shortcuts)} · ${data.shortcuts.size}",
                ),
                tab,
                { tab = it },
            )
        }

        if (tab == "BANNERS") {
            val draft = banner
            if (draft == null) {
                item {
                    AdminButton(tr(StrAdminX.cmsAddBanner), Modifier.fillMaxWidth(), Icons.Default.Add, AdminTone.Primary) {
                        banner = BannerDraft()
                    }
                }
            } else {
                item {
                    CmsFormCard(tr(if (draft.id == null) StrAdmin.newHomeBanner else StrAdminX.cmsEditBanner)) {
                        BannerImagePicker(draft.image) { banner = draft.copy(image = it) }
                        Spacer(Modifier.height(12.dp))
                        InkField(draft.title, { banner = draft.copy(title = it) }, tr(StrAdmin.headline))
                        Spacer(Modifier.height(8.dp))
                        InkField(draft.subtitle, { banner = draft.copy(subtitle = it) }, tr(StrAdmin.subtitle))
                        Spacer(Modifier.height(12.dp))
                        DestinationField(draft.target, { banner = draft.copy(target = it) }, allowNone = true, courses = courses)
                        if (draft.target.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            InkField(draft.cta, { banner = draft.copy(cta = it) }, tr(StrAdmin.buttonLabel))
                        }
                        CmsFormActions(
                            saveLabel = tr(if (draft.id == null) StrAdmin.publishBanner else StrAdminX.cmsSave),
                            canSave = draft.title.isNotBlank() && destinationValid(draft.target, allowNone = true),
                            onSave = {
                                vm.act {
                                    AdminRepository.saveBanner(
                                        draft.id, draft.title.trim(), draft.subtitle.trim(), draft.image,
                                        draft.cta.trim(), draft.target, draft.active,
                                    )
                                }
                                banner = null
                            },
                            onCancel = { banner = null },
                        )
                    }
                }
            }
            if (data.banners.isEmpty() && draft == null) {
                item { AdminEmpty(tr(StrAdminX.noBanners), tr(StrAdminX.noBannersBody), icon = Icons.Default.ViewCarousel) }
            }
            items(data.banners, key = { it.id }) { item ->
                InkCard(
                    contentPadding = PaddingValues(0.dp),
                    borderColor = if (item.isActive) null else Ink.Neutral.copy(alpha = 0.35f),
                ) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        CoverImage(item.imageUrl, Modifier.fillMaxWidth().height(140.dp))
                    }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.displayTitle, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                item.subtitle?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                Text(
                                    trf(StrAdminX.cmsLeadsTo, destinationLabel(item.ctaTarget, courses)),
                                    color = Ink.Amber,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            CmsEditIcon {
                                banner = BannerDraft(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = item.subtitle.orEmpty(),
                                    image = item.imageUrl.orEmpty(),
                                    cta = item.ctaLabel.orEmpty(),
                                    target = item.ctaTarget.orEmpty(),
                                    active = item.isActive,
                                )
                            }
                            AdminDeleteIcon(tr(StrAdmin.delete), tr(StrAdminKit.deleteBannerConfirm)) {
                                vm.act { AdminRepository.deleteBanner(item.id) }
                            }
                        }
                        ToggleRow(tr(StrAdmin.activeLabel), item.isActive) { value ->
                            vm.act {
                                AdminRepository.saveBanner(
                                    item.id,
                                    item.title,
                                    item.subtitle.orEmpty(),
                                    item.imageUrl.orEmpty(),
                                    item.ctaLabel.orEmpty(),
                                    item.ctaTarget.orEmpty(),
                                    value,
                                )
                            }
                        }
                    }
                }
            }
        } else if (tab == "CATEGORIES") {
            if (!categoryForm) {
                item {
                    AdminButton(tr(StrAdminX.cmsAddCategory), Modifier.fillMaxWidth(), Icons.Default.Add, AdminTone.Primary) {
                        categoryForm = true
                    }
                }
            } else {
                item {
                    CmsFormCard(tr(StrAdmin.newCategory)) {
                        InkField(categoryName, { categoryName = it }, tr(StrAdmin.categoryName))
                        Spacer(Modifier.height(12.dp))
                        IconPicker(categoryIcon) { categoryIcon = it }
                        CmsFormActions(
                            saveLabel = tr(StrAdmin.addCategory),
                            canSave = categoryName.isNotBlank(),
                            onSave = {
                                vm.act {
                                    AdminRepository.saveCategory(categoryName, categoryName, data.categories.size, categoryIcon.ifBlank { null })
                                }
                                categoryName = ""; categoryIcon = ""; categoryForm = false
                            },
                            onCancel = { categoryName = ""; categoryIcon = ""; categoryForm = false },
                        )
                    }
                }
            }
            if (data.categories.isEmpty() && !categoryForm) {
                item { AdminEmpty(tr(StrAdminX.noCategories), tr(StrAdminX.noCategoriesBody), icon = Icons.Default.Category) }
            }
            items(data.categories, key = { it.id }) { category ->
                AdminItemCard(
                    title = category.displayName,
                    leading = {
                        val icon = category.icon?.takeIf { it.isNotBlank() }
                        if (icon != null) AdminBadgeIcon(text = icon, tint = Ink.Coral) else AdminBadgeIcon(Icons.Default.Category, tint = Ink.Coral)
                    },
                    trailing = {
                        // Only published categories (and their courses) are offered to students.
                        androidx.compose.material3.Switch(
                            checked = category.isActive,
                            onCheckedChange = { value -> vm.act { AdminRepository.setCategoryActive(category.id, value) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink.Canvas,
                                checkedTrackColor = Ink.Amber,
                                uncheckedThumbColor = Ink.TextMuted,
                                uncheckedTrackColor = Ink.SurfaceHigh,
                            ),
                        )
                        AdminDeleteIcon(tr(StrAdmin.delete), tr(StrAdminKit.deleteCategoryConfirm)) {
                            vm.act { AdminRepository.deleteCategory(category.id) }
                        }
                    },
                    subtitle = if (category.isActive) tr(StrAdmin.published) else tr(StrAdminKit.hidden),
                    expandable = false,
                )
            }
        } else {
            val draft = shortcut
            if (draft == null) {
                item {
                    AdminButton(tr(StrAdminX.cmsAddShortcut), Modifier.fillMaxWidth(), Icons.Default.Add, AdminTone.Primary) {
                        shortcut = ShortcutDraft(sortOrder = data.shortcuts.size)
                    }
                }
                item { AdminNote(tr(StrAdmin.shortcutsHelp)) }
            } else {
                item {
                    CmsFormCard(tr(if (draft.id == null) StrAdmin.newShortcut else StrAdminX.cmsEditShortcut)) {
                        InkField(draft.title, { shortcut = draft.copy(title = it) }, tr(StrAdmin.shortcutTitle))
                        Spacer(Modifier.height(12.dp))
                        IconPicker(draft.icon) { shortcut = draft.copy(icon = it) }
                        Spacer(Modifier.height(12.dp))
                        DestinationField(draft.target, { shortcut = draft.copy(target = it) }, allowNone = false, courses = courses)
                        CmsFormActions(
                            saveLabel = tr(if (draft.id == null) StrAdmin.addShortcut else StrAdminX.cmsSave),
                            canSave = draft.title.isNotBlank() && draft.target.isNotBlank() && destinationValid(draft.target, allowNone = false),
                            onSave = {
                                vm.act {
                                    AdminRepository.saveShortcut(
                                        draft.id, draft.title.trim(), draft.icon, draft.target, draft.active, draft.sortOrder,
                                    )
                                }
                                shortcut = null
                            },
                            onCancel = { shortcut = null },
                        )
                    }
                }
            }
            if (data.shortcuts.isEmpty() && draft == null) {
                item { AdminEmpty(tr(StrAdminX.noShortcuts), tr(StrAdminX.noShortcutsBody), icon = Icons.Default.Apps) }
            }
            items(data.shortcuts, key = { it.id }) { item ->
                AdminItemCard(
                    title = item.displayTitle,
                    subtitle = trf(StrAdminX.cmsLeadsTo, destinationLabel(item.target, courses)),
                    leading = { AdminBadgeIcon(text = item.icon, tint = Ink.Teal) },
                    trailing = {
                        androidx.compose.material3.Switch(
                            checked = item.isActive,
                            onCheckedChange = { value ->
                                vm.act {
                                    AdminRepository.saveShortcut(item.id, item.title, item.icon, item.target, value, item.sortOrder)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink.Canvas,
                                checkedTrackColor = Ink.Amber,
                                uncheckedThumbColor = Ink.TextMuted,
                                uncheckedTrackColor = Ink.SurfaceHigh,
                            ),
                        )
                        CmsEditIcon {
                            shortcut = ShortcutDraft(item.id, item.title, item.icon, item.target, item.isActive, item.sortOrder)
                        }
                        AdminDeleteIcon(tr(StrAdmin.delete), tr(StrAdminKit.deleteShortcutConfirm)) {
                            vm.act { AdminRepository.deleteShortcut(item.id) }
                        }
                    },
                    expandable = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Ads

/** Everything the ads screen needs: the placements plus the global switches that gate them. */
data class AdsAdminData(
    val placements: List<AdmobPlacement>,
    /** Master switch — when false, ad_config refuses every screen regardless of placements. */
    val globallyEnabled: Boolean,
    /** When true, ads are suppressed on paid courses no matter what a placement says. */
    val freeContentOnly: Boolean,
)

class AdsVm : AdminListViewModel<AdsAdminData>({
    val placements = AdminRepository.adPlacements()
    val settings = runCatching { AdminRepository.settings() }.getOrDefault(emptyList())
    fun flag(key: String, default: Boolean): Boolean =
        settings.firstOrNull { it.key == key }
            ?.value?.toString()?.trim('"')
            ?.let { it.equals("true", true) } ?: default
    AdsAdminData(
        placements = placements,
        globallyEnabled = flag("ads.enabled", true),
        freeContentOnly = flag("ads.free_content_only", true),
    )
})

/**
 * Every screen that carries an ad slot, as the exact key it passes to AdBanner / AdInterstitial /
 * AdNative. A placement on any other key is still saved (the owner can add one by hand), but
 * nothing will request it until a screen asks for that key.
 */
private val APP_AD_SCREENS = listOf(
    "HOME",
    "COURSE_LIST",
    "COURSE_DETAIL",
    "FREE_LESSON",
    "TESTS",
    "EXERCISES",
    "RESULT",
    "BOOKING",
    "BOOKING_GROUPS",
    "ORDERS",
    "CERTIFICATES",
    "NOTIFICATIONS",
    "SUPPORT",
    "PROFILE",
)

/**
 * What the app actually asks for: (format, slot) per screen. A placement outside this list is
 * saved but never requested, so it can never show — the screen flags those instead of letting
 * them sit there looking like they work. Slot "" is a screen's default slot.
 */
private fun requestedFormats(screen: String): Map<String, Set<String>> = buildMap {
    put("BANNER", if (screen == "HOME") setOf("", "BOTTOM") else setOf(""))
    if (screen == "HOME" || screen == "COURSE_LIST") put("INTERSTITIAL", setOf(""))
    if (screen == "COURSE_LIST") put("NATIVE", setOf(""))
}

private fun isRequested(p: AdmobPlacement): Boolean {
    if (p.screen !in APP_AD_SCREENS) return true // a screen the owner named by hand: not ours to judge
    val slots = requestedFormats(p.screen)[p.format] ?: return false
    return p.section.orEmpty().uppercase() in slots
}

@Composable
fun AdminAdsScreen(navController: NavHostController) {
    val vm: AdsVm = viewModel()
    var openScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var cleaning by remember { mutableStateOf(false) }

    AdminScaffold(tr(StrAdmin.admob), navController, vm) { data ->
        val placements = data.placements

        // The two switches that override every placement below them.
        item {
            InkCard {
                Text(tr(StrAdmin.adsGlobalTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                ToggleRow(tr(StrAdmin.adsGloballyEnabled), data.globallyEnabled) { value ->
                    vm.act { AdminRepository.saveSetting("ads.enabled", value.toString()) }
                }
                Spacer(Modifier.height(4.dp))
                ToggleRow(tr(StrAdmin.adsFreeOnly), data.freeContentOnly) { value ->
                    vm.act { AdminRepository.saveSetting("ads.free_content_only", value.toString()) }
                }
                Spacer(Modifier.height(6.dp))
                Text(tr(StrAdminX.adsPolicyShort), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Text(tr(StrAdminX.adsScreensTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // One row per screen. Tapping a row opens just that screen's ads, with the add button
        // inside it — so the screen is never picked from a list of chips again.
        items(APP_AD_SCREENS, key = { "screen-$it" }) { key ->
            val mine = placements.filter { it.screen == key }
            AdScreenRow(
                screen = key,
                title = codeLabel(key),
                placements = mine,
                open = openScreen == key,
                onToggle = { openScreen = if (openScreen == key) null else key },
                allPlacements = placements,
                vm = vm,
            )
        }

        // Screens the owner added by hand (any key the app is not known to request).
        val knownScreens = APP_AD_SCREENS.toSet()
        val otherScreens = placements.map { it.screen }.filter { it !in knownScreens }.distinct()
        items(otherScreens, key = { "other-$it" }) { key ->
            AdScreenRow(
                screen = key,
                title = key,
                placements = placements.filter { it.screen == key },
                open = openScreen == key,
                onToggle = { openScreen = if (openScreen == key) null else key },
                allPlacements = placements,
                vm = vm,
            )
        }

        item { AdCustomScreenCard(placements, vm) }

        // Placements that exist but that no screen ever asks for.
        val unused = placements.filter { !isRequested(it) }
        if (unused.isNotEmpty()) {
            item {
                AdminButton(
                    trf(StrAdminX.adsCleanup, unused.size),
                    Modifier.fillMaxWidth(),
                    Icons.Outlined.Delete,
                    AdminTone.Neutral,
                ) { cleaning = true }
            }
        }
        if (cleaning) {
            item {
                ConfirmDialog(
                    title = trf(StrAdminX.adsCleanup, unused.size),
                    body = tr(StrAdminX.adsCleanupBody),
                    confirmLabel = tr(StrAdmin.delete),
                    destructive = true,
                    onDismiss = { cleaning = false },
                    onConfirm = {
                        cleaning = false
                        unused.forEach { u -> u.id?.let { id -> vm.act { AdminRepository.deleteAdPlacement(id) } } }
                    },
                )
            }
        }
    }
}

/** A screen: what it carries at a glance, and — opened — its ads and the button to add one. */
@Composable
private fun AdScreenRow(
    screen: String,
    title: String,
    placements: List<AdmobPlacement>,
    open: Boolean,
    onToggle: () -> Unit,
    allPlacements: List<AdmobPlacement>,
    vm: AdsVm,
) {
    val live = placements.filter { it.isEnabled }
    var adding by remember(screen) { mutableStateOf(false) }
    InkCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminBadgeIcon(Icons.Default.Tv, tint = if (live.isNotEmpty()) Ink.Teal else Ink.Neutral, size = 36)
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        live.isNotEmpty() -> live.map { it.format }.distinct().joinToString("، ") { codeLabel(it) }
                        placements.isNotEmpty() -> tr(StrAdminX.adsAllOff)
                        else -> tr(StrAdminX.adsNone)
                    },
                    color = if (live.isNotEmpty()) Ink.Teal else Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                tint = Ink.TextMuted,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                placements.forEach { p ->
                    PlacementCard(
                        placement = p,
                        unused = !isRequested(p),
                        onSave = { unitId, intervalSec, maxImp, free, enabled ->
                            vm.act {
                                AdminRepository.saveAdPlacement(
                                    p.id, p.screen, p.section.orEmpty(), p.format, unitId, enabled, intervalSec, maxImp, free,
                                )
                            }
                        },
                        onDelete = { p.id?.let { id -> vm.act { AdminRepository.deleteAdPlacement(id) } } },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (adding) {
                    AdPlacementForm(
                        screen = screen,
                        keyEditable = false,
                        allPlacements = allPlacements,
                        onDone = { adding = false },
                        vm = vm,
                    )
                } else {
                    AdminButton(tr(StrAdminX.adsAddHere), Modifier.fillMaxWidth(), Icons.Default.Add, AdminTone.Primary) { adding = true }
                }
            }
        }
    }
}

/** The rare case: an ad on a screen key that is not in the list above. Collapsed until asked for. */
@Composable
private fun AdCustomScreenCard(placements: List<AdmobPlacement>, vm: AdsVm) {
    var open by remember { mutableStateOf(false) }
    if (!open) {
        Text(
            tr(StrAdminX.adsCustomScreen),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(vertical = 8.dp, horizontal = 4.dp),
        )
    } else {
        InkCard {
            AdPlacementForm(screen = "", keyEditable = true, allPlacements = placements, onDone = { open = false }, vm = vm)
        }
    }
}

/**
 * Adds an ad to a screen: the format, the AdMob unit id, and that is all. Slot name, interval,
 * the per-session cap and "free content only" sit behind "Advanced" with sensible defaults.
 * Only formats the screen really asks for are offered, so nothing can be added that never shows.
 */
@Composable
private fun AdPlacementForm(
    screen: String,
    keyEditable: Boolean,
    allPlacements: List<AdmobPlacement>,
    onDone: () -> Unit,
    vm: AdsVm,
) {
    var customKey by remember { mutableStateOf("") }
    val key = if (keyEditable) customKey.trim().uppercase() else screen
    val formats = remember(key, keyEditable) {
        if (keyEditable || key !in APP_AD_SCREENS) listOf("BANNER", "NATIVE", "INTERSTITIAL") else requestedFormats(key).keys.toList()
    }
    var format by remember(key) { mutableStateOf(formats.firstOrNull() ?: "BANNER") }
    var unit by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("60") }
    var maxImpressions by remember { mutableStateOf("10") }
    var freeOnly by remember { mutableStateOf(false) }

    if (keyEditable) {
        InkField(customKey, { customKey = it }, tr(StrAdmin.customScreen))
        Spacer(Modifier.height(4.dp))
        Text(tr(StrAdmin.customScreenHelp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }
    if (formats.size > 1) {
        AdminChips(formats.map { it to codeLabel(it) }, format, { format = it }, tint = Ink.Sky)
        Spacer(Modifier.height(10.dp))
    }
    InkField(unit, { unit = it }, tr(StrAdmin.adUnitId))
    Spacer(Modifier.height(8.dp))
    Text(
        (if (advanced) "▾  " else "◂  ") + tr(StrAdminX.adsAdvanced),
        color = Ink.Amber,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { advanced = !advanced }.padding(vertical = 6.dp, horizontal = 2.dp),
    )
    if (advanced) {
        Spacer(Modifier.height(4.dp))
        InkField(section, { section = it }, tr(StrAdmin.sectionLabel))
        Spacer(Modifier.height(4.dp))
        Text(tr(StrAdmin.sectionHelp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                InkField(interval, { interval = it.filter(Char::isDigit) }, tr(StrAdmin.displayInterval), keyboardType = KeyboardType.Number)
            }
            Box(Modifier.weight(1f)) {
                InkField(maxImpressions, { maxImpressions = it.filter(Char::isDigit) }, tr(StrAdmin.maxImpressions), keyboardType = KeyboardType.Number)
            }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow(tr(StrAdmin.placementFreeOnly), freeOnly) { freeOnly = it }
    }

    // A placement is identified by screen + format + slot: adding the same one again updates it.
    val normalizedSection = section.trim().uppercase()
    val existing = allPlacements.firstOrNull {
        it.screen == key && it.format == format && it.section.orEmpty().uppercase() == normalizedSection
    }
    if (existing != null) {
        Spacer(Modifier.height(6.dp))
        Text(tr(StrAdmin.screenAlreadyConfigured), color = Ink.Amber, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
    PrimaryAction(tr(StrAdmin.addPlacement), modifier = Modifier.fillMaxWidth(), enabled = unit.isNotBlank() && key.isNotBlank()) {
        vm.act {
            // New placements start enabled, or they save invisibly and nothing shows.
            AdminRepository.saveAdPlacement(
                existing?.id, key, normalizedSection, format, unit.trim(), true,
                interval.toIntOrNull() ?: 60, maxImpressions.toIntOrNull() ?: 10, freeOnly,
            )
        }
        onDone()
    }
    Spacer(Modifier.height(6.dp))
    com.rork.pro.ui.components.SecondaryAction(tr(StrAdminX.cmsCancel), modifier = Modifier.fillMaxWidth()) { onDone() }
}

/**
 * One saved ad: what it is and a switch — nothing else until Edit is tapped. Edits are staged and
 * only written on Save, so a half-typed unit id is never sent. An ad no screen ever requests is
 * marked, since it looks configured but can never show.
 */
@Composable
private fun PlacementCard(
    placement: AdmobPlacement,
    unused: Boolean,
    onSave: (unitId: String, interval: Int, maxImpressions: Int, freeOnly: Boolean, enabled: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(placement.id) { mutableStateOf(false) }
    var unitId by remember(placement.id) { mutableStateOf(placement.adUnitId) }
    var interval by remember(placement.id) { mutableStateOf(placement.displayIntervalSeconds.toString()) }
    var maxImp by remember(placement.id) { mutableStateOf(placement.maxImpressions.toString()) }
    var freeOnly by remember(placement.id) { mutableStateOf(placement.freeContentOnly) }
    var showDeleteConfirm by remember(placement.id) { mutableStateOf(false) }

    val dirty = unitId != placement.adUnitId ||
        interval != placement.displayIntervalSeconds.toString() ||
        maxImp != placement.maxImpressions.toString() ||
        freeOnly != placement.freeContentOnly

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    codeLabel(placement.format) + (placement.section?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(placement.adUnitId, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (unused) {
                    Text(tr(StrAdminX.adsUnused), color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = placement.isEnabled,
                onCheckedChange = { value ->
                    onSave(placement.adUnitId, placement.displayIntervalSeconds, placement.maxImpressions, placement.freeContentOnly, value)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.Canvas,
                    checkedTrackColor = Ink.Amber,
                    uncheckedThumbColor = Ink.TextMuted,
                    uncheckedTrackColor = Ink.Surface,
                ),
            )
            androidx.compose.material3.IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.Close else Icons.Default.Edit, tr(if (expanded) StrAdmin.closeEdit else StrAdmin.editPlacement), tint = Ink.Amber)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            InkField(unitId, { unitId = it }, tr(StrAdmin.adUnitId))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    InkField(interval, { interval = it.filter(Char::isDigit) }, tr(StrAdmin.displayInterval), keyboardType = KeyboardType.Number)
                }
                Box(Modifier.weight(1f)) {
                    InkField(maxImp, { maxImp = it.filter(Char::isDigit) }, tr(StrAdmin.maxImpressions), keyboardType = KeyboardType.Number)
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(tr(StrAdmin.placementFreeOnly), freeOnly) { freeOnly = it }

            Spacer(Modifier.height(12.dp))
            AdminActions {
                AdminButton(tr(StrAdmin.save), Modifier.weight(1f), Icons.Default.Check, AdminTone.Primary, enabled = dirty && unitId.isNotBlank()) {
                    onSave(
                        unitId.trim(),
                        interval.toIntOrNull() ?: placement.displayIntervalSeconds,
                        maxImp.toIntOrNull() ?: placement.maxImpressions,
                        freeOnly,
                        placement.isEnabled,
                    )
                    expanded = false
                }
                AdminButton(tr(StrAdmin.delete), Modifier.weight(1f), Icons.Outlined.Delete, AdminTone.Danger) { showDeleteConfirm = true }
            }
        }

        if (showDeleteConfirm) {
            ConfirmDialog(
                title = tr(StrAdmin.delete),
                body = tr(StrAdmin.deletePlacementConfirm),
                confirmLabel = tr(StrAdmin.delete),
                destructive = true,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete()
                },
            )
        }
    }
}

// ---------------------------------------------------------------- Settings

class SettingsVm : AdminListViewModel<List<AppSetting>>({ AdminRepository.settings() })

@Composable
fun AdminSettingsScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: SettingsVm = viewModel()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    AdminScaffold(tr(StrAdmin.platformSettings), navController, vm) { settings ->
        // Forcing an app update stays with the owner alone; the other two cards follow the same
        // permissions the server checks (settings.manage / finance.manage).
        if (sessionState.isOwner) {
            item { ForceUpdateCard(settings) { key, value -> vm.act { AdminRepository.saveSetting(key, value) } } }
        }
        if (sessionState.can("settings.manage")) {
            item {
                LiveTranslationCard(settings) { enabled, url ->
                    vm.act { AdminRepository.saveLiveTranslation(enabled, url) }
                }
            }
        }
        if (sessionState.can("finance.manage")) {
            item { DubbingSettingsCard() }
        }
        item { AdminNote(tr(StrAdmin.settingsPolicy)) }
        item { AdminSearchField(query, { query = it }) }

        val q = query.trim()
        // App-update keys have their own card above (owner only), and the maintenance secret is a
        // credential, not a preference — neither belongs in the general list.
        val hiddenKeys = setOf(
            UpdateKeys.FORCE, UpdateKeys.MIN_BUILD, UpdateKeys.MESSAGE, UpdateKeys.URL, "maintenance.secret",
            // Owner-only card above; never editable from the general list.
            LiveTranslationKeys.ENABLED, LiveTranslationKeys.SERVER_URL,
        )
        val grouped = settings
            .filter { it.key !in hiddenKeys }
            .filter {
                q.isBlank() || it.key.contains(q, true) || settingTitle(it.key).contains(q, true) ||
                    it.description.orEmpty().contains(q, true)
            }
            .groupBy { it.key.substringBefore('.', "") }
            .toSortedMap(compareBy { settingGroupTitle(it) })
        if (grouped.isEmpty()) {
            item { AdminNoResults() }
        }
        grouped.forEach { (group, rows) ->
            item(key = "g-$group") {
                AdminSectionLabel(if (group.isBlank()) tr(StrAdminKit.general) else settingGroupTitle(group), rows.size)
            }
            items(rows, key = { it.key }) { setting -> SettingRow(setting) { json -> vm.act { AdminRepository.saveSetting(setting.key, json) } } }
        }
    }
}

/**
 * One setting, edited with the control its value actually needs: a switch for true/false, a
 * number pad for numbers, a text box for the rest. The raw JSON quoting is handled here, so no
 * one has to type `"` or remember that `false` must be lowercase.
 */
@Composable
private fun SettingRow(setting: AppSetting, onSave: (String) -> Unit) {
    val raw = setting.value.toString().trim('"')
    val isBool = raw.equals("true", true) || raw.equals("false", true)
    val isNumber = !isBool && raw.toDoubleOrNull() != null && setting.value.toString().firstOrNull() != '"'
    var value by remember(setting.key, raw) { mutableStateOf(raw) }
    val dirty = value != raw

    InkCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    settingTitle(setting.key),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                // The Arabic hint when one exists; the stored (English) description otherwise.
                val hint = settingHint(setting.key) ?: setting.description?.takeIf { it.isNotBlank() }
                if (hint != null) {
                    Text(hint, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isBool) {
                Switch(
                    checked = raw.equals("true", true),
                    onCheckedChange = { onSave(it.toString()) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.Canvas,
                        checkedTrackColor = Ink.Amber,
                        uncheckedThumbColor = Ink.TextMuted,
                        uncheckedTrackColor = Ink.SurfaceHigh,
                    ),
                )
            }
        }
        if (!isBool) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    InkField(
                        value,
                        { value = if (isNumber) it.filter { c -> c.isDigit() || c == '.' || c == '-' } else it },
                        tr(StrAdmin.valueLabel),
                        keyboardType = if (isNumber) KeyboardType.Decimal else KeyboardType.Text,
                    )
                }
                AdminButton(tr(StrAdmin.save), Modifier.padding(top = 6.dp), Icons.Default.Check, AdminTone.Primary, enabled = dirty) {
                    val json = when {
                        isNumber && value.toDoubleOrNull() != null -> value
                        value.equals("true", true) || value.equals("false", true) -> value.lowercase()
                        else -> "\"${value.replace("\"", "'")}\""
                    }
                    onSave(json)
                }
            }
        }
    }
}

/**
 * The owner's switch for forcing everyone onto a newer build.
 *
 * The switch alone does nothing until a minimum build number is set, so it is saved together
 * with the number, the message learners read, and where they go to get the update.
 */
@Composable
private fun ForceUpdateCard(settings: List<AppSetting>, onSave: (String, String) -> Unit) {
    fun read(key: String): String =
        settings.firstOrNull { it.key == key }?.value?.toString()?.trim('"').orEmpty()

    val storedOn = read(UpdateKeys.FORCE) == "true"
    var forced by remember(storedOn) { mutableStateOf(storedOn) }
    var minBuild by remember(settings) {
        mutableStateOf(read(UpdateKeys.MIN_BUILD).takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_CODE.toString())
    }
    var message by remember(settings) { mutableStateOf(read(UpdateKeys.MESSAGE)) }
    var link by remember(settings) { mutableStateOf(read(UpdateKeys.URL)) }

    InkCard(borderColor = if (forced) Ink.Amber.copy(alpha = 0.45f) else Ink.Hairline) {
        Text(tr(StrAdmin.appUpdates), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(tr(StrAdmin.forceUpdatePolicy), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        ToggleRow(tr(StrAdmin.forceUpdate), forced) { forced = it }
        Spacer(Modifier.height(6.dp))
        InkField(
            minBuild,
            { minBuild = it.filter(Char::isDigit) },
            tr(StrAdmin.minimumBuild),
            keyboardType = KeyboardType.Number,
        )
        Text(
            trf(StrAdmin.thisBuild, BuildConfig.VERSION_CODE.toString()),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        InkField(message, { message = it }, tr(StrAdmin.updateMessageField), singleLine = false, minLines = 2)
        Spacer(Modifier.height(8.dp))
        InkField(link, { link = it }, tr(StrAdmin.updateLinkField))
        Spacer(Modifier.height(14.dp))
        PrimaryAction(tr(StrAdmin.saveUpdateRule)) {
            onSave(UpdateKeys.FORCE, forced.toString())
            onSave(UpdateKeys.MIN_BUILD, (minBuild.toIntOrNull() ?: BuildConfig.VERSION_CODE).toString())
            onSave(UpdateKeys.MESSAGE, "\"${message.trim().replace("\"", "'")}\"")
            onSave(UpdateKeys.URL, "\"${link.trim()}\"")
        }
    }
}

/**
 * The owner's switch for Live Voice Translation inside meetings, saved together with the address
 * of the self-hosted translation server (the switch is meaningless without it).
 */
@Composable
private fun LiveTranslationCard(settings: List<AppSetting>, onSave: (Boolean, String) -> Unit) {
    fun read(key: String): String =
        settings.firstOrNull { it.key == key }?.value?.toString()?.trim('"').orEmpty()

    val storedOn = read(LiveTranslationKeys.ENABLED) == "true"
    val storedUrl = read(LiveTranslationKeys.SERVER_URL)
    var enabled by remember(storedOn) { mutableStateOf(storedOn) }
    var url by remember(storedUrl) { mutableStateOf(storedUrl) }
    val urlMissing = enabled && url.isBlank()

    InkCard(borderColor = if (storedOn) Ink.Amber.copy(alpha = 0.45f) else Ink.Hairline) {
        Text(tr(StrLiveTranslation.ownerCardTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(tr(StrLiveTranslation.ownerCardBody), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        ToggleRow(tr(StrLiveTranslation.ownerEnable), enabled) { enabled = it }
        Spacer(Modifier.height(6.dp))
        InkField(url, { url = it.trim() }, tr(StrLiveTranslation.ownerServerUrl), keyboardType = KeyboardType.Uri)
        if (urlMissing) {
            Text(
                tr(StrLiveTranslation.ownerUrlRequired),
                color = Ink.Coral,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryAction(tr(StrLiveTranslation.ownerSave), enabled = !urlMissing && (enabled != storedOn || url != storedUrl)) {
            onSave(enabled, url)
        }
    }
}

/**
 * Owner's controls for the dubbing feature: price, currency, free trial minutes, the video
 * length cap, and the dubbing-server URL the app needs to actually run a job (analogous to
 * [LiveTranslationCard]'s self-hosted server field, but backed by `dubbing_settings` — its own
 * table with real column types and RLS, not the generic app_settings key/value store).
 */
private fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun DubbingSettingsCard() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loaded by remember { mutableStateOf<com.rork.pro.dubbing.DubbingStatus?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { AdminRepository.dubbingSettings() }
            .onSuccess { loaded = it }
            .onFailure { error = it.message }
    }

    val current = loaded ?: run {
        Text(error ?: tr(Str.loading))
        return
    }

    var enabled by remember(current) { mutableStateOf(current.isEnabled) }
    var price by remember(current) { mutableStateOf(plain(current.pricePerMinute)) }
    var currency by remember(current) { mutableStateOf(current.currency) }
    var minMinutes by remember(current) { mutableStateOf(plain(current.minBillableMinutes)) }
    var freeMinutes by remember(current) { mutableStateOf(plain(current.freeTrialMinutesTotal)) }
    var maxMinutes by remember(current) { mutableStateOf(current.maxSourceMinutes.toString()) }
    var serverUrl by remember(current) { mutableStateOf(current.dubServerUrl) }

    val urlMissing = enabled && serverUrl.isBlank()
    val valid = !urlMissing && price.toDoubleOrNull() != null && currency.trim().length == 3 &&
        minMinutes.toDoubleOrNull() != null && freeMinutes.toDoubleOrNull() != null && (maxMinutes.toIntOrNull() ?: 0) > 0

    InkCard(borderColor = if (current.isEnabled) Ink.Amber.copy(alpha = 0.45f) else Ink.Hairline) {
        Text(tr(StrDubbing.settingsTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(tr(StrDubbing.settingsHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        ToggleRow(tr(StrDubbing.enabled), enabled) { enabled = it }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { InkField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrDubbing.pricePerMinute), keyboardType = KeyboardType.Decimal) }
            Box(Modifier.weight(1f)) { InkField(currency, { currency = it.uppercase().filter(Char::isLetter).take(3) }, tr(StrDubbing.currency)) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { InkField(minMinutes, { minMinutes = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrDubbing.minBillable), keyboardType = KeyboardType.Decimal) }
            Box(Modifier.weight(1f)) { InkField(maxMinutes, { maxMinutes = it.filter(Char::isDigit) }, tr(StrDubbing.maxVideoMinutes), keyboardType = KeyboardType.Number) }
        }
        Spacer(Modifier.height(6.dp))
        InkField(freeMinutes, { freeMinutes = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrDubbing.freeMinutesPerUser), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(6.dp))
        InkField(serverUrl, { serverUrl = it.trim() }, tr(StrDubbing.serverUrl), keyboardType = KeyboardType.Uri)
        if (urlMissing) {
            Text(tr(StrDubbing.serverUrlRequired), color = Ink.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        if (error != null) {
            Text(error!!, color = Ink.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryAction(if (saving) tr(StrDubbing.saving) else tr(StrDubbing.save), enabled = valid && !saving) {
            saving = true
            error = null
            scope.launch {
                runCatching {
                    AdminRepository.saveDubbingSettings(
                        isEnabled = enabled,
                        pricePerMinute = price.toDouble(),
                        currency = currency,
                        minBillableMinutes = minMinutes.toDouble(),
                        freeTrialMinutesTotal = freeMinutes.toDouble(),
                        maxSourceMinutes = maxMinutes.toIntOrNull() ?: 60,
                        dubServerUrl = serverUrl,
                    )
                    AdminRepository.dubbingSettings()
                }
                    .onSuccess { loaded = it; saving = false }
                    .onFailure { error = it.message; saving = false }
            }
        }
    }
}

// ---------------------------------------------------------------- Placement tests

data class TestsData(val tests: List<PlacementTest>, val counts: Map<String, Int>)

class TestsVm : AdminListViewModel<TestsData>({
    val tests = AdminRepository.tests()
    TestsData(tests, tests.associate { it.id to AdminRepository.questionCount(it.id) })
}) {
    /** Swaps two adjacent tests and persists the new order — same shape as course lesson reordering. */
    fun moveTest(tests: List<PlacementTest>, from: Int, to: Int) {
        if (to !in tests.indices) return
        val reordered = tests.toMutableList().apply { add(to, removeAt(from)) }
        act { AdminRepository.reorderTests(reordered) }
    }
}

@Composable
fun AdminTestsScreen(navController: NavHostController) {
    val vm: TestsVm = viewModel()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var questionCount by remember { mutableStateOf("20") }
    var minutes by remember { mutableStateOf("20") }

    var questionFor by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf("") }
    var options by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("") }
    var acceptedText by remember { mutableStateOf("") }
    var skill by remember { mutableStateOf("GRAMMAR") }
    var difficulty by remember { mutableStateOf("3") }
    var kind by remember { mutableStateOf("SINGLE") }
    var explanation by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var audioUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }

    val picker = rememberFilePicker()

    var loadedQuestions by remember { mutableStateOf<Map<String, List<JsonObject>>>(emptyMap()) }
    var editingQuestionId by remember { mutableStateOf<String?>(null) }

    AdminScaffold(tr(StrAdmin.placementTests), navController, vm) { data ->

        item {
            AdminFormCard(tr(StrAdmin.newTest), icon = Icons.Default.Quiz) { close ->
                InkField(title, { title = it }, tr(StrAdmin.titleField))
                Spacer(Modifier.height(8.dp))
                InkField(description, { description = it }, tr(StrAdmin.descriptionField), singleLine = false, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        InkField(questionCount, { questionCount = it.filter(Char::isDigit) }, tr(StrAdmin.questions), keyboardType = KeyboardType.Number)
                    }
                    Box(Modifier.weight(1f)) {
                        InkField(minutes, { minutes = it.filter(Char::isDigit) }, tr(StrAdmin.minutes), keyboardType = KeyboardType.Number)
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryAction(tr(StrAdmin.createAdaptiveTest), enabled = title.isNotBlank()) {
                    vm.act {
                        AdminRepository.saveTest(
                            null, title, description, true,
                            questionCount.toIntOrNull() ?: 20,
                            (minutes.toIntOrNull() ?: 20) * 60,
                            50.0,
                        )
                    }
                    title = ""; description = ""
                    close()
                }
            }
        }

        if (data.tests.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noTests), tr(StrAdmin.noTestsBody)) }
        }

        // Move up/down: simplest and fastest for a short admin list — no gesture to learn, no
        // drag threshold to fight with on a small screen.
        itemsIndexed(data.tests, key = { _, it -> it.id }) { index, test ->
            val count = data.counts[test.id] ?: 0
            var editing by remember(test.id) { mutableStateOf(false) }
            var editTitle by remember(test.id) { mutableStateOf(test.title) }
            var editDesc by remember(test.id) { mutableStateOf(test.description.orEmpty()) }
            var editCount by remember(test.id) { mutableStateOf(test.questionCount.toString()) }
            var editMinutes by remember(test.id) { mutableStateOf((test.timeLimitSeconds / 60).toString()) }
            var editPassing by remember(test.id) { mutableStateOf(test.passingScore.toString().removeSuffix(".0")) }
            var editAdaptive by remember(test.id) { mutableStateOf(test.isAdaptive) }
            InkCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(test.displayTitle, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    StatusPill(test.status)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AdminMeta("${tr(StrAdmin.questionsInBank)}: $count", if (count > 0) Ink.Teal else Ink.Coral)
                    Spacer(Modifier.weight(1f))
                    IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = index > 0) {
                        vm.moveTest(data.tests, index, index - 1)
                    }
                    IconAction(Icons.Default.ArrowDownward, tr(StrStudio.moveDown), enabled = index < data.tests.lastIndex) {
                        vm.moveTest(data.tests, index, index + 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyValueRow(tr(StrAdmin.askedPerAttempt), test.questionCount.toString())
                KeyValueRow(tr(StrAdmin.adaptive), if (test.isAdaptive) tr(StrAdmin.yes) else tr(StrAdmin.noWord))

                Spacer(Modifier.height(12.dp))
                if (questionFor == test.id) {
                    InkField(prompt, { prompt = it }, tr(StrAdmin.question), singleLine = false, minLines = 2)
                    Spacer(Modifier.height(8.dp))

                    Text(tr(StrAdmin.questionKind), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("SINGLE", "TRUE_FALSE", "TEXT")) { option ->
                            Pill(
                                codeLabel(option),
                                Modifier.clickable {
                                    kind = option
                                    if (option == "TRUE_FALSE") {
                                        options = "True, False"
                                    } else if (option == "TEXT") {
                                        options = ""
                                    }
                                },
                                background = if (kind == option) Ink.AmberSoft else Ink.SurfaceHigh,
                                foreground = if (kind == option) Ink.Amber else Ink.TextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (kind == "TEXT") {
                        InkField(acceptedText, { acceptedText = it }, tr(StrAdmin.correctAnswerText))
                    } else {
                        InkField(options, { options = it }, tr(StrAdmin.optionsCommaSeparated))
                        Spacer(Modifier.height(8.dp))
                        InkField(correct, { correct = it.filter(Char::isDigit) }, tr(StrAdmin.correctOptionNumber), keyboardType = KeyboardType.Number)
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("GRAMMAR", "VOCABULARY", "READING", "LISTENING", "WRITING", "SPEAKING")) { option ->
                            Pill(
                                codeLabel(option),
                                Modifier.clickable { skill = option },
                                background = if (skill == option) Ink.AmberSoft else Ink.SurfaceHigh,
                                foreground = if (skill == option) Ink.Amber else Ink.TextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    InkField(difficulty, { difficulty = it.filter(Char::isDigit).take(1) }, tr(StrAdmin.difficultyRange), keyboardType = KeyboardType.Number)

                    // Media is optional — anything left blank simply is not shown to the learner.
                    // Files come straight from the phone, so a question can be built without
                    // hosting anything anywhere else first.
                    Spacer(Modifier.height(12.dp))
                    Text(tr(StrAdmin.questionMedia), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    MediaSlot(tr(StrEx.image), "image/*", imageUrl, "questions", picker) { imageUrl = it }
                    Spacer(Modifier.height(8.dp))
                    MediaSlot(tr(StrEx.audio), "audio/*", audioUrl, "questions", picker) { audioUrl = it }
                    Spacer(Modifier.height(8.dp))
                    MediaSlot(tr(StrEx.video), "video/*", videoUrl, "questions", picker) { videoUrl = it }
                    Spacer(Modifier.height(8.dp))
                    InkField(explanation, { explanation = it }, tr(StrAdmin.questionExplanation))

                    Spacer(Modifier.height(12.dp))
                    val list = options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val accepted = acceptedText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val isEditingQuestion = editingQuestionId != null
                    PrimaryAction(
                        if (isEditingQuestion) tr(StrEx.editQuestion) else tr(StrAdmin.addQuestion),
                        enabled = prompt.isNotBlank() && when (kind) {
                            "TEXT" -> accepted.isNotEmpty()
                            else -> list.size >= 2 && correct.isNotBlank()
                        },
                    ) {
                        val index = (correct.toIntOrNull() ?: 1) - 1
                        val choiceKind = kind != "TEXT"
                        vm.act {
                            if (isEditingQuestion) {
                                AdminRepository.updateQuestion(
                                    id = editingQuestionId!!,
                                    kind = kind,
                                    skill = skill,
                                    difficulty = (difficulty.toIntOrNull() ?: 3).coerceIn(1, 6),
                                    prompt = prompt,
                                    options = if (choiceKind) list else emptyList(),
                                    // coerceIn(0, -1) throws on an empty list, so only choice kinds reach it.
                                    correctIndexes = if (choiceKind && list.isNotEmpty()) listOf(index.coerceIn(0, list.lastIndex)) else emptyList(),
                                    correctText = if (kind == "TEXT") accepted else emptyList(),
                                    explanation = explanation,
                                    imageUrl = imageUrl,
                                    audioUrl = audioUrl,
                                    videoUrl = videoUrl,
                                )
                                // Refresh the loaded questions for this test
                                val qs = AdminRepository.questions(test.id)
                                loadedQuestions = loadedQuestions + (test.id to qs)
                            } else {
                                AdminRepository.addQuestion(
                                    testId = test.id,
                                    kind = kind,
                                    skill = skill,
                                    difficulty = (difficulty.toIntOrNull() ?: 3).coerceIn(1, 6),
                                    prompt = prompt,
                                    options = if (choiceKind) list else emptyList(),
                                    // coerceIn(0, -1) throws on an empty list, so only choice kinds reach it.
                                    correctIndexes = if (choiceKind && list.isNotEmpty()) listOf(index.coerceIn(0, list.lastIndex)) else emptyList(),
                                    correctText = if (kind == "TEXT") accepted else emptyList(),
                                    explanation = explanation,
                                    imageUrl = imageUrl,
                                    audioUrl = audioUrl,
                                    videoUrl = videoUrl,
                                )
                            }
                        }
                        prompt = ""; options = ""; correct = ""; acceptedText = ""
                        explanation = ""; imageUrl = ""; audioUrl = ""; videoUrl = ""
                        editingQuestionId = null
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(tr(StrAdmin.close), Modifier.fillMaxWidth()) { questionFor = null; editingQuestionId = null }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminButton(tr(StrAdmin.addQuestion), Modifier.weight(1f), Icons.Default.Add, AdminTone.Primary) { questionFor = test.id }
                        AdminButton(
                            if (test.status == "PUBLISHED") tr(StrAdmin.unpublish) else tr(StrAdmin.publish),
                            Modifier.weight(1f),
                            if (test.status == "PUBLISHED") Icons.Default.VisibilityOff else Icons.Default.Check,
                            if (test.status == "PUBLISHED") AdminTone.Neutral else AdminTone.Success,
                            enabled = test.status == "PUBLISHED" || count > 0,
                        ) {
                            vm.act {
                                AdminRepository.setTestStatus(
                                    test.id,
                                    if (test.status == "PUBLISHED") "UNPUBLISHED" else "PUBLISHED",
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AdminButton(tr(StrAdmin.editTest), Modifier.weight(1f), Icons.Default.Edit) { editing = !editing }
                        AdminDeleteIcon(tr(StrAdmin.delete), tr(StrAdmin.delete) + " · " + test.displayTitle) {
                            vm.act { AdminRepository.deleteTest(test.id) }
                        }
                    }
                    // --- View / edit existing questions ---
                    if (count > 0) {
                        Spacer(Modifier.height(8.dp))
                        val showList = loadedQuestions.containsKey(test.id)
                        SecondaryAction(
                            if (showList) tr(StrAdmin.close) else tr(StrAdmin.questionsInBank),
                            Modifier.fillMaxWidth(),
                        ) {
                            if (showList) {
                                loadedQuestions = loadedQuestions - test.id
                                editingQuestionId = null
                            } else {
                                vm.act {
                                    val qs = AdminRepository.questions(test.id)
                                    loadedQuestions = loadedQuestions + (test.id to qs)
                                }
                            }
                        }
                        if (showList) {
                            val qs = loadedQuestions[test.id].orEmpty()
                            Spacer(Modifier.height(8.dp))
                            qs.forEach { q ->
                                val qId = q["id"]?.jsonPrimitive?.content ?: return@forEach
                                val qPrompt = q["prompt"]?.jsonPrimitive?.content.orEmpty()
                                val qKind = q["kind"]?.jsonPrimitive?.content.orEmpty()
                                val qSkill = q["skill"]?.jsonPrimitive?.content.orEmpty()
                                InkCard {
                                    Text(
                                        qPrompt.take(120) + if (qPrompt.length > 120) "…" else "",
                                        color = Ink.TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "$qKind · $qSkill",
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SecondaryAction(
                                            tr(StrEx.editQuestion),
                                            Modifier.weight(1f),
                                        ) {
                                            // Pre-fill the form fields from this question
                                            prompt = qPrompt
                                            kind = qKind
                                            skill = qSkill
                                            difficulty = (q["difficulty"]?.jsonPrimitive?.content ?: "3")
                                            val opts = q["options"]
                                            options = if (opts != null) {
                                                try {
                                                    Json.decodeFromString<JsonArray>(opts.toString())
                                                        .joinToString(", ") { it.jsonPrimitive.content }
                                                } catch (_: Exception) { "" }
                                            } else ""
                                            val ci = q["correct_indexes"]
                                            correct = if (ci != null) {
                                                try {
                                                    Json.decodeFromString<JsonArray>(ci.toString())
                                                        .firstOrNull()?.jsonPrimitive?.content?.toIntOrNull()?.plus(1)?.toString() ?: ""
                                                } catch (_: Exception) { "" }
                                            } else ""
                                            val ct = q["correct_text"]
                                            acceptedText = if (ct != null) {
                                                try {
                                                    Json.decodeFromString<JsonArray>(ct.toString())
                                                        .joinToString(", ") { it.jsonPrimitive.content }
                                                } catch (_: Exception) { "" }
                                            } else ""
                                            explanation = q["explanation"]?.jsonPrimitive?.content.orEmpty()
                                            imageUrl = q["media_image_url"]?.jsonPrimitive?.content.orEmpty()
                                            audioUrl = q["media_audio_url"]?.jsonPrimitive?.content.orEmpty()
                                            videoUrl = q["media_video_url"]?.jsonPrimitive?.content.orEmpty()
                                            editingQuestionId = qId
                                            questionFor = test.id
                                        }
                                        SecondaryAction(
                                            tr(StrAdmin.delete),
                                            Modifier.weight(1f),
                                            tint = Ink.Coral,
                                        ) {
                                            vm.act { AdminRepository.deleteQuestion(qId) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                    if (editing) {
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        InkField(editTitle, { editTitle = it }, tr(StrAdmin.titleField))
                        Spacer(Modifier.height(8.dp))
                        InkField(editDesc, { editDesc = it }, tr(StrAdmin.descriptionField), singleLine = false, minLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                InkField(editCount, { editCount = it.filter(Char::isDigit) }, tr(StrAdmin.questions), keyboardType = KeyboardType.Number)
                            }
                            Box(Modifier.weight(1f)) {
                                InkField(editMinutes, { editMinutes = it.filter(Char::isDigit) }, tr(StrAdmin.minutes), keyboardType = KeyboardType.Number)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                InkField(editPassing, { editPassing = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAdmin.passingScore), keyboardType = KeyboardType.Decimal)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(tr(StrAdmin.adaptive), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = editAdaptive,
                                    onCheckedChange = { editAdaptive = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Ink.Canvas,
                                        checkedTrackColor = Ink.Amber,
                                        uncheckedThumbColor = Ink.TextMuted,
                                        uncheckedTrackColor = Ink.SurfaceHigh,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryAction(tr(StrAdmin.save), enabled = editTitle.isNotBlank()) {
                            vm.act {
                                AdminRepository.saveTest(
                                    test.id, editTitle, editDesc, editAdaptive,
                                    editCount.toIntOrNull() ?: test.questionCount,
                                    (editMinutes.toIntOrNull() ?: (test.timeLimitSeconds / 60)) * 60,
                                    editPassing.toDoubleOrNull() ?: test.passingScore,
                                )
                            }
                            editing = false
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryAction(tr(StrAdmin.cancel), Modifier.fillMaxWidth()) { editing = false }
                    }
                    if (test.status != "PUBLISHED" && count == 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(StrAdmin.publishTestFirst),
                            color = Ink.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Moderation

class ModerationVm : AdminListViewModel<List<Review>>({ AdminRepository.reviews(null) })

@Composable
fun AdminModerationScreen(navController: NavHostController) {
    val vm: ModerationVm = viewModel()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var stars by rememberSaveable { mutableStateOf(0) }

    AdminScaffold(tr(StrAdmin.reviews), navController, vm) { reviews ->
        item {
            AdminChips(
                listOf("ALL" to tr(StrAdminKit.all), "VISIBLE" to tr(StrAdminKit.visible), "HIDDEN" to tr(StrAdminKit.hidden)),
                filter,
                { filter = it },
                counts = mapOf("ALL" to reviews.size, "VISIBLE" to reviews.count { !it.isHidden }, "HIDDEN" to reviews.count { it.isHidden }),
            )
        }
        item {
            AdminChips(
                listOf(0 to tr(StrAdminKit.all)) + (1..5).map { it to "★ $it" },
                stars,
                { stars = it },
                tint = Ink.Amber,
            )
        }
        val shown = reviews.filter {
            (filter == "ALL" || (filter == "HIDDEN") == it.isHidden) && (stars == 0 || it.rating == stars)
        }
        if (shown.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noReviews), tr(StrAdmin.noReviewsBody)) }
        }
        items(shown, key = { it.id }) { review ->
            AdminItemCard(
                title = review.author?.fullName ?: tr(StrAdmin.student),
                subtitle = listOfNotNull(
                    if (review.targetType.equals("TEACHER", true)) roleLabel("TEACHER") else codeLabel(review.targetType),
                    formatDate(review.createdAt),
                ).joinToString(" · "),
                leading = { Avatar(null, review.author?.fullName, 40.dp) },
                trailing = {
                    Pill(
                        "★".repeat(review.rating.coerceIn(0, 5)),
                        background = if (review.rating <= 2) Ink.CoralSoft else Ink.AmberSoft,
                        foreground = if (review.rating <= 2) Ink.Coral else Ink.Amber,
                    )
                },
                accent = if (review.isHidden) Ink.Neutral else null,
                expandable = false,
            ) {
                if (!review.body.isNullOrBlank()) {
                    Text(
                        review.body,
                        color = if (review.isHidden) Ink.TextMuted else Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (review.isHidden) {
                    AdminMetaRow { AdminMeta(tr(StrAdminKit.hidden), Ink.Coral) }
                }
                AdminActions {
                    AdminButton(
                        if (review.isHidden) tr(StrAdmin.restore) else tr(StrAdmin.hide),
                        Modifier.weight(1f),
                        if (review.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        if (review.isHidden) AdminTone.Success else AdminTone.Neutral,
                    ) {
                        vm.act { AdminRepository.setReviewHidden(review.id, !review.isHidden, tr(StrAdmin.moderated)) }
                    }
                    AdminDeleteIcon(tr(StrAdmin.delete), tr(StrAdminKit.deleteReviewConfirm)) {
                        vm.act { AdminRepository.deleteReview(review.id) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Support

class AdminSupportVm : AdminListViewModel<List<SupportTicket>>({ AdminRepository.tickets(null) })

@Composable
fun AdminSupportScreen(navController: NavHostController) {
    val vm: AdminSupportVm = viewModel()
    var filter by rememberSaveable { mutableStateOf("OPEN") }

    AdminScaffold(tr(StrAdmin.supportTickets), navController, vm) { tickets ->
        val closed = { t: SupportTicket -> t.status == "RESOLVED" || t.status == "CLOSED" }
        val groups = mapOf(
            "OPEN" to tickets.filter { it.status == "OPEN" },
            "ASSIGNED" to tickets.filter { !closed(it) && it.status != "OPEN" },
            "RESOLVED" to tickets.filter(closed),
            "ALL" to tickets,
        )
        item {
            AdminChips(
                listOf(
                    "OPEN" to tr(StrAdminKit.open),
                    "ASSIGNED" to tr(StrAdminKit.inProgress),
                    "RESOLVED" to tr(StrAdminKit.resolved),
                    "ALL" to tr(StrAdminKit.all),
                ),
                filter,
                { filter = it },
                counts = groups.mapValues { it.value.size },
            )
        }
        val shown = groups[filter].orEmpty()
            .sortedWith(compareByDescending<SupportTicket> { it.priority == "HIGH" || it.priority == "URGENT" })
        if (shown.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noTickets), tr(StrAdmin.noTicketsBody)) }
        }
        items(shown, key = { it.id }) { ticket ->
            val urgent = ticket.priority == "HIGH" || ticket.priority == "URGENT"
            AdminItemCard(
                title = ticket.subject,
                subtitle = listOfNotNull(ticket.category?.let { codeLabel(it) }, formatDate(ticket.createdAt)).joinToString(" · "),
                leading = { AdminBadgeIcon(Icons.Default.SupportAgent, tint = if (urgent) Ink.Coral else Ink.Sky) },
                trailing = { StatusPill(ticket.status) },
                accent = if (urgent && !closed(ticket)) Ink.Coral else null,
                stateKey = ticket.id,
                startExpanded = ticket.status == "OPEN",
            ) {
                if (urgent) AdminMetaRow { AdminMeta(tr(StrAdminX.urgent), Ink.Coral) }
                TicketThread(ticket.id, canReply = !closed(ticket))
                if (!closed(ticket)) {
                    AdminActions {
                        if (ticket.status == "OPEN") {
                            AdminButton(tr(StrAdmin.assignToMe), Modifier.weight(1f), Icons.Default.PersonAdd, AdminTone.Info) {
                                vm.act { AdminRepository.setTicketStatus(ticket.id, "ASSIGNED") }
                            }
                        }
                        AdminButton(tr(StrAdmin.resolve), Modifier.weight(1f), Icons.Default.Check, AdminTone.Success) {
                            vm.act { AdminRepository.setTicketStatus(ticket.id, "RESOLVED") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The ticket's messages, loaded when the card is opened, with a reply box underneath — so a
 * ticket can actually be answered from here instead of only being marked resolved unread.
 */
@Composable
private fun TicketThread(ticketId: String, canReply: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var messages by remember(ticketId) { mutableStateOf<List<com.rork.pro.data.TicketMessage>?>(null) }
    var reply by remember(ticketId) { mutableStateOf("") }
    var sending by remember(ticketId) { mutableStateOf(false) }

    suspend fun reload() {
        messages = runCatching { com.rork.pro.data.SessionRepository.ticketMessages(ticketId) }.getOrDefault(emptyList())
    }
    LaunchedEffect(ticketId) { reload() }

    Text(tr(StrAdminX.conversation), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    when (val list = messages) {
        null -> androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = Ink.Sky,
            trackColor = Ink.SurfaceHigh,
        )
        else -> if (list.isEmpty()) {
            Text(tr(StrAdminX.noMessages), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { m ->
                    Column(
                        Modifier
                            .fillMaxWidth(0.9f)
                            .align(if (m.isStaff) Alignment.End else Alignment.Start)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (m.isStaff) Ink.SkySoft else Ink.SurfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${if (m.isStaff) tr(StrAdminX.supportSide) else tr(StrAdminX.userSide)} · ${formatDate(m.createdAt)}",
                            color = if (m.isStaff) Ink.Sky else Ink.TextMuted,
                            fontSize = 11.sp,
                        )
                        Text(m.body, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (canReply) {
        Spacer(Modifier.height(10.dp))
        InkField(reply, { reply = it }, tr(StrAdminX.replyHint), singleLine = false, minLines = 2)
        AdminActions {
            AdminButton(
                tr(StrAdminX.send),
                Modifier.weight(1f),
                Icons.Default.Check,
                AdminTone.Info,
                enabled = reply.isNotBlank() && !sending,
            ) {
                val text = reply.trim()
                scope.launch {
                    sending = true
                    runCatching { com.rork.pro.data.SessionRepository.replyToTicket(ticketId, text, isStaff = true) }
                        .onSuccess { reply = "" }
                    reload()
                    sending = false
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Audit

class AuditVm : AdminListViewModel<List<AuditLog>>({ AdminRepository.auditLogs() })

@Composable
fun AdminAuditScreen(navController: NavHostController) {
    val vm: AuditVm = viewModel()
    var query by rememberSaveable { mutableStateOf("") }

    AdminScaffold(tr(StrAdmin.auditLog), navController, vm) { logs ->
        item { AdminSearchField(query, { query = it }) }
        val q = query.trim()
        val shown = logs.filter {
            q.isBlank() || it.action.contains(q, true) || it.actor?.fullName.orEmpty().contains(q, true) ||
                it.targetType.orEmpty().contains(q, true) || auditActionLabel(it.action).contains(q, true)
        }
        if (shown.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.nothingLogged), tr(StrAdmin.nothingLoggedBody)) }
        }
        items(shown, key = { it.id }) { log ->
            val destructive = listOf("DELETE", "REMOVE", "SUSPEND", "REJECT", "REFUND").any { log.action.contains(it, true) }
            Row(
                // Intrinsic height lets the rail run exactly as tall as the entry beside it —
                // the old fixed 58dp rail overshot short rows and fell short of wrapped ones.
                Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Timeline rail
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(12.dp).clip(CircleShape).background(if (destructive) Ink.Coral else Ink.Amber),
                    )
                    Box(Modifier.width(2.dp).weight(1f).padding(top = 4.dp).background(Ink.Hairline))
                }
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            auditActionLabel(log.action),
                            color = if (destructive) Ink.Coral else Ink.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatDate(log.createdAt), color = Ink.TextMuted, fontSize = 11.sp)
                    }
                    AdminMetaRow {
                        AdminMeta(
                            "${log.actor?.fullName ?: tr(StrAdmin.systemActor)}${log.actorRole?.let { " · ${roleLabel(it)}" } ?: ""}",
                            roleTint(log.actorRole),
                        )
                        if (!log.targetType.isNullOrBlank()) {
                            AdminMeta(auditTargetLabel(log.targetType))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Announcements

class AnnounceVm : AdminListViewModel<Unit>({ }) {
    private val _sent = MutableStateFlow<Int?>(null)
    val sent: StateFlow<Int?> = _sent.asStateFlow()

    fun send(audience: String, title: String, body: String) {
        _sent.value = null
        act { _sent.value = AdminRepository.broadcast(audience, title, body) }
    }

    fun clearSent() { _sent.value = null }
}

/**
 * One important message to one audience, with a live preview of how it lands on a phone and a
 * confirmation step — a broadcast can't be recalled.
 */
@Composable
fun AdminAnnouncementsScreen(navController: NavHostController) {
    val vm: AnnounceVm = viewModel()
    val sent by vm.sent.collectAsStateWithLifecycle()
    var audience by remember { mutableStateOf("ALL") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    val audiences = listOf(
        "ALL" to tr(StrAdmin.audienceAll),
        "STUDENTS" to tr(StrAdmin.audienceStudents),
        "TEACHERS" to tr(StrAdmin.audienceTeachers),
        "STAFF" to tr(StrAdmin.audienceStaff),
    )
    val audienceName = audiences.firstOrNull { it.first == audience }?.second.orEmpty()

    if (confirm) {
        ConfirmDialog(
            title = tr(StrAdminKit.confirmSendTitle),
            body = trf(StrAdminKit.confirmSendBody, audienceName),
            confirmLabel = tr(StrAdmin.sendAnnouncement),
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                vm.send(audience, title, body)
                title = ""; body = ""
            },
        )
    }

    AdminScaffold(tr(StrAdmin.announcements), navController, vm) {
        item { AdminNote(tr(StrAdmin.announcementPolicy)) }
        sent?.let { count ->
            item {
                InkCard(color = Ink.TealSoft, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal)
                        Text(trf(StrAdmin.announcementSent, count), color = Ink.Teal, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, null, tint = Ink.Teal, modifier = Modifier.size(18.dp).clickable { vm.clearSent() })
                    }
                }
            }
        }
        item {
            InkCard {
                Text(tr(StrAdmin.audience), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                AdminChips(audiences, audience, { audience = it })
                Spacer(Modifier.height(14.dp))
                InkField(title, { title = it.take(80) }, tr(StrAdmin.announcementTitle))
                Text("${title.length}/80", color = Ink.TextMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                Spacer(Modifier.height(6.dp))
                InkField(body, { body = it }, tr(StrAdmin.announcementBody), singleLine = false, minLines = 3)
            }
        }
        if (title.isNotBlank()) {
            item {
                AdminSectionLabel(tr(StrAdminKit.preview))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.SurfaceHigh).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AdminBadgeIcon(Icons.Default.Campaign, tint = Ink.Amber, size = 36)
                    Column(Modifier.weight(1f)) {
                        Text("7PRO", color = Ink.TextMuted, fontSize = 11.sp)
                        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        if (body.isNotBlank()) Text(body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        }
        item {
            PrimaryAction(tr(StrAdmin.sendAnnouncement), enabled = title.isNotBlank()) { confirm = true }
        }
    }
}
