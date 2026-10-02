package com.rork.pro.ui.screens.test

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.PlacementTest
import com.rork.pro.data.TeacherProfile
import com.rork.pro.data.TestRepository
import com.rork.pro.data.ExerciseRepository
import com.rork.pro.data.ExerciseSection
import com.rork.pro.data.groupExercises
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.HeaderSwoosh
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.RatingRow
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.StrTest
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.HomeInk
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

// ---------------------------------------------------------------- Shared visual language
//
// Teal is the practice accent, amber stays the academy's. Both are read from `Ink`, so these
// screens follow the light / dark switch, and every phrase comes from a bilingual `Tr`.

/** Page wash with a soft accent bloom behind the header. */
@Composable
private fun ExerciseBackdrop(accent: Color, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun ExercisePanel(
    modifier: Modifier = Modifier,
    accent: Color = Ink.Teal,
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
                    listOf(accent.copy(alpha = 0.08f), Ink.Surface, Ink.Surface),
                ),
            )
            .border(1.dp, Ink.Hairline, shape)
            .padding(padding),
        content = content,
    )
}

/** Small icon + label chip, matching the placement flow. */
@Composable
private fun ExerciseChip(icon: ImageVector, label: String, accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(13.dp))
        Text(label, color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Screen intro: eyebrow, title, one line of context and a count. */
@Composable
private fun ExerciseHero(
    kicker: String,
    title: String,
    body: String,
    countLabel: String?,
    icon: ImageVector,
    accent: Color,
) {
    ExercisePanel(accent = accent, padding = 20.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    kicker.uppercase(Locale.ROOT),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (!countLabel.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Pill(countLabel, background = accent.copy(alpha = 0.14f), foreground = accent)
        }
    }
}

// ---------------------------------------------------------------- Hub

/**
 * The two practice worlds, kept apart on purpose.
 *
 * A placement test is the academy's verdict on a learner's level; a teacher exercise is
 * training set by one teacher. Mixing them in a single list made both feel arbitrary, so each
 * gets its own door here.
 */
@Composable
fun TestsHubScreen(navController: NavHostController, session: com.rork.pro.ui.SessionViewModel) {
    val sessionState by session.state.collectAsStateWithLifecycle()
    // Same three-way split the rest of the app uses: staff manage every teacher's sessions,
    // a teacher manages their own, and everyone else only ever sees what they were invited to.
    val classroomScope = when {
        sessionState.isStaff -> com.rork.pro.ui.screens.classroom.ClassroomScope.STAFF
        sessionState.isTeacher -> com.rork.pro.ui.screens.classroom.ClassroomScope.TEACHER
        else -> com.rork.pro.ui.screens.classroom.ClassroomScope.STUDENT
    }

    // The page used to stack everything at the top and leave the lower half of the screen empty.
    // Now the title stays at the top and the two groups sit centred in the space that is left;
    // on a short screen (or with big system fonts) it simply scrolls as before.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Spacer(Modifier.height(14.dp))
            Text(
                tr(StrEx.hubTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = HomeInk.Text,
            )
            Spacer(Modifier.height(18.dp))
          }
          Column {

            // Group 1 — the academy's official test, the one big door of the screen.
            HubGroupLabel(tr(StrEx.hubGroupAcademy), HomeInk.Brand)
            Spacer(Modifier.height(10.dp))
            HubHero(
                icon = Icons.Default.WorkspacePremium,
                title = tr(StrEx.placementCard),
                body = tr(StrEx.placementCardSub),
                action = tr(StrEx.open),
            ) { navController.navigate(Routes.TEST_PLACEMENT) }

            Spacer(Modifier.height(26.dp))

            // Group 2 — practice with a teacher: written exercises and live class, side by side.
            // Virtual Classroom sits here, on the same page as the two practice worlds, per the
            // academy's requirement — not a new item in the bottom navigation.
            HubGroupLabel(tr(StrEx.hubGroupTeacher), HomeInk.Green)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HubSquare(
                    icon = Icons.Default.FitnessCenter,
                    title = tr(StrEx.exercisesCard),
                    body = tr(StrEx.exercisesCardSub),
                    tile = HomeInk.Green,
                    onTile = HomeInk.OnAccent,
                    container = HomeInk.GreenBg,
                    border = HomeInk.GreenLine,
                    textColor = HomeInk.GreenInk,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { navController.navigate(Routes.EXERCISE_TEACHERS) }
                HubSquare(
                    icon = Icons.Default.Videocam,
                    title = tr(com.rork.pro.ui.i18n.StrClassroom.hubCard),
                    body = tr(com.rork.pro.ui.i18n.StrClassroom.hubCardSub),
                    tile = HomeInk.AiAccent,
                    onTile = HomeInk.Ai,
                    container = HomeInk.Ai,
                    border = null,
                    textColor = HomeInk.OnAi,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { navController.navigate(com.rork.pro.ui.screens.classroom.ClassroomRoutes.home(classroomScope)) }
            }
          }
          Column {
            Spacer(Modifier.height(18.dp))
            AdBanner("TESTS")
          }
        }
    }
}

/** A group heading: a small coloured dot and a one-line label. */
@Composable
private fun HubGroupLabel(text: String, dot: Color) {
    Row(
        Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text, color = HomeInk.TextSecondary, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The hub's main door: a large solid brand card with a big icon, the title, a short description
 * and an inverted "Open" button, over a few quiet concentric rings. The rings are decoration
 * only — they sit at the end corner (opposite the text) at low alpha so they never compete with it.
 */
@Composable
private fun HubHero(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val on = HomeInk.OnBrand
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HomeInk.Brand)
            .clickable(onClick = onClick),
    ) {
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 52.dp, y = (-48).dp)
                .size(190.dp),
        ) {
            val c = center
            drawCircle(on.copy(alpha = 0.16f), radius = size.minDimension / 2f - 3.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
            drawCircle(on.copy(alpha = 0.16f), radius = size.minDimension * 0.36f, center = c, style = Stroke(1.5.dp.toPx()))
            drawCircle(on.copy(alpha = 0.10f), radius = size.minDimension * 0.23f, center = c)
        }
        Column(Modifier.padding(18.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(on.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = on, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = on, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = on.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(on)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(action, color = HomeInk.BrandButtonText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HomeInk.BrandButtonText, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * A half-width door: icon tile and a round arrow on top, title and description at the bottom.
 * Every colour is passed in because each pairing (tile on container, text on container) flips
 * between light and dark mode and cannot be derived from one colour alone.
 */
@Composable
private fun HubSquare(
    icon: ImageVector,
    title: String,
    body: String,
    tile: Color,
    onTile: Color,
    container: Color,
    border: Color?,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .heightIn(min = 196.dp)
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = onTile, modifier = Modifier.size(23.dp))
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = onTile, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Column {
            // One-line title and a description that always reserves two lines: the two cards sit
            // side by side, and a one-line description on one of them used to push its title lower
            // than its neighbour's.
            Text(
                title, color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body, color = textColor.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall,
                minLines = 2, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- Choose a teacher

data class ExerciseTeachersData(val teachers: List<TeacherProfile>, val counts: Map<String, Int>)

class ExerciseTeachersViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<ExerciseTeachersData>>(Async.Loading)
    val state: StateFlow<Async<ExerciseTeachersData>> = _state.asStateFlow()

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
                ExerciseTeachersData(CatalogRepository.exerciseTeachers(), CatalogRepository.exerciseCounts())
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

@Composable
fun ExerciseTeachersScreen(navController: NavHostController) {
    val vm: ExerciseTeachersViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    ExerciseBackdrop(Ink.Teal) {
        DetailHeader(tr(StrEx.exercisesCard), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        ExerciseHero(
                            kicker = tr(StrEx.teachersKicker),
                            title = tr(StrEx.chooseTeacher),
                            body = tr(StrEx.chooseTeacherBody),
                            countLabel = trf(StrEx.teachersCount, s.value.teachers.size)
                                .takeIf { s.value.teachers.isNotEmpty() },
                            icon = Icons.Default.FitnessCenter,
                            accent = Ink.Teal,
                        )
                    }

                    if (s.value.teachers.isEmpty()) {
                        item { EmptyBlock(tr(StrEx.noTeachers), tr(StrEx.noTeachersBody)) }
                    }

                    item { AdBanner("EXERCISES") }

                    items(s.value.teachers, key = { it.id }) { teacher ->
                        TeacherRow(
                            teacher = teacher,
                            exerciseCount = s.value.counts[teacher.id] ?: 0,
                        ) { navController.navigate(Routes.teacherExercises(teacher.id)) }
                    }
                }
            }
        }
    }
}

/** One teacher who publishes exercises. */
@Composable
private fun TeacherRow(teacher: TeacherProfile, exerciseCount: Int, onClick: () -> Unit) {
    val accent = Ink.Teal
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.99f else 1f, tween(140), label = "teacher")
    val shape = RoundedCornerShape(22.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                teacher.photoUrl ?: teacher.profile?.avatarUrl,
                teacher.profile?.fullName,
                size = 50.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                teacher.profile?.fullName.orEmpty().ifBlank { tr(StrEx.exercisesCard) },
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!teacher.headline.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    teacher.headline,
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RatingRow(teacher.ratingAvg, teacher.ratingCount)
                Pill(
                    trf(StrEx.exercisesCount, exerciseCount),
                    background = accent.copy(alpha = 0.13f),
                    foreground = accent,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Ink.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------- One teacher: levels, then exercises
//
// The learner's path is three steps: choose a teacher -> choose one of that teacher's levels ->
// see the exercises the teacher filed under it. Both of the last two screens read the same
// data (the teacher's exercises, levels and the learner's best scores), so they share one
// view model and one "plan" that decides numbering and locks.

/** Route key for the exercises a teacher never filed under a level. */
const val UNFILED_LEVEL = "none"

class TeacherExercisesViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<TeacherExercisesData>>(Async.Loading)
    val state: StateFlow<Async<TeacherExercisesData>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var teacherId: String = ""

    /**
     * Called every time the screen enters composition — including when the learner comes back
     * from an exercise. The first call loads with a spinner; later ones refresh quietly so the
     * scores and unlocks update without the list flashing.
     */
    fun bind(id: String) {
        val alreadyLoaded = id == teacherId && _state.value is Async.Success
        teacherId = id
        load(quiet = alreadyLoaded)
    }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                val me = com.rork.pro.data.SessionCache.profile()
                val isStaff = me?.isStaff == true
                val isSelf = me?.id == teacherId
                val exercises = CatalogRepository.teacherExercises(teacherId)
                // A failed levels read must never hide the exercises: the learner then simply
                // gets one "general" level holding everything.
                val sections = runCatching { ExerciseRepository.sectionsOf(teacherId) }.getOrDefault(emptyList())
                TeacherExercisesData(
                    exercises,
                    TestRepository.bestPercentByTest(exercises.map { it.id }),
                    sections,
                    // Browsing is open to everyone; only this teacher's students may start.
                    // The owner and admins can start every teacher's exercises, and a teacher
                    // can start their own — none of them is a "student" of anyone.
                    isStaff || isSelf || CatalogRepository.isStudentOf(teacherId) ||
                        CatalogRepository.canTakeExercise(teacherId),
                    isStaff || isSelf,
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

/** One teacher's published exercises plus the learner's best score on each. */
data class TeacherExercisesData(
    val exercises: List<PlacementTest>,
    val bestPercent: Map<String, Double>,
    /** The teacher's levels. Empty is normal — a teacher who hasn't filed anything yet. */
    val sections: List<ExerciseSection> = emptyList(),
    /** False for visitors: they see everything but are invited to join instead of starting. */
    val isStudent: Boolean = true,
    /** Owner / admin (any exercise) or the teacher on their own exercises: never held back by the teacher's lock. */
    val isStaff: Boolean = false,
)

/** One level as the learner sees it. [key] is the section id, or [UNFILED_LEVEL]. */
private data class LevelEntry(
    val key: String,
    val section: ExerciseSection?,
    val exercises: List<PlacementTest>,
    val passed: Int,
    /** Sequentially locked: the level before it isn't finished yet. */
    val locked: Boolean,
)

/**
 * Levels in the teacher's order (unfiled exercises last, as a "general" level), with the
 * sequential unlock run across the whole path in exactly that order — so what the learner
 * sees is what gates them: the first exercise of a level opens once the level before it is done.
 */
private class LevelPlan(val levels: List<LevelEntry>, val sequentialLock: Map<String, Boolean>)

private fun planFor(data: TeacherExercisesData): LevelPlan {
    val groups = groupExercises(data.sections, data.exercises).filter { it.exercises.isNotEmpty() }
    val ordered = groups.flatMap { it.exercises }
    // No sequential unlocking: every published exercise is open. The only lock left is the
    // one the teacher sets by hand (`is_locked`), applied where each card is drawn.
    val levels = groups.map { group ->
        LevelEntry(
            key = group.section?.id ?: UNFILED_LEVEL,
            section = group.section,
            exercises = group.exercises,
            passed = group.exercises.count { (data.bestPercent[it.id] ?: -1.0) >= it.passingScore },
            locked = false,
        )
    }
    return LevelPlan(levels, emptyMap())
}

private fun levelTitle(level: LevelEntry): String =
    level.section?.title?.takeIf { it.isNotBlank() } ?: tr(StrEx.generalLevel)

// ---------------------------------------------------------------- Step 2: choose a level

@Composable
fun TeacherExercisesScreen(navController: NavHostController, teacherId: String) {
    val vm: TeacherExercisesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(teacherId) { vm.bind(teacherId) }

    ExerciseBackdrop(Ink.Teal) {
        DetailHeader(tr(StrEx.chooseLevel), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val plan = remember(s.value) { planFor(s.value) }
                val author = s.value.exercises.firstOrNull()?.owner?.fullName
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        ExerciseHero(
                            kicker = author?.takeIf { it.isNotBlank() } ?: tr(StrEx.teachersKicker),
                            title = tr(StrEx.chooseLevel),
                            body = if (plan.levels.isEmpty()) tr(StrEx.noExercisesBody) else tr(StrEx.chooseLevelBody),
                            countLabel = trf(StrEx.levelsCount, plan.levels.size)
                                .takeIf { plan.levels.isNotEmpty() },
                            icon = Icons.Default.Layers,
                            accent = Ink.Teal,
                        )
                    }

                    if (plan.levels.isEmpty()) {
                        item { EmptyBlock(tr(StrEx.noExercises), tr(StrEx.noExercisesYetTeacher)) }
                    }

                    item { AdBanner("EXERCISES") }

                    itemsIndexed(plan.levels, key = { _, level -> level.key }) { index, level ->
                        LevelCard(level = level, position = index + 1) {
                            navController.navigate(Routes.levelExercises(teacherId, level.key))
                        }
                    }
                }
            }
        }
    }
}

/** One level: its number, title, how many exercises it holds and how far the learner got. */
@Composable
private fun LevelCard(level: LevelEntry, position: Int, onClick: () -> Unit) {
    val accent = Ink.Teal
    val total = level.exercises.size
    val done = total > 0 && level.passed >= total
    val progress by animateFloatAsState(
        if (total == 0) 0f else level.passed.toFloat() / total,
        tween(500),
        label = "level-progress",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !level.locked) 0.99f else 1f, tween(140), label = "level")
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (level.locked) 0.55f else 1f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), Ink.Surface, Ink.Surface)))
            .border(1.dp, accent.copy(alpha = 0.26f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !level.locked, onClick = onClick)
            .padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    level.locked -> Icon(Icons.Default.Lock, null, tint = accent, modifier = Modifier.size(20.dp))
                    done -> Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(22.dp))
                    else -> Text(
                        position.toString().padStart(2, '0'),
                        color = accent,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (level.section != null) trf(StrEx.levelTag, position) else tr(StrEx.exerciseTag),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    levelTitle(level),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!level.locked) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = Ink.TextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseChip(Icons.Default.FitnessCenter, trf(StrEx.exercisesCount, total), accent)
            if (done) ExerciseChip(Icons.Default.CheckCircle, tr(StrEx.levelDone), accent)
        }

        Spacer(Modifier.height(14.dp))
        if (level.locked) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SurfaceHigh)
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Lock, null, tint = Ink.TextMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    tr(StrEx.levelLocked),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.14f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                trf(StrEx.levelProgress, level.passed, total),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------- Step 3: the level's exercises

@Composable
fun LevelExercisesScreen(navController: NavHostController, teacherId: String, levelKey: String) {
    val vm: TeacherExercisesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(teacherId) { vm.bind(teacherId) }

    ExerciseBackdrop(Ink.Teal) {
        val plan = remember(state) { (state as? Async.Success<TeacherExercisesData>)?.value?.let(::planFor) }
        val levelIndex = plan?.levels?.indexOfFirst { it.key == levelKey } ?: -1
        val level = plan?.levels?.getOrNull(levelIndex)
        val title = level?.let { levelTitle(it) } ?: tr(StrEx.teacherExercises)

        DetailHeader(title, onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val exercises = level?.exercises.orEmpty()
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        ExerciseHero(
                            kicker = if (level?.section != null) trf(StrEx.levelTag, levelIndex + 1) else tr(StrEx.teachersKicker),
                            title = title,
                            body = if (exercises.isEmpty()) tr(StrEx.noExercisesInLevel) else tr(StrEx.availableExercises),
                            countLabel = level?.let { trf(StrEx.levelProgress, it.passed, it.exercises.size) }
                                .takeIf { exercises.isNotEmpty() },
                            icon = Icons.Default.FitnessCenter,
                            accent = Ink.Teal,
                        )
                    }

                    if (exercises.isEmpty()) {
                        item { EmptyBlock(tr(StrEx.noExercises), tr(StrEx.noExercisesInLevel)) }
                    }

                    item { AdBanner("EXERCISES") }

                    itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        // The teacher's lock is now a gate, not a dead end: a locked exercise
                        // opens by itself once every exercise before it in this level is
                        // passed. `passed` reads the same bestPercent map the progress bar and
                        // ✓ badges above already use, so this can never disagree with them.
                        val previousDone = exercises.take(index).all { prior ->
                            (s.value.bestPercent[prior.id] ?: -1.0) >= prior.passingScore
                        }
                        ExerciseCard(
                            exercise = exercise,
                            // Numbered inside the level: "exercise 1 of level 2" reads naturally.
                            position = index + 1,
                            locked = s.value.isStudent && !s.value.isStaff && exercise.isLocked && !previousDone,
                            bestPercent = s.value.bestPercent[exercise.id],
                            studentsOnly = !s.value.isStudent,
                        ) {
                            if (s.value.isStudent) navController.navigate(Routes.testRun(exercise.id))
                            else navController.navigate(Routes.bookingGroups(teacherId))
                        }
                    }
                }
            }
        }
    }
}

/** One publishable exercise, with its own number so a set reads as a sequence. Locked cards
 *  explain, in the learner's own numbers, exactly what unlocks them. */
@Composable
private fun ExerciseCard(
    exercise: PlacementTest,
    position: Int,
    locked: Boolean = false,
    bestPercent: Double? = null,
    studentsOnly: Boolean = false,
    onStart: () -> Unit,
) {
    val accent = Ink.Teal
    val passed = bestPercent != null && bestPercent >= exercise.passingScore
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !locked) 0.99f else 1f, tween(140), label = "exercise")
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (locked) 0.55f else 1f)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), Ink.Surface, Ink.Surface)),
            )
            .border(1.dp, accent.copy(alpha = 0.26f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !locked, onClick = onStart)
            .padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (locked) {
                    Icon(Icons.Default.Lock, null, tint = accent, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        position.toString().padStart(2, '0'),
                        color = accent,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tr(StrEx.exerciseTag),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    exercise.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (passed && !locked) {
                Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }

        if (!exercise.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                exercise.description,
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseChip(
                Icons.Default.HelpOutline,
                trf(StrTest.questionsCount, exercise.questionCount),
                accent,
            )
            ExerciseChip(
                Icons.Default.Schedule,
                trf(StrTest.minutes, exercise.timeLimitSeconds / 60),
                accent,
            )
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
                    tr(StrEx.lockedByTeacher),
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
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    when {
                        studentsOnly -> tr(StrEx.joinTeacherToStart)
                        passed -> tr(StrEx.retakeLabel)
                        bestPercent != null -> tr(StrEx.tryAgainLabel)
                        else -> tr(StrEx.startExercise)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
            if (studentsOnly) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(StrEx.studentsOnlyNote),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (bestPercent != null && !passed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    trf(StrEx.lastScoreLabel, bestPercent.toInt()),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
