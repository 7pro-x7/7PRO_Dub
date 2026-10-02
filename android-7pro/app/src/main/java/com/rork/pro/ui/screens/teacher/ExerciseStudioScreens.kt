package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.Backend
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.Course
import com.rork.pro.data.ExerciseRepository
import com.rork.pro.data.ExerciseSection
import com.rork.pro.data.OwnerExerciseOffer
import com.rork.pro.data.PlacementTest
import com.rork.pro.data.TestQuestionRow
import com.rork.pro.data.toAppError
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
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.StrExLink
import com.rork.pro.ui.i18n.StrStudio
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.admin.AdminActions
import com.rork.pro.ui.screens.admin.AdminRoutes
import com.rork.pro.ui.screens.admin.AdminButton
import com.rork.pro.ui.screens.admin.AdminTone
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.screens.studio.IconAction
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ExerciseStudioData(
    val exercises: List<PlacementTest>,
    val counts: Map<String, Int>,
    /** exercise id → the courses it is linked into (an exercise may sit in several courses). */
    val courseLinks: Map<String, Set<String>> = emptyMap(),
)

/**
 * Exercise studio for teachers and staff.
 *
 * The screen is the same for both; only the scope differs. A teacher sees the exercises they
 * own, `tests.manage` holders see every teacher's — and the database enforces exactly that, so
 * this flag decides what is fetched, never what is allowed.
 */
class ExerciseStudioViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<ExerciseStudioData>>(Async.Loading)
    val state: StateFlow<Async<ExerciseStudioData>> = _state.asStateFlow()

    private val _questions = MutableStateFlow<Map<String, List<TestQuestionRow>>>(emptyMap())
    val questions: StateFlow<Map<String, List<TestQuestionRow>>> = _questions.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _sections = MutableStateFlow<List<ExerciseSection>>(emptyList())
    val sections: StateFlow<List<ExerciseSection>> = _sections.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    /**
     * Whether the signed-in teacher may file their own exercises under one of their own courses
     * — an owner-granted permission (`teacher_profiles.can_manage_course_exercises`). Staff
     * scoped to one teacher (`manageAll`) are always unrestricted, so this only matters for a
     * teacher managing their own exercises; the database enforces the real restriction either way.
     */
    private val _canFileUnderCourse = MutableStateFlow(false)
    val canFileUnderCourse: StateFlow<Boolean> = _canFileUnderCourse.asStateFlow()

    private fun loadCourseExercisePermission() {
        if (manageAll) {
            _canFileUnderCourse.value = true
            return
        }
        val me = Backend.currentUserId ?: return
        viewModelScope.launch {
            runCatching {
                Backend.client.from("teacher_profiles")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("can_manage_course_exercises")) {
                        filter { eq("id", me) }
                        limit(1)
                    }
                    .decodeList<JsonObject>()
                    .firstOrNull()
                    ?.get("can_manage_course_exercises")
                    ?.toString() == "true"
            }.onSuccess { _canFileUnderCourse.value = it }
        }
    }

    private val _offers = MutableStateFlow<Async<List<OwnerExerciseOffer>>>(Async.Loading)
    val offers: StateFlow<Async<List<OwnerExerciseOffer>>> = _offers.asStateFlow()

    /** The owner's published exercises this teacher may copy (the server returns nothing else). */
    fun loadOffers(quiet: Boolean = false) {
        if (manageAll) return
        viewModelScope.launch {
            // Quiet refreshes (after a copy) keep the open dialog as it is instead of flashing a spinner.
            if (!quiet) _offers.value = Async.Loading
            runCatching { ExerciseRepository.ownerExercises() }
                .onSuccess { _offers.value = Async.Success(it) }
                .onFailure { if (!quiet) _offers.value = Async.Failure(it.toAppError()) }
        }
    }

    /**
     * Levels are the teacher's own. Staff scoped to one teacher see and edit that teacher's levels
     * exactly as the teacher does; staff on the flat "everyone" list have no single owner, so none.
     */
    fun loadSections() {
        if (manageAll && scopedTeacherId == null) return
        viewModelScope.launch {
            runCatching { ExerciseRepository.sections(scopedTeacherId) }.onSuccess { _sections.value = it }
        }
    }

    /**
     * The courses an exercise here could be linked to: the signed-in teacher's own courses, or,
     * when staff are scoped to one teacher, that teacher's — same published courses the "file
     * under level" picker's owner would see. Left empty (and hidden) when staff are browsing
     * every teacher's exercises at once, since there is no single owner to pick courses from.
     */
    fun loadCourses() {
        val owner = if (manageAll) scopedTeacherId else Backend.currentUserId
        if (owner == null) {
            _courses.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching { CatalogRepository.teacherCourses(owner) }.onSuccess { _courses.value = it }
        }
    }

    /** Runs a course change, then refreshes the exercises that reference it. */
    fun courseAct(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load(quiet = true)
        }
    }

    /** Runs a level change, then refreshes both the levels and the exercises that reference them. */
    fun sectionAct(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            loadSections()
            load(quiet = true)
        }
    }

    private var manageAll: Boolean = false
    private var scopedTeacherId: String? = null
    private var bound: Boolean = false

    /** [teacherId] narrows a staff ([all] = true) view to one teacher's exercises only. */
    fun bind(all: Boolean, teacherId: String? = null) {
        if (bound && manageAll == all && scopedTeacherId == teacherId) return
        manageAll = all
        scopedTeacherId = teacherId
        bound = true
        load()
        loadSections()
        loadCourses()
        loadCourseExercisePermission()
    }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                val list = when {
                    !manageAll -> ExerciseRepository.mine()
                    scopedTeacherId != null -> ExerciseRepository.ofTeacher(scopedTeacherId!!)
                    else -> ExerciseRepository.all()
                }
                ExerciseStudioData(
                    list,
                    ExerciseRepository.questionCounts(list.map { it.id }),
                    runCatching { com.rork.pro.data.CourseContentRepository.coursesOf(list.map { it.id }) }
                        .getOrDefault(emptyMap()),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun loadQuestions(exerciseId: String) {
        viewModelScope.launch {
            runCatching { ExerciseRepository.questions(exerciseId) }
                .onSuccess { _questions.value = _questions.value + (exerciseId to it) }
                .onFailure { _error.value = it.toAppError() }
        }
    }

    fun act(afterQuestionsOf: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load(quiet = true)
            afterQuestionsOf?.let { loadQuestions(it) }
        }
    }

    fun clearError() { _error.value = null }

    /** Swaps two adjacent exercises and persists the new order — same shape as course lesson reordering. */
    fun moveExercise(exercises: List<PlacementTest>, from: Int, to: Int) {
        if (to !in exercises.indices) return
        val reordered = exercises.toMutableList().apply { add(to, removeAt(from)) }
        act { ExerciseRepository.reorderExercises(reordered) }
    }
}

/**
 * Exercise studio — rebuilt around a dashboard rather than a form.
 *
 * The previous version opened on a permanent "new exercise" form, then a flat run of identical
 * cards, each of which expanded inline into an edit form, a question bank and a wizard. Two
 * things were wrong with that: the form held the best space on the screen even when the teacher
 * came to manage existing work, and every exercise looked exactly as important as every other,
 * so nothing could be scanned.
 *
 * This version leads with what a teacher actually wants to know — how many exercises exist,
 * how many are live, whether any are short of questions — then a searchable, filterable list of
 * cards that carry their own status colour and a readiness meter. Creating moves into a panel
 * that is opened deliberately and closes itself when done.
 */
@Composable
fun ExerciseStudioScreen(
    navController: NavHostController,
    manageAll: Boolean,
    levelKey: String? = null,
    /** Set by the owner/admin console's per-teacher view to scope this screen to one teacher. */
    teacherId: String? = null,
    teacherName: String? = null,
    /** Shown as a tab inside the Content hub: no header of its own. */
    embedded: Boolean = false,
) {
    val vm: ExerciseStudioViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val courses by vm.courses.collectAsStateWithLifecycle()
    val canFileUnderCourse by vm.canFileUnderCourse.collectAsStateWithLifecycle()
    val copyLink = rememberExerciseLinkCopier()
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    // Whose exercises a copied link points at: the scoped teacher for staff, otherwise me.
    val linkOwner = teacherId ?: Backend.currentUserId

    vm.bind(manageAll, teacherId)
    // Staff scoped to one teacher get the same create/edit freedom a teacher has over their own
    // exercises; unscoped staff (the flat "every teacher" list) stay read-mostly.
    val canCreate = !manageAll || teacherId != null

    // Two modes for a teacher: the front page lists their levels as buttons, and each button
    // opens this same screen scoped to that level's exercises. Staff (manageAll) keep the flat list.
    // Owner/admin scoped to one teacher get exactly the screen that teacher has: levels, lock, results,
    // reordering. Only the flat "everyone's exercises" list stays a read-mostly overview.
    val teacherLike = !manageAll || teacherId != null
    val inLevel = levelKey != null && teacherLike
    val browsingLevels = teacherLike && !inLevel

    // Coming back from a level, refresh so the counts on the level buttons are current.
    LifecycleResumeEffect(Unit) {
        vm.load(quiet = true)
        vm.loadSections()
        onPauseOrDispose { }
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("10") }
    var minutes by remember { mutableStateOf("15") }
    // Level picked while creating. Kept after a create so a teacher filling one level with
    // several exercises doesn't re-pick it every time; `levelPicked` separates "no level"
    // chosen on purpose from "nothing chosen yet".
    var newSectionId by remember { mutableStateOf<String?>(levelKey?.takeIf { it != NO_LEVEL_KEY }) }
    var levelPicked by remember { mutableStateOf(inLevel) }

    var composerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StudioFilter.ALL) }

    var editing by remember { mutableStateOf<String?>(null) }
    var questionsFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<PlacementTest?>(null) }
    var copyOpen by remember { mutableStateOf(false) }
    var copyNote by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Exercise whose "move to position" picker is open.
    var positionPickerFor by remember { mutableStateOf<String?>(null) }
    val offers by vm.offers.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .imePadding(),
    ) {
        if (!embedded) DetailHeader(
            when {
                inLevel -> if (levelKey == NO_LEVEL_KEY) {
                    tr(StrEx.noLevel)
                } else {
                    sections.firstOrNull { it.id == levelKey }?.title
                        ?: teacherName?.takeIf { it.isNotBlank() }
                        ?: tr(StrEx.myExercises)
                }
                manageAll && teacherId != null -> teacherName?.takeIf { it.isNotBlank() } ?: tr(StrEx.allExercises)
                manageAll -> tr(StrEx.allExercises)
                else -> tr(StrEx.myExercises)
            },
            onBack = { navController.popBackStack() },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> {
                val all = s.value.exercises
                val counts = s.value.counts
                // In a level, only that level's exercises are shown and counted.
                val scoped = if (inLevel) {
                    all.filter { if (levelKey == NO_LEVEL_KEY) it.sectionId == null else it.sectionId == levelKey }
                } else {
                    all
                }
                val visible = scoped.filter { exercise ->
                    val matchesFilter = when (filter) {
                        StudioFilter.ALL -> true
                        StudioFilter.PUBLISHED -> exercise.status == "PUBLISHED"
                        StudioFilter.DRAFT -> exercise.status != "PUBLISHED"
                    }
                    val matchesQuery = com.rork.pro.ui.screens.admin.SearchMatch.matches(
                        query,
                        exercise.title,
                        exercise.titleAr,
                        exercise.titleEn,
                        exercise.description,
                        exercise.owner?.fullName,
                    )
                    matchesFilter && matchesQuery
                }

                positionPickerFor?.let { exId ->
                    val from = visible.indexOfFirst { it.id == exId }
                    if (from < 0) {
                        positionPickerFor = null
                    } else {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { positionPickerFor = null },
                            containerColor = Ink.Surface,
                            titleContentColor = Ink.TextPrimary,
                            textContentColor = Ink.TextSecondary,
                            title = { Text(tr(StrEx.moveToPositionTitle), style = MaterialTheme.typography.titleLarge) },
                            text = {
                                Column {
                                    Text(tr(StrEx.moveToPositionBody), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    LazyColumn(
                                        Modifier.heightIn(max = 320.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(visible, key = { _, it -> it.id }) { i, ex ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (i == from) Ink.Amber.copy(alpha = 0.16f) else Ink.SurfaceHigh)
                                                    .clickable(enabled = i != from) {
                                                        vm.moveExercise(visible, from, i)
                                                        positionPickerFor = null
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    (i + 1).toString(),
                                                    color = Ink.Amber,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    ex.displayTitle,
                                                    color = Ink.TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (i == from) {
                                                    Text(tr(StrEx.currentPosition), color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { positionPickerFor = null }) {
                                    Text(tr(StrEx.close), color = Ink.TextSecondary)
                                }
                            },
                        )
                    }
                }

                Refreshable(refreshing, { vm.refresh() }) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            top = 4.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        error?.let { err ->
                            item {
                                InkCard(borderColor = Ink.Coral.copy(alpha = 0.4f)) {
                                    Text(err.message, color = Ink.Coral, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                    SecondaryAction(tr(StrEx.close)) { vm.clearError() }
                                }
                            }
                        }

                        item {
                            StudioSummary(
                                total = scoped.size,
                                published = scoped.count { it.status == "PUBLISHED" },
                                questions = scoped.sumOf { counts[it.id] ?: 0 },
                            )
                        }

                        // One link to all of a single teacher's exercises. Hidden on the
                        // owner's all-teachers view, where there is no single teacher to point at.
                        if (!(manageAll && teacherId == null) && scoped.any { it.status == "PUBLISHED" }) {
                            item {
                                SecondaryAction(tr(StrExLink.copyAll), Modifier.fillMaxWidth()) {
                                    copyLink(linkOwner, null)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tr(StrExLink.copyAllHelp),
                                    color = Ink.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        if (canCreate) {
                            item {
                                if (composerOpen) {
                                    ComposerPanel(
                                        title = title,
                                        onTitle = { title = it },
                                        description = description,
                                        onDescription = { description = it },
                                        count = count,
                                        onCount = { count = it.filter(Char::isDigit) },
                                        minutes = minutes,
                                        onMinutes = { minutes = it.filter(Char::isDigit) },
                                        sections = sections,
                                        // A level deleted since it was picked falls back to "not chosen".
                                        sectionId = newSectionId?.takeIf { id -> sections.any { it.id == id } },
                                        levelPicked = levelPicked &&
                                            (newSectionId == null || sections.any { it.id == newSectionId }),
                                        onSection = { newSectionId = it; levelPicked = true },
                                        busy = busy,
                                        onClose = { composerOpen = false },
                                        onCreate = {
                                            val newTitle = title
                                            val newDescription = description
                                            val section = newSectionId?.takeIf { id -> sections.any { it.id == id } }
                                            vm.act {
                                                ExerciseRepository.create(
                                                    title = newTitle,
                                                    description = newDescription,
                                                    questionCount = count.toIntOrNull() ?: 10,
                                                    timeLimitSeconds = (minutes.toIntOrNull() ?: 15) * 60,
                                                    sectionId = section,
                                                    teacherId = teacherId,
                                                )
                                            }
                                            title = ""; description = ""
                                            composerOpen = false
                                        },
                                    )
                                } else {
                                    NewExerciseCallout { composerOpen = true }
                                }
                            }
                        }
                        if (browsingLevels && !manageAll) {
                            item {
                                AdminButton(
                                    tr(StrEx.copyFromOwner),
                                    Modifier.fillMaxWidth(),
                                    Icons.Default.ContentCopy,
                                    AdminTone.Info,
                                ) {
                                    copyNote = null
                                    vm.loadOffers()
                                    copyOpen = true
                                }
                            }
                        }

                        if (browsingLevels) {
                            item {
                                LevelsPanel(
                                    sections = sections,
                                    busy = busy,
                                    onCreate = { name -> vm.sectionAct { ExerciseRepository.createSection(name, teacherId) } },
                                    onRename = { id, name -> vm.sectionAct { ExerciseRepository.renameSection(id, name) } },
                                    onDelete = { id -> vm.sectionAct { ExerciseRepository.deleteSection(id) } },
                                    onMove = { from, to ->
                                        if (to in sections.indices) {
                                            val reordered = sections.toMutableList().apply { add(to, removeAt(from)) }
                                            vm.sectionAct { ExerciseRepository.reorderSections(reordered) }
                                        }
                                    },
                                )
                            }
                        }

                        if (browsingLevels) {
                            val unfiled = all.filter { ex -> ex.sectionId == null || sections.none { it.id == ex.sectionId } }
                            items(sections, key = { "level-${it.id}" }) { section ->
                                val inThis = all.filter { it.sectionId == section.id }
                                LevelButton(
                                    title = section.title,
                                    exercises = inThis.size,
                                    published = inThis.count { it.status == "PUBLISHED" },
                                ) {
                                    navController.navigate(
                                        if (manageAll && teacherId != null) AdminRoutes.exerciseTeacherLevel(teacherId, teacherName.orEmpty(), section.id)
                                        else TeacherRoutes.exerciseLevel(section.id),
                                    )
                                }
                            }
                            if (unfiled.isNotEmpty()) {
                                item(key = "level-none") {
                                    LevelButton(
                                        title = tr(StrEx.noLevel),
                                        exercises = unfiled.size,
                                        published = unfiled.count { it.status == "PUBLISHED" },
                                    ) {
                                        navController.navigate(
                                            if (manageAll && teacherId != null) AdminRoutes.exerciseTeacherLevel(teacherId, teacherName.orEmpty(), NO_LEVEL_KEY)
                                            else TeacherRoutes.exerciseLevel(NO_LEVEL_KEY),
                                        )
                                    }
                                }
                            }
                            if (all.isEmpty() && sections.isEmpty()) {
                                item { EmptyBlock(tr(StrEx.noExercisesYet), tr(StrEx.noExercisesYetBody)) }
                            }
                        }

                        if (!browsingLevels && scoped.isNotEmpty()) {
                            item {
                                StudioFilters(
                                    query = query,
                                    onQuery = { query = it },
                                    filter = filter,
                                    onFilter = { filter = it },
                                )
                            }
                        }

                        if (!browsingLevels && scoped.isEmpty()) {
                            item {
                                EmptyBlock(
                                    if (manageAll && teacherId == null) tr(StrEx.noExercisesAdmin) else tr(StrEx.noExercisesYet),
                                    if (manageAll && teacherId == null) tr(StrEx.noExercisesAdminBody) else tr(StrEx.noExercisesYetBody),
                                )
                            }
                        } else if (visible.isEmpty()) {
                            item { EmptyBlock(tr(StrEx.noMatches), tr(StrEx.noMatchesBody)) }
                        }

                        // Reordering only makes sense on the unfiltered, unsearched list — the
                        // arrows move an exercise relative to its neighbours, and those
                        // neighbours have to be the real ones.
                        val reorderable = teacherLike && filter == StudioFilter.ALL && query.isBlank()

                        itemsIndexed(if (browsingLevels) emptyList() else visible, key = { _, it -> it.id }) { index, exercise ->
                            ExerciseCard(
                                exercise = exercise,
                                questionCount = counts[exercise.id] ?: 0,
                                showOwner = manageAll && teacherId == null,
                                busy = busy,
                                editing = editing == exercise.id,
                                questionsOpen = questionsFor == exercise.id,
                                questions = questions[exercise.id].orEmpty(),
                                onCopyLink = { copyLink(exercise.ownerId ?: linkOwner, exercise.id) },
                                // Owner/admin browsing anyone's exercises can take a copy of one that isn't theirs.
                                onCopyToMine = if (manageAll && exercise.ownerId != Backend.currentUserId) {
                                    {
                                        vm.act {
                                            ExerciseRepository.staffCopy(exercise.id)
                                            android.widget.Toast.makeText(toastContext, tr(StrEx.copiedToMine), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    null
                                },
                                onToggleEdit = { editing = if (editing == exercise.id) null else exercise.id },
                                onToggleQuestions = {
                                    navController.navigate(
                                        if (manageAll) AdminRoutes.exerciseQuestions(exercise.id)
                                        else TeacherRoutes.exerciseQuestions(exercise.id),
                                    )
                                },
                                onSave = { newTitle, newDescription, newCount, newMinutes ->
                                    vm.act {
                                        ExerciseRepository.update(
                                            exercise.id,
                                            newTitle,
                                            newDescription,
                                            newCount,
                                            newMinutes * 60,
                                        )
                                    }
                                    editing = null
                                },
                                onStatus = { status -> vm.act { ExerciseRepository.setStatus(exercise.id, status) } },
                                onDelete = { confirmDelete = exercise },
                                onAddQuestion = { draft ->
                                    vm.act(afterQuestionsOf = exercise.id) {
                                        ExerciseRepository.addQuestion(
                                            exerciseId = exercise.id,
                                            kind = draft.kind,
                                            skill = draft.skill,
                                            prompt = draft.prompt,
                                            options = draft.options,
                                            correctIndexes = draft.correctIndexes,
                                            correctText = draft.correctText,
                                            explanation = draft.explanation,
                                            imageUrl = draft.imageUrl,
                                            audioUrl = draft.audioUrl,
                                            videoUrl = draft.videoUrl,
                                            sortOrder = questions[exercise.id].orEmpty().size,
                                        )
                                    }
                                },
                                onDeleteQuestion = { questionId ->
                                    vm.act(afterQuestionsOf = exercise.id) {
                                        ExerciseRepository.deleteQuestion(questionId)
                                    }
                                },
                                onToggleQuestionActive = { questionId, active ->
                                    vm.act(afterQuestionsOf = exercise.id) {
                                        ExerciseRepository.setQuestionActive(questionId, active)
                                    }
                                },
                                onUpdateQuestion = { questionId, draft ->
                                    vm.act(afterQuestionsOf = exercise.id) {
                                        ExerciseRepository.updateQuestion(
                                            id = questionId,
                                            kind = draft.kind,
                                            skill = draft.skill,
                                            prompt = draft.prompt,
                                            options = draft.options,
                                            correctIndexes = draft.correctIndexes,
                                            correctText = draft.correctText,
                                            explanation = draft.explanation,
                                            imageUrl = draft.imageUrl,
                                            audioUrl = draft.audioUrl,
                                            videoUrl = draft.videoUrl,
                                        )
                                    }
                                },
                                sections = sections,
                                // Only the same teacher's other exercises: a question never
                                // changes owner by being moved.
                                moveTargets = all.filter { it.id != exercise.id && it.ownerId == exercise.ownerId },
                                onMoveQuestion = { questionId, targetId ->
                                    val targetCached = questions.containsKey(targetId)
                                    vm.act(afterQuestionsOf = exercise.id) {
                                        ExerciseRepository.moveQuestion(questionId, targetId)
                                        if (targetCached) vm.loadQuestions(targetId)
                                    }
                                },
                                onSection = if (!teacherLike) null else { sectionId ->
                                    vm.sectionAct { ExerciseRepository.setSection(exercise.id, sectionId) }
                                },
                                // Hidden for the flat "every teacher" staff list (no single owner
                                // to pick courses from) and, for a teacher managing their own
                                // exercises, hidden until the owner grants can_manage_course_exercises
                                // — the database enforces the real restriction either way.
                                courses = if ((manageAll && teacherId == null) || !canFileUnderCourse) emptyList() else courses,
                                linkedCourseIds = s.value.courseLinks[exercise.id].orEmpty(),
                                // A course chip toggles that one link; "none" removes every link
                                // this user may remove. The same exercise can sit in several courses.
                                onCourse = if ((manageAll && teacherId == null) || !canFileUnderCourse) null else { courseId ->
                                    val linked = s.value.courseLinks[exercise.id].orEmpty()
                                    vm.courseAct {
                                        when {
                                            courseId == null -> ExerciseRepository.setCourse(exercise.id, null)
                                            courseId in linked -> com.rork.pro.data.CourseContentRepository.unlink(courseId, exercise.id)
                                            else -> com.rork.pro.data.CourseContentRepository.link(courseId, listOf(exercise.id), null)
                                        }
                                    }
                                },
                                onLock = if (!teacherLike) null else { locked ->
                                    vm.act { ExerciseRepository.setLocked(exercise.id, locked) }
                                },
                                onResults = if (!teacherLike) {
                                    null
                                } else {
                                    { navController.navigate(TeacherRoutes.exerciseResults(exercise.id)) }
                                },
                                reorderControls = if (!reorderable) {
                                    null
                                } else {
                                    {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Where this exercise sits now — the order the student sees.
                                            Text(
                                                trf(StrEx.positionOf, index + 1, visible.size),
                                                color = Ink.TextMuted,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !busy && visible.size > 1) { positionPickerFor = exercise.id }
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                            )
                                            IconAction(
                                                Icons.Default.KeyboardDoubleArrowUp,
                                                tr(StrEx.moveToFirst),
                                                enabled = index > 0 && !busy,
                                            ) { vm.moveExercise(visible, index, 0) }
                                            IconAction(
                                                Icons.Default.KeyboardDoubleArrowDown,
                                                tr(StrEx.moveToLast),
                                                enabled = index < visible.lastIndex && !busy,
                                            ) { vm.moveExercise(visible, index, visible.lastIndex) }
                                            IconAction(
                                                Icons.Default.ArrowUpward,
                                                tr(StrStudio.moveUp),
                                                enabled = index > 0 && !busy,
                                            ) { vm.moveExercise(visible, index, index - 1) }
                                            IconAction(
                                                Icons.Default.ArrowDownward,
                                                tr(StrStudio.moveDown),
                                                enabled = index < visible.lastIndex && !busy,
                                            ) { vm.moveExercise(visible, index, index + 1) }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (copyOpen) {
        CopyOwnerExerciseDialog(
            offers = offers,
            busy = busy,
            note = copyNote,
            onRetry = { vm.loadOffers() },
            // The dialog stays open after a copy, so several exercises can be taken in one visit.
            onCopyExercise = { offer ->
                vm.act {
                    runCatching { ExerciseRepository.copyOwnerExercise(offer.id) }
                        .onSuccess { copyNote = tr(StrEx.copyDone) to false }
                        .onFailure { copyNote = it.toAppError().message to true }
                    vm.loadOffers(quiet = true)
                }
            },
            onCopyLevel = { sectionId ->
                vm.act {
                    runCatching { ExerciseRepository.copyOwnerLevel(sectionId) }
                        .onSuccess { n ->
                            copyNote = (if (n > 0) trf(StrEx.copyLevelDone, n) else tr(StrEx.copyLevelNothing)) to false
                        }
                        .onFailure { copyNote = it.toAppError().message to true }
                    vm.loadOffers(quiet = true)
                }
            },
            onDismiss = { copyOpen = false },
        )
    }

    confirmDelete?.let { target ->
        ConfirmDialog(
            title = tr(StrEx.deleteExercise),
            body = trf(StrEx.deleteExerciseBody, target.displayTitle),
            confirmLabel = tr(StrEx.delete),
            onConfirm = {
                vm.act { ExerciseRepository.delete(target.id) }
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

private enum class StudioFilter { ALL, PUBLISHED, DRAFT }

/** Route key for the group of exercises that aren't filed under any level. */
internal const val NO_LEVEL_KEY = "none"

/** One level on the front page: a button that opens that level's exercises on their own screen. */
@Composable
private fun LevelButton(title: String, exercises: Int, published: Int, onClick: () -> Unit) {
    val readiness = if (exercises > 0) published.toFloat() / exercises.toFloat() else 0f
    InkCard(borderColor = Ink.Amber.copy(alpha = 0.28f), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Ink.AmberSoft, Ink.TealSoft))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MenuBook, null, tint = Ink.Amber, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    trf(StrEx.levelButtonMeta, exercises, published),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (exercises > 0) {
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(Dimens.chipRadius))
                            .background(Ink.SurfaceHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(readiness.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(Dimens.chipRadius))
                                .background(Ink.Teal),
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Ink.TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The hero the teacher's exercise studio opens on — the three numbers that matter, plus a
 * publish-readiness bar, wrapped in a warm gradient banner instead of three flat tiles. This is
 * the screen's "hook": one glance says how much exists, how much is live, and how close the rest
 * is to being ready, before the teacher scrolls to a single exercise.
 */
@Composable
private fun StudioSummary(total: Int, published: Int, questions: Int) {
    val readiness = if (total > 0) published.toFloat() / total.toFloat() else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Brush.linearGradient(listOf(Ink.AmberSoft, Ink.TealSoft)))
            .border(1.dp, Ink.Amber.copy(alpha = 0.25f), RoundedCornerShape(Dimens.cardRadius))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Ink.Amber, Ink.AmberPressed))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.FitnessCenter, null, tint = Ink.OnAmber, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tr(StrEx.myExercises),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tr(StrEx.myExercisesSub),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(Icons.Default.Layers, total.toString(), tr(StrEx.summaryExercises), Ink.Sky, Modifier.weight(1f))
            SummaryTile(Icons.Default.CheckCircle, published.toString(), tr(StrEx.summaryPublished), Ink.Teal, Modifier.weight(1f))
            SummaryTile(Icons.Default.Quiz, questions.toString(), tr(StrEx.summaryQuestions), Ink.Amber, Modifier.weight(1f))
        }
        if (total > 0) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr(StrEx.readinessLabel), color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(
                    "${(readiness * 100).toInt()}%",
                    color = Ink.Teal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .background(Ink.Surface.copy(alpha = 0.6f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(readiness.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Dimens.chipRadius))
                        .background(Brush.horizontalGradient(listOf(Ink.Teal, Ink.Amber))),
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Surface.copy(alpha = 0.55f))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ink.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StudioFilters(
    query: String,
    onQuery: (String) -> Unit,
    filter: StudioFilter,
    onFilter: (StudioFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InkField(
            query,
            onQuery,
            tr(StrEx.searchExercises),
            trailing = {
                if (query.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, null, tint = Ink.TextMuted, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Icon(Icons.Default.Search, null, tint = Ink.TextMuted, modifier = Modifier.size(18.dp))
                }
            },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(Ink.SurfaceHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(tr(StrEx.filterAll), Icons.Default.Apps, filter == StudioFilter.ALL, Modifier.weight(1f)) {
                onFilter(StudioFilter.ALL)
            }
            FilterChip(tr(StrEx.filterPublished), Icons.Default.CheckCircle, filter == StudioFilter.PUBLISHED, Modifier.weight(1f)) {
                onFilter(StudioFilter.PUBLISHED)
            }
            FilterChip(tr(StrEx.filterDraft), Icons.Default.Edit, filter == StudioFilter.DRAFT, Modifier.weight(1f)) {
                onFilter(StudioFilter.DRAFT)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(if (selected) Ink.Amber else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Ink.OnAmber else Ink.TextMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (selected) Ink.OnAmber else Ink.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The entry point to creating an exercise — a gradient callout instead of a plain button, so it reads as an invitation rather than one more row of chrome. */
@Composable
private fun NewExerciseCallout(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Brush.linearGradient(listOf(Ink.Amber, Ink.AmberPressed)))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Ink.OnAmber.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, null, tint = Ink.OnAmber, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                tr(StrEx.newExercise),
                color = Ink.OnAmber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr(StrEx.newExerciseHint),
                color = Ink.OnAmber.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.OnAmber, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(Ink.SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ink.TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** Creating an exercise, opened deliberately instead of always sitting at the top of the list. */
@Composable
private fun ComposerPanel(
    title: String,
    onTitle: (String) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    count: String,
    onCount: (String) -> Unit,
    minutes: String,
    onMinutes: (String) -> Unit,
    sections: List<ExerciseSection>,
    sectionId: String?,
    levelPicked: Boolean,
    onSection: (String?) -> Unit,
    busy: Boolean,
    onClose: () -> Unit,
    onCreate: () -> Unit,
) {
    // With levels to choose from, the teacher must pick one (or "no level" on purpose) — the
    // level decides where students find the exercise, so it shouldn't be left to chance.
    val needsLevel = sections.isNotEmpty() && !levelPicked
    InkCard(borderColor = Ink.Amber.copy(alpha = 0.45f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(StrEx.newExercise),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                tr(StrEx.close),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onClose() }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        InkField(title, onTitle, tr(StrEx.titleField))
        Spacer(Modifier.height(8.dp))
        InkField(description, onDescription, tr(StrEx.descriptionField), singleLine = false, minLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                InkField(count, onCount, tr(StrEx.questionsField), keyboardType = KeyboardType.Number)
            }
            Box(Modifier.weight(1f)) {
                InkField(minutes, onMinutes, tr(StrEx.minutesField), keyboardType = KeyboardType.Number)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(tr(StrEx.chooseLevelForNew), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (sections.isEmpty()) {
            Text(tr(StrEx.noLevelsForNew), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(tr(StrEx.chooseLevelForNewHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections, key = { it.id }) { section ->
                    val chosen = levelPicked && sectionId == section.id
                    Pill(
                        section.title,
                        Modifier.clickable { onSection(section.id) },
                        background = if (chosen) Ink.AmberSoft else Ink.SurfaceHigh,
                        foreground = if (chosen) Ink.Amber else Ink.TextMuted,
                    )
                }
                item {
                    val chosen = levelPicked && sectionId == null
                    Pill(
                        tr(StrEx.noLevel),
                        Modifier.clickable { onSection(null) },
                        background = if (chosen) Ink.AmberSoft else Ink.SurfaceHigh,
                        foreground = if (chosen) Ink.Amber else Ink.TextMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryAction(
            tr(StrEx.createExercise),
            enabled = title.isNotBlank() && !needsLevel,
            loading = busy,
        ) { onCreate() }
        if (needsLevel && title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tr(StrEx.pickLevelFirst), color = Ink.Amber, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * One exercise. Carries its own status colour down the leading edge, the three numbers that
 * matter as chips rather than a key/value table, and a readiness meter showing how full the
 * question bank is against what an attempt asks for — the thing that actually decides whether
 * this exercise can go live.
 */
@Composable
private fun ExerciseCard(
    exercise: PlacementTest,
    questionCount: Int,
    showOwner: Boolean,
    busy: Boolean,
    editing: Boolean,
    questionsOpen: Boolean,
    questions: List<TestQuestionRow>,
    onToggleEdit: () -> Unit,
    onToggleQuestions: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit,
    onStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onAddQuestion: (QuestionDraft) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onUpdateQuestion: (String, QuestionDraft) -> Unit,
    onToggleQuestionActive: ((String, Boolean) -> Unit)? = null,
    reorderControls: (@Composable () -> Unit)? = null,
    onResults: (() -> Unit)? = null,
    /** Levels this teacher owns, for the "file under" picker. Empty hides the picker entirely. */
    sections: List<ExerciseSection> = emptyList(),
    onSection: ((String?) -> Unit)? = null,
    /** Courses this exercise could be linked to — an additional filing option alongside [sections]. */
    courses: List<Course> = emptyList(),
    linkedCourseIds: Set<String> = emptySet(),
    onCourse: ((String?) -> Unit)? = null,
    onLock: ((Boolean) -> Unit)? = null,
    /** The same teacher's other exercises a question can be moved into. */
    moveTargets: List<PlacementTest> = emptyList(),
    onMoveQuestion: ((questionId: String, targetExerciseId: String) -> Unit)? = null,
    /** Copies the web link for this exercise; only offered once it is published. */
    onCopyLink: (() -> Unit)? = null,
    /** Owner/admin only: copy this exercise into their own account (null hides the button). */
    onCopyToMine: (() -> Unit)? = null,
) {
    val published = exercise.status == "PUBLISHED"
    val accent = if (published) Ink.Teal else Ink.Amber
    val ready = questionCount >= exercise.questionCount

    InkCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 120.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (published) Ink.TealSoft else Ink.AmberSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Quiz, null, tint = accent, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            exercise.displayTitle,
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showOwner && !exercise.owner?.fullName.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                trf(StrEx.by, exercise.owner?.fullName.orEmpty()),
                                color = Ink.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(exercise.status)
                        if (exercise.isLocked) {
                            Spacer(Modifier.height(6.dp))
                            Pill(
                                tr(StrEx.lockedChip),
                                background = Ink.CoralSoft,
                                foreground = Ink.Coral,
                            )
                        }
                        if (onCopyLink != null && published) {
                            Spacer(Modifier.height(2.dp))
                            androidx.compose.material3.IconButton(
                                onClick = onCopyLink,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = tr(StrExLink.copyOne),
                                    tint = Ink.Amber,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(Icons.Default.Quiz, trf(StrEx.chipQuestions, questionCount))
                    MetaChip(Icons.Default.Layers, trf(StrEx.chipPerAttempt, exercise.questionCount))
                    MetaChip(Icons.Default.Timer, trf(StrEx.chipMinutes, exercise.timeLimitSeconds / 60))
                }

                Spacer(Modifier.height(12.dp))
                ReadinessMeter(have = questionCount, need = exercise.questionCount, ready = ready)

                if (reorderControls != null) {
                    Spacer(Modifier.height(10.dp))
                    reorderControls()
                }

                Spacer(Modifier.height(14.dp))

                if (editing) {
                    var title by remember(exercise.id) { mutableStateOf(exercise.title) }
                    var description by remember(exercise.id) { mutableStateOf(exercise.description.orEmpty()) }
                    var count by remember(exercise.id) { mutableStateOf(exercise.questionCount.toString()) }
                    var minutes by remember(exercise.id) { mutableStateOf((exercise.timeLimitSeconds / 60).toString()) }

                    Divider()
                    Spacer(Modifier.height(12.dp))
                    InkField(title, { title = it }, tr(StrEx.titleField))
                    Spacer(Modifier.height(8.dp))
                    InkField(description, { description = it }, tr(StrEx.descriptionField), singleLine = false, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            InkField(
                                count,
                                { count = it.filter(Char::isDigit) },
                                tr(StrEx.questionsField),
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            InkField(
                                minutes,
                                { minutes = it.filter(Char::isDigit) },
                                tr(StrEx.minutesField),
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryAction(tr(StrEx.saveChanges), enabled = title.isNotBlank(), loading = busy) {
                        onSave(
                            title,
                            description,
                            count.toIntOrNull() ?: exercise.questionCount,
                            minutes.toIntOrNull() ?: (exercise.timeLimitSeconds / 60),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(tr(StrEx.close), Modifier.fillMaxWidth()) { onToggleEdit() }
                } else {
                    if (onSection != null && sections.isNotEmpty()) {
                        Text(tr(StrEx.fileUnderLevel), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                val none = exercise.sectionId == null
                                Pill(
                                    tr(StrEx.noLevel),
                                    Modifier.clickable { onSection(null) },
                                    background = if (none) Ink.AmberSoft else Ink.SurfaceHigh,
                                    foreground = if (none) Ink.Amber else Ink.TextMuted,
                                )
                            }
                            items(sections) { section ->
                                val chosen = exercise.sectionId == section.id
                                Pill(
                                    section.title,
                                    Modifier.clickable { onSection(section.id) },
                                    background = if (chosen) Ink.AmberSoft else Ink.SurfaceHigh,
                                    foreground = if (chosen) Ink.Amber else Ink.TextMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (onCourse != null && courses.isNotEmpty()) {
                        Text(tr(com.rork.pro.ui.i18n.StrContent.linkedCourses), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                val none = linkedCourseIds.isEmpty()
                                Pill(
                                    tr(StrEx.noCourseLink),
                                    Modifier.clickable { onCourse(null) },
                                    background = if (none) Ink.TealSoft else Ink.SurfaceHigh,
                                    foreground = if (none) Ink.Teal else Ink.TextMuted,
                                )
                            }
                            items(courses) { course ->
                                val chosen = course.id in linkedCourseIds
                                Pill(
                                    course.displayTitle,
                                    Modifier.clickable { onCourse(course.id) },
                                    background = if (chosen) Ink.TealSoft else Ink.SurfaceHigh,
                                    foreground = if (chosen) Ink.Teal else Ink.TextMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // One clear primary action (the bank is where the work happens), the rest
                    // secondary — instead of four buttons all shouting equally.
                    PrimaryAction(
                        if (questionsOpen) tr(StrEx.hideQuestions) else tr(StrEx.questions),
                        Modifier.fillMaxWidth(),
                    ) { onToggleQuestions() }
                    if (onCopyToMine != null) {
                        Spacer(Modifier.height(8.dp))
                        AdminButton(tr(StrEx.copyToMine), Modifier.fillMaxWidth(), Icons.Default.ContentCopy, AdminTone.Success, enabled = !busy) {
                            onCopyToMine()
                        }
                    }
                    if (onResults != null) {
                        Spacer(Modifier.height(8.dp))
                        AdminButton(tr(StrEx.results), Modifier.fillMaxWidth(), Icons.Default.BarChart, AdminTone.Info) {
                            onResults()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AdminActions {
                        AdminButton(tr(StrEx.edit), Modifier.weight(1f), Icons.Default.Edit, AdminTone.Neutral) {
                            onToggleEdit()
                        }
                        AdminButton(
                            if (published) tr(StrEx.unpublish) else tr(StrEx.publish),
                            Modifier.weight(1f),
                            if (published) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (published) AdminTone.Neutral else AdminTone.Success,
                            enabled = published || ready,
                        ) {
                            onStatus(if (published) "UNPUBLISHED" else "PUBLISHED")
                        }
                        AdminButton(tr(StrEx.delete), Modifier.weight(1f), Icons.Default.Delete, AdminTone.Danger) {
                            onDelete()
                        }
                    }
                    if (onLock != null) {
                        Spacer(Modifier.height(8.dp))
                        AdminButton(
                            if (exercise.isLocked) tr(StrEx.unlockExercise) else tr(StrEx.lockExercise),
                            Modifier.fillMaxWidth(),
                            if (exercise.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            if (exercise.isLocked) AdminTone.Success else AdminTone.Neutral,
                            // Disabled while a request is in flight: without this, a second tap
                            // before the refreshed state arrives reads the same stale isLocked
                            // value and re-sends the same toggle, so quick double-taps flip the
                            // lock and immediately flip it back — looking like the button does
                            // nothing.
                            enabled = !busy,
                        ) { onLock(!exercise.isLocked) }
                    }
                }

                if (questionsOpen && !editing) {
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(14.dp))
                    SectionHeader(tr(StrEx.questionBank))
                    Spacer(Modifier.height(10.dp))

                    var editingQuestionId by remember { mutableStateOf<String?>(null) }
                    var movingQuestion by remember { mutableStateOf<TestQuestionRow?>(null) }
                    questions.forEach { question ->
                        if (editingQuestionId == question.id) {
                            QuestionWizard(
                                wizardKey = question.id,
                                initial = question,
                                busy = busy,
                                submitLabel = tr(StrEx.saveChanges),
                                onSubmit = { draft ->
                                    onUpdateQuestion(question.id, draft)
                                    editingQuestionId = null
                                },
                                onCancel = { editingQuestionId = null },
                            )
                        } else {
                            QuestionRow(
                                question = question,
                                onEdit = { editingQuestionId = question.id },
                                onDelete = { onDeleteQuestion(question.id) },
                                onMove = if (onMoveQuestion == null) null else { { movingQuestion = question } },
                                onToggleActive = if (onToggleQuestionActive == null) {
                                    null
                                } else {
                                    { onToggleQuestionActive(question.id, !question.isActive) }
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    movingQuestion?.let { question ->
                        MoveQuestionDialog(
                            targets = moveTargets,
                            sections = sections,
                            // Moving out of a live exercise can leave it short of questions.
                            warnShort = published && questionCount - 1 < exercise.questionCount,
                            onPick = { target ->
                                onMoveQuestion?.invoke(question.id, target.id)
                                movingQuestion = null
                            },
                            onDismiss = { movingQuestion = null },
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    QuestionWizard(
                        wizardKey = exercise.id,
                        initial = null,
                        busy = busy,
                        submitLabel = tr(StrEx.addQuestion),
                        onSubmit = onAddQuestion,
                        onCancel = null,
                    )
                }
            }
        }
    }
}

/** A dedicated question workspace: the question button opens here instead of expanding a long card inline. */
data class QuestionBankScreenData(
    val exercise: PlacementTest,
    val questions: List<TestQuestionRow>,
)

class QuestionBankViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<QuestionBankScreenData>>(Async.Loading)
    val state: StateFlow<Async<QuestionBankScreenData>> = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun load(exerciseId: String, manageAll: Boolean) {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching {
                val exercises = if (manageAll) ExerciseRepository.all() else ExerciseRepository.mine()
                val exercise = exercises.firstOrNull { it.id == exerciseId } ?: error("EXERCISE_NOT_FOUND")
                QuestionBankScreenData(exercise, ExerciseRepository.questions(exerciseId))
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    fun act(exerciseId: String, manageAll: Boolean, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
            _busy.value = false
            load(exerciseId, manageAll)
        }
    }
}

@Composable
fun ExerciseQuestionsScreen(navController: NavHostController, exerciseId: String, manageAll: Boolean) {
    val vm: QuestionBankViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editingQuestionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exerciseId, manageAll) { vm.load(exerciseId, manageAll) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        when (val screen = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(screen.error, Modifier.fillMaxSize()) {
                vm.load(exerciseId, manageAll)
            }
            is Async.Success -> {
                val exercise = screen.value.exercise
                val questions = screen.value.questions
                DetailHeader(exercise.displayTitle, onBack = { navController.popBackStack() })
                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.cardRadius))
                                .background(Brush.linearGradient(listOf(Ink.AmberSoft, Ink.TealSoft)))
                                .border(1.dp, Ink.Amber.copy(alpha = 0.3f), RoundedCornerShape(Dimens.cardRadius))
                                .padding(20.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Ink.Amber, Ink.AmberPressed))),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.Quiz, null, tint = Ink.OnAmber, modifier = Modifier.size(26.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(tr(StrEx.questionStudioTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(3.dp))
                                    Text(tr(StrEx.questionStudioSubtitle), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryTile(Icons.Default.Quiz, questions.size.toString(), tr(StrEx.questions), Ink.Amber, Modifier.weight(1f))
                                SummaryTile(Icons.Default.Layers, exercise.questionCount.toString(), tr(StrEx.questionsField), Ink.Sky, Modifier.weight(1f))
                                SummaryTile(Icons.Default.Timer, (exercise.timeLimitSeconds / 60).toString(), tr(StrEx.minutesField), Ink.Teal, Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Column {
                            Text(tr(StrEx.questionBankHook), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(tr(StrEx.questionBankHookBody), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (questions.isEmpty()) {
                        item { EmptyBlock(tr(StrEx.noQuestions), tr(StrEx.questionBankHookBody)) }
                    }
                    items(questions, key = { it.id }) { question ->
                        if (editingQuestionId == question.id) {
                            QuestionWizard(
                                wizardKey = question.id,
                                initial = question,
                                busy = busy,
                                submitLabel = tr(StrEx.saveChanges),
                                onSubmit = { draft ->
                                    vm.act(exerciseId, manageAll) {
                                        ExerciseRepository.updateQuestion(
                                            id = question.id,
                                            kind = draft.kind,
                                            skill = draft.skill,
                                            prompt = draft.prompt,
                                            options = draft.options,
                                            correctIndexes = draft.correctIndexes,
                                            correctText = draft.correctText,
                                            explanation = draft.explanation,
                                            imageUrl = draft.imageUrl,
                                            audioUrl = draft.audioUrl,
                                            videoUrl = draft.videoUrl,
                                        )
                                    }
                                    editingQuestionId = null
                                },
                                onCancel = { editingQuestionId = null },
                            )
                        } else {
                            QuestionRow(
                                question = question,
                                onEdit = { editingQuestionId = question.id },
                                onDelete = { vm.act(exerciseId, manageAll) { ExerciseRepository.deleteQuestion(question.id) } },
                                onToggleActive = { vm.act(exerciseId, manageAll) { ExerciseRepository.setQuestionActive(question.id, !question.isActive) } },
                            )
                        }
                    }
                    item {
                        SectionHeader(tr(StrEx.addQuestion))
                        Spacer(Modifier.height(4.dp))
                        QuestionWizard(
                            wizardKey = "$exerciseId-new",
                            initial = null,
                            busy = busy,
                            submitLabel = tr(StrEx.addQuestion),
                            onSubmit = { draft ->
                                vm.act(exerciseId, manageAll) {
                                    ExerciseRepository.addQuestion(
                                        exerciseId = exerciseId,
                                        kind = draft.kind,
                                        skill = draft.skill,
                                        prompt = draft.prompt,
                                        options = draft.options,
                                        correctIndexes = draft.correctIndexes,
                                        correctText = draft.correctText,
                                        explanation = draft.explanation,
                                        imageUrl = draft.imageUrl,
                                        audioUrl = draft.audioUrl,
                                        videoUrl = draft.videoUrl,
                                        sortOrder = questions.size,
                                    )
                                }
                            },
                            onCancel = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * How full the question bank is against what one attempt draws. This is the number that decides
 * whether the exercise can be published, so it gets a bar rather than a line of text buried in
 * a table.
 */
@Composable
private fun ReadinessMeter(have: Int, need: Int, ready: Boolean) {
    val fraction = if (need <= 0) 1f else (have.toFloat() / need).coerceIn(0f, 1f)
    val color = if (ready) Ink.Teal else Ink.Amber
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (ready) tr(StrEx.readyToPublish) else trf(StrEx.needsMore, (need - have).coerceAtLeast(0)),
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
            Text("$have / $need", color = Ink.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.SurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

/** A question in the bank — kind and skill read at a glance, actions kept quiet. */
@Composable
private fun QuestionRow(
    question: TestQuestionRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)? = null,
    onToggleActive: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.SurfaceHigh)
            .padding(12.dp),
    ) {
        Text(
            question.prompt,
            // A locked question stays in the bank but a student never sees it, so it's dimmed
            // here rather than hidden — the teacher should still be able to find and unlock it.
            color = if (question.isActive) Ink.TextPrimary else Ink.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                codeLabel(question.kind),
                background = Ink.Surface,
                foreground = Ink.TextMuted,
            )
            Spacer(Modifier.width(6.dp))
            Pill(codeLabel(question.skill), background = Ink.Surface, foreground = Ink.TextMuted)
            Spacer(Modifier.weight(1f))
            if (onMove != null) {
                IconAction(Icons.Default.SwapHoriz, tr(StrEx.moveQuestion), tint = Ink.Teal) { onMove() }
            }
            if (onToggleActive != null) {
                IconAction(
                    if (question.isActive) Icons.Default.LockOpen else Icons.Default.Lock,
                    if (question.isActive) tr(StrEx.lockQuestion) else tr(StrEx.unlockQuestion),
                    tint = if (question.isActive) Ink.TextMuted else Ink.Teal,
                ) { onToggleActive() }
            }
            IconAction(Icons.Default.Edit, tr(StrEx.edit), tint = Ink.Sky) { onEdit() }
            IconAction(Icons.Default.Delete, tr(StrEx.delete), tint = Ink.Coral) { onDelete() }
        }
    }
}

/**
 * Creating and ordering the levels a teacher files exercises under. Collapsed by default: most
 * visits to this screen are about exercises, not about reshaping the levels themselves.
 *
 * Deleting a level never deletes its exercises — the database clears their link and they fall
 * back to the unfiled group — so this doesn't ask for a scary confirmation it doesn't need.
 */
@Composable
private fun LevelsPanel(
    sections: List<ExerciseSection>,
    busy: Boolean,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }

    InkCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.MenuBook, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                tr(StrEx.levels),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (sections.isEmpty()) tr(StrEx.noLevelsYet) else sections.size.toString(),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = Ink.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        if (open) {
            Spacer(Modifier.height(12.dp))
            sections.forEachIndexed { index, section ->
                if (renaming == section.id) {
                    var draft by remember(section.id) { mutableStateOf(section.title) }
                    InkField(draft, { draft = it }, tr(StrEx.levelName))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryAction(tr(StrEx.saveChanges), Modifier.weight(1f), enabled = draft.isNotBlank(), loading = busy) {
                            onRename(section.id, draft)
                            renaming = null
                        }
                        SecondaryAction(tr(StrEx.close), Modifier.weight(1f)) { renaming = null }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Ink.SurfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            section.title,
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = index > 0 && !busy) {
                            onMove(index, index - 1)
                        }
                        IconAction(
                            Icons.Default.ArrowDownward,
                            tr(StrStudio.moveDown),
                            enabled = index < sections.lastIndex && !busy,
                        ) { onMove(index, index + 1) }
                        IconAction(Icons.Default.Edit, tr(StrEx.edit), tint = Ink.Sky) { renaming = section.id }
                        IconAction(Icons.Default.Delete, tr(StrEx.delete), tint = Ink.Coral) { onDelete(section.id) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            InkField(newName, { newName = it }, tr(StrEx.levelName))
            Spacer(Modifier.height(8.dp))
            PrimaryAction(tr(StrEx.addLevel), Modifier.fillMaxWidth(), enabled = newName.isNotBlank(), loading = busy) {
                onCreate(newName)
                newName = ""
            }
        }
    }
}

/**
 * Picks the exercise a question moves into. Each target shows its level so a teacher with
 * several exercises of the same name can still tell them apart.
 */
@Composable
private fun MoveQuestionDialog(
    targets: List<PlacementTest>,
    sections: List<ExerciseSection>,
    warnShort: Boolean,
    onPick: (PlacementTest) -> Unit,
    onDismiss: () -> Unit,
) {
    val levelNames = remember(sections) { sections.associate { it.id to it.title } }
    // Same order the teacher sees their levels in, unfiled last.
    val levelOrder = remember(sections) { sections.withIndex().associate { it.value.id to it.index } }
    val ordered = remember(targets, levelOrder) {
        targets.sortedWith(
            compareBy<PlacementTest>({ levelOrder[it.sectionId] ?: Int.MAX_VALUE }, { it.sortOrder }),
        )
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(StrEx.moveQuestionTitle), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    if (ordered.isEmpty()) tr(StrEx.moveNoTargets) else tr(StrEx.moveQuestionBody),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (warnShort && ordered.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(StrEx.moveDropsBelow), color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
                if (ordered.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ordered, key = { it.id }) { target ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Ink.SurfaceHigh)
                                    .clickable { onPick(target) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    target.displayTitle,
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Staff managing another teacher's work don't load that
                                // teacher's levels, so the line is skipped rather than wrong.
                                val level = when {
                                    target.sectionId != null -> levelNames[target.sectionId]
                                    sections.isNotEmpty() -> tr(StrEx.noLevel)
                                    else -> null
                                }
                                if (level != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        trf(StrEx.moveInLevel, level),
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(tr(StrEx.close), color = Ink.TextSecondary)
            }
        },
    )
}

/** One of the owner's levels as the teacher sees it: its exercises, and how many are still not copied. */
private class OfferLevel(val sectionId: String?, val title: String, val offers: List<OwnerExerciseOffer>) {
    val key: String get() = sectionId ?: ""
    val remaining: Int get() = offers.count { !it.alreadyCopied }
}

/** The server orders by the owner's level order with unfiled last; grouping keeps that order. */
private fun offerLevels(offers: List<OwnerExerciseOffer>): List<OfferLevel> =
    offers.groupBy { it.sectionId }.map { (id, list) ->
        OfferLevel(id, list.first().sectionTitle?.takeIf { it.isNotBlank() } ?: tr(StrEx.copyUnfiled), list)
    }

/**
 * The owner's published exercises, by level. A teacher can copy a whole level in one tap, or open
 * a level and copy only the exercises they want. Nothing else can appear here: the list comes from
 * `owner_exercises_available_to_copy`, which only ever returns exercises authored by the owner.
 */
@Composable
private fun CopyOwnerExerciseDialog(
    offers: Async<List<OwnerExerciseOffer>>,
    busy: Boolean,
    note: Pair<String, Boolean>?,
    onRetry: () -> Unit,
    onCopyExercise: (OwnerExerciseOffer) -> Unit,
    onCopyLevel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = the list of levels; otherwise the key of the level being browsed.
    var openKey by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(StrEx.copyFromOwnerTitle), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                when (offers) {
                    is Async.Loading -> LoadingBlock(Modifier.fillMaxWidth())
                    is Async.Failure -> ErrorBlock(offers.error, Modifier.fillMaxWidth()) { onRetry() }
                    is Async.Success -> {
                        val levels = remember(offers.value) { offerLevels(offers.value) }
                        val open = levels.firstOrNull { it.key == openKey }
                        if (offers.value.isEmpty()) {
                            Text(tr(StrEx.copyFromOwnerEmpty), style = MaterialTheme.typography.bodyMedium)
                        } else if (open == null) {
                            Text(tr(StrEx.copyFromOwnerBody), style = MaterialTheme.typography.bodyMedium)
                            note?.let { (text, isError) ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text,
                                    color = if (isError) Ink.Coral else Ink.Teal,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(levels, key = { it.key }) { level ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Ink.SurfaceHigh)
                                            .clickable { openKey = level.key }
                                            .padding(12.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    level.title,
                                                    color = Ink.TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    trf(StrEx.copyLevelExercises, level.offers.size) +
                                                        if (level.remaining == 0) " · " + tr(StrEx.copyLevelAllCopied) else "",
                                                    color = if (level.remaining == 0) Ink.Teal else Ink.TextMuted,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
                                        }
                                        // The unfiled group has no level to copy as a whole.
                                        if (level.sectionId != null && level.remaining > 0) {
                                            Spacer(Modifier.height(8.dp))
                                            AdminButton(
                                                trf(StrEx.copyLevelAll, level.remaining),
                                                Modifier.fillMaxWidth(),
                                                Icons.Default.ContentCopy,
                                                AdminTone.Info,
                                                enabled = !busy,
                                            ) { onCopyLevel(level.sectionId) }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { openKey = null }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink.TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(StrEx.copyLevelBack), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(open.title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            note?.let { (text, isError) ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text,
                                    color = if (isError) Ink.Coral else Ink.Teal,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (open.sectionId != null && open.remaining > 0) {
                                Spacer(Modifier.height(10.dp))
                                AdminButton(
                                    trf(StrEx.copyLevelAll, open.remaining),
                                    Modifier.fillMaxWidth(),
                                    Icons.Default.ContentCopy,
                                    AdminTone.Info,
                                    enabled = !busy,
                                ) { onCopyLevel(open.sectionId) }
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(open.offers, key = { it.id }) { offer ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Ink.SurfaceHigh)
                                            // A copied exercise is not copied twice: its row is just marked.
                                            .clickable(enabled = !busy && !offer.alreadyCopied) { onCopyExercise(offer) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                offer.displayTitle,
                                                color = Ink.TextPrimary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                trf(StrEx.copyQuestionsCount, offer.questionsTotal) +
                                                    if (offer.alreadyCopied) " · " + tr(StrEx.copiedBefore) else "",
                                                color = if (offer.alreadyCopied) Ink.Teal else Ink.TextMuted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            if (offer.alreadyCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = if (offer.alreadyCopied) tr(StrEx.copiedBefore) else tr(StrEx.copyToMine),
                                            tint = if (offer.alreadyCopied) Ink.Teal else Ink.TextSecondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(tr(StrEx.close), color = Ink.TextSecondary)
            }
        },
    )
}
