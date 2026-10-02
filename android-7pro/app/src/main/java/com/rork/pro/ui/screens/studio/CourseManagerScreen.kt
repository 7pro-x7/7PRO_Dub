package com.rork.pro.ui.screens.studio

import com.rork.pro.ui.components.coursePriceLabel
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.Course
import com.rork.pro.data.CourseScope
import com.rork.pro.data.CourseStudioRepository
import com.rork.pro.data.Profile
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrStudio
import com.rork.pro.ui.i18n.statusLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val STATUS_FILTERS = listOf("DRAFT", "PENDING_REVIEW", "PUBLISHED", "ARCHIVED")

class CourseManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<Course>>>(Async.Loading)
    val state: StateFlow<Async<List<Course>>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _teachers = MutableStateFlow<List<Profile>>(emptyList())
    val teachers: StateFlow<List<Profile>> = _teachers.asStateFlow()

    private var scope: CourseScope = CourseScope.MINE
    private var bound = false

    var search: String = ""
        private set
    var status: String? = null
        private set

    fun bind(scope: CourseScope) {
        if (bound && this.scope == scope) return
        this.scope = scope
        bound = true
        load()
        if (scope == CourseScope.ALL) loadTeachers()
    }

    fun setSearch(value: String) {
        search = value
        load()
    }

    fun setStatus(value: String?) {
        status = value
        load()
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        _refreshing.value = true
        viewModelScope.launch {
            runCatching { CourseStudioRepository.courses(scope, search, status) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun load() {
        viewModelScope.launch {
            runCatching { CourseStudioRepository.courses(scope, search, status) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    private fun loadTeachers() {
        viewModelScope.launch {
            runCatching { CourseStudioRepository.teacherOptions() }.onSuccess { _teachers.value = it }
        }
    }

    fun create(title: String, teacherId: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { CourseStudioRepository.createCourse(title, teacherId) }
                .onSuccess { onCreated(it.id) }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }
}

/**
 * The course list behind both dashboards. [scope] decides whether it shows the signed-in
 * teacher's own catalogue or every course on the platform.
 */
@Composable
fun CourseManagerScreen(
    navController: NavHostController,
    session: SessionViewModel,
    scope: CourseScope,
    /** Shown as a tab inside the Content hub: no header of its own. */
    embedded: Boolean = false,
) {
    val vm: CourseManagerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val teachers by vm.teachers.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(scope) { vm.bind(scope) }
    // Coming back from the editor should show the change that was just made.
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry?.destination?.route) {
        val route = backStackEntry?.destination?.route
        if (route == StudioRoutes.COURSES || route == StudioRoutes.CONTENT) vm.load()
    }

    if (creating) {
        CreateCourseDialog(
            teachers = if (scope == CourseScope.ALL) teachers else emptyList(),
            myId = sessionState.profile?.id,
            busy = busy,
            error = error,
            onDismiss = { creating = false },
            onCreate = { title, teacherId ->
                vm.create(title, teacherId) { id ->
                    creating = false
                    navController.navigate(StudioRoutes.editor(id))
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .imePadding(),
    ) {
        if (!embedded) {
            DetailHeader(
                if (scope == CourseScope.ALL) tr(StrStudio.allCourses) else tr(StrStudio.myCourses),
                onBack = { navController.popBackStack() },
            )
        }

        Refreshable(refreshing, { vm.refresh() }) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (scope == CourseScope.ALL) tr(StrStudio.manageAllCourses) else tr(StrStudio.manageMyCourses),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item { PrimaryAction(tr(StrStudio.newCourse)) { creating = true } }

            item {
                InkField(
                    search,
                    {
                        search = it
                        vm.setSearch(it)
                    },
                    tr(StrStudio.searchCourses),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(tr(StrStudio.allStatuses), status == null) {
                            status = null
                            vm.setStatus(null)
                        }
                    }
                    items(STATUS_FILTERS) { option ->
                        FilterChip(statusLabel(option), status == option) {
                            status = option
                            vm.setStatus(option)
                        }
                    }
                }
            }

            when (val s = state) {
                is Async.Loading -> item { LoadingBlock() }
                is Async.Failure -> item { ErrorBlock(s.error) { vm.load() } }
                is Async.Success -> {
                    if (s.value.isEmpty()) {
                        item {
                            EmptyBlock(
                                tr(StrStudio.noCoursesYet),
                                if (search.isBlank() && status == null) {
                                    tr(StrStudio.noCoursesBody)
                                } else {
                                    tr(StrStudio.noCoursesMatch)
                                },
                            )
                        }
                    } else {
                        items(s.value, key = { it.id }) { course ->
                            CourseRow(course, showTeacher = scope == CourseScope.ALL) {
                                navController.navigate(StudioRoutes.editor(course.id))
                            }
                        }
                    }
                }
            }
        }
        } // Refreshable
    }
}

@Composable
private fun CourseRow(course: Course, showTeacher: Boolean, onClick: () -> Unit) {
    InkCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(
                course.thumbnailUrl,
                Modifier
                    .size(width = 84.dp, height = 60.dp)
                    .clip(RoundedCornerShape(12.dp)),
                fallbackLabel = course.displayTitle,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    course.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                if (showTeacher) {
                    Text(
                        course.teacher?.fullName.orEmpty().ifBlank { tr(StrStudio.ownedBy) },
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(course.status)
                    Text(
                        if (course.isFreeCourse) tr(Str.free) else coursePriceLabel(course),
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (course.enrollmentsCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        trf(StrStudio.enrolledCount, course.enrollmentsCount),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCourseDialog(
    teachers: List<Profile>,
    myId: String?,
    busy: Boolean,
    error: AppError?,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var teacherId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(StrStudio.createCourseTitle), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InkField(title, { title = it }, tr(StrStudio.courseTitleHint))
                if (teachers.isNotEmpty()) {
                    Text(
                        tr(StrStudio.chooseTeacher),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(tr(Str.me), teacherId == null) { teacherId = null }
                        }
                        items(teachers.filter { it.id != myId }) { teacher ->
                            FilterChip(teacher.displayName, teacherId == teacher.id) { teacherId = teacher.id }
                        }
                    }
                }
                error?.let {
                    Text(it.message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim(), teacherId) },
                enabled = title.isNotBlank() && !busy,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(StrStudio.create), color = Ink.Amber)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(StrStudio.cancel), color = Ink.TextMuted) }
        },
    )
}

@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Pill(
        label,
        Modifier.clickable(onClick = onClick),
        background = if (selected) Ink.AmberSoft else Ink.SurfaceHigh,
        foreground = if (selected) Ink.Amber else Ink.TextMuted,
    )
}

@Composable
internal fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = Ink.TextSecondary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.SurfaceHigh)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else Ink.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(17.dp),
        )
    }
}
