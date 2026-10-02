package com.rork.pro.ui.screens.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.rork.pro.data.CourseContentRepository
import com.rork.pro.data.CourseExerciseItem
import com.rork.pro.data.CourseExercisePanel
import com.rork.pro.data.LessonBlock
import com.rork.pro.data.LinkableExercise
import com.rork.pro.ui.i18n.StrContent
import com.rork.pro.ui.i18n.trf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.Category
import com.rork.pro.data.CourseDraft
import com.rork.pro.data.CourseSection
import com.rork.pro.data.CourseStudioRepository
import com.rork.pro.data.Lesson
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.i18n.StrCourses
import com.rork.pro.ui.i18n.StrStudio
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.i18n.levelLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
private val LESSON_KINDS = listOf("VIDEO", "DOCUMENT", "TEXT", "QUIZ")

/** The editable half of a course; the curriculum is saved row by row as it is edited. */
data class CourseForm(
    val title: String,
    val subtitle: String,
    val description: String,
    val thumbnailUrl: String,
    val level: String,
    val categoryId: String?,
    val price: Double,
    /** Optional monthly-subscription price; null (or a free course) means not offered. */
    val monthlyPrice: Double? = null,
    /** Sold only by monthly subscription (requires [monthlyPrice]). */
    val monthlyOnly: Boolean = false,
    val currency: String,
    val isFree: Boolean,
    val certificateEnabled: Boolean,
)

class CourseEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<CourseDraft>>(Async.Loading)
    val state: StateFlow<Async<CourseDraft>> = _state.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _savedTick = MutableStateFlow(0)
    val savedTick: StateFlow<Int> = _savedTick.asStateFlow()

    /** False when the owner requires review before a teacher's course can go live. */
    private val _directPublish = MutableStateFlow(true)
    val directPublish: StateFlow<Boolean> = _directPublish.asStateFlow()

    private var courseId: String = ""
    private var bound = false

    /** Exercises linked into this course, and whether the caller may link / link any. */
    private val _panel = MutableStateFlow(CourseExercisePanel())
    val panel: StateFlow<CourseExercisePanel> = _panel.asStateFlow()

    private val _linkable = MutableStateFlow<Async<List<LinkableExercise>>>(Async.Loading)
    val linkable: StateFlow<Async<List<LinkableExercise>>> = _linkable.asStateFlow()

    private fun loadPanel() {
        viewModelScope.launch {
            runCatching { CourseContentRepository.panel(courseId) }.onSuccess { _panel.value = it }
        }
    }

    fun loadLinkable(all: Boolean, search: String) {
        viewModelScope.launch {
            _linkable.value = Async.Loading
            runCatching { CourseContentRepository.linkable(courseId, all, search) }
                .onSuccess { _linkable.value = Async.Success(it) }
                .onFailure { _linkable.value = Async.Failure(it.toAppError()) }
        }
    }

    fun linkExercises(ids: List<String>, sectionId: String?) = act {
        CourseContentRepository.link(courseId, ids, sectionId)
    }

    fun unlinkExercise(exerciseId: String) = act { CourseContentRepository.unlink(courseId, exerciseId) }

    /** Moves an exercise inside its own unit; the rest of the course keeps its order. */
    fun moveExercise(group: List<CourseExerciseItem>, from: Int, to: Int) {
        if (from !in group.indices || to !in group.indices) return
        val reordered = ArrayDeque(group.toMutableList().apply { add(to, removeAt(from)) })
        val ids = _panel.value.items.sortedBy { it.sortOrder }.map { item ->
            if (item in group) reordered.removeFirst().exerciseId else item.exerciseId
        }
        act { CourseContentRepository.reorder(courseId, ids) }
    }

    fun bind(id: String) {
        if (bound && courseId == id) return
        courseId = id
        bound = true
        load()
        viewModelScope.launch {
            runCatching { CatalogRepository.categories() }.onSuccess { _categories.value = it }
            runCatching { CatalogRepository.settings() }.onSuccess { settings ->
                _directPublish.value = settings["courses.teacher_direct_publish"] != "false"
            }
        }
    }

    /** Adds a category the teacher typed themselves, then refreshes the list so it shows
     * immediately in the picker (and, once the course is saved, to students browsing courses). */
    fun createCategory(name: String, onCreated: (String) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching { TeacherRepository.createCategory(trimmed) }
                .onSuccess { category ->
                    runCatching { CatalogRepository.categories() }.onSuccess { _categories.value = it }
                    onCreated(category.id)
                }
                .onFailure { _error.value = it.toAppError() }
        }
    }

    fun load() {
        viewModelScope.launch {
            runCatching { CourseStudioRepository.draft(courseId) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
        loadPanel()
    }

    /** Saves the detail form, optionally moving the course to a new status in the same action. */
    fun save(form: CourseForm, thenStatus: String? = null) {
        act {
            CourseStudioRepository.saveCourse(
                id = courseId,
                title = form.title.trim(),
                subtitle = form.subtitle.trim(),
                description = form.description.trim(),
                thumbnailUrl = form.thumbnailUrl.trim(),
                level = form.level.trim(),
                categoryId = form.categoryId,
                price = form.price,
                monthlyPrice = form.monthlyPrice,
                monthlyOnly = form.monthlyOnly,
                currency = form.currency.trim(),
                isFree = form.isFree,
                certificateEnabled = form.certificateEnabled,
            )
            if (thenStatus != null) CourseStudioRepository.setStatus(courseId, thenStatus)
            _savedTick.value += 1
        }
    }

    fun deleteCourse(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { CourseStudioRepository.deleteCourse(courseId) }
                .onSuccess { onDeleted() }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
        }
    }

    /** Force-delete a course even with enrolled students. Owner/admin only. */
    fun deleteCourseForce(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { CourseStudioRepository.deleteCourseForce(courseId) }
                .onSuccess { onDeleted() }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
        }
    }

    fun addSection(title: String, count: Int) = act {
        CourseStudioRepository.addSection(courseId, title, count)
        CourseStudioRepository.resequence(courseId)
    }

    fun renameSection(id: String, title: String) = act { CourseStudioRepository.renameSection(id, title) }

    fun deleteSection(id: String) = act {
        CourseStudioRepository.deleteSection(id)
        CourseStudioRepository.resequence(courseId)
    }

    fun moveSection(sections: List<CourseSection>, from: Int, to: Int) {
        if (to !in sections.indices) return
        val reordered = sections.toMutableList().apply { add(to, removeAt(from)) }
        act {
            CourseStudioRepository.reorderSections(reordered)
            CourseStudioRepository.resequence(courseId)
        }
    }

    /** Moves a lesson inside its own group, then renumbers the course so playback order follows. */
    fun moveLesson(draft: CourseDraft, sectionId: String?, lessons: List<Lesson>, from: Int, to: Int) {
        if (from !in lessons.indices || to !in lessons.indices) return
        val reordered = lessons.toMutableList().apply { add(to, removeAt(from)) }
        val full = buildList {
            draft.sections.forEach { section ->
                addAll(if (section.id == sectionId) reordered else draft.lessonsIn(section.id))
            }
            addAll(if (sectionId == null) reordered else draft.looseLessons)
        }
        act { CourseStudioRepository.reorderLessons(full) }
    }

    fun saveLesson(
        id: String?,
        sectionId: String?,
        title: String,
        kind: String,
        videoUrl: String,
        documentUrl: String,
        content: String,
        minutes: Int,
        sortOrder: Int,
        isPreview: Boolean,
        blocks: List<LessonBlock>? = null,
        allowPdf: Boolean = true,
        allowTxt: Boolean = true,
    ) = act {
        CourseStudioRepository.saveLesson(
            id = id,
            courseId = courseId,
            sectionId = sectionId,
            title = title.trim(),
            kind = kind,
            videoUrl = videoUrl.trim(),
            documentUrl = documentUrl.trim(),
            content = content.trim(),
            durationSeconds = minutes * 60,
            sortOrder = sortOrder,
            isPreview = isPreview,
            blocks = blocks,
            allowPdf = allowPdf,
            allowTxt = allowTxt,
        )
        CourseStudioRepository.resequence(courseId)
    }

    fun deleteLesson(id: String) = act {
        CourseStudioRepository.deleteLesson(id)
        CourseStudioRepository.resequence(courseId)
    }

    /** Reads the picked file and uploads it, handing the public URL back to the form. */
    fun upload(context: android.content.Context, uri: Uri, folder: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _uploading.value = true
            _error.value = null
            runCatching {
                val file = MediaRepository.read(context, uri)
                MediaRepository.upload(file, folder)
            }.onSuccess { onDone(it) }
                .onFailure { _error.value = it.toAppError() }
            _uploading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.toAppError() }
            _busy.value = false
            load()
        }
    }
}

/**
 * One editor for both dashboards. What the caller may do is decided by the database:
 * teachers reach their own courses, `courses.manage` holders reach every course.
 */
@Composable
fun CourseEditorScreen(navController: NavHostController, session: SessionViewModel, courseId: String) {
    val vm: CourseEditorViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val directPublish by vm.directPublish.collectAsStateWithLifecycle()
    val savedTick by vm.savedTick.collectAsStateWithLifecycle()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val linkable by vm.linkable.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(courseId) { vm.bind(courseId) }

    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var thumb by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var price by remember { mutableStateOf("") }
    var monthlyPrice by remember { mutableStateOf("") }
    var monthlyOnly by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf("EGP") }
    var isFree by remember { mutableStateOf(false) }
    var certificate by remember { mutableStateOf(true) }
    var seeded by remember { mutableStateOf(false) }

    var lessonSheet by remember { mutableStateOf<LessonTarget?>(null) }
    var linkSheet by remember { mutableStateOf<LinkTarget?>(null) }
    var sectionDialog by remember { mutableStateOf<SectionDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmForceDelete by remember { mutableStateOf(false) }

    val pendingPick = remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingPick.value?.invoke(it) }
        pendingPick.value = null
    }

    val draft = (state as? Async.Success)?.value
    LaunchedEffect(draft?.course?.id) {
        val course = draft?.course ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        title = course.title
        subtitle = course.subtitle.orEmpty()
        description = course.description.orEmpty()
        thumb = course.thumbnailUrl.orEmpty()
        level = course.level.orEmpty()
        categoryId = course.categoryId
        // A subscription-only course mirrors its monthly price into base_price; that is not a one-time price.
        monthlyOnly = course.monthlyOnly
        price = if (course.basePrice > 0 && !course.monthlyOnly) course.basePrice.toString().removeSuffix(".0") else ""
        monthlyPrice = course.monthlyPrice?.takeIf { it > 0 }?.toString()?.removeSuffix(".0").orEmpty()
        currency = course.baseCurrency
        isFree = course.isFreeCourse
        certificate = course.certificateEnabled
        seeded = true
    }

    fun form(): CourseForm = CourseForm(
        title = title,
        subtitle = subtitle,
        description = description,
        thumbnailUrl = thumb,
        level = level,
        categoryId = categoryId,
        // Free is authoritative: a course marked free is saved with a zero price outright, so a
        // number left over in the (now-hidden) price field from before the toggle was flipped
        // can never be charged at checkout.
        price = if (isFree) 0.0 else if (monthlyOnly) (monthlyPrice.toDoubleOrNull() ?: 0.0) else (price.toDoubleOrNull() ?: 0.0),
        // Only a paid course can be subscribed to; an empty or zero field turns the option off.
        monthlyPrice = if (isFree) null else monthlyPrice.toDoubleOrNull()?.takeIf { it > 0.0 },
        currency = currency,
        // Subscription-only is never free; without a monthly price the server refuses the save.
        isFree = !monthlyOnly && (isFree || (price.toDoubleOrNull() ?: 0.0) <= 0.0),
        monthlyOnly = !isFree && monthlyOnly,
        certificateEnabled = certificate,
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(
            draft?.course?.title?.takeIf { it.isNotBlank() } ?: tr(StrStudio.editCourse),
            onBack = { navController.popBackStack() },
            trailing = { draft?.let { StatusPill(it.course.status, Modifier.padding(end = 12.dp)) } },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> {
                val course = s.value.course
                val canReviewOthers = sessionState.can("courses.manage")
                val needsReview = !canReviewOthers && !directPublish

                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            StatusNotice(course.status, course.rejectionNote, needsReview)
                        }

                        if (canReviewOthers && !course.teacher?.fullName.isNullOrBlank()) {
                            item {
                                Text(
                                    "${tr(StrStudio.ownedBy)}: ${course.teacher?.fullName}",
                                    color = Ink.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        item { SectionHeader(tr(StrStudio.cover)) }
                        item {
                            CoverPicker(
                                url = thumb,
                                uploading = uploading,
                                onPick = {
                                    pendingPick.value = { uri ->
                                        vm.upload(context, uri, "covers") { thumb = it }
                                    }
                                    picker.launch("image/*")
                                },
                                onClear = { thumb = "" },
                            )
                        }

                        item { SectionHeader(tr(StrStudio.details)) }
                        item {
                            InkCard {
                                InkField(title, { title = it }, tr(StrStudio.titleField))
                                Spacer(Modifier.height(8.dp))
                                InkField(subtitle, { subtitle = it }, tr(StrStudio.subtitleField))
                                Spacer(Modifier.height(8.dp))
                                InkField(
                                    description,
                                    { description = it },
                                    tr(StrStudio.descriptionField),
                                    singleLine = false,
                                    minLines = 4,
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(tr(StrStudio.levelField), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(LEVELS) { option ->
                                        FilterChip(levelLabel(option).ifBlank { option }, level == option) {
                                            level = if (level == option) "" else option
                                        }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(tr(StrStudio.categoryField), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                if (categories.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(tr(StrStudio.noCategory), categoryId == null) { categoryId = null }
                                        }
                                        items(categories) { category ->
                                            FilterChip(category.name, categoryId == category.id) {
                                                categoryId = category.id
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                var newCategoryName by remember { mutableStateOf("") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InkField(
                                        newCategoryName,
                                        { newCategoryName = it },
                                        tr(StrStudio.newCategoryField),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    SecondaryAction(
                                        tr(StrStudio.addCategoryAction),
                                        enabled = newCategoryName.isNotBlank(),
                                    ) {
                                        val typed = newCategoryName
                                        vm.createCategory(typed) { newId -> categoryId = newId }
                                        newCategoryName = ""
                                    }
                                }
                            }
                        }

                        item { SectionHeader(tr(StrStudio.pricing)) }
                        item {
                            InkCard {
                                ToggleRow(tr(StrStudio.freeCourse), tr(StrStudio.freeCourseSub), isFree) { isFree = it }
                                if (!isFree) {
                                    Spacer(Modifier.height(10.dp))
                                    ToggleRow(tr(StrCourses.monthlyOnlyToggle), tr(StrCourses.monthlyOnlyToggleSub), monthlyOnly) { monthlyOnly = it }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!monthlyOnly) Box(Modifier.weight(1f)) {
                                            InkField(
                                                price,
                                                { price = it.filter { c -> c.isDigit() || c == '.' } },
                                                tr(StrStudio.priceField),
                                                keyboardType = KeyboardType.Decimal,
                                            )
                                        }
                                        Box(Modifier.weight(1f)) {
                                            InkField(
                                                currency,
                                                { currency = it.uppercase().take(3) },
                                                tr(StrStudio.currencyField),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    InkField(
                                        monthlyPrice,
                                        { monthlyPrice = it.filter { c -> c.isDigit() || c == '.' } },
                                        tr(StrCourses.monthlyPriceField),
                                        keyboardType = KeyboardType.Decimal,
                                    )
                                    Text(
                                        if (monthlyOnly && monthlyPrice.toDoubleOrNull().let { it == null || it <= 0.0 }) {
                                            tr(StrCourses.monthlyOnlyNeedsPrice)
                                        } else {
                                            tr(StrCourses.monthlyPriceHint)
                                        },
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Divider()
                                Spacer(Modifier.height(10.dp))
                                ToggleRow(
                                    tr(StrStudio.certificate),
                                    tr(StrStudio.certificateSub),
                                    certificate,
                                ) { certificate = it }
                            }
                        }

                        item {
                            SectionHeader(
                                tr(StrStudio.curriculum),
                                actionLabel = tr(StrStudio.addSection),
                                onAction = { sectionDialog = SectionDialog(null, "") },
                            )
                        }

                        if (panel.canManage && !panel.canLink) {
                            item {
                                Text(tr(StrContent.linkLocked), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (s.value.sections.isEmpty() && s.value.lessons.isEmpty() && panel.items.isEmpty()) {
                            item {
                                InkCard {
                                    Text(
                                        tr(StrStudio.emptyCurriculum),
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    SecondaryAction(tr(StrStudio.addLesson), Modifier.fillMaxWidth()) {
                                        lessonSheet = LessonTarget(null, null, s.value.lessons.size)
                                    }
                                    if (panel.canLink) {
                                        Spacer(Modifier.height(8.dp))
                                        SecondaryAction(tr(StrContent.linkExercise), Modifier.fillMaxWidth(), tint = Ink.Teal) {
                                            linkSheet = LinkTarget(null)
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(s.value.sections) { index, section ->
                            val sectionLessons = s.value.lessonsIn(section.id)
                            val sectionExercises = panel.items.filter { it.sectionId == section.id }.sortedBy { it.sortOrder }
                            SectionCard(
                                section = section,
                                lessons = sectionLessons,
                                exercises = sectionExercises,
                                canLink = panel.canLink,
                                canReorderExercises = panel.canManage,
                                onLinkExercise = { linkSheet = LinkTarget(section.id) },
                                onMoveExercise = { item, delta ->
                                    val position = sectionExercises.indexOf(item)
                                    vm.moveExercise(sectionExercises, position, position + delta)
                                },
                                onUnlinkExercise = { vm.unlinkExercise(it.exerciseId) },
                                isFirst = index == 0,
                                isLast = index == s.value.sections.lastIndex,
                                onMove = { delta -> vm.moveSection(s.value.sections, index, index + delta) },
                                onRename = { sectionDialog = SectionDialog(section.id, section.title) },
                                onDelete = { vm.deleteSection(section.id) },
                                onAddLesson = {
                                    lessonSheet = LessonTarget(null, section.id, s.value.lessons.size)
                                },
                                onEditLesson = { lesson ->
                                    lessonSheet = LessonTarget(lesson, section.id, lesson.sortOrder)
                                },
                                onMoveLesson = { lesson, delta ->
                                    val position = sectionLessons.indexOf(lesson)
                                    vm.moveLesson(s.value, section.id, sectionLessons, position, position + delta)
                                },
                                onDeleteLesson = { vm.deleteLesson(it.id) },
                            )
                        }

                        val loose = s.value.looseLessons
                        val looseExercises = panel.items.filter { item ->
                            item.sectionId == null || s.value.sections.none { it.id == item.sectionId }
                        }.sortedBy { it.sortOrder }
                        if (loose.isNotEmpty() || looseExercises.isNotEmpty()) {
                            item {
                                SectionCard(
                                    section = null,
                                    lessons = loose,
                                    exercises = looseExercises,
                                    canLink = panel.canLink,
                                    canReorderExercises = panel.canManage,
                                    onLinkExercise = { linkSheet = LinkTarget(null) },
                                    onMoveExercise = { item, delta ->
                                        val position = looseExercises.indexOf(item)
                                        vm.moveExercise(looseExercises, position, position + delta)
                                    },
                                    onUnlinkExercise = { vm.unlinkExercise(it.exerciseId) },
                                    isFirst = true,
                                    isLast = true,
                                    onMove = {},
                                    onRename = {},
                                    onDelete = {},
                                    onAddLesson = { lessonSheet = LessonTarget(null, null, s.value.lessons.size) },
                                    onEditLesson = { lesson -> lessonSheet = LessonTarget(lesson, null, lesson.sortOrder) },
                                    onMoveLesson = { lesson, delta ->
                                        val position = loose.indexOf(lesson)
                                        vm.moveLesson(s.value, null, loose, position, position + delta)
                                    },
                                    onDeleteLesson = { vm.deleteLesson(it.id) },
                                )
                            }
                        }

                        item { SectionHeader(tr(StrStudio.dangerZone)) }
                        item {
                            InkCard(borderColor = Ink.Coral.copy(alpha = 0.2f)) {
                                Text(tr(StrStudio.archiveCourse), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                Text(tr(StrStudio.archiveCourseSub), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                                SecondaryAction(tr(StrStudio.archiveCourse), Modifier.fillMaxWidth()) {
                                    vm.save(form(), thenStatus = "ARCHIVED")
                                }
                                Spacer(Modifier.height(16.dp))
                                Divider()
                                Spacer(Modifier.height(16.dp))
                                Text(tr(StrStudio.deleteCourse), color = Ink.Coral, style = MaterialTheme.typography.titleSmall)
                                Text(tr(StrStudio.deleteCourseSub), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                                SecondaryAction(tr(StrStudio.deleteCourse), Modifier.fillMaxWidth()) { confirmDelete = true }
                                // Owner/Admin can force-delete even with enrolled students
                                if (sessionState.isStaff) {
                                    Spacer(Modifier.height(12.dp))
                                    Divider()
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        tr(StrStudio.forceDeleteCourse),
                                        color = Ink.Coral,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        tr(StrStudio.forceDeleteCourseSub),
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SecondaryAction(
                                        tr(StrStudio.forceDeleteCourse),
                                        Modifier.fillMaxWidth(),
                                        tint = Ink.Coral,
                                    ) { confirmForceDelete = true }
                                }
                            }
                        }

                        error?.let {
                            item {
                                Text(it.message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }

                EditorActionBar(
                    status = course.status,
                    needsReview = needsReview,
                    busy = busy,
                    savedTick = savedTick,
                    onSave = { vm.save(form()) },
                    onPublish = { vm.save(form(), thenStatus = "PUBLISHED") },
                    onUnpublish = { vm.save(form(), thenStatus = "DRAFT") },
                )

                if (lessonSheet != null) {
                    LessonSheet(
                        target = lessonSheet!!,
                        uploading = uploading,
                        onPickMedia = { mime, assign ->
                            pendingPick.value = { uri -> vm.upload(context, uri, "lessons", assign) }
                            picker.launch(mime)
                        },
                        onDismiss = { lessonSheet = null },
                        onSave = { d ->
                            val target = lessonSheet
                            vm.saveLesson(
                                id = target?.lesson?.id,
                                sectionId = target?.sectionId,
                                title = d.title,
                                kind = d.kind,
                                videoUrl = d.videoUrl,
                                documentUrl = d.documentUrl,
                                content = d.content,
                                minutes = d.minutes,
                                sortOrder = target?.sortOrder ?: 0,
                                isPreview = d.preview,
                                blocks = d.blocks,
                                allowPdf = d.allowPdf,
                                allowTxt = d.allowTxt,
                            )
                            lessonSheet = null
                        },
                    )
                }

                linkSheet?.let { target ->
                    LinkExerciseSheet(
                        sections = s.value.sections,
                        initialSectionId = target.sectionId,
                        canLinkAny = panel.canLinkAny,
                        state = linkable,
                        onQuery = { all, search -> vm.loadLinkable(all, search) },
                        onDismiss = { linkSheet = null },
                        onConfirm = { ids, sectionId ->
                            vm.linkExercises(ids, sectionId)
                            linkSheet = null
                        },
                    )
                }

                sectionDialog?.let { dialog ->
                    SectionTitleDialog(
                        initial = dialog.title,
                        onDismiss = { sectionDialog = null },
                        onConfirm = { value ->
                            if (dialog.id == null) {
                                vm.addSection(value, s.value.sections.size)
                            } else {
                                vm.renameSection(dialog.id, value)
                            }
                            sectionDialog = null
                        },
                    )
                }

                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        containerColor = Ink.Surface,
                        titleContentColor = Ink.TextPrimary,
                        textContentColor = Ink.TextSecondary,
                        title = { Text(tr(StrStudio.deleteCourse)) },
                        text = { Text(tr(StrStudio.deleteCourseConfirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                vm.deleteCourse { navController.popBackStack() }
                            }) { Text(tr(StrStudio.confirmDelete), color = Ink.Coral) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(tr(StrStudio.cancel), color = Ink.TextMuted)
                            }
                        },
                    )
                }

                if (confirmForceDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmForceDelete = false },
                        containerColor = Ink.Surface,
                        titleContentColor = Ink.TextPrimary,
                        textContentColor = Ink.TextSecondary,
                        title = { Text(tr(StrStudio.forceDeleteCourse)) },
                        text = { Text(tr(StrStudio.forceDeleteCourseConfirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmForceDelete = false
                                vm.deleteCourseForce { navController.popBackStack() }
                            }) { Text(tr(StrStudio.confirmDelete), color = Ink.Coral) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmForceDelete = false }) {
                                Text(tr(StrStudio.cancel), color = Ink.TextMuted)
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class LessonTarget(val lesson: Lesson?, val sectionId: String?, val sortOrder: Int)

/** Which unit the "link exercise" sheet adds to (null = no unit). */
private data class LinkTarget(val sectionId: String?)

/** Everything the lesson sheet hands back on save. */
private data class LessonDraftForm(
    val title: String,
    val kind: String,
    val videoUrl: String,
    val documentUrl: String,
    val content: String,
    val minutes: Int,
    val preview: Boolean,
    val blocks: List<LessonBlock>?,
    val allowPdf: Boolean,
    val allowTxt: Boolean,
)

private data class SectionDialog(val id: String?, val title: String)

@Composable
private fun StatusNotice(status: String, rejectionNote: String?, needsReview: Boolean) {
    val (text, color) = when (status) {
        "PUBLISHED" -> tr(StrStudio.publishedNotice) to Ink.Teal
        "PENDING_REVIEW" -> tr(StrStudio.reviewNotice) to Ink.Amber
        "REJECTED" -> tr(StrStudio.rejectedNotice) to Ink.Coral
        else -> (if (needsReview) tr(StrStudio.reviewRequiredNotice) else tr(StrStudio.draftNotice)) to Ink.TextMuted
    }
    InkCard(contentPadding = PaddingValues(14.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        if (status == "REJECTED" && !rejectionNote.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(rejectionNote, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CoverPicker(url: String, uploading: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Dimens.cardRadius))
                .clickable(enabled = !uploading, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            CoverImage(url.takeIf { it.isNotBlank() }, Modifier.fillMaxSize())
            if (uploading) {
                CircularProgressIndicator(color = Ink.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            } else if (url.isBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null, tint = Ink.Amber, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(tr(StrStudio.addCover), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(tr(StrStudio.uploadLimit), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (url.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryAction(tr(StrStudio.replaceCover), Modifier.weight(1f), enabled = !uploading, onClick = onPick)
                SecondaryAction(tr(StrStudio.removeCover), Modifier.weight(1f), onClick = onClear)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Canvas,
                checkedTrackColor = Ink.Amber,
                uncheckedThumbColor = Ink.TextMuted,
                uncheckedTrackColor = Ink.SurfaceHigh,
                uncheckedBorderColor = Ink.Hairline,
            ),
        )
    }
}

@Composable
private fun SectionCard(
    section: CourseSection?,
    lessons: List<Lesson>,
    exercises: List<CourseExerciseItem> = emptyList(),
    canLink: Boolean = false,
    canReorderExercises: Boolean = false,
    onLinkExercise: () -> Unit = {},
    onMoveExercise: (CourseExerciseItem, Int) -> Unit = { _, _ -> },
    onUnlinkExercise: (CourseExerciseItem) -> Unit = {},
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddLesson: () -> Unit,
    onEditLesson: (Lesson) -> Unit,
    onMoveLesson: (Lesson, Int) -> Unit,
    onDeleteLesson: (Lesson) -> Unit,
) {
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                section?.title ?: tr(StrStudio.ungrouped),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (section != null) {
                IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = !isFirst) { onMove(-1) }
                IconAction(Icons.Default.ArrowDownward, tr(StrStudio.moveDown), enabled = !isLast) { onMove(1) }
                IconAction(Icons.Default.Edit, tr(StrStudio.renameSection)) { onRename() }
                IconAction(Icons.Default.DeleteOutline, tr(StrStudio.deleteSection), tint = Ink.Coral) { onDelete() }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (lessons.isEmpty() && exercises.isEmpty()) {
            Text(tr(StrStudio.noLessonsInSection), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            lessons.forEachIndexed { index, lesson ->
                LessonRow(
                    lesson = lesson,
                    position = index + 1,
                    isFirst = index == 0,
                    isLast = index == lessons.lastIndex,
                    onEdit = { onEditLesson(lesson) },
                    onMove = { delta -> onMoveLesson(lesson, delta) },
                    onDelete = { onDeleteLesson(lesson) },
                )
                if (index != lessons.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                }
            }
            exercises.forEachIndexed { index, item ->
                Spacer(Modifier.height(6.dp))
                LinkedExerciseRow(
                    item = item,
                    isFirst = index == 0,
                    isLast = index == exercises.lastIndex,
                    canReorder = canReorderExercises,
                    onMove = { delta -> onMoveExercise(item, delta) },
                    onUnlink = { onUnlinkExercise(item) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(tr(StrStudio.addLesson), Modifier.weight(1f), onClick = onAddLesson)
            if (canLink) {
                SecondaryAction(tr(StrContent.linkExercise), Modifier.weight(1f), tint = Ink.Teal, onClick = onLinkExercise)
            }
        }
    }
}

@Composable
private fun LinkedExerciseRow(
    item: CourseExerciseItem,
    isFirst: Boolean,
    isLast: Boolean,
    canReorder: Boolean,
    onMove: (Int) -> Unit,
    onUnlink: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.TealSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.FactCheck, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(item.displayTitle, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Text(
                buildList {
                    add(trf(StrContent.questionsShort, item.questionCount))
                    if (item.ownerName.isNotBlank()) add(item.ownerName)
                    if (item.courseCount > 1) add(trf(StrContent.usedInCourses, item.courseCount))
                }.joinToString(" · "),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            if (item.status != "PUBLISHED") {
                Spacer(Modifier.height(3.dp))
                Pill(tr(StrContent.draftBadge), background = Ink.AmberSoft, foreground = Ink.Amber)
            }
        }
        if (canReorder) {
            IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = !isFirst) { onMove(-1) }
            IconAction(Icons.Default.ArrowDownward, tr(StrStudio.moveDown), enabled = !isLast) { onMove(1) }
        }
        if (item.canUnlink) {
            IconAction(Icons.Default.LinkOff, tr(StrContent.unlink), tint = Ink.Coral) { onUnlink() }
        }
    }
}

@Composable
private fun LessonRow(
    lesson: Lesson,
    position: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.SurfaceHigh)
            .clickable(onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$position. ${lesson.title}",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(codeLabel(lesson.kind), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                if (lesson.durationSeconds > 0) {
                    Text(
                        "· ${lesson.durationSeconds / 60}",
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (lesson.isPreview) {
                    Pill(tr(StrStudio.previewBadge), background = Ink.TealSoft, foreground = Ink.Teal)
                }
                if (lesson.kind == "TEXT" && lesson.hasBlocks) {
                    if (lesson.allowPdf) Pill("PDF", background = Ink.SkySoft, foreground = Ink.Sky)
                    if (lesson.allowTxt) Pill("TXT", background = Ink.SkySoft, foreground = Ink.Sky)
                }
            }
        }
        IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = !isFirst) { onMove(-1) }
        IconAction(Icons.Default.ArrowDownward, tr(StrStudio.moveDown), enabled = !isLast) { onMove(1) }
        IconAction(Icons.Default.DeleteOutline, tr(StrStudio.delete), tint = Ink.Coral) { onDelete() }
    }
}

@Composable
private fun EditorActionBar(
    status: String,
    needsReview: Boolean,
    busy: Boolean,
    savedTick: Int,
    onSave: () -> Unit,
    onPublish: () -> Unit,
    onUnpublish: () -> Unit,
) {
    val live = status == "PUBLISHED" || status == "PENDING_REVIEW"
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Surface)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
    ) {
        if (savedTick > 0 && !busy) {
            Text(tr(StrStudio.saved), color = Ink.Teal, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryAction(
                if (status == "DRAFT") tr(StrStudio.saveDraft) else tr(StrStudio.save),
                Modifier.weight(1f),
                enabled = !busy,
                onClick = onSave,
            )
            Box(Modifier.weight(1f)) {
                if (live) {
                    PrimaryAction(tr(StrStudio.unpublish), loading = busy, onClick = onUnpublish)
                } else {
                    PrimaryAction(
                        if (needsReview) tr(StrStudio.submitForReview) else tr(StrStudio.publish),
                        loading = busy,
                        onClick = onPublish,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonSheet(
    target: LessonTarget,
    uploading: Boolean,
    onPickMedia: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (LessonDraftForm) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lesson = target.lesson
    // A reading lesson written before blocks existed opens as a paragraph (+ its picture).
    val legacyText = lesson != null && lesson.kind == "TEXT" && !lesson.hasBlocks

    var title by remember { mutableStateOf(lesson?.title.orEmpty()) }
    var kind by remember { mutableStateOf(lesson?.kind ?: "VIDEO") }
    var videoUrl by remember { mutableStateOf(lesson?.videoUrl.orEmpty()) }
    var documentUrl by remember { mutableStateOf(if (legacyText) "" else lesson?.documentUrl.orEmpty()) }
    var content by remember { mutableStateOf(lesson?.content.orEmpty()) }
    var minutes by remember {
        mutableStateOf(if ((lesson?.durationSeconds ?: 0) > 0) ((lesson?.durationSeconds ?: 0) / 60).toString() else "")
    }
    var preview by remember { mutableStateOf(lesson?.isPreview ?: false) }
    var allowPdf by remember { mutableStateOf(lesson?.allowPdf ?: true) }
    var allowTxt by remember { mutableStateOf(lesson?.allowTxt ?: true) }
    val blocks = remember {
        mutableStateListOf<LessonBlock>().apply {
            val existing = lesson?.blocks
            if (!existing.isNullOrEmpty()) {
                addAll(existing)
            } else if (legacyText) {
                val oldText = lesson?.content.orEmpty()
                val oldImage = lesson?.documentUrl.orEmpty()
                if (oldText.isNotBlank()) add(LessonBlock(LessonBlock.PARAGRAPH, text = oldText))
                if (oldImage.isNotBlank()) add(LessonBlock(LessonBlock.IMAGE, url = oldImage))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Surface,
        contentColor = Ink.TextPrimary,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 28.dp)
                .imePadding(),
        ) {
            Text(
                if (lesson == null) tr(StrStudio.newLesson) else tr(StrStudio.editLesson),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(14.dp))

            InkField(title, { title = it }, tr(StrStudio.lessonTitle))
            Spacer(Modifier.height(12.dp))

            Text(tr(StrStudio.lessonKind), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LESSON_KINDS.forEach { option ->
                    FilterChip(codeLabel(option), kind == option) { kind = option }
                }
            }

            Spacer(Modifier.height(14.dp))

            when (kind) {
                "VIDEO" -> MediaField(
                    label = tr(StrStudio.uploadVideo),
                    icon = Icons.Default.Videocam,
                    value = videoUrl,
                    uploading = uploading,
                    onPick = { onPickMedia("video/*") { videoUrl = it } },
                    onChange = { videoUrl = it },
                )
                "TEXT" -> {
                    BlockEditor(
                        blocks = blocks,
                        uploading = uploading,
                        onPickImage = { index ->
                            onPickMedia("image/*") { url ->
                                if (index in blocks.indices) blocks[index] = blocks[index].copy(url = url)
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    InkCard(color = Ink.SurfaceHigh) {
                        Text(tr(StrContent.downloadsTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(tr(StrContent.allowPdf), "", allowPdf) { allowPdf = it }
                        Spacer(Modifier.height(4.dp))
                        ToggleRow(tr(StrContent.allowTxt), "", allowTxt) { allowTxt = it }
                        Spacer(Modifier.height(6.dp))
                        Text(tr(StrContent.downloadsHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        if (allowPdf) {
                            Spacer(Modifier.height(10.dp))
                            MediaField(
                                label = tr(StrContent.readyPdf),
                                icon = Icons.Default.PictureAsPdf,
                                value = documentUrl,
                                uploading = uploading,
                                onPick = { onPickMedia("application/pdf") { documentUrl = it } },
                                onChange = { documentUrl = it },
                            )
                        }
                    }
                }
                "QUIZ" -> {
                    InkField(content, { content = it }, tr(StrStudio.lessonText), singleLine = false, minLines = 5)
                    Spacer(Modifier.height(10.dp))
                    MediaField(
                        label = tr(StrStudio.uploadImage),
                        icon = Icons.Default.Image,
                        value = documentUrl,
                        uploading = uploading,
                        onPick = { onPickMedia("image/*") { documentUrl = it } },
                        onChange = { documentUrl = it },
                    )
                }
                else -> MediaField(
                    label = tr(StrStudio.uploadDocument),
                    icon = Icons.Default.AttachFile,
                    value = documentUrl,
                    uploading = uploading,
                    onPick = { onPickMedia("*/*") { documentUrl = it } },
                    onChange = { documentUrl = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            InkField(
                minutes,
                { minutes = it.filter(Char::isDigit) },
                tr(StrStudio.minutesField),
                keyboardType = KeyboardType.Number,
            )

            Spacer(Modifier.height(14.dp))
            ToggleRow(tr(StrStudio.freePreview), tr(StrStudio.freePreviewSub), preview) { preview = it }

            Spacer(Modifier.height(18.dp))
            PrimaryAction(tr(StrStudio.save), enabled = title.isNotBlank() && !uploading) {
                val reading = kind == "TEXT"
                onSave(
                    LessonDraftForm(
                        title = title,
                        kind = kind,
                        videoUrl = if (kind == "VIDEO") videoUrl else "",
                        documentUrl = if (kind == "VIDEO" || (reading && !allowPdf)) "" else documentUrl,
                        content = if (reading) "" else content,
                        minutes = minutes.toIntOrNull() ?: 0,
                        preview = preview,
                        blocks = if (reading) blocks.toList() else null,
                        allowPdf = allowPdf,
                        allowTxt = allowTxt,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            SecondaryAction(tr(StrStudio.cancel), Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** The reading-lesson editor: an ordered list of heading / paragraph / image / list blocks. */
@Composable
private fun BlockEditor(
    blocks: SnapshotStateList<LessonBlock>,
    uploading: Boolean,
    onPickImage: (Int) -> Unit,
) {
    Text(tr(StrContent.lessonContent), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
    Text(tr(StrContent.blocksHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    if (blocks.isEmpty()) {
        Text(tr(StrContent.noBlocks), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }

    blocks.forEachIndexed { index, block ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.SurfaceHigh)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(blockLabel(block.type), background = Ink.SkySoft, foreground = Ink.Sky)
                Spacer(Modifier.weight(1f))
                IconAction(Icons.Default.ArrowUpward, tr(StrStudio.moveUp), enabled = index > 0) {
                    blocks.add(index - 1, blocks.removeAt(index))
                }
                IconAction(Icons.Default.ArrowDownward, tr(StrStudio.moveDown), enabled = index < blocks.lastIndex) {
                    blocks.add(index + 1, blocks.removeAt(index))
                }
                IconAction(Icons.Default.DeleteOutline, tr(StrContent.removeBlock), tint = Ink.Coral) {
                    blocks.removeAt(index)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (block.type) {
                LessonBlock.HEADING -> InkField(block.text, { blocks[index] = block.copy(text = it) }, tr(StrContent.headingText))
                LessonBlock.LIST -> InkField(
                    block.items.joinToString("\n"),
                    { blocks[index] = block.copy(items = it.split("\n")) },
                    tr(StrContent.listItems),
                    singleLine = false,
                    minLines = 3,
                )
                LessonBlock.IMAGE -> {
                    if (!block.url.isNullOrBlank()) {
                        CoverImage(
                            block.url,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    SecondaryAction(
                        if (uploading) tr(StrStudio.uploading)
                        else if (block.url.isNullOrBlank()) tr(StrContent.pickImage) else tr(StrContent.replaceImage),
                        Modifier.fillMaxWidth(),
                        enabled = !uploading,
                    ) { onPickImage(index) }
                    Spacer(Modifier.height(8.dp))
                    InkField(block.caption.orEmpty(), { blocks[index] = block.copy(caption = it) }, tr(StrContent.imageCaption))
                }
                else -> InkField(
                    block.text,
                    { blocks[index] = block.copy(text = it) },
                    tr(StrContent.paragraphText),
                    singleLine = false,
                    minLines = 3,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LessonBlock.TYPES.forEach { type ->
            FilterChip(trf(StrContent.addBlock, blockLabel(type)), false) {
                blocks.add(LessonBlock(type))
                if (type == LessonBlock.IMAGE) onPickImage(blocks.lastIndex)
            }
        }
    }
}

private fun blockLabel(type: String): String = when (type) {
    LessonBlock.HEADING -> tr(StrContent.blockHeading)
    LessonBlock.IMAGE -> tr(StrContent.blockImage)
    LessonBlock.LIST -> tr(StrContent.blockList)
    else -> tr(StrContent.blockParagraph)
}

/** Picker for linking existing exercises into this course, into one unit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkExerciseSheet(
    sections: List<CourseSection>,
    initialSectionId: String?,
    canLinkAny: Boolean,
    state: Async<List<LinkableExercise>>,
    onQuery: (Boolean, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var all by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sectionId by remember { mutableStateOf(initialSectionId) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(all, search) {
        kotlinx.coroutines.delay(250)
        onQuery(all, search)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Surface,
        contentColor = Ink.TextPrimary,
    ) {
        LazyColumn(
            Modifier.imePadding(),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(tr(StrContent.linkSheetTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge) }

            if (canLinkAny) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tr(StrContent.scopeMine), !all) { all = false }
                        FilterChip(tr(StrContent.scopeAll), all) { all = true }
                    }
                }
                item { Text(tr(StrContent.scopeAllHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }

            item { InkField(search, { search = it }, tr(StrContent.searchExercises)) }

            item {
                Text(tr(StrContent.addTo), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(tr(StrContent.noUnit), sectionId == null) { sectionId = null } }
                    items(sections, key = { it.id }) { section ->
                        FilterChip(section.title, sectionId == section.id) { sectionId = section.id }
                    }
                }
            }

            when (state) {
                is Async.Loading -> item { LoadingBlock() }
                is Async.Failure -> item { ErrorBlock(state.error) { onQuery(all, search) } }
                is Async.Success -> {
                    if (state.value.isEmpty()) {
                        item { Text(tr(StrContent.noLinkable), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(state.value, key = { it.id }) { exercise ->
                        val checked = exercise.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (checked) Ink.AmberSoft else Ink.SurfaceHigh)
                                .clickable(enabled = !exercise.alreadyLinked) {
                                    if (checked) selected.remove(exercise.id) else selected.add(exercise.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked || exercise.alreadyLinked,
                                enabled = !exercise.alreadyLinked,
                                onCheckedChange = { on -> if (on) selected.add(exercise.id) else selected.remove(exercise.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Ink.Amber, checkmarkColor = Ink.Canvas),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(exercise.displayTitle, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    if (exercise.alreadyLinked) {
                                        tr(StrContent.alreadyLinked)
                                    } else {
                                        buildList {
                                            add(trf(StrContent.questionsShort, exercise.questionCount))
                                            if (!exercise.mine && exercise.ownerName.isNotBlank()) add(exercise.ownerName)
                                            if (exercise.courseCount > 0) add(trf(StrContent.usedInCourses, exercise.courseCount))
                                            if (exercise.status != "PUBLISHED") add(tr(StrContent.draftBadge))
                                        }.joinToString(" · ")
                                    },
                                    color = if (exercise.alreadyLinked) Ink.Teal else Ink.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(tr(StrContent.linkNote), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                PrimaryAction(trf(StrContent.linkCount, selected.size), enabled = selected.isNotEmpty()) {
                    onConfirm(selected.toList(), sectionId)
                }
                Spacer(Modifier.height(8.dp))
                SecondaryAction(tr(StrStudio.cancel), Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MediaField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    uploading: Boolean,
    onPick: () -> Unit,
    onChange: (String) -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.SurfaceHigh)
                .clickable(enabled = !uploading, onClick = onPick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uploading) {
                CircularProgressIndicator(color = Ink.Amber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, null, tint = Ink.Amber, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (uploading) tr(StrStudio.uploading) else label,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (value.isNotBlank()) tr(StrStudio.attached) else tr(StrStudio.uploadLimit),
                    color = if (value.isNotBlank()) Ink.Teal else Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Default.Add, null, tint = Ink.TextMuted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        InkField(value, onChange, tr(StrStudio.mediaUrl))
    }
}

@Composable
private fun SectionTitleDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        title = { Text(if (initial.isBlank()) tr(StrStudio.addSection) else tr(StrStudio.renameSection)) },
        text = {
            Column {
                InkField(value, { value = it }, tr(StrStudio.sectionTitle))
                if (initial.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(StrStudio.deleteSectionBody), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(tr(StrStudio.save), color = Ink.Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(StrStudio.cancel), color = Ink.TextMuted) }
        },
    )
}
