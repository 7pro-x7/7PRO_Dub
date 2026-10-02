package com.rork.pro.ui.screens.support

import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.rork.pro.data.SupportSounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.Backend
import com.rork.pro.data.MySupportChat
import com.rork.pro.data.SupportChatInfo
import com.rork.pro.data.SupportChatRepository
import com.rork.pro.data.SupportInboxRow
import com.rork.pro.data.SupportMessage
import com.rork.pro.data.SupportStaffState
import com.rork.pro.data.SupportStatus
import com.rork.pro.data.SupportTeamMember
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.StrSupport
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.screens.admin.AdminSearchField
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ============================================================ look

/**
 * The support chat's own palette. Everyone — learners and the admin console alike — sees it in
 * the app's sky / navy redesign (navy header, sky ground, orange actions). The original espresso
 * / cream / saffron colours only come back if AppPalette.sky is switched off.
 */
internal object SupportColors {
    private val sky: Boolean get() = com.rork.pro.ui.theme.AppPalette.sky
    private val dark: Boolean get() = com.rork.pro.ui.theme.AppAppearance.dark
    private fun pick(skyLight: Long, skyDark: Long, old: Long): Color =
        Color(if (!sky) old else if (dark) skyDark else skyLight)

    val Espresso: Color get() = pick(0xFF0E2A55, 0xFF0A1E3A, 0xFF231A15)
    val OnEspresso: Color get() = pick(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF6F1EA)
    val OnEspressoMuted: Color get() = pick(0xFFB2D7F9, 0xFF9BBEF2, 0xFFC9BBAB)
    val EspressoChip: Color get() = pick(0x1FFFFFFF, 0x1FFFFFFF, 0x14F6F1EA)
    val Ground: Color get() = pick(0xFFF3FAFE, 0xFF04172F, 0xFFF6F1EA)
    val Surface: Color get() = pick(0xFFFFFFFF, 0xFF0A1E3A, 0xFFFFFFFF)
    val Field: Color get() = pick(0xFFEEF4FB, 0xFF0F2747, 0xFFFBF8F4)
    val Line: Color get() = pick(0xFFDFE8F3, 0xFF213F6E, 0xFFEDE4D8)
    val Ink: Color get() = pick(0xFF0B1B45, 0xFFFFFFFF, 0xFF1C1410)
    val Ink2: Color get() = pick(0xFF3B5378, 0xFFC5D6F2, 0xFF4B3F35)
    val Muted: Color get() = pick(0xFF56739E, 0xFF9BBEF2, 0xFF6A5D51)
    val Saffron: Color get() = pick(0xFFE8601A, 0xFFFD8233, 0xFFE0A043)
    val Mine: Color get() = pick(0xFF0E2A55, 0xFF1B4384, 0xFF2B211B)
    val Online = Color(0xFF5FC49A)
    // Status chips and staff notes: pale fills with dark text in light mode, and the same hues
    // as deep fills with light text in dark mode (they used to stay pale and glare on navy).
    val SuccessSoft: Color get() = pick(0xFFDCEFE5, 0xFF143A2B, 0xFFDCEFE5)
    val Success: Color get() = pick(0xFF1D5A40, 0xFF7FD6A2, 0xFF1D5A40)
    val WarnSoft: Color get() = pick(0xFFFBE7C9, 0xFF3B2B12, 0xFFFBE7C9)
    val Warn: Color get() = pick(0xFF7A4A0E, 0xFFF2BC6B, 0xFF7A4A0E)
    val UrgentSoft: Color get() = pick(0xFFF8DAD3, 0xFF41201A, 0xFFF8DAD3)
    val Urgent: Color get() = pick(0xFF8E2F1E, 0xFFF4A190, 0xFF8E2F1E)
    val NoteBg: Color get() = pick(0xFFFFF4CC, 0xFF3A3212, 0xFFFFF4CC)
    val NoteBorder: Color get() = pick(0xFFD9B94F, 0xFF8C7426, 0xFFD9B94F)
    val NoteText: Color get() = pick(0xFF3D3007, 0xFFF4E4A6, 0xFF3D3007)
    val PaySoft: Color get() = pick(0xFFF7E3C4, 0xFF3B2A14, 0xFFF7E3C4)
    val Pay: Color get() = pick(0xFF7A4512, 0xFFF2BC6B, 0xFF7A4512)
    val AccountSoft: Color get() = pick(0xFFE4E1F4, 0xFF262250, 0xFFE4E1F4)
    val Account: Color get() = pick(0xFF3E3680, 0xFFB9B2F2, 0xFF3E3680)
    // The redesigned support screens (blue wave header, sky ground, cartoon art).
    private val isDark: Boolean get() = sky && dark
    val WaveA: Color get() = if (isDark) Color(0xFF0E3A7A) else Color(0xFF39A6FA)
    val WaveB: Color get() = if (isDark) Color(0xFF0A2552) else Color(0xFF1562E2)
    val OnWave = Color.White
    val OnWaveChip: Color get() = Color.White.copy(alpha = if (isDark) 0.14f else 0.24f)
    val Strip: Color get() = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.88f)
    val StripText: Color get() = if (isDark) Color.White else Color(0xFF0B2A6B)
    val StripMuted: Color get() = if (isDark) Color(0xFFB2D7F9) else Color(0xFF3B5378)
    val SkyTop: Color get() = if (isDark) Color(0xFF071B38) else Color(0xFFE6F3FE)
    val SkyBottom: Color get() = if (isDark) Color(0xFF04172F) else Color(0xFFF2F9FE)
    val Cloud: Color get() = if (isDark) Color(0xFF0C2850).copy(alpha = 0.7f) else Color(0xFFD7EBFC).copy(alpha = 0.75f)
    val Doodle: Color get() = if (isDark) Color(0xFF3B6FB0) else Color(0xFF8CC4F5)
    val ArtAlpha: Float get() = if (isDark) 0.55f else 1f
    val MineBubble: Color get() = if (isDark) Color(0xFF1B4384) else Color(0xFF0B3A8C)
    val Blue: Color get() = if (isDark) Color(0xFF3D8BFF) else Color(0xFF1E78F0)
    val BlueText: Color get() = if (isDark) Color(0xFF6FB4FF) else Color(0xFF1F5FBF)
    val DayPillBg: Color get() = if (isDark) Color(0xFF12325F) else Color(0xFFDCEEFD)
    val Sheet: Color get() = if (isDark) Color(0xFF0A1E3A) else Color.White
    val FieldBg: Color get() = if (isDark) Color(0xFF0F2747) else Color(0xFFEEF4FB)
    val FieldLine: Color get() = if (isDark) Color(0xFF213F6E) else Color(0xFFDCE8F5)
    val Title: Color get() = if (isDark) Color.White else Color(0xFF0B1B45)
    val HomeSkyTop: Color get() = if (isDark) Color(0xFF0B2A55) else Color(0xFF9AD8FD)
    val Star: Color get() = pick(0xFFF59E2B, 0xFFFD8233, 0xFFE0A043)
    val StarOff: Color get() = pick(0xFFD3DEEC, 0xFF2C4E82, 0xFFD9CDBF)
}

private typealias C = SupportColors

internal fun topicLabel(topic: String): String = when (topic) {
    SupportChatRepository.Topic.PAYMENT -> tr(StrSupport.topicPayment)
    SupportChatRepository.Topic.COURSE -> tr(StrSupport.topicCourse)
    SupportChatRepository.Topic.ACCOUNT -> tr(StrSupport.topicAccount)
    else -> tr(StrSupport.topicOther)
}

private fun topicIcon(topic: String): ImageVector = when (topic) {
    SupportChatRepository.Topic.PAYMENT -> Icons.Default.CreditCard
    SupportChatRepository.Topic.COURSE -> Icons.Default.PlayCircleOutline
    SupportChatRepository.Topic.ACCOUNT -> Icons.Default.Person
    else -> Icons.Default.HelpOutline
}

private fun topicTint(topic: String): Pair<Color, Color> = when (topic) {
    SupportChatRepository.Topic.PAYMENT -> C.PaySoft to C.Pay
    SupportChatRepository.Topic.COURSE -> C.SuccessSoft to C.Success
    SupportChatRepository.Topic.ACCOUNT -> C.AccountSoft to C.Account
    else -> C.Field to C.Ink2
}

// ============================================================ time

private fun instantOf(iso: String?): Instant? =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

private fun clockOf(iso: String?): String {
    val at = instantOf(iso) ?: return ""
    return DateTimeFormatter.ofPattern("h:mm a", AppLanguage.locale()).withZone(ZoneId.systemDefault()).format(at)
}

private fun dayOf(iso: String?): LocalDate? = instantOf(iso)?.atZone(ZoneId.systemDefault())?.toLocalDate()

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> tr(StrSupport.today)
        today.minusDays(1) -> tr(StrSupport.yesterday)
        else -> DateTimeFormatter.ofPattern("d MMMM", AppLanguage.locale()).format(day)
    }
}

/** "3:59 PM" today, otherwise a short date — for list rows. */
private fun whenLabel(iso: String?): String {
    val day = dayOf(iso) ?: return ""
    return if (day == LocalDate.now()) clockOf(iso) else DateTimeFormatter.ofPattern("d MMM", AppLanguage.locale()).format(day)
}

private fun minutesSince(iso: String?): Long =
    instantOf(iso)?.let { Duration.between(it, Instant.now()).toMinutes().coerceAtLeast(0) } ?: 0

private fun durationLabel(minutes: Long): String =
    if (minutes < 60) trf(StrSupport.minutesShort, minutes.toInt().coerceAtLeast(1)) else trf(StrSupport.hoursShort, (minutes / 60).toInt())

// ============================================================ shared pieces

@Composable
private fun EspressoIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.EspressoChip)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = C.OnEspresso, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PersonAvatar(url: String?, name: String?, size: Int, online: Boolean = false, ring: Color = C.Surface) {
    Box(Modifier.size(size.dp)) {
        Avatar(url, name, size.dp)
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(C.Online),
            )
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count <= 0) return
    Box(
        Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(C.Saffron).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stars(rating: Int, size: Int = 14) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(5) { i ->
            Icon(if (i < rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (i < rating) C.Star else C.StarOff, modifier = Modifier.size(size.dp))
        }
    }
}

@Composable
private fun ErrorLine(error: AppError?, onDismiss: () -> Unit) {
    if (error == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.UrgentSoft)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(error.message, color = C.Urgent, fontSize = 13.sp)
    }
}

// ============================================================ student: support home

class SupportHomeVm : ViewModel() {
    data class Data(val status: SupportStatus, val chats: List<MySupportChat>) {
        val active: MySupportChat? get() = chats.firstOrNull { it.status != "CLOSED" }
    }

    private val _state = MutableStateFlow<Async<Data>>(Async.Loading)
    val state: StateFlow<Async<Data>> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                load(quiet = true)
            }
        }
    }

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun delete(chatId: String) {
        viewModelScope.launch {
            runCatching { SupportChatRepository.delete(chatId) }
                .onFailure { _error.value = it.toAppError() }
            load(quiet = true)
        }
    }

    fun clearError() { _error.value = null }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet && _state.value !is Async.Success) _state.value = Async.Loading
            runCatching { Data(SupportChatRepository.status(), SupportChatRepository.myChats()) }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet || _state.value !is Async.Success) _state.value = Async.Failure(it.toAppError()) }
        }
    }
}

@Composable
fun SupportHomeScreen(navController: NavHostController) {
    val vm: SupportHomeVm = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    ReloadOnResume { vm.load(quiet = true) }

    val data = (state as? Async.Success)?.value
    val active = data?.active
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val homeError by vm.error.collectAsStateWithLifecycle()
    fun startOrContinue(topic: String) {
        if (active != null) navController.navigate("support/chat/${active.id}")
        else navController.navigate("support/new/$topic")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.HomeSkyTop, C.SkyTop, C.SkyBottom)))
            .verticalScroll(rememberScrollState()),
    ) {
        // ── hero: back pill, the cartoon learner, the question and the team status ──
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    val c = C.Cloud
                    drawCircle(c, radius = size.width * 0.16f, center = androidx.compose.ui.geometry.Offset(size.width * 0.02f, size.height * 0.18f))
                    drawCircle(c, radius = size.width * 0.20f, center = androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.98f))
                    drawCircle(c, radius = size.width * 0.14f, center = androidx.compose.ui.geometry.Offset(size.width * 1.0f, size.height * 0.62f))
                },
            )
            // the learner, on the end side, cut by the card like in the design; the bulb sits over
            // the raised finger, so it is placed on the picture itself (not mirrored).
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.46f)
                    .aspectRatio(400f / 440f),
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_boy),
                    contentDescription = null,
                    alpha = if (C.Title == Color.White) 0.95f else 1f,
                    modifier = Modifier.fillMaxSize(),
                )
                LightBulb(Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopRight).absoluteOffset(x = 6.dp, y = (-6).dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 46.dp),
            ) {
                // back pill: arrow · headset · "Support centre"
                Row(
                    Modifier
                        .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x551562E2), spotColor = Color(0x551562E2))
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.horizontalGradient(listOf(C.WaveA, C.WaveB)))
                        .clickable { navController.popBackStack() }
                        .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(StrSupport.centerTitle), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.SupportAgent, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(StrSupport.centerTitle), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(30.dp))
                Column(Modifier.fillMaxWidth(0.62f)) {
                    Text(tr(StrSupport.heroTitle), color = C.Title, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(tr(StrSupport.heroBody), color = C.Ink2, fontSize = 15.sp, lineHeight = 24.sp)
                    Spacer(Modifier.height(16.dp))
                    val status = data?.status
                    val online = status?.online == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val agents = status?.agents.orEmpty()
                        Box(
                            Modifier
                                .size(54.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (C.Title == Color.White) Color(0xFF12325F) else Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (agents.isNotEmpty()) {
                                Text(agents.first().take(1).uppercase(), color = C.Title, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            } else {
                                Icon(Icons.Default.SupportAgent, null, tint = C.Blue, modifier = Modifier.size(26.dp))
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) C.Online else C.Muted))
                                Text(
                                    tr(if (online) StrSupport.teamOnline else StrSupport.teamOffline),
                                    color = C.Title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                tr(if (online) StrSupport.replyUsually else StrSupport.leaveMessage),
                                color = C.Ink2,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── start card ──
        Column(
            Modifier
                .offset(y = (-26).dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x261562E2), spotColor = Color(0x261562E2))
                .clip(RoundedCornerShape(30.dp))
                .background(C.Sheet)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (active != null) {
                Text(tr(StrSupport.openChatNote), color = C.Ink2, fontSize = 14.sp, lineHeight = 22.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Forum, null, tint = C.Blue, modifier = Modifier.size(30.dp))
                    Text(tr(StrSupport.whatAbout), color = C.Title, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Sparks(Color(0xFFF59E2B))
                }
                SupportChatRepository.Topic.ALL.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { topic ->
                            TopicTile(topic, Modifier.weight(1f)) { startOrContinue(topic) }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .shadow(10.dp, RoundedCornerShape(31.dp), ambientColor = Color(0x66F26B0F), spotColor = Color(0x66F26B0F))
                    .clip(RoundedCornerShape(31.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF8B2C), Color(0xFFF26B0F))))
                    .clickable { startOrContinue(SupportChatRepository.Topic.OTHER) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Sparks(Color.White.copy(alpha = 0.9f))
                Icon(Icons.Outlined.MarkChatUnread, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(tr(if (active != null) StrSupport.continueChat else StrSupport.writeMessage), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // ── my chats ──
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Outlined.Forum, null, tint = C.Blue, modifier = Modifier.size(30.dp))
                Text(tr(StrSupport.yourChats), color = C.Title, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            when (val s = state) {
                is Async.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                }
                is Async.Failure -> Text(s.error.message, color = C.Urgent, fontSize = 13.sp, modifier = Modifier.clickable { vm.load() })
                is Async.Success -> if (s.value.chats.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_empty),
                            contentDescription = null,
                            alpha = C.ArtAlpha.coerceAtLeast(0.8f),
                            modifier = Modifier.width(220.dp).aspectRatio(300f / 160f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrSupport.noChats), color = C.Ink2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    s.value.chats.forEach { chat ->
                        MyChatRow(chat, onDelete = { deleteTarget = chat.id }) { navController.navigate("support/chat/${chat.id}") }
                    }
                }
            }
        }
        ErrorLine(homeError) { vm.clearError() }
        AdBanner("SUPPORT", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = C.Surface,
            title = { Text(tr(StrSupport.deleteChatTitle), color = C.Ink) },
            text = { Text(tr(StrSupport.deleteChatBody), color = C.Ink2) },
            confirmButton = { TextButton(onClick = { deleteTarget = null; vm.delete(id) }) { Text(tr(StrSupport.delete), color = C.Urgent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr(com.rork.pro.ui.i18n.StrAdmin.cancel), color = C.Ink2) } },
        )
    }
}

/** One of the four topic tiles: pastel card, icon in a soft circle, label, forward chevron. */
@Composable
private fun TopicTile(topic: String, modifier: Modifier, onClick: () -> Unit) {
    val dark = C.Title == Color.White
    data class Look(val bg: Long, val line: Long, val circle: Long, val tint: Long, val darkBg: Long)
    val look = when (topic) {
        SupportChatRepository.Topic.PAYMENT -> Look(0xFFFFF1E4, 0xFFFAD9BD, 0xFFFDE0C6, 0xFFE8711A, 0xFF3B2A14)
        SupportChatRepository.Topic.COURSE -> Look(0xFFE8F7EF, 0xFFCDEBD9, 0xFFCFEEDC, 0xFF16A34A, 0xFF143A2B)
        SupportChatRepository.Topic.ACCOUNT -> Look(0xFFE6F1FD, 0xFFCFE3F8, 0xFFD3E6FB, 0xFF1E78F0, 0xFF12325F)
        else -> Look(0xFFEFEAFE, 0xFFDDD3FA, 0xFFDDD3FB, 0xFF7C3AED, 0xFF2A2563)
    }
    val tint = Color(look.tint)
    val icon = when (topic) {
        SupportChatRepository.Topic.PAYMENT -> Icons.Outlined.CreditCard
        SupportChatRepository.Topic.COURSE -> Icons.Default.PlayCircle
        SupportChatRepository.Topic.ACCOUNT -> Icons.Default.Person
        else -> Icons.Default.Help
    }
    Row(
        modifier
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (dark) Color(look.darkBg) else Color(look.bg))
            .border(1.dp, if (dark) tint.copy(alpha = 0.35f) else Color(look.line), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (dark) tint.copy(alpha = 0.22f) else Color(look.circle)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(topicLabel(topic), color = C.Title, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 2)
        val chevron = when {
            dark -> tint
            topic == SupportChatRepository.Topic.COURSE || topic == SupportChatRepository.Topic.OTHER -> Color(0xFF0B2A6B)
            else -> tint
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = chevron, modifier = Modifier.size(26.dp))
    }
}

/** Three little "spark" strokes, as drawn next to the card title and on the orange button. */
@Composable
private fun Sparks(color: Color) {
    Box(
        Modifier.size(width = 18.dp, height = 26.dp).drawBehind {
            val sw = 2.5.dp.toPx()
            val cx = size.width * 0.3f
            val cy = size.height / 2f
            drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy - 9.dp.toPx()), androidx.compose.ui.geometry.Offset(cx + 7.dp.toPx(), cy - 12.dp.toPx()), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx + 9.dp.toPx(), cy), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy + 9.dp.toPx()), androidx.compose.ui.geometry.Offset(cx + 7.dp.toPx(), cy + 12.dp.toPx()), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        },
    )
}

/** The yellow bulb with its rays above the learner's raised finger. */
@Composable
private fun LightBulb(modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier.size(64.dp).drawBehind {
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val rays = 7
                for (i in 0 until rays) {
                    val a = Math.toRadians(-160.0 + i * (140.0 / (rays - 1)))
                    val r0 = 22.dp.toPx()
                    val r1 = 30.dp.toPx()
                    drawLine(
                        Color(0xFFFFC83D),
                        androidx.compose.ui.geometry.Offset(c.x + (r0 * Math.cos(a)).toFloat(), c.y + (r0 * Math.sin(a)).toFloat()),
                        androidx.compose.ui.geometry.Offset(c.x + (r1 * Math.cos(a)).toFloat(), c.y + (r1 * Math.sin(a)).toFloat()),
                        3.dp.toPx(),
                        androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFFCD45), modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun MyChatRow(chat: MySupportChat, onDelete: () -> Unit, onClick: () -> Unit) {
    val closed = chat.status == "CLOSED"
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1A1562E2), spotColor = Color(0x1A1562E2))
            .clip(RoundedCornerShape(22.dp))
            .background(C.Sheet)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (bg, fg) = topicTint(chat.topic)
        Box(Modifier.size(46.dp).clip(CircleShape).background(if (closed) C.Field else bg), contentAlignment = Alignment.Center) {
            Icon(topicIcon(chat.topic), null, tint = if (closed) C.Ink2 else fg, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(topicLabel(chat.topic), color = C.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                when (chat.status) {
                    "CLOSED" -> Tag(tr(StrSupport.ended), C.Field, C.Ink2)
                    "WAITING" -> Tag(tr(StrSupport.waiting), C.WarnSoft, C.Warn)
                    else -> Tag(tr(StrSupport.open), C.SuccessSoft, C.Success)
                }
            }
            if (closed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (chat.rating != null) {
                        Stars(chat.rating)
                        Text(tr(StrSupport.rated), color = C.Muted, fontSize = 12.sp)
                    } else {
                        Text(tr(StrSupport.rateIt), color = C.Saffron, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val who = if (chat.lastSender == "STAFF") chat.agentName?.let { "$it: " }.orEmpty() else ""
                Text(
                    who + chat.lastMessagePreview.orEmpty(),
                    color = if (chat.userUnread > 0) C.Ink else C.Muted,
                    fontWeight = if (chat.userUnread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(whenLabel(chat.lastMessageAt), color = C.Muted, fontSize = 12.sp)
            UnreadBadge(chat.userUnread)
        }
        Icon(
            Icons.Default.Delete,
            contentDescription = tr(StrSupport.deleteChat),
            tint = C.Muted,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete).padding(8.dp),
        )
    }
}

// ============================================================ chat (student and team)

class SupportChatVm : ViewModel() {
    private var chatId: String? = null
    private var topic: String = SupportChatRepository.Topic.OTHER
    private var bound = false

    private val _info = MutableStateFlow<SupportChatInfo?>(null)
    val info: StateFlow<SupportChatInfo?> = _info.asStateFlow()
    private val _messages = MutableStateFlow<List<SupportMessage>>(emptyList())
    val messages: StateFlow<List<SupportMessage>> = _messages.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()
    private val _otherTyping = MutableStateFlow(false)
    val otherTyping: StateFlow<Boolean> = _otherTyping.asStateFlow()
    private val _created = MutableStateFlow<String?>(null)
    /** Set once the first message of a brand-new chat created it on the server. */
    val created: StateFlow<String?> = _created.asStateFlow()
    private val _team = MutableStateFlow<List<SupportTeamMember>>(emptyList())
    val team: StateFlow<List<SupportTeamMember>> = _team.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    /** True once this chat no longer exists (deleted here or by the other side) — the screen leaves. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _incoming = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Fires when a message from the other side arrives while this chat is open. */
    val incoming: SharedFlow<Unit> = _incoming.asSharedFlow()

    private var channel: RealtimeChannel? = null
    private var typingJob: Job? = null
    private var lastTypingPing = 0L
    private var visible = true

    private val mySide: String get() = if (_info.value?.isStaffView == true) "staff" else "user"

    fun bind(id: String?, newTopic: String?) {
        if (bound) return
        bound = true
        if (id == null) {
            topic = newTopic?.takeIf { it in SupportChatRepository.Topic.ALL } ?: SupportChatRepository.Topic.OTHER
            _loading.value = false
            return
        }
        chatId = id
        viewModelScope.launch {
            refresh(initial = true)
            _loading.value = false
        }
        watch(id)
        viewModelScope.launch {
            while (true) {
                delay(8_000)
                if (visible) refresh()
            }
        }
    }

    val newTopic: String get() = topic

    fun setVisible(v: Boolean) {
        visible = v
        if (v) viewModelScope.launch { refresh() }
    }

    private suspend fun refresh(initial: Boolean = false) {
        val id = chatId ?: return
        runCatching { SupportChatRepository.info(id) }
            .onSuccess { _info.value = it }
            .onFailure {
                val err = it.toAppError()
                if (err.code == "CHAT_NOT_FOUND") _deleted.value = true
                else if (_info.value == null) _error.value = err
            }
        if (_deleted.value) return
        val after = _messages.value.lastOrNull()?.id ?: 0L
        runCatching { SupportChatRepository.messages(id, after) }
            .onSuccess { fresh ->
                if (fresh.isNotEmpty()) {
                    _messages.value = (_messages.value + fresh).distinctBy { it.id }.sortedBy { it.id }
                    val staffView = _info.value?.isStaffView == true
                    val fromOther = fresh.any { it.kind == "TEXT" && it.fromStaff != staffView }
                    if (fromOther) _otherTyping.value = false
                    if (fromOther && !initial && visible) _incoming.tryEmit(Unit)
                }
                if (visible && (initial || fresh.isNotEmpty())) SupportChatRepository.markRead(id)
            }
    }

    private fun watch(id: String) {
        viewModelScope.launch {
            runCatching {
                val ch = SupportChatRepository.openChatChannel(id)
                channel = ch
                val changed = SupportChatRepository.liveFlow(ch, SupportChatRepository.Live.CHANGED)
                val typing = SupportChatRepository.liveFlow(ch, SupportChatRepository.Live.TYPING)
                val inserts = SupportChatRepository.messageInserts(ch, id)
                val updates = SupportChatRepository.chatUpdates(ch, id)
                launch { runCatching { changed.collect { refresh() } } }
                launch { runCatching { inserts.collect { refresh() } } }
                launch { runCatching { updates.collect { refresh() } } }
                launch {
                    runCatching {
                        typing.collect { payload ->
                            val side = runCatching { payload["side"]?.jsonPrimitive?.content }.getOrNull()
                            if (side != null && side != mySide) {
                                _otherTyping.value = true
                                typingJob?.cancel()
                                typingJob = launch {
                                    delay(4_000)
                                    _otherTyping.value = false
                                }
                            }
                        }
                    }
                }
                ch.subscribe(blockUntilSubscribed = true)
            }.onFailure {
                android.util.Log.w("SupportChat", "live updates unavailable: ${it.message}")
            }
        }
    }

    fun onDraftChanged() {
        val ch = channel ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingPing < 2_500) return
        lastTypingPing = now
        viewModelScope.launch {
            SupportChatRepository.ping(ch, SupportChatRepository.Live.TYPING, buildJsonObject { put("side", mySide) })
        }
    }

    fun send(text: String, note: Boolean, onSent: () -> Unit) {
        val body = text.trim()
        if (body.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            val id = chatId
            if (id == null) {
                runCatching { SupportChatRepository.start(topic, body) }
                    .onSuccess { onSent(); _created.value = it }
                    .onFailure { _error.value = it.toAppError() }
            } else {
                runCatching { SupportChatRepository.send(id, body, note) }
                    .onSuccess { msg ->
                        onSent()
                        _messages.value = (_messages.value + msg).distinctBy { it.id }.sortedBy { it.id }
                        channel?.let { SupportChatRepository.ping(it, SupportChatRepository.Live.CHANGED) }
                        refresh()
                    }
                    .onFailure { _error.value = it.toAppError() }
            }
            _sending.value = false
        }
    }

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    /** A photo (camera / gallery) or any file, sent as its own message in this chat. */
    fun sendAttachment(context: android.content.Context, uri: android.net.Uri, image: Boolean) {
        val id = chatId ?: return
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            _error.value = null
            runCatching {
                val mime = context.contentResolver.getType(uri)
                val file = com.rork.pro.data.MediaRepository.read(
                    context, uri, SupportChatRepository.MAX_ATTACHMENT_BYTES, compressVideo = false,
                )
                val isImage = image || mime?.startsWith("image/") == true
                SupportChatRepository.sendAttachment(id, file, if (isImage) "image/jpeg" else mime, isImage)
            }
                .onSuccess { msg ->
                    _messages.value = (_messages.value + msg).distinctBy { it.id }.sortedBy { it.id }
                    channel?.let { SupportChatRepository.ping(it, SupportChatRepository.Live.CHANGED) }
                    refresh()
                }
                .onFailure { _error.value = it.toAppError() }
            _uploading.value = false
        }
    }

    private fun act(block: suspend (String) -> Unit) {
        val id = chatId ?: return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            runCatching { block(id) }
                .onSuccess { channel?.let { SupportChatRepository.ping(it, SupportChatRepository.Live.CHANGED) } }
                .onFailure { _error.value = it.toAppError() }
            refresh()
            _sending.value = false
        }
    }

    fun claim() = act { SupportChatRepository.claim(it) }
    fun takeOver() = act { id -> SupportChatRepository.transfer(id, Backend.currentUserId ?: error("UNAUTHORIZED")) }
    fun transfer(to: String) = act { SupportChatRepository.transfer(it, to) }
    fun close() = act { SupportChatRepository.close(it) }

    fun delete() {
        val id = chatId ?: return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            // Ping first so the other side's open screen refetches and finds the chat gone.
            channel?.let { SupportChatRepository.ping(it, SupportChatRepository.Live.CHANGED) }
            runCatching { SupportChatRepository.delete(id) }
                .onSuccess { _deleted.value = true }
                .onFailure { _error.value = it.toAppError() }
            _sending.value = false
        }
    }
    fun rate(stars: Int, comment: String) = act { SupportChatRepository.rate(it, stars, comment.takeIf { c -> c.isNotBlank() }) }
    fun clearError() { _error.value = null }

    fun loadTeam() {
        viewModelScope.launch { runCatching { SupportChatRepository.team() }.onSuccess { _team.value = it } }
    }

    override fun onCleared() {
        val ch = channel
        channel = null
        Backend.appScope.launch { ch?.let { runCatching { Backend.realtime.removeChannel(it) } } }
        super.onCleared()
    }
}

/** Runs [block] every time the screen comes back to the foreground. */
@Composable
private fun ReloadOnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else block()
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/**
 * One chat, for either side. The server tells the screen which side it is on
 * ([SupportChatInfo.isStaffView]); [chatId] null means a new chat that starts with the first message.
 */
@Composable
fun SupportChatScreen(
    navController: NavHostController,
    chatId: String?,
    newTopic: String? = null,
    canOpenCourse: Boolean = false,
    onOpenCourse: () -> Unit = {},
) {
    val vm: SupportChatVm = viewModel(key = "support-chat-${chatId ?: "new-$newTopic"}")
    LaunchedEffect(chatId) { vm.bind(chatId, newTopic) }

    val info by vm.info.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val otherTyping by vm.otherTyping.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()
    val team by vm.team.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) { if (deleted) navController.popBackStack() }

    val soundContext = LocalContext.current
    LaunchedEffect(vm) { vm.incoming.collect { SupportSounds.playIncoming(soundContext) } }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> vm.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> vm.setVisible(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(created) {
        created?.let { id ->
            navController.navigate("support/chat/$id") {
                popUpTo("support/new/{topic}") { inclusive = true }
            }
        }
    }

    val staffView = info?.isStaffView == true
    val closed = info?.isClosed == true
    val myId = Backend.currentUserId
    var draft by rememberSaveable { mutableStateOf("") }
    var noteMode by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(ComposerPanel.NONE) }
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
    val picker = com.rork.pro.ui.components.rememberFilePicker()
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok -> val u = cameraUri; if (ok && u != null) vm.sendAttachment(appContext, u, image = true) }
    fun launchCamera() {
        runCatching {
            val dir = java.io.File(appContext.cacheDir, "camera").apply { mkdirs() }
            val file = java.io.File(dir, "support-${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(appContext, appContext.packageName + ".files", file)
            cameraUri = uri
            takePicture.launch(uri)
        }
    }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() else android.widget.Toast.makeText(appContext, tr(StrSupport.cameraDenied), android.widget.Toast.LENGTH_SHORT).show() }
    fun openCamera() {
        val has = androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (has) launchCamera() else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    val listState = rememberLazyListState()
    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId, otherTyping) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.SkyTop, C.SkyBottom))).imePadding()) {
        // ── header: blue wave, round glass buttons, then the topic strip ──
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawWaveHeader(waveDepth = 26.dp.toPx()) },
        ) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, tr(StrSupport.centerTitle)) { navController.popBackStack() }
                if (staffView) {
                    val u = info?.user
                    PersonAvatar(u?.avatarUrl, u?.fullName ?: u?.email, 46, online = u?.online == true, ring = C.WaveB)
                    Column(Modifier.weight(1f)) {
                        Text(u?.fullName?.takeIf { it.isNotBlank() } ?: u?.email.orEmpty(), color = C.OnWave, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(u?.email.orEmpty(), color = C.OnWave.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!closed) {
                        GlassCircle(Icons.Default.SwapHoriz, tr(StrSupport.transfer)) { vm.loadTeam(); showTransfer = true }
                        Box(
                            Modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(C.Saffron).clickable { confirmEnd = true }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(tr(StrSupport.end), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (info != null) {
                        Box {
                            GlassCircle(Icons.Default.MoreVert, tr(StrSupport.options)) { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr(StrSupport.deleteChat), color = C.Urgent) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                } else {
                    val agent = info?.agentName
                    if (agent != null) {
                        PersonAvatar(info?.agentAvatar, agent, 46, online = info?.agentOnline == true && !closed, ring = C.WaveB)
                    } else {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(C.OnWaveChip), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SupportAgent, null, tint = C.OnWave, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(agent ?: tr(StrSupport.supportTeam), color = C.OnWave, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (closed) C.OnWave.copy(alpha = 0.5f) else C.Online))
                            Text(
                                when {
                                    closed -> tr(StrSupport.chatEnded)
                                    agent == null -> tr(StrSupport.waitingFirstReply)
                                    info?.agentOnline == true -> tr(StrSupport.agentOnline)
                                    else -> tr(StrSupport.agentSub)
                                },
                                color = C.OnWave.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (info != null) {
                        Box {
                            GlassCircle(Icons.Default.MoreVert, tr(StrSupport.options)) { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!closed) {
                                    DropdownMenuItem(text = { Text(tr(StrSupport.endChat)) }, onClick = { menuOpen = false; confirmEnd = true })
                                }
                                DropdownMenuItem(
                                    text = { Text(tr(StrSupport.deleteChat), color = C.Urgent) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.size(46.dp))
                    }
                }
            }
            // topic strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(if (C.Strip.alpha > 0.5f) 6.dp else 0.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x331562E2), spotColor = Color(0x331562E2))
                    .clip(RoundedCornerShape(24.dp))
                    .background(C.Strip)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val t = info?.topic ?: vm.newTopic
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFF5A623)), contentAlignment = Alignment.Center) {
                    Icon(if (t == SupportChatRepository.Topic.OTHER) Icons.Default.QuestionMark else topicIcon(t), null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                if (staffView) {
                    val courses = info?.courses.orEmpty()
                    val text = when {
                        courses.isEmpty() -> tr(StrSupport.noCourses)
                        courses.size == 1 -> courses.first().displayTitle
                        else -> courses.first().displayTitle + " · " + trf(StrSupport.moreCourses, courses.size - 1)
                    }
                    Text(topicLabel(t) + " · " + text, color = C.StripText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (canOpenCourse) {
                        Text(tr(StrSupport.openCourse), color = C.Saffron, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpenCourse).padding(4.dp))
                    }
                } else {
                    Text(topicLabel(t), color = C.StripText, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        "•  " + tr(if (closed) StrSupport.chatEnded else StrSupport.chatOpen),
                        color = C.StripMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.School, null, tint = C.Blue, modifier = Modifier.size(24.dp))
                }
            }
        }
        }

        // ── messages ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatDecor()
            if (loading) {
                CircularProgressIndicator(color = C.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp).align(Alignment.Center))
            } else if (chatId == null && messages.isEmpty()) {
                Text(
                    tr(StrSupport.emptyNew),
                    color = C.Muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                val otherName = if (staffView) info?.user?.fullName?.substringBefore(' ') else info?.agentName
                val otherReadAt = instantOf(info?.otherLastReadAt)
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    var lastDay: LocalDate? = null
                    val rows = buildList<Pair<String, Any>> {
                        messages.forEach { m ->
                            val d = dayOf(m.createdAt)
                            if (d != null && d != lastDay) {
                                add("day-$d" to d)
                                lastDay = d
                            }
                            add("m-${m.id}" to m)
                        }
                    }
                    items(rows, key = { it.first }) { (_, value) ->
                        when (value) {
                            is LocalDate -> DayPill(dayLabel(value))
                            is SupportMessage -> when {
                                value.isEvent -> EventLine(value, staffView)
                                value.isNote -> NoteCard(value)
                                else -> {
                                    val mine = if (staffView) value.fromStaff else !value.fromStaff
                                    val read = mine && otherReadAt != null && instantOf(value.createdAt)?.let { !it.isAfter(otherReadAt) } == true
                                    Bubble(value, mine, read, mineAvatar = !staffView)
                                }
                            }
                        }
                    }
                    if (otherTyping && !closed) {
                        item(key = "typing") { TypingRow(otherName) }
                    }
                }
            }
        }

        ErrorLine(error) { vm.clearError() }

        // ── bottom ──
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = Color(0x261562E2), spotColor = Color(0x261562E2))
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(C.Sheet)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val i = info
            when {
                closed && !staffView -> RatingPanel(
                    agentName = i?.agentName,
                    rating = i?.rating,
                    busy = sending,
                    onRate = { stars, comment -> vm.rate(stars, comment) },
                    onNewChat = {
                        navController.navigate("support") { popUpTo("support") { inclusive = true } }
                    },
                )
                closed -> {
                    Text(tr(StrSupport.closedBanner), color = C.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    i?.rating?.let { r ->
                        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stars(r, 16)
                            Text(trf(StrSupport.ratingGiven, r), color = C.Ink2, fontSize = 13.sp)
                        }
                        i?.ratingComment?.takeIf { it.isNotBlank() }?.let {
                            Text("“$it”", color = C.Ink2, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
                else -> {
                    val assignedToOther = staffView && i?.assignedTo != null && i.assignedTo != myId
                    if (staffView) {
                        if (i?.isWaiting == true) {
                            StaffBanner(tr(StrSupport.waitingBanner), tr(StrSupport.claim), sending) { vm.claim() }
                        } else if (assignedToOther) {
                            StaffBanner(trf(StrSupport.otherBanner, i?.agentName.orEmpty()), tr(StrSupport.takeOver), sending) { vm.takeOver() }
                        }
                        if (!noteMode) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(StrSupport.quick1, StrSupport.quick2, StrSupport.quick3, StrSupport.quick4).forEach { q ->
                                    val text = tr(q)
                                    Box(
                                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, C.FieldLine, RoundedCornerShape(18.dp)).background(C.FieldBg)
                                            .clickable { draft = text }.padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(text, color = C.Ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                                }
                            }
                        }
                        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(C.DayPillBg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            ModeTab(tr(StrSupport.replyTab), !noteMode, C.Ink) { noteMode = false }
                            ModeTab(tr(StrSupport.noteTab), noteMode, C.NoteText) { noteMode = true }
                        }
                    }
                    val canSend = draft.isNotBlank() && !sending && (!assignedToOther || noteMode)
                    val note = noteMode && staffView
                    Composer(
                        value = draft,
                        onChange = { draft = it.take(2000); vm.onDraftChanged() },
                        placeholder = tr(
                            when {
                                note -> StrSupport.typeNote
                                staffView -> StrSupport.typeReply
                                else -> StrSupport.typeMessage
                            },
                        ),
                        note = note,
                        enabled = canSend,
                        sending = sending,
                        emojiOpen = panel == ComposerPanel.EMOJI,
                        onEmoji = { panel = if (panel == ComposerPanel.EMOJI) ComposerPanel.NONE else ComposerPanel.EMOJI },
                    ) { vm.send(draft, note) { draft = "" } }
                    when (panel) {
                        ComposerPanel.EMOJI -> EmojiGrid(SupportEmoji.faces, big = false) { e -> draft = (draft + e).take(2000); vm.onDraftChanged() }
                        ComposerPanel.STICKERS -> EmojiGrid(SupportEmoji.stickers, big = true) { e ->
                            panel = ComposerPanel.NONE
                            vm.send(e, false) {}
                        }
                        ComposerPanel.NONE -> Unit
                    }
                    if (!note && !(staffView && assignedToOther)) {
                        AttachActions(
                            enabled = chatId != null && !uploading,
                            uploading = uploading,
                            onStickers = { panel = if (panel == ComposerPanel.STICKERS) ComposerPanel.NONE else ComposerPanel.STICKERS },
                            onCamera = { openCamera() },
                            onPhoto = { picker.pick("image/*") { uri -> vm.sendAttachment(appContext, uri, image = true) } },
                            onFile = { picker.pick("*/*") { uri -> vm.sendAttachment(appContext, uri, image = false) } },
                        )
                        if (chatId == null) {
                            Text(tr(StrSupport.attachAfterFirst), color = C.Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            containerColor = C.Surface,
            title = { Text(tr(StrSupport.endChatTitle), color = C.Ink) },
            text = { Text(tr(StrSupport.endChatBody), color = C.Ink2) },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.close() }) { Text(tr(StrSupport.end), color = C.Urgent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(tr(com.rork.pro.ui.i18n.StrAdmin.cancel), color = C.Ink2) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = C.Surface,
            title = { Text(tr(StrSupport.deleteChatTitle), color = C.Ink) },
            text = { Text(tr(if (staffView) StrSupport.deleteChatBodyTeam else StrSupport.deleteChatBody), color = C.Ink2) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(tr(StrSupport.delete), color = C.Urgent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr(com.rork.pro.ui.i18n.StrAdmin.cancel), color = C.Ink2) } },
        )
    }

    if (showTransfer) {
        AlertDialog(
            onDismissRequest = { showTransfer = false },
            containerColor = C.Surface,
            title = { Text(tr(StrSupport.transferTitle), color = C.Ink) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (team.isEmpty()) {
                        CircularProgressIndicator(color = C.Saffron, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    team.forEach { m ->
                        val isMe = m.userId == myId
                        val current = m.userId == info?.assignedTo
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !current) { showTransfer = false; vm.transfer(m.userId) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PersonAvatar(m.avatarUrl, m.fullName, 36, online = m.online && m.available)
                            Column(Modifier.weight(1f)) {
                                Text((m.fullName ?: "—") + if (isMe) " " + tr(StrSupport.me) else "", color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    tr(if (!m.available) StrSupport.away else if (m.online) StrSupport.online else StrSupport.offline),
                                    color = C.Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (current) Icon(Icons.Default.DoneAll, null, tint = C.Success, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTransfer = false }) { Text(tr(com.rork.pro.ui.i18n.StrAdmin.cancel), color = C.Ink2) } },
        )
    }
}

@Composable
private fun DayPill(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp).height(3.dp).clip(CircleShape).background(C.Doodle.copy(alpha = 0.7f)))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = C.Title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.DayPillBg).padding(horizontal = 22.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(22.dp).height(3.dp).clip(CircleShape).background(C.Doodle.copy(alpha = 0.7f)))
    }
}

@Composable
private fun EventLine(m: SupportMessage, staffView: Boolean) {
    val name = m.metaText("name").orEmpty()
    val text = when (m.body) {
        "CLAIMED" -> trf(if (staffView) StrSupport.eventClaimedStaff else StrSupport.eventClaimed, name)
        "TRANSFERRED" -> trf(StrSupport.eventTransferred, name)
        "CLOSED" -> trf(StrSupport.eventClosed, name)
        else -> m.body
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (m.body == "CLOSED") C.Muted else C.Success))
        Spacer(Modifier.width(6.dp))
        Text(text + " · " + clockOf(m.createdAt), color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun NoteCard(m: SupportMessage) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.NoteBg)
            .border(1.dp, C.NoteBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Lock, null, tint = C.NoteText, modifier = Modifier.size(14.dp))
            Text(tr(StrSupport.noteLabel), color = C.NoteText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(clockOf(m.createdAt), color = C.NoteText, fontSize = 11.sp)
        }
        Text(m.body, color = C.NoteText, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun Bubble(m: SupportMessage, mine: Boolean, read: Boolean, mineAvatar: Boolean = false) {
    val fill = if (mine) C.MineBubble else C.Surface
    val onFill = if (mine) Color.White else C.Ink
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .padding(bottom = 10.dp)
                .drawBehind {
                    // The bubble with a small curled tail at its bottom corner, on the side that
                    // faces the sender (end side for mine, start side for the team).
                    val r = 26.dp.toPx()
                    drawRoundRect(fill, cornerRadius = CornerRadius(r, r))
                    val tailOnRight = (mine && !rtl) || (!mine && rtl)
                    val w = size.width
                    val h = size.height
                    val t = 14.dp.toPx()
                    val path = Path().apply {
                        if (tailOnRight) {
                            moveTo(w - r * 0.9f, h - 1f)
                            quadraticTo(w - 2f, h - 2f, w + t * 0.15f, h + t * 0.75f)
                            quadraticTo(w - t * 0.2f, h - r * 0.2f, w - 2f, h - r)
                            close()
                        } else {
                            moveTo(r * 0.9f, h - 1f)
                            quadraticTo(2f, h - 2f, -t * 0.15f, h + t * 0.75f)
                            quadraticTo(t * 0.2f, h - r * 0.2f, 2f, h - r)
                            close()
                        }
                    }
                    drawPath(path, fill)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                m.isImage -> AttachmentImage(m)
                m.isFile -> AttachmentFile(m, onFill)
                else -> Text(m.body, color = onFill, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(clockOf(m.createdAt), color = if (mine) Color(0xFFB9D6FF) else C.Muted, fontSize = 13.sp)
                if (mine) {
                    Icon(
                        Icons.Default.DoneAll,
                        contentDescription = if (read) tr(StrSupport.read) else null,
                        tint = if (read) C.Online else Color(0xFF8DB8F5),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        if (mine && mineAvatar) {
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_chat_avatar),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/** A photo sent in the chat: loads through a short-lived signed link; tap to open full size. */
@Composable
private fun AttachmentImage(m: SupportMessage) {
    val path = m.metaText("path") ?: return
    val context = LocalContext.current
    var url by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) { url = runCatching { SupportChatRepository.attachmentUrl(path) }.getOrNull() }
    Box(
        Modifier
            .size(width = 220.dp, height = 200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.Field)
            .clickable(enabled = url != null) { openUrl(context, url) },
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            CircularProgressIndicator(color = C.Blue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = m.metaText("name"),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A file sent in the chat: icon, name and size; tap to open it. */
@Composable
private fun AttachmentFile(m: SupportMessage, onFill: Color) {
    val path = m.metaText("path") ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val size = m.metaText("size")?.toLongOrNull() ?: 0L
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                scope.launch {
                    runCatching { SupportChatRepository.attachmentUrl(path) }.onSuccess { openUrl(context, it) }
                }
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(onFill.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AttachFile, null, tint = onFill, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.widthIn(max = 190.dp)) {
            Text(m.metaText("name") ?: m.body, color = onFill, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (size > 0) {
                Text(
                    if (size >= 1024 * 1024) String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0) else "${(size / 1024).coerceAtLeast(1)} KB",
                    color = onFill.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String?) {
    if (url == null) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun TypingRow(name: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { Box(Modifier.size(7.dp).clip(CircleShape).background(C.Blue.copy(alpha = 0.6f))) }
        }
        Text(trf(StrSupport.typing, name ?: "…"), color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StaffBanner(text: String, action: String, busy: Boolean, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.WarnSoft).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = C.Warn, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(C.Blue).clickable(enabled = !busy, onClick = onAction).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(action, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) (if (fg == C.NoteText) C.NoteBg else C.Surface) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun Composer(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    note: Boolean,
    enabled: Boolean,
    sending: Boolean,
    emojiOpen: Boolean = false,
    onEmoji: () -> Unit = {},
    onSend: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Send: the round blue paper-plane button on the start side.
        Box(
            Modifier
                .size(56.dp)
                .shadow(if (enabled) 10.dp else 0.dp, CircleShape, ambientColor = C.Blue, spotColor = C.Blue)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.verticalGradient(listOf(Color(0xFF3B95FF), Color(0xFF1A63E8)))
                    else Brush.verticalGradient(listOf(Color(0xFF6FAEFF), Color(0xFF4A8CF0))),
                )
                .clickable(enabled = enabled, onClickLabel = tr(StrSupport.send), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (sending) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr(StrSupport.send), tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            placeholder = { Text(placeholder, color = C.Muted, fontSize = 16.sp) },
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (note) C.NoteBg else C.FieldBg,
                unfocusedContainerColor = if (note) C.NoteBg else C.FieldBg,
                focusedBorderColor = if (note) C.NoteBorder else C.Blue,
                unfocusedBorderColor = if (note) C.NoteBorder else C.FieldLine,
                cursorColor = C.Blue,
                focusedTextColor = C.Ink,
                unfocusedTextColor = C.Ink,
            ),
        )
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (emojiOpen) C.Blue.copy(alpha = 0.18f) else C.FieldBg)
                .clickable(onClickLabel = tr(StrSupport.emoji), onClick = onEmoji),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EmojiEmotions, contentDescription = tr(StrSupport.emoji), tint = if (emojiOpen) C.Blue else C.Muted, modifier = Modifier.size(30.dp))
        }
    }
}

internal enum class ComposerPanel { NONE, EMOJI, STICKERS }

internal object SupportEmoji {
    val faces = listOf("😀", "😂", "😊", "😍", "🙏", "👍", "👏", "🙌", "🤔", "😅", "😢", "😡", "❤️", "🔥", "✅", "🎉", "👌", "💯", "🙂", "😉", "🤝", "💪", "👋", "⭐")
    val stickers = listOf("👍", "❤️", "🙏", "😂", "🎉", "👏", "😍", "🤝", "✅", "💯", "🔥", "🌟")
}

@Composable
private fun EmojiGrid(items: List<String>, big: Boolean, onPick: (String) -> Unit) {
    val perRow = if (big) 6 else 8
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.FieldBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { e ->
                    Box(
                        Modifier.weight(1f).height(if (big) 54.dp else 42.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = if (big) 32.sp else 24.sp) }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Stickers · Camera · Photo · Attach file — the four soft tiles under the field. */
@Composable
private fun AttachActions(
    enabled: Boolean,
    uploading: Boolean,
    onStickers: () -> Unit,
    onCamera: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
) {
    val dark = C.Title == Color.White
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        AttachTile(Icons.Outlined.EmojiEmotions, tr(StrSupport.stickers), Color(0xFFE0A100), if (dark) Color(0xFF3A3212) else Color(0xFFFFF4D6), true, onStickers)
        AttachTile(Icons.Outlined.PhotoCamera, tr(StrSupport.camera), Color(0xFF2F7BF6), if (dark) Color(0xFF15305A) else Color(0xFFE6F0FE), enabled, onCamera, labelColor = C.Blue)
        AttachTile(Icons.Outlined.Image, tr(StrSupport.photo), Color(0xFF12A06A), if (dark) Color(0xFF123A2C) else Color(0xFFE2F6EE), enabled, onPhoto)
        AttachTile(Icons.Default.AttachFile, tr(StrSupport.attachFile), Color(0xFF8B5CF6), if (dark) Color(0xFF2A2563) else Color(0xFFEFEAFE), enabled, onFile)
    }
    if (uploading) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = C.Blue, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr(StrSupport.uploading), color = C.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AttachTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    bg: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    labelColor: Color = C.Ink2,
) {
    Column(
        Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 70.dp, height = 54.dp).clip(RoundedCornerShape(27.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** A round frosted button on the blue header. */
@Composable
private fun GlassCircle(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(C.OnWaveChip)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = C.OnWave, modifier = Modifier.size(24.dp))
    }
}

/** Blue gradient with a soft wave along the bottom edge (the header of both support screens). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveHeader(waveDepth: Float) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h - waveDepth * 1.2f)
        cubicTo(w * 0.78f, h - waveDepth * 0.2f, w * 0.62f, h - waveDepth * 1.6f, w * 0.42f, h - waveDepth * 0.7f)
        cubicTo(w * 0.24f, h + waveDepth * 0.1f, w * 0.10f, h - waveDepth * 0.3f, 0f, h - waveDepth * 0.5f)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(C.WaveA, C.WaveB), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, h)))
}

/** The chat's sky: soft clouds, a few line doodles and the books and leaves in the corners. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatDecor() {
    val doodle = C.Doodle
    Box(
        Modifier.matchParentSize().drawBehind {
            val c = C.Cloud
            val w = size.width
            val h = size.height
            drawCircle(c, radius = w * 0.22f, center = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.30f))
            drawCircle(c, radius = w * 0.20f, center = androidx.compose.ui.geometry.Offset(w * 1.02f, h * 0.10f))
            drawCircle(c, radius = w * 0.26f, center = androidx.compose.ui.geometry.Offset(w * 1.05f, h * 0.86f))
            drawCircle(c, radius = w * 0.24f, center = androidx.compose.ui.geometry.Offset(-w * 0.02f, h * 0.98f))
            // dashed flight path of the paper plane
            val dash = Path().apply {
                moveTo(w * 0.99f, h * 0.30f)
                cubicTo(w * 0.86f, h * 0.38f, w * 0.74f, h * 0.36f, w * 0.80f, h * 0.29f)
                cubicTo(w * 0.86f, h * 0.24f, w * 0.88f, h * 0.18f, w * 0.89f, h * 0.15f)
            }
            drawPath(dash, doodle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        },
    )
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        val w = maxWidth
        val h = maxHeight
        Icon(Icons.AutoMirrored.Outlined.Send, null, tint = doodle, modifier = Modifier.absoluteOffset(x = w * 0.85f, y = h * 0.08f).size(44.dp))
        Icon(Icons.Outlined.MenuBook, null, tint = doodle, modifier = Modifier.absoluteOffset(x = w * 0.04f, y = h * 0.36f).size(46.dp))
        Icon(Icons.Outlined.StarOutline, null, tint = doodle, modifier = Modifier.absoluteOffset(x = w * 0.87f, y = h * 0.48f).size(30.dp))
    }
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_chat_books),
        contentDescription = null,
        alpha = C.ArtAlpha,
        modifier = Modifier.align(Alignment.BottomEnd).width(118.dp),
    )
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_chat_leaves),
        contentDescription = null,
        alpha = C.ArtAlpha,
        modifier = Modifier.align(Alignment.BottomStart).width(84.dp),
    )
}

@Composable
private fun RatingPanel(
    agentName: String?,
    rating: Int?,
    busy: Boolean,
    onRate: (Int, String) -> Unit,
    onNewChat: () -> Unit,
) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rating == null) {
            Text(
                if (agentName != null) trf(StrSupport.rateTitle, agentName) else tr(StrSupport.rateTitleTeam),
                color = C.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(tr(StrSupport.rateSub), color = C.Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    IconButton(onClick = { stars = n }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (n <= stars) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = trf(StrSupport.star, n),
                            tint = if (n <= stars) C.Star else C.StarOff,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            if (stars > 0) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr(StrSupport.rateComment), color = C.Muted, fontSize = 14.sp) },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = C.Field,
                        unfocusedContainerColor = C.Field,
                        focusedBorderColor = C.Blue,
                        unfocusedBorderColor = C.FieldLine,
                        cursorColor = C.Blue,
                        focusedTextColor = C.Ink,
                        unfocusedTextColor = C.Ink,
                    ),
                )
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3B95FF), Color(0xFF1A63E8))))
                        .clickable(enabled = !busy) { onRate(stars, comment) },
                    contentAlignment = Alignment.Center,
                ) { Text(tr(StrSupport.rateSend), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
            }
        } else {
            Stars(rating, 22)
            Text(tr(StrSupport.rateThanks), color = C.Ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            tr(StrSupport.newChat),
            color = C.Blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onNewChat).padding(8.dp),
        )
    }
}

// ============================================================ team inbox

class SupportInboxVm : ViewModel() {
    private val _staff = MutableStateFlow<SupportStaffState?>(null)
    val staff: StateFlow<SupportStaffState?> = _staff.asStateFlow()
    private val _rows = MutableStateFlow<Async<List<SupportInboxRow>>>(Async.Loading)
    val rows: StateFlow<Async<List<SupportInboxRow>>> = _rows.asStateFlow()
    private val _filter = MutableStateFlow("WAITING")
    val filter: StateFlow<String> = _filter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private val _incoming = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Fires when the number of chats waiting on the team grows while the inbox is open. */
    val incoming: SharedFlow<Unit> = _incoming.asSharedFlow()
    private var lastPending: Int? = null

    private var job: Job? = null
    private var channel: RealtimeChannel? = null
    private var pickedDefault = false

    init {
        reload(quiet = false)
        watch()
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                reload()
            }
        }
    }

    fun setFilter(f: String) {
        pickedDefault = true
        if (_filter.value == f) return
        _filter.value = f
        reload(quiet = false)
    }

    fun setQuery(q: String) {
        _query.value = q
        reload(debounce = true)
    }

    fun reload(quiet: Boolean = true, debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(300)
            if (!quiet) _rows.value = Async.Loading
            runCatching { SupportChatRepository.staffState() }.onSuccess { st ->
                _staff.value = st
                val pending = st.waiting + st.mineUnread
                val before = lastPending
                lastPending = pending
                if (before != null && pending > before) _incoming.tryEmit(Unit)
                // First open: land where the work is.
                if (!pickedDefault) {
                    pickedDefault = true
                    if (st.waiting == 0 && st.mine > 0) _filter.value = "MINE"
                }
            }
            runCatching { SupportChatRepository.inbox(_filter.value, _query.value) }
                .onSuccess { _rows.value = Async.Success(it) }
                .onFailure { if (_rows.value !is Async.Success || !quiet) _rows.value = Async.Failure(it.toAppError()) }
        }
    }

    private fun watch() {
        viewModelScope.launch {
            runCatching {
                val ch = SupportChatRepository.openInboxChannel()
                channel = ch
                val changes = SupportChatRepository.anyChatChange(ch)
                launch { runCatching { changes.collect { reload() } } }
                ch.subscribe(blockUntilSubscribed = true)
            }
        }
    }

    fun toggleAvailable() {
        val now = _staff.value?.available ?: true
        viewModelScope.launch {
            runCatching { SupportChatRepository.setAvailable(!now) }
                .onSuccess { _staff.value = _staff.value?.copy(available = !now) }
                .onFailure { _error.value = it.toAppError() }
        }
    }

    fun claim(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { SupportChatRepository.claim(id) }
                .onSuccess { onDone() }
                .onFailure { _error.value = it.toAppError(); reload() }
        }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        val ch = channel
        channel = null
        Backend.appScope.launch { ch?.let { runCatching { Backend.realtime.removeChannel(it) } } }
        super.onCleared()
    }
}

@Composable
fun SupportInboxScreen(navController: NavHostController, onOldTickets: () -> Unit) {
    val vm: SupportInboxVm = viewModel()
    val staff by vm.staff.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    ReloadOnResume { vm.reload() }
    val soundContext = LocalContext.current
    LaunchedEffect(vm) { vm.incoming.collect { SupportSounds.playIncoming(soundContext) } }

    fun open(id: String) = navController.navigate("admin/support/chat/$id")

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.SkyTop, C.SkyBottom)))) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawWaveHeader(waveDepth = 22.dp.toPx()) }
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, tr(StrSupport.inboxTitle)) { navController.popBackStack() }
                Text(tr(StrSupport.inboxTitle), color = C.OnWave, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                val on = staff?.available != false
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) Color(0x405FC49A) else C.OnWaveChip)
                        .clickable { vm.toggleAvailable() }
                        .padding(start = 6.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.width(34.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF2E7D5B) else C.Muted).padding(3.dp),
                        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
                    ) { Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White)) }
                    Text(tr(if (on) StrSupport.available else StrSupport.away), color = C.OnWave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Counter(staff?.waiting ?: 0, tr(StrSupport.countWaiting), highlight = true, Modifier.weight(1f)) { vm.setFilter("WAITING") }
                Counter(staff?.mine ?: 0, tr(StrSupport.countMine), highlight = false, Modifier.weight(1f)) { vm.setFilter("MINE") }
                Counter(staff?.team ?: 0, tr(StrSupport.countTeam), highlight = false, Modifier.weight(1f)) { vm.setFilter("ALL") }
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminSearchField(query, { vm.setQuery(it) }, tr(StrSupport.searchHint))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.DayPillBg).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "WAITING" to tr(StrSupport.tabWaiting),
                    "MINE" to tr(StrSupport.tabMine),
                    "ALL" to tr(StrSupport.tabAll),
                    "CLOSED" to tr(StrSupport.tabClosed),
                ).forEach { (key, label) ->
                    val selected = filter == key
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) C.Sheet else Color.Transparent)
                            .clickable { vm.setFilter(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (selected) C.Blue else C.Ink2, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }

        ErrorLine(error) { vm.clearError() }

        Box(Modifier.weight(1f)) {
            when (val s = rows) {
                is Async.Loading -> CircularProgressIndicator(color = C.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp).align(Alignment.Center))
                is Async.Failure -> Text(s.error.message, color = C.Urgent, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp).clickable { vm.reload(quiet = false) })
                is Async.Success -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (s.value.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(com.rork.pro.R.drawable.sup_empty),
                                    contentDescription = null,
                                    alpha = C.ArtAlpha.coerceAtLeast(0.8f),
                                    modifier = Modifier.width(200.dp).aspectRatio(300f / 160f),
                                )
                                Text(tr(if (filter == "WAITING") StrSupport.inboxEmptyWaiting else StrSupport.inboxEmpty), color = C.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                if (filter == "WAITING") Text(tr(StrSupport.inboxEmptyWaitingBody), color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    items(s.value, key = { it.id }) { row ->
                        if (row.status == "WAITING") {
                            WaitingCard(row, onOpen = { open(row.id) }) { vm.claim(row.id) { open(row.id) } }
                        } else {
                            InboxRow(row, Backend.currentUserId) { open(row.id) }
                        }
                    }
                    item {
                        Text(
                            tr(StrSupport.oldTickets),
                            color = C.Blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOldTickets).padding(12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Counter(value: Int, label: String, highlight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight && value > 0) C.Saffron else C.OnWaveChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value.toString(), color = C.OnWave, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = C.OnWave.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WaitingCard(row: SupportInboxRow, onOpen: () -> Unit, onClaim: () -> Unit) {
    val minutes = minutesSince(row.createdAt)
    val urgent = minutes >= 5
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1A1562E2), spotColor = Color(0x1A1562E2)).clip(RoundedCornerShape(22.dp)).background(C.Sheet).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(row.avatarUrl, row.displayName, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.displayName, color = C.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Tag(
                        trf(StrSupport.waitingFor, durationLabel(minutes)),
                        if (urgent) C.UrgentSoft else C.WarnSoft,
                        if (urgent) C.Urgent else C.Warn,
                        Icons.Default.Schedule,
                    )
                }
                Text(row.lastMessagePreview.orEmpty(), color = C.Ink2, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).border(BorderStroke(1.dp, C.Line), RoundedCornerShape(8.dp)).background(C.Field).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(topicLabel(row.topic), color = C.Ink2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3B95FF), Color(0xFF1A63E8)))).clickable(onClick = onClaim),
            contentAlignment = Alignment.Center,
        ) { Text(tr(StrSupport.claim), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun InboxRow(row: SupportInboxRow, myId: String?, onClick: () -> Unit) {
    val closed = row.status == "CLOSED"
    val mine = row.assignedTo != null && row.assignedTo == myId
    Row(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1A1562E2), spotColor = Color(0x1A1562E2)).clip(RoundedCornerShape(22.dp)).background(C.Sheet).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(row.avatarUrl, row.displayName, 42)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.displayName, color = C.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                when {
                    closed -> Tag(tr(StrSupport.ended), C.Field, C.Ink2)
                    mine -> Tag(tr(StrSupport.withYou), C.SuccessSoft, C.Success)
                    row.assignedName != null -> Tag(trf(StrSupport.withName, row.assignedName), C.Field, C.Ink2)
                }
            }
            if (closed && row.rating != null) {
                Stars(row.rating)
            } else {
                Text(
                    row.lastMessagePreview.orEmpty(),
                    color = if (row.staffUnread > 0) C.Ink else C.Muted,
                    fontWeight = if (row.staffUnread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(whenLabel(row.lastMessageAt), color = C.Muted, fontSize = 12.sp)
            if (!closed && mine) UnreadBadge(row.staffUnread)
        }
    }
}
