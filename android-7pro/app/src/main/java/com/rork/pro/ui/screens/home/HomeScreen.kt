package com.rork.pro.ui.screens.home

import com.rork.pro.ui.components.coursePriceLabel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.data.Async
import androidx.compose.material.icons.filled.FactCheck
import com.rork.pro.ui.screens.admin.AdminRoutes
import com.rork.pro.ui.screens.admin.ApprovalAccess
import com.rork.pro.ui.screens.admin.StaffApprovals
import com.rork.pro.ui.screens.admin.approvalAccess
import com.rork.pro.ui.screens.admin.loadStaffApprovals
import com.rork.pro.data.Banner
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.Category
import com.rork.pro.data.ClassroomRepository
import com.rork.pro.data.ClassroomSession
import com.rork.pro.data.Backend
import com.rork.pro.data.Course
import com.rork.pro.data.TestRepository
import com.rork.pro.data.TestAttemptRow
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.AdInterstitial
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.SubscriptionHomeBanner
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.HeaderSwoosh
import com.rork.pro.ui.components.HomeActionTile
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LevelBadge
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.RatingRow
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.classroom.ClassroomRoutes
import com.rork.pro.ui.screens.classroom.openClassroomInBrowser
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.HomeInk
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.FitnessCenter
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.realtime.RealtimeChannel
import java.time.LocalTime
import com.rork.pro.ui.i18n.StrClassroom
import com.rork.pro.ui.i18n.StrHome
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.Str
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.rork.pro.R
import com.rork.pro.ui.components.BannerKind
import com.rork.pro.ui.components.BannerSpec
import com.rork.pro.ui.components.levelName
import com.rork.pro.ui.theme.HomeSky

data class HomeData(
    val banners: List<Banner>,
    val categories: List<Category>,
    val featured: List<Course>,
    val latestResult: TestAttemptRow?,
    val shortcuts: List<com.rork.pro.data.HomeShortcut> = emptyList(),
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<HomeData>>(Async.Loading)
    val state: StateFlow<Async<HomeData>> = _state.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    init {
        load()
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Re-reads everything without blanking what is already on screen. */
    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
        refreshLive()
    }

    // ------------------------------------------------------------ staff approvals shortcut
    //
    // Owner / admin only. Kept out of HomeData for the same reason as the live card: if a count
    // cannot be fetched, Home must still open — the shortcut just shows what it could read.

    private val _approvals = MutableStateFlow<StaffApprovals?>(null)

    /** Pending items per kind for the signed-in staff member; null until the first read lands. */
    val approvals: StateFlow<StaffApprovals?> = _approvals.asStateFlow()

    /**
     * Counts only what this staff member is allowed to open (see [approvalAccess]), so the badge
     * never promises a queue they cannot enter. The approvals page reads them the same way.
     */
    fun refreshApprovals(access: ApprovalAccess) {
        viewModelScope.launch { _approvals.value = loadStaffApprovals(access) }
    }

    // ------------------------------------------------------------ live meeting card
    //
    // Deliberately separate from HomeData: a hiccup fetching classroom sessions must never turn
    // the whole Home screen into an error page, and this list refreshes on its own schedule.

    private val _liveSessions = MutableStateFlow<List<ClassroomSession>>(emptyList())

    /** Meetings running right now for this user; empty (card hidden) otherwise. */
    val liveSessions: StateFlow<List<ClassroomSession>> = _liveSessions.asStateFlow()

    /** null = the card is off for this user, true = look at own sessions (teacher), false = invited ones. */
    private var liveMode: Boolean? = null
    private var liveChannel: RealtimeChannel? = null

    private var liveJob: kotlinx.coroutines.Job? = null

    fun setLiveMode(asTeacher: Boolean?) {
        // A different role (student vs teacher) means a different list: never show the old one.
        if (liveMode != asTeacher) {
            liveJob?.cancel()
            _liveSessions.value = emptyList()
        }
        liveMode = asTeacher
        if (asTeacher != null) watchLive()
    }

    fun refreshLive() {
        val asTeacher = liveMode ?: return
        // Only the newest fetch may write: a burst of realtime events (or a poll landing on top of
        // one) must not leave several requests racing, with an older answer overwriting a newer one.
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            runCatching { ClassroomRepository.liveNow(asTeacher) }
                .onSuccess { _liveSessions.value = it }
                // Offline or a server blip: keep what is showing (a running meeting's button
                // must not flicker away), but still drop anything whose time has run out.
                .onFailure { _liveSessions.value = _liveSessions.value.filter { s -> s.isInProgress() } }
        }
    }

    /**
     * Makes the card react the moment a meeting starts or ends instead of waiting for the next
     * poll: any classroom session row I can see changing, or an invite addressed to me.
     * Flows are created before subscribe() on purpose — see ClassroomRepository.openChannel.
     */
    private fun watchLive() {
        if (liveChannel != null) return
        viewModelScope.launch {
            runCatching {
                val ch = ClassroomRepository.openHomeChannel()
                liveChannel = ch
                val sessions = ClassroomRepository.visibleSessionsChangeFlow(ch)
                val invites = ClassroomRepository.myInvitesChangeFlow(ch)
                launch { runCatching { sessions.collect { refreshLive() } } }
                launch { runCatching { invites.collect { refreshLive() } } }
                ClassroomRepository.subscribeChannel(ch)
            }.onFailure {
                android.util.Log.w("HomeVM", "Live meeting updates unavailable: ${it.message}")
            }
        }
    }

    override fun onCleared() {
        val ch = liveChannel
        liveChannel = null
        Backend.appScope.launch {
            ch?.let { runCatching { Backend.realtime.removeChannel(it) } }
        }
        super.onCleared()
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching {
                HomeData(
                    banners = CatalogRepository.banners(),
                    categories = CatalogRepository.categories(),
                    featured = CatalogRepository.publishedCourses(categoryId = _category.value, limit = 8),
                    latestResult = TestRepository.latestResult(),
                    shortcuts = runCatching { CatalogRepository.homeShortcuts() }.getOrDefault(emptyList()),
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }

    fun selectCategory(id: String?) {
        _category.value = id
        load()
    }
}

@Composable
fun HomeScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedCategory by vm.category.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val liveSessions by vm.liveSessions.collectAsStateWithLifecycle()
    val approvals by vm.approvals.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { session.refreshNotifications() }

    val lifecycleOwner = LocalLifecycleOwner.current

    // The approvals shortcut is for owner / admin only, and only for the queues each may open.
    val approvalAccess = sessionState.approvalAccess()
    val showApprovalsButton = approvalAccess.any

    // Counts are re-read every time Home comes back on screen (that is when something was just
    // approved on another screen) and once a minute while it stays.
    LaunchedEffect(approvalAccess) {
        if (!showApprovalsButton) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshApprovals(approvalAccess)
                delay(60_000)
            }
        }
    }

    // The header (logo, buttons, greeting, illustration) does not depend on the feed, so it is on
    // screen from the first frame — while loading, on error, and as the first row of the feed.
    val header: @Composable () -> Unit = {
        HomeHeader(
            greetingName = sessionState.profile?.displayName ?: tr(StrHome.thereFallback),
            level = sessionState.profile?.currentLevel,
            unread = sessionState.unreadCount,
            onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
            onSearch = {
                com.rork.pro.ui.screens.courses.CourseSearchLaunch.request()
                navController.navigate(Routes.COURSES)
            },
            showApprovals = showApprovalsButton,
            approvalsCount = approvals?.total ?: 0,
            onApprovals = { navController.navigate(AdminRoutes.APPROVALS_HUB) },
        )
    }

    // Home paints its own page (it runs edge to edge, under the floating tab bar) so the sky /
    // navy wash is continuous all the way down, exactly like the design.
    Box(
        Modifier
            .fillMaxSize()
            .background(HomeSky.pageBrush())
            .homeBlobs(),
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (val s = state) {
                is Async.Loading -> Column(Modifier.fillMaxSize()) {
                    header()
                    LoadingBlock(Modifier.fillMaxWidth().weight(1f).padding(bottom = 96.dp).navigationBarsPadding())
                }
                is Async.Failure -> Column(Modifier.fillMaxSize()) {
                    header()
                    ErrorBlock(s.error, Modifier.fillMaxWidth().weight(1f).padding(bottom = 96.dp).navigationBarsPadding()) { vm.load() }
                }
                is Async.Success -> Refreshable(
                    refreshing,
                    {
                        vm.refresh()
                        session.refreshNotifications()
                    },
                ) {
                    HomeContent(
                        header = header,
                        data = s.value,
                        isTeacher = sessionState.isTeacher,
                        selectedCategory = selectedCategory,
                        onCategory = vm::selectCategory,
                        navController = navController,
                        liveSessions = liveSessions,
                    )
                }
            }
        }
    }
}

/** Two very soft washes of light behind the feed — the cloud shapes of the design. */
private fun Modifier.homeBlobs(): Modifier = this.drawBehind {
    val r1 = size.width * 0.62f
    val c1 = Offset(size.width * 1.02f, size.height * 0.40f)
    drawCircle(Brush.radialGradient(listOf(HomeSky.Blob, Color.Transparent), center = c1, radius = r1), r1, c1)
    val r2 = size.width * 0.70f
    val c2 = Offset(-size.width * 0.10f, size.height * 0.66f)
    drawCircle(Brush.radialGradient(listOf(HomeSky.Blob, Color.Transparent), center = c2, radius = r2), r2, c2)
}

/** Soft blue drop shadow used under light-mode surfaces; dark mode uses hairlines instead. */
private fun Modifier.homeShadow(shape: androidx.compose.ui.graphics.Shape, elevation: Dp = 8.dp): Modifier =
    if (HomeSky.dark) this else this.shadow(elevation, shape, ambientColor = HomeSky.Shadow, spotColor = HomeSky.Shadow)

// ============================================================================ header

/**
 * Logo + header buttons, the greeting, the name, the level pill — and the illustration that sits
 * behind them on the other side, its feet resting on the AI tutor bar right below.
 */
@Composable
private fun HomeHeader(
    greetingName: String,
    level: String?,
    unread: Int,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
    showApprovals: Boolean,
    approvalsCount: Int,
    onApprovals: () -> Unit,
) {
    val dark = HomeSky.dark
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    Box(Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(if (dark) R.drawable.home_hero_dark else R.drawable.home_hero_light),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.6f)
                .aspectRatio(560f / 297f)
                // The artwork bleeds off the screen edge in Arabic. In English it sits on the
                // right, so its inner edge is faded instead of cut.
                .then(
                    if (ltr) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.horizontalGradient(0f to Color.Transparent, 0.2f to Color.Black),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        )
        Column(Modifier.fillMaxWidth()) {
            HomeTopRow(unread, onNotifications, onSearch, showApprovals, approvalsCount, onApprovals)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    greeting(),
                    color = HomeSky.Greeting,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                )
                BasicText(
                    greetingName,
                    modifier = Modifier.fillMaxWidth(0.68f),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = HomeSky.Text,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Start,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 24.sp),
                )
                Spacer(Modifier.height(8.dp))
                HomeLevelPill(level)
            }
        }
    }
}

@Composable
private fun HomeTopRow(
    unread: Int,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
    showApprovals: Boolean,
    approvalsCount: Int,
    onApprovals: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(if (HomeSky.dark) R.drawable.home_logo_dark else R.drawable.home_logo_light),
                contentDescription = "7PRO",
                modifier = Modifier.height(33.dp),
            )
            Text(
                tr(StrHome.tagline),
                color = HomeSky.Tagline,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showApprovals) {
                HomeIconButton(onClick = onApprovals, count = approvalsCount, max = 99) {
                    Icon(Icons.Default.FactCheck, tr(StrHome.approvalsTitle), tint = HomeSky.IconBtnFg, modifier = Modifier.size(19.dp))
                }
            }
            HomeIconButton(onClick = onNotifications, count = unread, max = 9) {
                Icon(Icons.Default.Notifications, tr(StrHome.notifications), tint = HomeSky.IconBtnFg, modifier = Modifier.size(19.dp))
            }
            HomeIconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, tr(StrHome.searchCourses), tint = HomeSky.IconBtnFg, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Round header button; a red count bubble sits on the icon's top-right corner when [count] > 0. */
@Composable
private fun HomeIconButton(onClick: () -> Unit, count: Int = 0, max: Int = 9, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .homeShadow(CircleShape, 6.dp)
            .clip(CircleShape)
            .background(HomeSky.IconBtnBg)
            .border(1.dp, HomeSky.IconBtnBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            content()
            if (count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .heightIn(min = 16.dp)
                        .widthIn(min = 16.dp)
                        .clip(CircleShape)
                        .background(HomeSky.Badge)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > max) "$max+" else count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

/** "(A1) A1 · مبتدئ" in an orange outline, with a soft scale-in on first appearance. */
@Composable
private fun HomeLevelPill(level: String?) {
    if (level.isNullOrBlank()) return
    val entrance = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .graphicsLayer { scaleX = entrance.value; scaleY = entrance.value }
            .clip(shape)
            .background(HomeSky.LevelPillBg)
            .border(1.5.dp, HomeSky.Brand, shape)
            .padding(start = 5.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.5.dp, HomeSky.Brand, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(level.take(2), color = HomeSky.Brand, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
        }
        Text(
            levelName(level),
            color = HomeSky.Brand,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// ============================================================================ feed

@Composable
private fun HomeContent(
    header: @Composable () -> Unit,
    data: HomeData,
    isTeacher: Boolean,
    selectedCategory: String?,
    onCategory: (String?) -> Unit,
    navController: NavHostController,
    liveSessions: List<ClassroomSession>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pad = 18.dp
    // Home runs under the floating tab bar, so the last card must be able to scroll clear of it.
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 92.dp
    // The HOME interstitial placement existed in the database with nothing requesting it, so it
    // could never fire. Session caps and the delay inside AdInterstitial keep it from greeting
    // the learner every single time they open the app.
    AdInterstitial("HOME")

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header, AI tutor bar and the two tiles are one block: the illustration stands on the
        // tutor bar, so nothing may ever be inserted between them.
        item {
            Column {
                header()
                SubscriptionHomeBanner(
                    navController,
                    Modifier.padding(horizontal = pad),
                    tutor = { m ->
                        TutorBar(m) { navController.navigate(Routes.AI_TUTOR) }
                    },
                    side = { m ->
                        HomeTile(
                            tone = HomeSky.TileBlue,
                            icon = Icons.Default.FitnessCenter,
                            title = tr(StrHome.teacherExercises),
                            subtitle = null,
                            art = if (HomeSky.dark) {
                                TileArt(R.drawable.home_tile_books_dark, BiasAlignment(0.76f, 0.33f), 0.49f, 206f / 164f)
                            } else {
                                TileArt(R.drawable.home_tile_book_light, BiasAlignment(-0.03f, -0.33f), 0.45f, 186f / 142f)
                            },
                            modifier = m,
                        ) { navController.navigate(Routes.EXERCISE_TEACHERS) }
                    },
                    card = { spec, m -> HomeBannerTile(spec, m) },
                )
                // The ad lives inside this item so an empty slot takes no room. The server
                // decides whether this learner sees an ad here at all.
                AdBanner("HOME", Modifier.padding(start = pad, end = pad, top = 12.dp))
            }
        }

        // A running meeting shows right under the entry points (temporarily — the moment the
        // meeting ends it is gone). Empty list = nothing is drawn here.
        if (liveSessions.isNotEmpty()) {
            items(liveSessions, key = { "live-${it.id}" }) { live ->
                LiveMeetingCard(live, Modifier.padding(horizontal = pad)) {
                    // Below Android 8 the in-app video cannot run, so the same button opens the
                    // web classroom in the browser instead of a dead end.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        scope.launch { openClassroomInBrowser(context, live.id) }
                    } else {
                        navController.navigate(ClassroomRoutes.lobby(live.id, autoJoin = true))
                    }
                }
            }
        }

        // The placement-test prompt / level-result card, while no meeting is running.
        if (liveSessions.isEmpty()) {
            if (data.latestResult == null) {
                item { PlacementPrompt(Modifier.padding(horizontal = pad)) { navController.navigate(Routes.TEST_START) } }
            } else {
                item {
                    ResultRecap(
                        result = data.latestResult,
                        modifier = Modifier.padding(horizontal = pad),
                    ) { navController.navigate(Routes.testResult(data.latestResult.id)) }
                }
            }
        }

        // Owner-configured shortcuts (Admin > Home content > Shortcuts). Hidden when none.
        if (data.shortcuts.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.shortcuts, key = { it.id }) { shortcut ->
                        HomeShortcutTile(shortcut) {
                            resolveHomeShortcutTarget(navController, context, shortcut.target, isTeacher)
                        }
                    }
                }
            }
        }

        if (data.banners.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.banners, key = { it.id }) { banner ->
                        // A banner leads to the same places a shortcut can: the owner picks the
                        // destination from one list, so both must understand every entry in it.
                        HeroBanner(banner) { target ->
                            if (!target.isNullOrBlank()) {
                                resolveHomeShortcutTarget(navController, context, target, isTeacher)
                            }
                        }
                    }
                }
            }
        }

        // "Featured courses · See all", then the category chips, then the courses.
        item {
            HomeSectionHeader(
                tr(StrHome.featuredCourses),
                tr(StrHome.seeAll),
                Modifier.padding(horizontal = pad),
            ) { navController.navigate(Routes.COURSES) }
        }

        if (data.categories.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        HomeChip(tr(StrHome.all), selectedCategory == null) { onCategory(null) }
                    }
                    items(data.categories, key = { it.id }) { category ->
                        val label = category.icon?.takeIf { it.isNotBlank() }?.let { "$it ${category.displayName}" } ?: category.displayName
                        HomeChip(label, selectedCategory == category.id) { onCategory(category.id) }
                    }
                }
            }
        }

        if (data.featured.isEmpty()) {
            item {
                EmptyBlock(
                    tr(StrHome.noCoursesYet),
                    tr(StrHome.noCoursesYetBody),
                )
            }
        } else {
            items(data.featured, key = { it.id }) { course ->
                HomeCourseCard(
                    course = course,
                    modifier = Modifier.padding(horizontal = pad),
                ) { navController.navigate(Routes.courseDetail(course.id)) }
            }
        }

        // A second, independent banner slot. It stays completely invisible until the owner
        // creates a HOME / BANNER placement with the slot name "BOTTOM".
        item {
            AdBanner(
                "HOME",
                Modifier.padding(horizontal = pad),
                section = "BOTTOM",
            )
        }
    }
}

// ============================================================================ AI tutor bar

/** The one-row AI tutor bar in deep teal: mint icon, title, white "start chatting" pill. */
@Composable
private fun TutorBar(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val dark = HomeSky.dark
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (dark) {
                    Modifier.shadow(12.dp, shape, ambientColor = HomeSky.AiBorder, spotColor = HomeSky.AiBorder)
                } else {
                    Modifier.homeShadow(shape, 10.dp)
                },
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(HomeSky.AiStart, HomeSky.AiEnd)))
            .border(1.dp, HomeSky.AiBorder, shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(HomeSky.AiIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (dark) Icons.Default.RecordVoiceOver else Icons.Default.SupportAgent,
                contentDescription = null,
                tint = HomeSky.AiIconFg,
                modifier = Modifier.size(20.dp),
            )
        }
        BasicText(
            tr(com.rork.pro.ui.i18n.StrTutor.homeTitle),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(color = HomeSky.OnAi, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp),
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HomeSky.AiButtonBg)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                tr(StrHome.startChat),
                color = HomeSky.AiButtonFg,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (dark) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = HomeSky.AiButtonFg, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ============================================================================ the two tiles

/** A decorative illustration inside a tile, placed by bias and sized as a share of the tile width. */
private class TileArt(val res: Int, val align: BiasAlignment, val widthFraction: Float, val aspect: Float)

/**
 * A half-width tile: gradient icon square and chevron on top, title (and line) at the bottom,
 * with an optional illustration in the free corner.
 */
@Composable
private fun HomeTile(
    tone: HomeSky.Tile,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    art: TileArt? = null,
    busy: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(18.dp)
    val tappable = onClick != null && !busy
    Box(
        modifier
            .heightIn(min = 90.dp)
            .homeShadow(shape, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tone.top, tone.bottom)))
            .border(1.dp, tone.border, shape)
            .then(if (tappable) Modifier.clickable { onClick?.invoke() } else Modifier),
    ) {
        if (art != null) {
            Image(
                painter = painterResource(art.res),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(art.align)
                    .fillMaxWidth(art.widthFraction)
                    .aspectRatio(art.aspect),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(tone.iconStart, tone.iconEnd))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp).rotate(iconRotation),
                    )
                }
                if (busy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = tone.chevron,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = tone.chevron,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Column {
                Text(
                    title,
                    color = HomeSky.Text,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = if (HomeSky.dark) Color(0xFFCAD1E0) else HomeSky.TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The group-subscription banner painted as a Home tile. Which state applies is the server's call. */
@Composable
private fun HomeBannerTile(spec: BannerSpec, modifier: Modifier = Modifier) {
    val tone = when (spec.kind) {
        BannerKind.BRAND -> HomeSky.TileOrange
        BannerKind.INFO -> HomeSky.TileBlue
        BannerKind.SUCCESS -> HomeSky.TileGreen
        BannerKind.DANGER -> HomeSky.TileRed
    }
    HomeTile(
        tone = tone,
        icon = spec.icon,
        title = spec.title,
        subtitle = spec.body,
        modifier = modifier,
        art = if (HomeSky.dark && spec.kind == BannerKind.BRAND) {
            TileArt(R.drawable.home_tile_live_dark, BiasAlignment(0.70f, 0.40f), 0.43f, 182f / 178f)
        } else {
            null
        },
        busy = spec.busy,
        onClick = spec.onClick,
    )
}

// ============================================================================ level card

/** White (navy) rounded card used for the level result and the placement prompt. */
@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .homeShadow(shape)
            .clip(shape)
            .background(HomeSky.Card)
            .border(1.dp, HomeSky.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The free placement test as one row: icon, title + one line, and an orange "Start" pill. */
@Composable
private fun PlacementPrompt(modifier: Modifier = Modifier, onStart: () -> Unit) {
    HomeCard(modifier, onClick = onStart) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(HomeSky.Brand.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Quiz, contentDescription = null, tint = HomeSky.Brand, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(StrHome.freePlacementTest),
                color = HomeSky.Text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr(StrHome.placementShort),
                color = HomeSky.TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(HomeSky.BrandDeep, HomeSky.Brand)))
                .clickable(onClick = onStart)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(tr(StrHome.startShort), color = HomeSky.OnBrand, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The level-result card: the ring (with its rosette), "Your level result", the real score line,
 * and a bar at the same percent underneath — ring, bar and number never disagree.
 */
@Composable
private fun ResultRecap(result: TestAttemptRow, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    HomeCard(modifier, onClick = onOpen) {
        LevelProgressRing(result.level, result.percent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(StrHome.yourLevelResult),
                color = HomeSky.Text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                trf(StrHome.scoredBreakdown, result.percent.toInt()),
                color = HomeSky.TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgress(result.percent / 100.0)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = HomeSky.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LevelProgressRing(level: String?, percent: Double) {
    val animated by animateFloatAsState((percent / 100.0).toFloat().coerceIn(0.04f, 1f), tween(720), label = "levelRing")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = HomeSky.RingTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = HomeSky.Brand,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(level ?: "—", color = HomeSky.Text, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFDB070), Color(0xFFF07A22)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** Bare progress bar — no label, for rows that already show the number in text next to it. */
@Composable
private fun LinearProgress(fraction: Double) {
    val animated by animateFloatAsState(fraction.toFloat().coerceIn(0f, 1f), tween(720), label = "linearProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HomeSky.Track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(999.dp))
                .background(HomeSky.Brand),
        )
    }
}

// ============================================================================ courses

@Composable
private fun HomeSectionHeader(title: String, action: String, modifier: Modifier = Modifier, onAction: () -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = HomeSky.Text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold),
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                action,
                color = HomeSky.Brand,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = HomeSky.Brand, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .then(
                if (selected) {
                    Modifier.shadow(6.dp, shape, ambientColor = HomeSky.ChipSelEnd, spotColor = HomeSky.ChipSelEnd)
                } else {
                    Modifier.homeShadow(shape, 2.dp)
                },
            )
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(HomeSky.ChipSelStart, HomeSky.ChipSelEnd)) else SolidColor(HomeSky.ChipBg),
            )
            .border(1.dp, if (selected) Color.Transparent else HomeSky.ChipBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) HomeSky.OnBrand else HomeSky.ChipText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** Home's course card: cover on the start side, title, teacher, then "new"/rating and the price pill. */
@Composable
private fun HomeCourseCard(course: Course, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .homeShadow(shape)
            .clip(shape)
            .background(HomeSky.Card)
            .border(1.dp, HomeSky.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            CoverImage(
                course.thumbnailUrl,
                Modifier
                    .size(width = 148.dp, height = 97.dp)
                    .clip(RoundedCornerShape(15.dp)),
                fallbackLabel = course.displayTitle,
            )
            if (course.isFreeCourse) {
                Pill(
                    tr(Str.free),
                    background = HomeSky.FreeBg,
                    foreground = HomeSky.FreeFg,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 4.dp, bottom = 2.dp, end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    course.displayTitle,
                    color = HomeSky.Text,
                    style = com.rork.pro.ui.components.titleDirectionStyle(MaterialTheme.typography.titleMedium)
                        .copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Avatar(course.teacher?.avatarUrl, course.teacher?.fullName, 21.dp)
                    Text(
                        course.teacher?.fullName ?: tr(StrHome.sevenProTeacher),
                        color = HomeSky.TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (course.ratingCount <= 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HomeSky.NewPillBg)
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    ) {
                        Text(tr(Str.new), color = HomeSky.NewPillFg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    RatingRow(course.ratingAvg, course.ratingCount)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (course.isFreeCourse) {
                                SolidColor(HomeSky.FreeBg)
                            } else {
                                Brush.horizontalGradient(listOf(HomeSky.BrandDeep, HomeSky.Brand))
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (course.isFreeCourse) tr(Str.free) else coursePriceLabel(course),
                        color = if (course.isFreeCourse) HomeSky.FreeFg else HomeSky.OnBrand,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ============================================================================ other rows

/**
 * "Join the meeting now" — shown only while a meeting is genuinely running (see
 * [ClassroomSession.isInProgress]) and removed by the ViewModel as soon as it ends.
 */
@Composable
private fun LiveMeetingCard(session: ClassroomSession, modifier: Modifier = Modifier, onJoin: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "liveDot")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .homeShadow(shape)
            .clip(shape)
            .background(HomeSky.Card)
            .border(1.dp, HomeSky.Brand.copy(alpha = 0.5f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Ink.TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Videocam, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Coral.copy(alpha = pulse)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr(StrClassroom.live),
                        color = Ink.Coral,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    session.title,
                    color = HomeSky.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                session.teacher?.fullName?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(
                        name,
                        color = HomeSky.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryAction(tr(StrHome.joinMeetingNow)) { onJoin() }
    }
}

/** One shortcut tile: an emoji badge over a short label — a row of quick launchers. */
@Composable
private fun HomeShortcutTile(shortcut: com.rork.pro.data.HomeShortcut, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(78.dp)
            .homeShadow(shape, 4.dp)
            .clip(shape)
            .background(HomeSky.Card)
            .border(1.dp, HomeSky.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(HomeSky.Brand.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(shortcut.icon, fontSize = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            shortcut.displayTitle,
            color = HomeSky.Text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> tr(StrHome.goodMorning)
    in 12..16 -> tr(StrHome.goodAfternoon)
    else -> tr(StrHome.goodEvening)
}


@Composable
private fun HeroBanner(banner: Banner, onClick: (String?) -> Unit) {
    Box(
        Modifier
            .width(320.dp)
            .aspectRatio(1.72f)
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Ink.SurfaceHigh)
            .clickable { onClick(banner.ctaTarget) },
    ) {
        if (!banner.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Ink.Canvas.copy(alpha = 0.94f),
                            Ink.Canvas.copy(alpha = 0.69f),
                            Ink.Canvas.copy(alpha = 0.19f),
                        ),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                banner.displayTitle,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!banner.displaySubtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(banner.displaySubtitle, color = Ink.Amber, style = MaterialTheme.typography.titleSmall)
            }
            if (!banner.displayCtaLabel.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Pill(banner.displayCtaLabel)
            }
        }
    }
}


/**
 * Resolves a shortcut's [target] string, set by the owner in Admin > Home content > Shortcuts.
 * Shares the same convention as [Banner.ctaTarget] ("test", "course:<id>") plus a few keys for
 * screens a shortcut can usefully jump straight to. An unrecognised target is a no-op rather than
 * a crash, since it is free text the owner typed.
 */
private fun resolveHomeShortcutTarget(
    navController: NavHostController,
    context: android.content.Context,
    target: String,
    isTeacher: Boolean,
) {
    when {
        target.isBlank() -> Unit
        target == "classroom" -> navController.navigate(
            ClassroomRoutes.home(if (isTeacher) com.rork.pro.ui.screens.classroom.ClassroomScope.TEACHER else com.rork.pro.ui.screens.classroom.ClassroomScope.STUDENT),
        )
        target == "tutor" -> navController.navigate(Routes.AI_TUTOR)
        target == "dubbing" -> navController.navigate(Routes.DUBBING)
        target == "courses" -> navController.navigate(Routes.COURSES)
        target == "profile" -> navController.navigate(Routes.PROFILE)
        target == "orders" -> navController.navigate(Routes.ORDERS)
        target == "certificates" -> navController.navigate(Routes.CERTIFICATES)
        target == "support" -> navController.navigate(Routes.SUPPORT)
        target == "test" -> navController.navigate(Routes.TEST_START)
        target.startsWith("course:") -> navController.navigate(Routes.courseDetail(target.removePrefix("course:")))
        target.startsWith("url:") -> runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target.removePrefix("url:"))))
        }
        else -> navController.navigate(Routes.COURSES)
    }
}

@Composable
fun CourseRowCard(
    course: Course,
    modifier: Modifier = Modifier,
    // Home passes its own palette; every other screen leaves these null and keeps the app one.
    surface: Color? = null,
    line: Color? = null,
    onClick: () -> Unit,
) {
    InkCard(
        modifier = modifier,
        onClick = onClick,
        color = surface ?: Ink.Surface,
        borderColor = line,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                CoverImage(
                    course.thumbnailUrl,
                    Modifier
                        .size(width = 118.dp, height = 96.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    fallbackLabel = course.displayTitle,
                )
                if (course.isFreeCourse) {
                    Pill(
                        tr(Str.free),
                        background = Ink.TealSoft,
                        foreground = Ink.Teal,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    course.displayTitle,
                    color = Ink.TextPrimary,
                    style = com.rork.pro.ui.components.titleDirectionStyle(MaterialTheme.typography.titleMedium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Avatar(course.teacher?.avatarUrl, course.teacher?.fullName, 22.dp)
                    Text(
                        course.teacher?.fullName ?: tr(StrHome.sevenProTeacher),
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RatingRow(course.ratingAvg, course.ratingCount)
                    Pill(
                        if (course.isFreeCourse) tr(Str.free) else coursePriceLabel(course),
                        background = if (course.isFreeCourse) Ink.TealSoft else Ink.Amber,
                        foreground = if (course.isFreeCourse) Ink.Teal else Ink.OnAmber,
                    )
                }
            }
        }
    }
}

