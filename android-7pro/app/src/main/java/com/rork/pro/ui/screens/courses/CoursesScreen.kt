package com.rork.pro.ui.screens.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.Category
import com.rork.pro.data.CommerceRepository
import com.rork.pro.data.Course
import com.rork.pro.data.Enrollment
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.AdInterstitial
import com.rork.pro.ui.components.AdNative
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.home.CourseRowCard
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.StrCourses
import com.rork.pro.ui.i18n.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.titleDirectionStyle
import com.rork.pro.ui.i18n.StrCourseLink
import com.rork.pro.ui.i18n.trf
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusRequester

data class CatalogData(
    val categories: List<Category>,
    val courses: List<Course>,
    val myCourses: List<Enrollment>,
    /** Courses that became paid, or whose monthly subscription ended: shown until the learner pays. */
    val lockedCourses: List<Enrollment> = emptyList(),
)

class CoursesViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<CatalogData>>(Async.Loading)
    val state: StateFlow<Async<CatalogData>> = _state.asStateFlow()

    var query: String = ""
        private set

    /** The whole published catalogue for the chosen category; searching filters this on the device. */
    private var catalogue: List<Course> = emptyList()
    private var enrolled: List<Enrollment> = emptyList()
    private var locked: List<Enrollment> = emptyList()
    private var categories: List<Category> = emptyList()
    private val _categoryId = MutableStateFlow<String?>(null)
    val categoryId: StateFlow<String?> = _categoryId.asStateFlow()

    init {
        load()
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Re-reads the catalogue without blanking the list that is already on screen. */
    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                // Fetched without a text filter: the match happens locally so it can be exact
                // across Arabic/English titles, descriptions and teacher names (see CourseSearch).
                categories = CatalogRepository.categories()
                catalogue = CatalogRepository.publishedCourses(categoryId = _categoryId.value, limit = CATALOGUE_LIMIT)
                enrolled = CommerceRepository.myEnrollments()
                locked = runCatching { CommerceRepository.myLockedEnrollments() }.getOrDefault(emptyList())
                snapshot()
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    private fun snapshot() = CatalogData(
        categories = categories,
        courses = CourseSearch.filter(query, catalogue),
        myCourses = CourseSearch.filterEnrollments(query, enrolled),
        lockedCourses = CourseSearch.filterEnrollments(query, locked),
    )

    fun search(text: String) {
        query = text
        // Filtered on the device from the loaded catalogue: instant, no spinner, no network call
        // per keystroke. Before the first load finishes the result simply applies once it does.
        if (_state.value is Async.Success) _state.value = Async.Success(snapshot())
    }

    private companion object {
        /** Large enough to hold the whole catalogue so a search can't miss a course. */
        const val CATALOGUE_LIMIT = 1000L
    }

    fun selectCategory(id: String?) {
        _categoryId.value = id
        load()
    }
}

@Composable
fun CoursesScreen(navController: NavHostController, canCopyLink: Boolean = false) {
    val vm: CoursesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.categoryId.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { vm.load() }
    // Tapping the search button on Home lands here with the cursor already in the search box.
    LaunchedEffect(Unit) {
        if (CourseSearchLaunch.consume()) runCatching { searchFocus.requestFocus() }
    }
    // Search as the learner types: a short pause after the last keystroke, then one query.
    // Skipped while the field already matches what the list was loaded for.
    LaunchedEffect(text) {
        if (text != vm.query) {
            delay(150)
            vm.search(text)
        }
    }
    val copyCourseLink = rememberCourseLinkCopier(canCopyLink)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr(StrCourses.coursesTitle),
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                // Copying the courses link is limited to teachers, admins and the owner.
                if (canCopyLink) {
                    LinkPill(tr(StrCourseLink.copyAll)) { copyCourseLink(null) }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .focusRequester(searchFocus),
                placeholder = { Text(tr(StrCourses.searchCourses)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink.TextSecondary) },
                trailingIcon = if (text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Default.Close, tr(com.rork.pro.ui.i18n.Str.close), tint = Ink.TextSecondary)
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Ink.Surface,
                    unfocusedContainerColor = Ink.Surface,
                    focusedBorderColor = Ink.Amber,
                    unfocusedBorderColor = Ink.Hairline,
                    cursorColor = Ink.Amber,
                    focusedTextColor = Ink.TextPrimary,
                    unfocusedTextColor = Ink.TextPrimary,
                    focusedPlaceholderColor = Ink.TextSecondary,
                    unfocusedPlaceholderColor = Ink.TextSecondary,
                ),
            )

            // Full-screen slot the owner configured for this screen; capped per session.
            AdInterstitial("COURSE_LIST")

            when (val s = state) {
                is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
                is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
                is Async.Success -> {
                    val data = s.value
                    // "Continue learning" is for courses the learner has actually started; the
                    // ones they own but have not opened yet get their own short section instead
                    // of a row of empty progress bars.
                    val inProgress = data.myCourses.filter { it.progressPercent > 0.0 }
                    val notStarted = data.myCourses.filter { it.progressPercent <= 0.0 }
                    Refreshable(refreshing, { vm.refresh() }) {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (data.categories.isNotEmpty()) {
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        item {
                                            CourseChip(tr(StrCourses.all), selected == null) { vm.selectCategory(null) }
                                        }
                                        items(data.categories, key = { it.id }) { c ->
                                            val label = c.icon?.takeIf { it.isNotBlank() }?.let { "$it ${c.displayName}" } ?: c.displayName
                                            CourseChip(label, selected == c.id) { vm.selectCategory(c.id) }
                                        }
                                    }
                                }
                            }

                            if (inProgress.isNotEmpty()) {
                                item { CoursesSectionTitle(tr(StrCourses.continueLearning)) }
                                items(inProgress, key = { it.id }) { enrollment ->
                                    EnrolledCard(enrollment, Modifier.padding(horizontal = Dimens.screenPadding)) {
                                        navController.navigate(Routes.coursePlayer(enrollment.courseId))
                                    }
                                }
                            }

                            if (notStarted.isNotEmpty()) {
                                item { CoursesSectionTitle(tr(StrCourses.startYourCourses)) }
                                items(notStarted, key = { "new-${it.id}" }) { enrollment ->
                                    EnrolledCard(enrollment, Modifier.padding(horizontal = Dimens.screenPadding)) {
                                        navController.navigate(Routes.coursePlayer(enrollment.courseId))
                                    }
                                }
                            }

                            if (data.lockedCourses.isNotEmpty()) {
                                item { CoursesSectionTitle(tr(StrCourses.subscriptionNeeded)) }
                                items(data.lockedCourses, key = { "locked-${it.id}" }) { enrollment ->
                                    EnrolledCard(enrollment, Modifier.padding(horizontal = Dimens.screenPadding), locked = true) {
                                        // The course page is where the price, the monthly option and the coupon live.
                                        navController.navigate(Routes.courseDetail(enrollment.courseId))
                                    }
                                }
                            }

                            item { AdBanner("COURSE_LIST", Modifier.padding(horizontal = Dimens.screenPadding)) }

                            item { CoursesSectionTitle(tr(StrCourses.allCourses)) }

                            // A native ad sits among the course cards, styled like them. It stays
                            // invisible until the owner adds a COURSE_LIST / NATIVE placement.
                            item { AdNative("COURSE_LIST", Modifier.padding(horizontal = Dimens.screenPadding)) }

                            if (data.courses.isEmpty()) {
                                item {
                                    EmptyBlock(
                                        tr(StrCourses.nothingHereYet),
                                        if (vm.query.isBlank()) {
                                            tr(StrCourses.publishedAppearHere)
                                        } else {
                                            tr(StrCourses.noCourseMatched)
                                        },
                                    )
                                }
                            } else {
                                items(data.courses, key = { it.id }) { course ->
                                    CourseRowCard(course, Modifier.padding(horizontal = Dimens.screenPadding)) {
                                        navController.navigate(Routes.courseDetail(course.id))
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

/** Section heading: clearly below the page title in size and weight, so the page has levels. */
@Composable
private fun CoursesSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = Dimens.screenPadding),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink.TextPrimary,
    )
}

/** The labelled "copy link" button in the title row. */
@Composable
private fun LinkPill(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Ink.SurfaceHigh)
            .border(1.dp, Ink.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Link, contentDescription = null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
        Text(label, color = Ink.TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * A category filter. Smaller than the stock Material chip so the three usual ones fit inside the
 * page margins, with a solid brand fill when selected (the old pale fill with orange text
 * barely stood out from the unselected ones).
 */
@Composable
private fun CourseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Ink.Amber else Ink.SurfaceHigh)
            .border(1.dp, if (selected) Ink.Amber else Ink.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Ink.OnAmber else Ink.TextPrimary,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
    )
}

/**
 * A course the learner already has: cover, title, teacher, and one line of state — a progress bar
 * once started, a "Start learning" button before that, or the reason it is locked. The same
 * shape as the catalogue cards below it, so the page reads as one list rather than two designs.
 */
@Composable
private fun EnrolledCard(
    enrollment: Enrollment,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val course = enrollment.course
    val percent = enrollment.progressPercent
    InkCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                course?.thumbnailUrl,
                Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)),
                fallbackLabel = course?.displayTitle.orEmpty(),
            )
            Column(
                Modifier.weight(1f).heightIn(min = 96.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    course?.displayTitle ?: tr(StrCourses.yourCourse),
                    color = Ink.TextPrimary,
                    style = titleDirectionStyle(MaterialTheme.typography.titleMedium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val teacher = course?.teacher
                teacher?.fullName?.takeIf { it.isNotBlank() }?.let { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Avatar(teacher.avatarUrl, name, 20.dp)
                        Text(
                            name,
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (!locked && enrollment.isMonthly) {
                    enrollment.expiresDay?.let {
                        Text(
                            trf(StrCourses.subscriptionEndsOn, it),
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                when {
                    locked -> Text(
                        if (enrollment.status == "PAYMENT_REQUIRED") {
                            tr(StrCourses.nowPaidBanner)
                        } else {
                            tr(StrCourses.subscriptionEndedBanner)
                        },
                        color = Ink.Amber,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    percent > 0.0 -> CourseProgress(percent)
                    else -> Pill(
                        tr(StrCourses.startLearning),
                        background = Ink.Amber,
                        foreground = Ink.OnAmber,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Ink.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Progress as a bar with its percentage right beside it (not at the opposite edge of the card). */
@Composable
private fun CourseProgress(percent: Double) {
    val animated by animateFloatAsState((percent / 100.0).toFloat().coerceIn(0f, 1f), tween(720), label = "courseProgress")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.HairlineStrong),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.Amber),
            )
        }
        Text(
            "${percent.toInt()}%",
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
