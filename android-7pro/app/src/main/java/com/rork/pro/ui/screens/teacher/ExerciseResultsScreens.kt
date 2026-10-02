package com.rork.pro.ui.screens.teacher

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AnswerReview
import com.rork.pro.data.Async
import com.rork.pro.data.ExerciseRepository
import com.rork.pro.data.StudentAttempt
import com.rork.pro.data.TestRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.test.AnswerReviewSection
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExerciseResultsViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<StudentAttempt>>>(Async.Loading)
    val state: StateFlow<Async<List<StudentAttempt>>> = _state.asStateFlow()

    fun load(exerciseId: String) {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching { ExerciseRepository.attempts(exerciseId) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }
}

/**
 * Who sat this exercise and how they did. Tapping a row opens that student's full answer
 * review — the same component the student sees on their own result screen, so a teacher and a
 * learner are always looking at exactly the same thing.
 */
@Composable
fun ExerciseResultsScreen(navController: NavHostController, exerciseId: String) {
    val vm: ExerciseResultsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(exerciseId) { vm.load(exerciseId) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrEx.studentResults), onBack = { navController.popBackStack() })
        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load(exerciseId) }
            is Async.Success -> {
                // Only finished attempts carry a meaningful score; one still in progress would
                // show a misleading 0%.
                val done = s.value.filter { it.attempt.submittedAt != null }
                // A student who retries the same exercise gets a new row every time, which reads
                // as the same name repeated down the list. The repo already returns attempts
                // newest-first, so keeping just the first row per student keeps their latest one.
                val latestPerStudent = done.distinctBy { it.attempt.userId }
                if (latestPerStudent.isEmpty()) {
                    EmptyBlock(tr(StrEx.noAttempts), tr(StrEx.noAttemptsBody))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(Dimens.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            ResultsSummary(
                                attempts = latestPerStudent.size,
                                average = latestPerStudent.map { it.attempt.percent }.average(),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(latestPerStudent, key = { it.attempt.id }) { row ->
                            StudentAttemptRow(row) {
                                navController.navigate(TeacherRoutes.studentAttempt(row.attempt.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSummary(attempts: Int, average: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            attempts.toString() to tr(StrEx.summaryAttempts),
            "${average.toInt()}%" to tr(StrEx.summaryAverage),
        ).forEachIndexed { index, (value, label) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink.SurfaceHigh)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
            ) {
                Text(
                    value,
                    color = if (index == 0) Ink.Sky else Ink.Amber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StudentAttemptRow(row: StudentAttempt, onClick: () -> Unit) {
    val percent = row.attempt.percent
    val accent = when {
        percent >= 80 -> Ink.Teal
        percent >= 50 -> Ink.Amber
        else -> Ink.Coral
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SurfaceHigh)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.student?.avatarUrl, row.student?.fullName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.student?.fullName?.takeIf { it.isNotBlank() } ?: tr(StrEx.unknownStudent),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            formatDate(row.attempt.submittedAt).takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "${percent.toInt()}%",
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

class StudentAttemptViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<AnswerReview>>>(Async.Loading)
    val state: StateFlow<Async<List<AnswerReview>>> = _state.asStateFlow()

    fun load(attemptId: String) {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching { TestRepository.review(attemptId) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }
}

/** One student's attempt, answer by answer — the teacher's side of the same review. */
@Composable
fun StudentAttemptScreen(navController: NavHostController, attemptId: String) {
    val vm: StudentAttemptViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(attemptId) { vm.load(attemptId) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrEx.attemptReview), onBack = { navController.popBackStack() })
        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load(attemptId) }
            is Async.Success -> {
                if (s.value.isEmpty()) {
                    // An attempt with nothing stored — an old one, or one abandoned before any
                    // answer was saved. Saying so plainly beats an empty screen.
                    EmptyBlock(tr(StrEx.noStoredAnswers), tr(StrEx.noStoredAnswersBody))
                } else {
                    LazyColumn(contentPadding = PaddingValues(Dimens.screenPadding)) {
                        item { AnswerReviewSection(s.value) }
                    }
                }
            }
        }
    }
}
