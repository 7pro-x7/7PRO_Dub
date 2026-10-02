package com.rork.pro.ui.screens.test

import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.coursePriceLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import com.rork.pro.data.AnswerResult
import com.rork.pro.data.AnswerSounds
import com.rork.pro.data.AppError
import com.rork.pro.data.AnswerReview
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.NextQuestion
import com.rork.pro.data.PlacementTest
import com.rork.pro.data.Recommendations
import com.rork.pro.data.TestQuestionView
import com.rork.pro.data.TestRepository
import com.rork.pro.data.TestResult
import com.rork.pro.data.lockStatesFor
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.LessonVideo
import com.rork.pro.ui.components.LevelDial
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.QuestionAudio
import com.rork.pro.ui.components.QuestionImage
import com.rork.pro.ui.components.resolveLessonMedia
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.SkillBar
import com.rork.pro.ui.components.StatTile
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.StrTest
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.i18n.levelLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.Str
import java.util.Locale

// ---------------------------------------------------------------- Shared visual language
//
// Every colour below is read from `Ink`, which flips with the light / dark switch, so the
// placement experience never carries a hard-coded palette of its own. Text comes from `Tr`
// phrases and layout uses start/end padding, so Arabic mirrors the whole screen for free.

/** Accent of a CEFR band: teal for the first steps, sky in the middle, amber at the top. */
private fun accentFor(level: String?): Color = when (level?.uppercase(Locale.US)) {
    "A1", "A2" -> Ink.Teal
    "B1", "B2" -> Ink.Sky
    else -> Ink.Amber
}

private val CEFR_STEPS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

/** Page wash: the app canvas plus a soft accent bloom behind the header. */
@Composable
private fun ScreenBackdrop(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .appBackdrop(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        radius = 780f,
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding(), content = content)
    }
}

/** Rounded panel used for every block on these screens. */
@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = Ink.Amber,
    radius: Dp = 24.dp,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.07f), Ink.Surface, Ink.Surface),
                ),
            )
            .border(1.dp, Ink.Hairline, shape)
            .padding(padding),
        content = content,
    )
}

/** Level code inside a partly drawn ring — the badge of the whole placement flow. */
@Composable
private fun LevelMedallion(code: String, accent: Color, size: Dp = 58.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val radius = this.size.minDimension / 2 - stroke
            drawCircle(color = accent.copy(alpha = 0.16f), radius = radius, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accent.copy(alpha = 0.2f), accent, accent.copy(alpha = 0.2f)),
                ),
                startAngle = -100f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            code,
            color = accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            maxLines = 1,
        )
    }
}

/** Small icon + label chip used for question count, duration and mode. */
@Composable
private fun MetaChip(icon: ImageVector, label: String, accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(13.dp))
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Start

class PlacementStartViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<PlacementStartData>>(Async.Loading)
    val state: StateFlow<Async<PlacementStartData>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                val tests = CatalogRepository.publishedTests()
                PlacementStartData(tests, TestRepository.bestPercentByTest(tests.map { it.id }))
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

/** The published tests plus the learner's best score on each, so a locked card can explain why. */
data class PlacementStartData(val tests: List<PlacementTest>, val bestPercent: Map<String, Double>)

@Composable
fun PlacementStartScreen(navController: NavHostController) {
    val vm: PlacementStartViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    ScreenBackdrop(Ink.Amber) {
        DetailHeader(tr(StrTest.placementTest), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val tests = s.value.tests
                val locks = remember(s.value) { lockStatesFor(tests, s.value.bestPercent) }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { PlacementHero() }

                    if (tests.isEmpty()) {
                        item {
                            EmptyBlock(
                                tr(StrTest.noTestPublished),
                                tr(StrTest.noTestPublishedBody),
                            )
                        }
                    } else {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    tr(StrTest.availableTests),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Ink.TextPrimary,
                                )
                                Pill(trf(StrTest.testsCount, tests.size))
                            }
                        }
                        itemsIndexed(tests, key = { _, it -> it.id }) { index, test ->
                            PlacementTestCard(
                                test = test,
                                locked = locks[index],
                                bestPercent = s.value.bestPercent[test.id],
                            ) { navController.navigate(Routes.testRun(test.id)) }
                        }
                    }

                    item { TrustCard() }
                }
            }
        }
    }
}

/** Opening panel: what the test is, plus the ladder of levels it can land on. */
@Composable
private fun PlacementHero() {
    GlassPanel(accent = Ink.Amber, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
            Text(
                tr(StrTest.kicker).uppercase(Locale.ROOT),
                color = Ink.Amber,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tr(StrTest.findYourLevel),
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(StrTest.adaptiveIntro),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        CefrLadder()
        Spacer(Modifier.height(10.dp))
        Text(
            tr(StrTest.cefrScale),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Six rising bars, one per CEFR band — mirrors with the language, so it always climbs forward. */
@Composable
private fun CefrLadder(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        CEFR_STEPS.forEachIndexed { index, code ->
            val accent = accentFor(code)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((16 + index * 8).dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 7.dp,
                                topEnd = 7.dp,
                                bottomStart = 3.dp,
                                bottomEnd = 3.dp,
                            ),
                        )
                        .background(
                            Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))),
                        ),
                )
                Spacer(Modifier.height(7.dp))
                Text(code, color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** One published placement test, presented as its own premium card. Locked cards explain, in
 *  the learner's own numbers, exactly what unlocks them — never just a bare padlock. */
@Composable
private fun PlacementTestCard(
    test: PlacementTest,
    locked: Boolean = false,
    bestPercent: Double? = null,
    onStart: () -> Unit,
) {
    val accent = accentFor(test.targetLevel)
    val passed = bestPercent != null && bestPercent >= test.passingScore
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !locked) 0.99f else 1f, tween(140), label = "testCard")
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (locked) 0.55f else 1f)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.13f), Ink.Surface, Ink.Surface),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !locked, onClick = onStart)
            .padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelMedallion(test.targetLevel?.take(2) ?: "7", accent)
            Column(Modifier.weight(1f)) {
                val bandName = levelLabel(test.targetLevel).ifBlank {
                    if (test.isAdaptive) tr(StrTest.adaptive) else tr(StrTest.fixed)
                }
                Text(
                    bandName,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    test.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked) {
                Icon(Icons.Default.Lock, null, tint = Ink.TextMuted, modifier = Modifier.size(20.dp))
            } else if (passed) {
                Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal, modifier = Modifier.size(22.dp))
            }
        }

        if (!test.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                test.description,
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetaChip(
                icon = if (test.isAdaptive) Icons.Default.BarChart else Icons.Default.Lock,
                label = if (test.isAdaptive) tr(StrTest.adaptive) else tr(StrTest.fixed),
                accent = accent,
            )
            MetaChip(Icons.Default.HelpOutline, trf(StrTest.questionsCount, test.questionCount), accent)
            MetaChip(Icons.Default.Schedule, trf(StrTest.minutes, test.timeLimitSeconds / 60), accent)
        }

        Spacer(Modifier.height(16.dp))
        if (locked) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SurfaceHigh)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Lock, null, tint = Ink.TextMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    trf(StrTest.completeToUnlock, test.passingScore.toInt()),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (accent == Ink.Amber) Ink.OnAmber else Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    when {
                        passed -> tr(StrTest.retakeLabel)
                        bestPercent != null -> tr(StrTest.tryAgainLabel)
                        else -> tr(StrTest.startFreeTest)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
            if (bestPercent != null && !passed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    trf(StrTest.lastScoreLabel, bestPercent.toInt()),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Closing reassurance panel. */
@Composable
private fun TrustCard() {
    GlassPanel(accent = Ink.Teal, radius = 20.dp, padding = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = Ink.Teal, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tr(StrTest.trustTitle),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tr(StrTest.trustBody),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Runner

sealed interface RunnerState {
    data object Loading : RunnerState
    data class Question(val question: TestQuestionView, val index: Int, val total: Int) : RunnerState
    data class Finished(val attemptId: String) : RunnerState
    data class Failed(val error: AppError) : RunnerState
}

class PlacementRunnerViewModel : ViewModel() {
    private val _state = MutableStateFlow<RunnerState>(RunnerState.Loading)
    val state: StateFlow<RunnerState> = _state.asStateFlow()

    private val _feedback = MutableStateFlow<AnswerResult?>(null)
    val feedback: StateFlow<AnswerResult?> = _feedback.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // Drives the runner's own branding (teal "Exercise" header vs. amber "Level test" header),
    // the same distinction PlacementResultScreen already makes from `kind` further down the
    // flow — so a teacher's exercise reads as an exercise from the very first question, not
    // just once it's scored.
    private val _test = MutableStateFlow<PlacementTest?>(null)
    val test: StateFlow<PlacementTest?> = _test.asStateFlow()

    /** Seconds left on the server clock, re-synced on every question so it can never drift. */
    private val _secondsLeft = MutableStateFlow<Int?>(null)
    val secondsLeft: StateFlow<Int?> = _secondsLeft.asStateFlow()

    private val _timedOut = MutableStateFlow(false)
    val timedOut: StateFlow<Boolean> = _timedOut.asStateFlow()

    private var attemptId: String? = null
    private var ticker: Job? = null
    private var finishing: Boolean = false

    fun start(testId: String) {
        viewModelScope.launch {
            ticker?.cancel()
            finishing = false
            _timedOut.value = false
            _feedback.value = null
            _test.value = null
            _state.value = RunnerState.Loading
            runCatching {
                val attempt = TestRepository.start(testId)
                attemptId = attempt.attemptId
                _secondsLeft.value = attempt.secondsLeft ?: attempt.timeLimitSeconds.takeIf { it > 0 }
                TestRepository.next(attempt.attemptId)
            }.onSuccess { serve(it) }
                .onFailure { fail(it) }
            // Best-effort and non-blocking: the question sheet must never wait on this, and a
            // failure here just leaves the runner in its default placement-test branding.
            attemptId?.let { id ->
                runCatching { TestRepository.attemptTest(id) }.getOrNull()?.let { _test.value = it }
            }
        }
    }

    fun answer(questionId: String, selected: List<Int>, text: String?) {
        val attempt = attemptId ?: return
        viewModelScope.launch {
            _busy.value = true
            runCatching { TestRepository.answer(attempt, questionId, selected, text) }
                .onSuccess { _feedback.value = it }
                .onFailure { fail(it) }
            _busy.value = false
        }
    }

    fun advance() {
        val attempt = attemptId ?: return
        viewModelScope.launch {
            _feedback.value = null
            _busy.value = true
            runCatching { TestRepository.next(attempt) }
                .onSuccess { serve(it) }
                .onFailure { fail(it) }
            _busy.value = false
        }
    }

    /** Records a blank answer for a question that cannot be answered, so the run continues. */
    fun skip(questionId: String) {
        val attempt = attemptId ?: return
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                TestRepository.answer(attempt, questionId, emptyList(), null)
                TestRepository.next(attempt)
            }.onSuccess {
                _feedback.value = null
                serve(it)
            }.onFailure { fail(it) }
            _busy.value = false
        }
    }

    private fun serve(next: NextQuestion) {
        next.secondsLeft?.let { _secondsLeft.value = it }
        if (next.done || next.question == null) {
            if (next.reason == "TIME_UP") _timedOut.value = true
            finishNow()
            return
        }
        _state.value = RunnerState.Question(next.question, next.index, next.total)
        startTicker()
    }

    /**
     * Drives the on-screen countdown. The server enforces the limit anyway; this makes the
     * deadline visible instead of ending the test without warning.
     */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        if (_secondsLeft.value == null) return
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val left = _secondsLeft.value ?: return@launch
                if (left <= 1) {
                    _secondsLeft.value = 0
                    _timedOut.value = true
                    finishNow()
                    return@launch
                }
                _secondsLeft.value = left - 1
            }
        }
    }

    private fun finishNow() {
        val attempt = attemptId ?: return
        if (finishing) return
        finishing = true
        ticker?.cancel()
        _state.value = RunnerState.Loading
        viewModelScope.launch {
            runCatching { TestRepository.finish(attempt) }
                .onSuccess { _state.value = RunnerState.Finished(it.attemptId) }
                .onFailure {
                    finishing = false
                    fail(it)
                }
        }
    }

    private fun fail(error: Throwable) {
        ticker?.cancel()
        _state.value = RunnerState.Failed(error.toAppError())
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}

@Composable
fun PlacementRunnerScreen(navController: NavHostController, testId: String) {
    val vm: PlacementRunnerViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val secondsLeft by vm.secondsLeft.collectAsStateWithLifecycle()
    val timedOut by vm.timedOut.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val isExercise = test?.kind == "EXERCISE"

    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var typed by remember { mutableStateOf("") }

    // AnimatedVisibility keeps rendering its content while it fades out, and by then `advance()`
    // has already nulled `feedback` for the next question — so the banner would briefly recompute
    // from null (defaulting to "wrong", in red) and flash an incorrect verdict on a correct answer
    // right as it exits. Holding the last non-null result lets the exit animation fade out the
    // real verdict instead of a stale-null one.
    var lastFeedback by remember { mutableStateOf<AnswerResult?>(null) }
    LaunchedEffect(feedback) { if (feedback != null) lastFeedback = feedback }

    // Same runner serves teacher exercises and level tests, so one hook covers both: a cheerful
    // chime for a correct answer, a soft tone for a wrong one. Ungraded answers (no model answer)
    // stay silent since there is no verdict to announce.
    val soundContext = LocalContext.current
    LaunchedEffect(Unit) { AnswerSounds.preload(soundContext) }
    LaunchedEffect(feedback) {
        val f = feedback ?: return@LaunchedEffect
        if (f.graded) AnswerSounds.play(soundContext, f.correct)
    }

    LaunchedEffect(testId) { vm.start(testId) }

    // Keyed on the question, so a new question always starts from a clean answer sheet while
    // feedback for the current one never wipes what the learner just picked.
    val questionId = (state as? RunnerState.Question)?.question?.id
    LaunchedEffect(questionId) {
        picked = emptySet()
        typed = ""
    }

    LaunchedEffect(state) {
        val finished = state as? RunnerState.Finished ?: return@LaunchedEffect
        navController.navigate(Routes.testResult(finished.attemptId)) {
            popUpTo(Routes.TEST_START) { inclusive = true }
        }
    }

    val accent = if (isExercise) Ink.Teal else Ink.Amber
    val headerTitle = if (isExercise) tr(StrEx.exerciseTag) else tr(StrTest.levelTest)
    ScreenBackdrop(accent) {
        DetailHeader(headerTitle, onBack = { navController.popBackStack() })

        when (val s = state) {
            is RunnerState.Loading, is RunnerState.Finished -> LoadingBlock(
                Modifier.fillMaxSize(),
                if (timedOut) tr(StrTest.timeUpBody) else tr(StrTest.scoringAnswers),
            )
            is RunnerState.Failed -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.start(testId) }
            is RunnerState.Question -> {
                val multi = s.question.kind == "MULTI"
                val options: List<String> = when {
                    s.question.options.isNotEmpty() -> s.question.options
                    s.question.kind == "TRUE_FALSE" -> listOf(tr(StrTest.trueLabel), tr(StrTest.falseLabel))
                    else -> emptyList()
                }
                val answerable = s.question.kind == "TEXT" || options.isNotEmpty()

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            top = 4.dp,
                            bottom = 130.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { RunProgress(s.index, s.total, secondsLeft, accent) }

                        item {
                            GlassPanel(accent = accent, padding = 18.dp) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MetaChip(
                                        Icons.Default.AutoAwesome,
                                        codeLabel(s.question.skill.uppercase(Locale.US)),
                                        accent,
                                    )
                                    Text(
                                        trf(StrTest.questionXofY, s.index, s.total),
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    s.question.prompt,
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                // Media is optional and dynamic: a question shows whatever the
                                // academy attached to it, and nothing at all when it attached none.
                                QuestionMediaBlock(s.question)
                            }
                        }

                        if (s.question.kind == "TEXT") {
                            item {
                                com.rork.pro.ui.screens.auth.InkField(
                                    typed,
                                    { typed = it },
                                    tr(StrTest.yourAnswer),
                                    singleLine = false,
                                    minLines = 3,
                                )
                            }
                        } else if (options.isNotEmpty()) {
                            if (multi) {
                                item {
                                    Text(
                                        tr(StrTest.chooseAllThatApply),
                                        color = Ink.TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            items(options.size) { index ->
                                OptionRow(
                                    number = index + 1,
                                    text = options[index],
                                    selected = index in picked,
                                    locked = feedback != null,
                                    isCorrectSelection = feedback?.correct == true,
                                    accent = accent,
                                ) {
                                    // Single-answer questions replace the choice; multi-answer
                                    // ones toggle it, which is why a MULTI question could never
                                    // be answered correctly before.
                                    picked = when {
                                        !multi -> setOf(index)
                                        index in picked -> picked - index
                                        else -> picked + index
                                    }
                                }
                            }
                        } else {
                            item {
                                EmptyBlock(
                                    tr(StrTest.questionUnavailable),
                                    tr(StrTest.questionUnavailableBody),
                                )
                            }
                        }

                        item {
                            Column(Modifier.fillMaxWidth()) {
                                AnimatedVisibility(
                                    visible = feedback != null,
                                    enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
                                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f),
                                ) {
                                    FeedbackBanner(feedback ?: lastFeedback)
                                }
                            }
                        }
                    }

                    // Bottom action bar, lifted above the page wash.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Ink.CanvasDeep.copy(alpha = 0f), Ink.CanvasDeep, Ink.CanvasDeep),
                                ),
                            )
                            .padding(horizontal = Dimens.screenPadding, vertical = 14.dp)
                            .navigationBarsPadding(),
                    ) {
                        if (feedback != null) {
                            PrimaryAction(tr(StrTest.nextQuestion), loading = busy) { vm.advance() }
                        } else if (!answerable) {
                            PrimaryAction(tr(StrTest.skipQuestion), loading = busy) { vm.skip(s.question.id) }
                        } else {
                            PrimaryAction(
                                tr(StrTest.submitAnswer),
                                enabled = when {
                                    s.question.kind == "TEXT" -> typed.isNotBlank()
                                    else -> picked.isNotEmpty()
                                },
                                loading = busy,
                            ) {
                                vm.answer(
                                    s.question.id,
                                    picked.sorted(),
                                    typed.takeIf { s.question.kind == "TEXT" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Progress track plus the live countdown, which pulses once the last half minute starts. */
@Composable
private fun RunProgress(index: Int, total: Int, secondsLeft: Int?, accent: Color) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) ((index - 1).toFloat() / total).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(500),
        label = "progress",
    )
    val urgent = (secondsLeft ?: Int.MAX_VALUE) <= 30
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.SurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent)),
                    ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(progress * 100).toInt()}%",
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            secondsLeft?.let { left ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (urgent) Ink.Coral.copy(alpha = 0.14f * pulse + 0.06f) else Ink.SurfaceHigh,
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = tr(StrTest.timeLeftLabel),
                        tint = if (urgent) Ink.Coral else Ink.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        clockText(left),
                        color = if (urgent) Ink.Coral else Ink.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * Verdict for the answer that was just submitted.
 *
 * Deliberately louder than a typical inline hint: a tinted gradient card, a circular icon badge
 * that pops in with a spring bounce, and a bold colored headline — so "right" and "wrong" read
 * unmistakably at a glance instead of blending into the rest of the question card.
 */
@Composable
private fun FeedbackBanner(feedback: AnswerResult?) {
    val graded = feedback?.graded != false
    val isCorrect = feedback?.correct == true
    val color = when {
        !graded -> Ink.Sky
        isCorrect -> Ink.Teal
        else -> Ink.Coral
    }
    val icon = when {
        !graded -> Icons.Default.Info
        isCorrect -> Icons.Default.CheckCircle
        else -> Icons.Default.Cancel
    }
    val shape = RoundedCornerShape(20.dp)

    // The badge scales up from a small dot into full size with a springy overshoot each time a
    // new verdict lands, which is what makes the result feel like it "arrives" rather than just
    // being painted on screen.
    val badgeScale = remember(feedback?.correct, feedback?.graded) { Animatable(0.3f) }
    LaunchedEffect(feedback?.correct, feedback?.graded) {
        badgeScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.08f)),
                ),
            )
            .border(1.5.dp, color.copy(alpha = 0.45f), shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .scale(badgeScale.value)
                .size(46.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.20f))
                .border(1.5.dp, color.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    !graded -> tr(StrTest.answerRecorded)
                    isCorrect -> tr(StrTest.correct)
                    else -> tr(StrTest.notQuite)
                },
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            if (!feedback?.explanation.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    feedback?.explanation.orEmpty(),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Image, audio and video attached to a placement question, in that order. */
@Composable
private fun QuestionMediaBlock(question: TestQuestionView) {
    val image = question.imageUrl?.trim().orEmpty()
    val audio = question.audioUrl?.trim().orEmpty()
    val video = question.videoUrl?.trim().orEmpty()
    if (image.isEmpty() && audio.isEmpty() && video.isEmpty()) return

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (image.isNotEmpty()) QuestionImage(image)
        if (audio.isNotEmpty()) QuestionAudio(audio)
        if (video.isNotEmpty()) LessonVideo(resolveLessonMedia(video))
    }
}

/** Whole seconds as m:ss, for the countdown chip. */
private fun clockText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

/**
 * One answer option.
 *
 * The leading badge carries the option number rather than a letter, so the sheet reads the
 * same in Arabic and English. Once the question is locked, the row the learner picked flips
 * from the neutral accent to a clear verdict color — teal-green with a check for right, coral
 * with an X for wrong — while every other row fades back so the picked answer is the only thing
 * still fighting for attention.
 */
@Composable
private fun OptionRow(
    number: Int,
    text: String,
    selected: Boolean,
    locked: Boolean,
    isCorrectSelection: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val verdictColor = when {
        !locked || !selected -> null
        isCorrectSelection -> Ink.Teal
        else -> Ink.Coral
    }
    val effectiveAccent = verdictColor ?: accent

    val pressScale by animateFloatAsState(
        if (pressed && !locked) 0.985f else 1f,
        tween(120),
        label = "option",
    )
    // A small pop the instant a verdict lands on this row, so it visibly reacts rather than
    // just silently recoloring.
    val popScale = remember(verdictColor) { Animatable(if (verdictColor != null) 0.92f else 1f) }
    LaunchedEffect(verdictColor) {
        if (verdictColor != null) {
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            verdictColor != null -> verdictColor
            selected -> accent
            else -> Ink.Hairline
        },
        animationSpec = tween(200),
        label = "optBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            verdictColor != null -> verdictColor.copy(alpha = 0.14f)
            selected -> accent.copy(alpha = 0.10f)
            else -> Ink.Surface
        },
        animationSpec = tween(200),
        label = "optBg",
    )
    // Rows the learner didn't pick recede once the answer is locked, so the verdict on the
    // chosen row reads instantly instead of competing with a wall of identical options.
    val rowAlpha by animateFloatAsState(
        if (locked && !selected) 0.45f else 1f,
        tween(200),
        label = "optAlpha",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .scale(pressScale * popScale.value)
            .clip(shape)
            .background(bgColor)
            .border(if (selected || verdictColor != null) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !locked,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (selected) effectiveAccent else Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    if (verdictColor == Ink.Coral) Icons.Default.Cancel else Icons.Default.CheckCircle,
                    null,
                    tint = if (verdictColor == null && accent == Ink.Amber) Ink.OnAmber else Color.White,
                    modifier = Modifier.size(17.dp),
                )
            } else {
                Text(
                    number.toString(),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            text,
            color = if (verdictColor != null) effectiveAccent else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (verdictColor != null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------- Result

data class ResultData(
    val result: TestResult,
    val recommendations: Recommendations,
    val test: PlacementTest?,
    /** Per-question review. Empty when the viewer isn't allowed to see it, or nothing was stored. */
    val review: List<AnswerReview> = emptyList(),
)

class PlacementResultViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<ResultData>>(Async.Loading)
    val state: StateFlow<Async<ResultData>> = _state.asStateFlow()

    /**
     * Reads the stored result. This screen used to call the closing routine, which meant
     * merely opening an old score could close a test the learner still had open.
     */
    fun load(attemptId: String) {
        viewModelScope.launch {
            _state.value = Async.Loading
            // Fetch the result first — this is the critical call.
            val result = runCatching { TestRepository.result(attemptId) }
            result.onFailure { _state.value = Async.Failure(it.toAppError()); return@launch }
            // The test itself — its kind and its own passing score — is what tells this screen
            // whether to show a CEFR level or a plain pass/fail exercise result, and what the
            // learner actually needed to clear. Optional: a stale or deleted test must not hide
            // the score itself.
            val test = runCatching { TestRepository.attemptTest(attemptId) }.getOrNull()
            // Recommendations are optional; a failure here must not hide the score.
            val recs = runCatching {
                result.getOrNull()?.level?.let { TestRepository.recommendations(it) }
            }.getOrNull() ?: Recommendations()
            // The per-question review is a bonus, never a reason to hide the score: an old
            // attempt with no stored answers, or a viewer the database won't show them to,
            // comes back empty and the screen simply omits the section.
            val review = runCatching { TestRepository.review(attemptId) }.getOrDefault(emptyList())
            _state.value = Async.Success(
                ResultData(
                    result = result.getOrThrow(),
                    recommendations = recs,
                    test = test,
                    review = review,
                ),
            )
        }
    }
}

@Composable
fun PlacementResultScreen(navController: NavHostController, attemptId: String, session: SessionViewModel) {
    val vm: PlacementResultViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(attemptId) {
        vm.load(attemptId)
        session.loadEverything()
    }

    val data = (state as? Async.Success)?.value
    // A teacher exercise has no CEFR band, so its own `kind` — not a guess from a missing level
    // — decides whether this is a level result or a plain pass/fail one.
    val isExercise = data?.test?.kind == "EXERCISE"
    val passed = data?.result?.passed == true
    val level = data?.result?.level
    val accent = when {
        isExercise -> if (passed) Ink.Teal else Ink.Coral
        else -> accentFor(level)
    }

    ScreenBackdrop(accent) {
        DetailHeader(
            if (isExercise) tr(StrTest.result) else tr(StrTest.yourLevelResult),
            onBack = { navController.popBackStack() },
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load(attemptId) }
            // An attempt that ended with nothing answered has nothing to score, so it gets an
            // honest explanation and a way back into the test instead of a fake 0% dial. A
            // teacher exercise legitimately has no CEFR level even when fully answered, so a
            // missing level alone is no longer read as "unfinished".
            is Async.Success -> if (s.value.result.answered == 0) {
                EmptyBlock(
                    tr(StrTest.notCompleted),
                    tr(StrTest.notCompletedBody),
                    Modifier.fillMaxSize(),
                    actionLabel = tr(StrTest.retakeTest),
                    onAction = {
                        navController.navigate(Routes.TEST_START) {
                            popUpTo(Routes.TEST_START) { inclusive = true }
                        }
                    },
                )
            } else {
                val passingScore = s.value.test?.passingScore ?: 50.0
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            top = 4.dp,
                            bottom = 120.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            if (isExercise) {
                                ExerciseResultHero(s.value.result, passingScore, accent)
                            } else {
                                ResultHero(s.value.result, accent)
                                Spacer(Modifier.height(12.dp))
                                PassFailStrip(s.value.result, passingScore, accent)
                            }
                        }

                        // The per-question review sits right under the score, before the
                        // skill averages: "which ones did I get wrong" is the first thing a
                        // learner looks for, and a breakdown by skill can't answer it.
                        if (s.value.review.isNotEmpty()) {
                            item {
                                GlassPanel(accent = accent, padding = 18.dp) {
                                    AnswerReviewSection(s.value.review)
                                }
                            }
                        }

                        if (s.value.result.skills.isNotEmpty()) {
                            item {
                                GlassPanel(accent = accent, padding = 18.dp) {
                                    Text(
                                        tr(StrTest.skillBreakdown),
                                        color = Ink.TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    s.value.result.skills.entries.sortedByDescending { it.value }
                                        .forEach { (skill, value) ->
                                            SkillBar(
                                                codeLabel(skill.uppercase(Locale.US)),
                                                value,
                                                Modifier.padding(vertical = 6.dp),
                                                color = accent,
                                            )
                                        }
                                }
                            }
                        }

                        if (s.value.result.strengths.isNotEmpty() || s.value.result.weaknesses.isNotEmpty()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    InsightCard(
                                        tr(StrTest.strengths),
                                        s.value.result.strengths.joinToString(", ") { codeLabel(it.uppercase(Locale.US)) },
                                        Ink.Teal,
                                        Modifier.weight(1f),
                                    )
                                    InsightCard(
                                        tr(StrTest.focus),
                                        s.value.result.weaknesses.joinToString(", ") { codeLabel(it.uppercase(Locale.US)) },
                                        Ink.Coral,
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // Course recommendations are tied to a CEFR level; an exercise has none,
                        // so this whole shelf — and its "browse courses" bottom action — is a
                        // placement-only feature.
                        if (!isExercise) {
                            if (s.value.recommendations.courses.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        trf(StrTest.recommendedFor, s.value.result.level),
                                        actionLabel = tr(StrTest.seeAll),
                                        onAction = { navController.navigate(Routes.COURSES) },
                                    )
                                }
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(s.value.recommendations.courses, key = { it.id }) { course ->
                                            val shape = RoundedCornerShape(20.dp)
                                            Column(
                                                Modifier
                                                    .width(210.dp)
                                                    .clip(shape)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(accent.copy(alpha = 0.12f), Ink.Surface),
                                                        ),
                                                    )
                                                    .border(1.dp, accent.copy(alpha = 0.22f), shape)
                                                    .clickable { navController.navigate(Routes.courseDetail(course.id)) }
                                                    .padding(16.dp),
                                            ) {
                                                Text(
                                                    course.displayTitle,
                                                    color = Ink.TextPrimary,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                Pill(
                                                    if (course.isFreeCourse) {
                                                        tr(Str.free)
                                                    } else {
                                                        coursePriceLabel(course)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    EmptyBlock(
                                        tr(StrTest.noRecommendations),
                                        tr(StrTest.noRecommendationsBody),
                                    )
                                }
                            }
                        }
                        item { AdBanner("RESULT") }
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Ink.CanvasDeep.copy(alpha = 0f), Ink.CanvasDeep, Ink.CanvasDeep),
                                ),
                            )
                            .padding(horizontal = Dimens.screenPadding, vertical = 14.dp)
                            .navigationBarsPadding(),
                    ) {
                        if (!isExercise) {
                            // Booking leads, browsing follows: a student who has just been
                            // told their level is being offered the thing that acts on it.
                            ResultActions(
                                onBook = { navController.navigate(Routes.BOOKING_TEACHERS) },
                                onBrowse = { navController.navigate(Routes.COURSES) },
                            )
                        } else {
                            val testId = s.value.test?.id
                            val ownerId = s.value.test?.ownerId
                            if (passed || testId == null) {
                                PrimaryAction(tr(StrTest.backToList)) {
                                    if (ownerId != null) {
                                        // Back to the level this exercise lives in, keeping
                                        // teacher -> level underneath so "back" still walks
                                        // the same path the learner came down.
                                        val levelKey = s.value.test?.sectionId ?: UNFILED_LEVEL
                                        navController.navigate(Routes.levelExercises(ownerId, levelKey)) {
                                            popUpTo(Routes.TEACHER_EXERCISES)
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            } else {
                                PrimaryAction(tr(StrTest.tryAgainLabel)) {
                                    navController.navigate(Routes.testRun(testId)) {
                                        popUpTo(Routes.TEST_RUN) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A compact pass/fail line under a CEFR result: the same real numbers, just made explicit. */
@Composable
private fun PassFailStrip(result: TestResult, passingScore: Double, accent: Color) {
    val passed = result.passed
    val statusColor = if (passed) Ink.Teal else Ink.Coral
    GlassPanel(accent = statusColor, padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                null,
                tint = statusColor,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (passed) tr(StrTest.passedLabel) else tr(StrTest.failedLabel),
                    color = statusColor,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    trf(StrTest.passRequirement, passingScore.toInt()),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Result hero for a teacher exercise: no CEFR band applies, so this leads with a plain,
 *  legible pass/fail verdict and the exact numbers behind it — never a dial standing in for a
 *  level the exercise was never scored against. */
@Composable
private fun ExerciseResultHero(result: TestResult, passingScore: Double, accent: Color) {
    val passed = result.passed
    GlassPanel(accent = accent, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null,
                    tint = accent,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (passed) tr(StrTest.passedLabel) else tr(StrTest.failedLabel),
                color = accent,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (passed) tr(StrTest.nextUnlockedBody) else trf(StrTest.retryBody, passingScore.toInt()),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatTile("${result.percent.toInt()}%", tr(StrTest.yourScore), accent = accent)
                Box(Modifier.width(1.dp).height(34.dp).background(Ink.Hairline))
                StatTile("${passingScore.toInt()}%", tr(StrTest.scoreLabel), accent = Ink.TextPrimary)
                Box(Modifier.width(1.dp).height(34.dp).background(Ink.Hairline))
                StatTile(result.answered.toString(), tr(StrTest.answeredLabel), accent = Ink.TextPrimary)
            }
        }
    }
}

/** Dial, level name and the three headline numbers of the attempt. */
@Composable
private fun ResultHero(result: TestResult, accent: Color) {
    GlassPanel(accent = accent, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LevelDial(
                level = result.level ?: "—",
                percent = result.percent,
                accent = accent,
                caption = levelLabel(result.level).ifBlank { null },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                levelLabel(result.level).ifBlank { result.level ?: tr(StrTest.result) },
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            result.betterThanPercent?.let {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = accent, modifier = Modifier.size(15.dp))
                    Text(
                        trf(StrTest.topPercentOfLearners, (100 - it).toInt()),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatTile("${result.percent.toInt()}%", tr(StrTest.scoreLabel), accent = accent)
                Box(Modifier.width(1.dp).height(34.dp).background(Ink.Hairline))
                StatTile(result.answered.toString(), tr(StrTest.answeredLabel), accent = Ink.TextPrimary)
                result.betterThanPercent?.let {
                    Box(Modifier.width(1.dp).height(34.dp).background(Ink.Hairline))
                    StatTile("${(100 - it).toInt()}%", tr(StrTest.rankLabel), accent = Ink.Teal)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.20f), shape)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            body.ifBlank { "—" },
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}


/**
 * The two things worth doing with a level result.
 *
 * Booking is the primary action — it is what turns a score into lessons — and carries the
 * full-width gradient card; browsing courses sits beneath it as an equally reachable but
 * visibly quieter second option, rather than the two competing as identical buttons.
 */
@Composable
private fun ResultActions(onBook: () -> Unit, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(Ink.CtaGreen, Ink.CtaGreenPressed)),
                )
                .clickable(onClick = onBook)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Ink.OnCtaGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    tint = Ink.OnCtaGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr(StrBooking.bookNearestGroup),
                    color = Ink.OnCtaGreen,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tr(StrBooking.bookNearestGroupSub),
                    color = Ink.OnCtaGreen.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Ink.OnCtaGreen,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.Surface)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(18.dp))
                .clickable(onClick = onBrowse)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = Ink.Teal,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                tr(StrBooking.browseCourses),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Ink.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * What the learner actually answered, question by question. Correct and wrong are separated by
 * colour and icon rather than only by wording, and a wrong answer shows both what they gave and
 * what would have counted — a score alone tells nobody what to study next.
 *
 * Renders nothing at all when [review] is empty, which covers an old attempt with no stored
 * answers and a viewer the database won't show them to.
 */
@Composable
fun AnswerReviewSection(review: List<AnswerReview>, modifier: Modifier = Modifier) {
    if (review.isEmpty()) return
    val correct = review.count { it.answer.isCorrect }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(StrTest.reviewTitle),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                trf(StrTest.reviewScore, correct, review.size),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(12.dp))
        review.forEachIndexed { index, item ->
            ReviewRow(index + 1, item)
            if (index != review.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReviewRow(number: Int, item: AnswerReview) {
    val ok = item.answer.isCorrect
    val accent = if (ok) Ink.Teal else Ink.Coral
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.SurfaceHigh)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                trf(StrTest.reviewQuestionNumber, number),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Pill(codeLabel(item.question.skill), background = Ink.Surface, foreground = Ink.TextMuted)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.question.prompt, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)

        val given = item.givenText
        val expected = item.expectedText
        if (given.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            ReviewLine(tr(StrTest.reviewYourAnswer), given, accent)
        }
        // Only worth showing when they got it wrong — repeating it after a correct answer is noise.
        if (!ok && expected.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            ReviewLine(tr(StrTest.reviewCorrectAnswer), expected, Ink.Teal)
        }
        item.question.explanation?.takeIf { it.isNotBlank() }?.let { why ->
            Spacer(Modifier.height(8.dp))
            Text(why, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}
