package com.rork.pro.ui.screens.courses

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.Course
import com.rork.pro.data.CourseSection
import com.rork.pro.data.Enrollment
import com.rork.pro.data.LearningRepository
import com.rork.pro.data.Lesson
import com.rork.pro.data.LessonProgress
import com.rork.pro.data.LessonQuiz
import com.rork.pro.data.QuizResult
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LessonMedia
import com.rork.pro.ui.components.LessonVideo
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.SkillBar
import com.rork.pro.ui.components.resolveLessonMedia
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.StrCourses
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.StrContent
import com.rork.pro.data.CourseContentRepository
import com.rork.pro.data.CourseExerciseItem
import com.rork.pro.data.LessonBlock
import com.rork.pro.data.LessonExport
import com.rork.pro.ui.navigation.Routes
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast

data class PlayerData(
    val course: Course,
    val lessons: List<Lesson>,
    val sections: List<CourseSection>,
    val progress: Map<String, LessonProgress>,
    val enrollment: Enrollment?,
    val certificateSerial: String?,
    /** Published exercises linked into this course (each knows its unit). */
    val exercises: List<CourseExerciseItem> = emptyList(),
)

class CoursePlayerViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<PlayerData>>(Async.Loading)
    val state: StateFlow<Async<PlayerData>> = _state.asStateFlow()

    private val _quizzes = MutableStateFlow<List<LessonQuiz>>(emptyList())
    val quizzes: StateFlow<List<LessonQuiz>> = _quizzes.asStateFlow()

    private val _quizResult = MutableStateFlow<QuizResult?>(null)
    val quizResult: StateFlow<QuizResult?> = _quizResult.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun load(courseId: String) {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching {
                val course = CatalogRepository.course(courseId) ?: error("COURSE_NOT_AVAILABLE")
                val enrollment = LearningRepository.enrollment(courseId)
                val certificate = com.rork.pro.data.CommerceRepository.myCertificates()
                    .firstOrNull { it.courseTitle == course.title }
                PlayerData(
                    course = course,
                    lessons = CatalogRepository.lessons(courseId),
                    sections = CatalogRepository.courseSections(courseId),
                    progress = LearningRepository.progressFor(courseId).associateBy { it.lessonId },
                    enrollment = enrollment,
                    certificateSerial = certificate?.serial,
                    exercises = runCatching { CourseContentRepository.panel(courseId).items }
                        .getOrDefault(emptyList())
                        .filter { it.status == "PUBLISHED" },
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    fun openQuiz(lessonId: String) {
        viewModelScope.launch {
            _quizResult.value = null
            runCatching { LearningRepository.quizzes(lessonId) }
                .onSuccess { _quizzes.value = it }
                .onFailure { _quizzes.value = emptyList() }
        }
    }

    fun closeQuiz() {
        _quizzes.value = emptyList()
        _quizResult.value = null
    }

    fun toggleComplete(courseId: String, lessonId: String, completed: Boolean, watchedSeconds: Int) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { LearningRepository.markProgress(lessonId, watchedSeconds, completed) }
            _busy.value = false
            load(courseId)
        }
    }

    fun submitQuiz(courseId: String, lessonId: String, answers: Map<String, List<Int>>) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { LearningRepository.submitQuiz(lessonId, answers) }
                .onSuccess { _quizResult.value = it }
            _busy.value = false
            load(courseId)
        }
    }
}

@Composable
fun CoursePlayerScreen(navController: NavHostController, courseId: String, initialLessonId: String? = null) {
    val vm: CoursePlayerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val quizzes by vm.quizzes.collectAsStateWithLifecycle()
    val quizResult by vm.quizResult.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A lesson tapped from the course list must open directly and reliably — falling back to
    // "first incomplete" only when no specific lesson was requested (the existing behavior).
    var selectedLessonId by remember(courseId, initialLessonId) { mutableStateOf(initialLessonId) }
    // Which download is being prepared ("pdf" / "txt"), so its button shows a spinner.
    var downloading by remember { mutableStateOf<String?>(null) }
    val downloadScope = rememberCoroutineScope()

    fun download(lesson: Lesson, pdf: Boolean) {
        if (downloading != null) return
        // A ready-made PDF the teacher attached is opened as is; otherwise the file is built here.
        val readyPdf = lesson.documentUrl?.takeIf { pdf && it.isNotBlank() }
        if (readyPdf != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, readyPdf.toUri())) }
            return
        }
        downloading = if (pdf) "pdf" else "txt"
        downloadScope.launch {
            runCatching {
                if (pdf) LessonExport.savePdf(context, lesson) else LessonExport.saveTxt(context, lesson)
            }.onSuccess { saved ->
                if (saved is LessonExport.Saved.InDownloads) {
                    Toast.makeText(context, tr(StrContent.savedToDownloads), Toast.LENGTH_SHORT).show()
                    LessonExport.open(context, saved)
                }
            }.onFailure {
                Toast.makeText(context, tr(StrContent.downloadFailed), Toast.LENGTH_SHORT).show()
            }
            downloading = null
        }
    }

    LaunchedEffect(courseId) { vm.load(courseId) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrCourses.learning), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load(courseId) }
            is Async.Success -> {
                val data = s.value
                val active = data.lessons.firstOrNull { it.id == selectedLessonId }
                    ?: data.lessons.firstOrNull { data.progress[it.id]?.isCompleted != true }
                    ?: data.lessons.firstOrNull()

                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
                            Text(
                                data.course.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink.TextPrimary,
                            )
                            Spacer(Modifier.height(12.dp))
                            SkillBar(tr(StrCourses.courseTitle), data.enrollment?.progressPercent ?: 0.0)
                        }
                    }

                    // Free lessons may carry a banner; paid content never does (server-decided).
                    item {
                        AdBanner(
                            "FREE_LESSON",
                            Modifier.padding(horizontal = Dimens.screenPadding),
                            courseId = data.course.id,
                        )
                    }

                    if (data.certificateSerial != null) {
                        item {
                            InkCard(
                                Modifier.padding(horizontal = Dimens.screenPadding),
                                borderColor = Ink.Teal.copy(alpha = 0.4f),
                            ) {
                                Text(tr(StrCourses.certificateIssued), color = Ink.Teal, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    trf(StrCourses.serialNumber, data.certificateSerial),
                                    color = Ink.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (active != null) {
                        item {
                            ActiveLessonCard(
                                lesson = active,
                                completed = data.progress[active.id]?.isCompleted == true,
                                busy = busy,
                                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                onOpenMedia = { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                    }
                                },
                                onToggleComplete = { done ->
                                    vm.toggleComplete(courseId, active.id, done, active.durationSeconds)
                                },
                                onOpenQuiz = { vm.openQuiz(active.id) },
                                downloading = downloading,
                                onDownload = { pdf -> download(active, pdf) },
                            )
                        }

                        // Exercises linked to this lesson's unit (or, for a course without units,
                        // every exercise not filed under a unit).
                        val unitExercises = data.exercises.filter { item ->
                            if (active.sectionId == null) {
                                item.sectionId == null || data.sections.none { it.id == item.sectionId }
                            } else {
                                item.sectionId == active.sectionId
                            }
                        }.sortedBy { it.sortOrder }
                        if (unitExercises.isNotEmpty()) {
                            item {
                                Text(
                                    if (active.sectionId == null) tr(StrContent.courseExercises) else tr(StrContent.unitExercises),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ink.TextPrimary,
                                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                )
                            }
                            items(unitExercises, key = { "ex-${it.exerciseId}" }) { item ->
                                UnitExerciseRow(
                                    item = item,
                                    unlocked = data.enrollment != null || data.course.isFreeCourse,
                                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                ) { navController.navigate(Routes.testRun(item.exerciseId)) }
                            }
                        }
                    }

                    if (quizzes.isNotEmpty()) {
                        item {
                            QuizCard(
                                quizzes = quizzes,
                                result = quizResult,
                                busy = busy,
                                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                onSubmit = { answers -> active?.let { vm.submitQuiz(courseId, it.id, answers) } },
                                onClose = { vm.closeQuiz() },
                            )
                        }
                    }

                    item {
                        Text(
                            tr(StrCourses.lessons),
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink.TextPrimary,
                            modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                        )
                    }

                    groupLessons(data.sections, data.lessons).forEach { group ->
                        group.section?.let { section ->
                            item(key = "section-${section.id}") {
                                Text(
                                    section.displayTitle,
                                    color = Ink.Amber,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(
                                        start = Dimens.screenPadding,
                                        end = Dimens.screenPadding,
                                        top = 6.dp,
                                    ),
                                )
                            }
                        }
                        items(group.lessons, key = { it.id }) { lesson ->
                            PlaylistRow(
                                lesson = lesson,
                                completed = data.progress[lesson.id]?.isCompleted == true,
                                active = lesson.id == active?.id,
                                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                            ) {
                                selectedLessonId = lesson.id
                                vm.closeQuiz()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveLessonCard(
    lesson: Lesson,
    completed: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onOpenMedia: (String) -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onOpenQuiz: () -> Unit,
    downloading: String? = null,
    onDownload: (Boolean) -> Unit = {},
) {
    val reading = lesson.kind == "TEXT" && lesson.hasBlocks
    InkCard(modifier = modifier) {
        Text(lesson.displayTitle, color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge)
        if (!lesson.displayDescription.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(lesson.displayDescription, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (reading && (lesson.allowPdf || lesson.allowTxt)) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lesson.allowPdf) {
                    DownloadButton(tr(StrContent.downloadPdf), downloading == "pdf", Modifier.weight(1f)) { onDownload(true) }
                }
                if (lesson.allowTxt) {
                    DownloadButton(tr(StrContent.downloadTxt), downloading == "txt", Modifier.weight(1f)) { onDownload(false) }
                }
            }
        }

        if (reading) {
            Spacer(Modifier.height(12.dp))
            LessonBlocksView(lesson.blocks.orEmpty())
        } else if (!lesson.content.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(lesson.content, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(14.dp))

        // For a reading lesson documentUrl is the optional ready-made PDF, offered by the download button.
        val media = lesson.videoUrl?.takeIf { it.isNotBlank() } ?: lesson.documentUrl?.takeIf { !reading }
        if (!media.isNullOrBlank()) {
            val resolved = remember(media) { resolveLessonMedia(media) }
            val playable = !lesson.videoUrl.isNullOrBlank() && resolved !is LessonMedia.Link
            if (playable) {
                LessonVideo(resolved, Modifier.fillMaxWidth())
            } else {
                PrimaryAction(
                    if (!lesson.videoUrl.isNullOrBlank()) tr(StrCourses.playLesson) else tr(StrCourses.openDocument),
                ) { onOpenMedia(media) }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (lesson.kind == "QUIZ") {
            SecondaryAction(tr(StrCourses.openQuiz), Modifier.fillMaxWidth(), onClick = onOpenQuiz)
            Spacer(Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = completed,
                onCheckedChange = { if (!busy) onToggleComplete(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Ink.Amber,
                    uncheckedColor = Ink.Hairline,
                    checkmarkColor = Ink.Canvas,
                ),
            )
            Text(
                if (completed) tr(StrCourses.completed) else tr(StrCourses.markComplete),
                color = if (completed) Ink.Teal else Ink.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun QuizCard(
    quizzes: List<LessonQuiz>,
    result: QuizResult?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onSubmit: (Map<String, List<Int>>) -> Unit,
    onClose: () -> Unit,
) {
    val answers = remember(quizzes) { mutableStateMapOf<String, Int>() }

    InkCard(modifier = modifier, borderColor = Ink.Amber.copy(alpha = 0.3f)) {
        Text(tr(StrCourses.lessonQuiz), color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        quizzes.forEachIndexed { index, quiz ->
            Text(
                "${index + 1}. ${quiz.question}",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            quiz.options.forEachIndexed { optionIndex, option ->
                val selected = answers[quiz.id] == optionIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Ink.AmberSoft else Ink.SurfaceHigh)
                        .clickable { answers[quiz.id] = optionIndex }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.Circle,
                        null,
                        tint = if (selected) Ink.Amber else Ink.TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(option, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (result != null) {
            Divider()
            Spacer(Modifier.height(12.dp))
            Text(
                trf(if (result.passed) StrCourses.quizPassed else StrCourses.quizNotPassed, result.percent.toInt()),
                color = if (result.passed) Ink.Teal else Ink.Coral,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryAction(tr(StrCourses.close), Modifier.fillMaxWidth(), onClick = onClose)
        } else {
            PrimaryAction(
                tr(StrCourses.submitAnswers),
                enabled = answers.size == quizzes.size,
                loading = busy,
            ) {
                onSubmit(answers.mapValues { listOf(it.value) })
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    lesson: Lesson,
    completed: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    InkCard(
        modifier = modifier,
        onClick = onClick,
        color = if (active) Ink.SurfaceHigh else Ink.Surface,
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                when {
                    completed -> Icons.Default.CheckCircle
                    lesson.kind == "DOCUMENT" || lesson.kind == "TEXT" -> Icons.Default.Description
                    else -> Icons.Default.PlayArrow
                },
                null,
                tint = if (completed) Ink.Teal else Ink.Amber,
                modifier = Modifier.size(20.dp),
            )
            Text(
                lesson.displayTitle,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (lesson.durationSeconds > 0) {
                Text(
                    trf(StrCourses.minutesShort, lesson.durationSeconds / 60),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}


@Composable
private fun DownloadButton(label: String, busy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SurfaceHigh)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Ink.Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.Download, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(
            if (busy) tr(StrContent.preparing) else label,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

/** A reading lesson's blocks, in order. */
@Composable
private fun LessonBlocksView(blocks: List<LessonBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block.type) {
                LessonBlock.HEADING -> Text(
                    block.text,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                LessonBlock.IMAGE -> Column {
                    if (!block.url.isNullOrBlank()) {
                        coil3.compose.AsyncImage(
                            model = block.url,
                            contentDescription = block.caption,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Ink.SurfaceHigh),
                        )
                    }
                    if (!block.caption.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            block.caption,
                            color = Ink.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                LessonBlock.LIST -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SurfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.items.filter { it.isNotBlank() }.forEach { item ->
                        Text("•  $item", color = Ink.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> Text(block.text, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun UnitExerciseRow(
    item: CourseExerciseItem,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
) {
    InkCard(modifier = modifier, contentPadding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FactCheck, null, tint = Ink.Teal, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    trf(StrContent.questionsShort, item.questionCount),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (unlocked && !item.isLocked) {
                Pill(tr(StrContent.start), Modifier.clickable(onClick = onStart))
            }
        }
    }
}
