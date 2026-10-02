package com.rork.pro.ui.screens.tutor

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rork.pro.tutor.CreateExportDocument
import com.rork.pro.tutor.LineRole
import com.rork.pro.tutor.TranscriptDoc
import com.rork.pro.tutor.TranscriptExport
import com.rork.pro.tutor.TranscriptLine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rork.pro.character.TutorCharacter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.tutor.TutorActivity
import com.rork.pro.tutor.TutorAvatar
import com.rork.pro.tutor.TutorFailure
import com.rork.pro.tutor.TutorPresence
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.StrAiPlans
import com.rork.pro.ui.i18n.StrTutor
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop
import java.time.Duration
import java.time.OffsetDateTime

private val LEVELS: List<Pair<String, Tr>> = listOf(
    "beginner" to StrTutor.levelBeginner,
    "intermediate" to StrTutor.levelIntermediate,
    "advanced" to StrTutor.levelAdvanced,
)

// The call itself always sits on a dark "video" stage, whatever the app theme — like any call app.
private val StageTop = Color(0xFF0A2147)
private val StageBottom = Color(0xFF031128)
private val StageText = Color(0xFFFFFFFF)
private val StageMuted = Color(0xFF9BBEF2)
private val StagePanel = Color(0xFF102849)
private val StageGreen = Color(0xFF3FAF8E)
private val StageRed = Color(0xFFD64535)

@Composable
fun TutorScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: TutorViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sessionState.profile?.currentLevel) { vm.presetLevel(sessionState.profile?.currentLevel) }

    val firstName = sessionState.profile?.displayName?.trim()?.split(" ")?.firstOrNull()
    var showMicCheck by remember { mutableStateOf(false) }
    fun begin() { if (vm.micChecked) vm.startCall(firstName) else showMicCheck = true }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
    }
    fun start() {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (has) begin() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    if (showMicCheck) {
        MicCheckDialog(vm, onDone = { showMicCheck = false; vm.startCall(firstName) }, onDismiss = { showMicCheck = false })
    }

    when (state.phase) {
        TutorPhase.LOBBY -> TutorLobby(state, vm, isOwner = sessionState.isOwner, onBack = { navController.popBackStack() }, onStart = ::start, onManageCharacters = { navController.navigate(com.rork.pro.ui.navigation.Routes.AI_TUTOR_CHARACTERS) }, onOpenPlans = { navController.navigate(com.rork.pro.ui.navigation.Routes.AI_TUTOR_PLANS) }, onManagePlans = { navController.navigate(com.rork.pro.ui.screens.admin.AdminRoutes.AI_TUTOR_PLANS) })
        TutorPhase.CALL -> {
            BackHandler { vm.endCall() }
            val view = LocalView.current
            DisposableEffect(Unit) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            TutorCall(state, vm)
        }
        TutorPhase.SUMMARY -> TutorSummary(state, onNewCall = vm::backToLobby, onDone = { navController.popBackStack() })
    }
}

// ============================================================================== lobby

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorLobby(state: TutorUiState, vm: TutorViewModel, isOwner: Boolean, onBack: () -> Unit, onStart: () -> Unit, onManageCharacters: () -> Unit, onOpenPlans: () -> Unit, onManagePlans: () -> Unit) {
    Column(Modifier.fillMaxSize().appBackdrop().statusBarsPadding()) {
        DetailHeader(tr(StrTutor.screenTitle), onBack = onBack)
        if (state.loading && state.status == null) {
            LoadingBlock(Modifier.fillMaxSize())
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(Dimens.cardRadius))
                        .background(Brush.verticalGradient(listOf(StageTop, StageBottom)))
                        .border(1.dp, StageGreen.copy(alpha = 0.35f), RoundedCornerShape(Dimens.cardRadius)),
                ) {
                    val heroChar = state.character
                    if (heroChar != null) {
                        AsyncImage(model = heroChar.imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(24.dp))
                    } else {
                        TutorPresence(vm.feed, StageGreen, Modifier.fillMaxSize())
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
                    )
                    TutorOnlineChip(Modifier.align(Alignment.TopEnd).padding(14.dp))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text(tutorNameText(state), color = StageText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        val progress = state.modelProgress
                        if (progress != null) {
                            Text(trf(StrTutor.preparingTutor, (progress * 100).toInt()), color = StageMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            val status = state.status
            when {
                status == null -> item {
                    InkCard {
                        Text(tr(StrTutor.networkError), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        SecondaryAction(tr(StrTutor.tryAgain), Modifier.fillMaxWidth()) { vm.refresh() }
                    }
                }
                !status.enabled -> item { InfoCard(tr(StrTutor.notReadyTitle), tr(StrTutor.notReadyBody)) }
                status.remaining <= 0 -> item {
                    Column {
                        // With no free replies and no plan the tutor is paid-only: say that, instead of "you used them all".
                        if (status.perUserDaily <= 0) {
                            InfoCard(tr(StrAiPlans.paidOnlyTitle), tr(StrAiPlans.paidOnlyBody))
                        } else {
                            InfoCard(tr(StrTutor.limitTitle), trf(StrTutor.limitBody, untilReset(status.resetsAt)))
                        }
                        if (!isOwner) {
                            Spacer(Modifier.height(12.dp))
                            TutorBuyCta(tr(StrAiPlans.subscribeToKeepTalking), onClick = onOpenPlans)
                        }
                    }
                }
                !status.globalAvailable -> item { InfoCard(tr(StrTutor.busyTitle), tr(StrTutor.busyBody)) }
                else -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { LobbySectionTitle(tr(StrTutor.pickTutor)) }
                            if (isOwner) {
                                Text(tr(StrAiPlans.managePrices), color = Ink.Teal, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = onManagePlans).padding(end = 14.dp))
                                Text(tr(StrTutor.manageCharacters), color = Ink.Teal, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = onManageCharacters))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.showMale) TutorPickCard(tr(StrTutor.tutorMaleChoice), null, state.character == null && state.voice != "female") { vm.setVoice("male") }
                            if (state.showFemale) TutorPickCard(tr(StrTutor.tutorFemaleChoice), null, state.character == null && state.voice == "female") { vm.setVoice("female") }
                            state.characters.forEach { c ->
                                CharacterChoice(c, state.character?.id == c.id) { vm.selectCharacter(c) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LanguageNote(tr(StrTutor.bothLanguages))
                    }
                    item {
                        LobbySectionTitle(tr(StrTutor.pickLevel))
                        Spacer(Modifier.height(10.dp))
                        LevelSegmented(state.level) { vm.setLevel(it) }
                    }
                    item {
                        RepliesCard(
                            text = trf(StrTutor.repliesLeft, status.remaining),
                            fraction = status.remaining.toFloat() / status.perUserDaily.coerceAtLeast(status.remaining).coerceAtLeast(1),
                            creditsText = if (status.credits > 0) trf(StrAiPlans.creditsPill, status.credits, formatDate(status.creditsUntil)) else null,
                        )
                        Spacer(Modifier.height(16.dp))
                        TutorStartCta(onClick = onStart)
                        if (!isOwner) {
                            Spacer(Modifier.height(12.dp))
                            TutorBuyCta(if (status.credits <= 0) tr(StrAiPlans.getMore) else tr(StrAiPlans.seePlans), onClick = onOpenPlans)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tr(StrTutor.needsInternet), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * The lobby's main action. A solid green gradient card with a glowing mic bubble that breathes
 * slowly, so the eye lands here first: title, a one-line promise, and an arrow bubble.
 */
@Composable
private fun TutorStartCta(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val infinite = rememberInfiniteTransition(label = "startCta")
    val ring by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .shadow(14.dp, shape, ambientColor = Ink.CtaGreen.copy(alpha = 0.5f), spotColor = Ink.CtaGreen.copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Ink.CtaGreen, Ink.CtaGreenPressed)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(56.dp).scale(ring).clip(CircleShape).background(Ink.OnCtaGreen.copy(alpha = ringAlpha)))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Ink.OnCtaGreen.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Ink.OnCtaGreen, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(StrTutor.startCall), color = Ink.OnCtaGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(2.dp))
            Text(tr(StrTutor.startCallSub), color = Ink.OnCtaGreen.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Ink.OnCtaGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Ink.CtaGreenPressed, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * "Buy replies" as a secondary card that still pulls: an amber wash with a lightning bubble, so
 * it reads as a boost next to the green call button instead of a grey afterthought.
 */
@Composable
private fun TutorBuyCta(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Ink.Amber.copy(alpha = 0.26f), Ink.Amber.copy(alpha = 0.08f))))
            .border(1.2.dp, Ink.Amber.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Amber),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Ink.OnAmber, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(tr(StrTutor.buyRepliesSub), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink.Amber, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LobbySectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Ink.CtaGreen))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TutorOnlineChip(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "online")
    val dot by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, StageGreen.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(StageGreen.copy(alpha = dot)))
        Spacer(Modifier.width(7.dp))
        Text(tr(StrTutor.onlineNow), color = StageText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** A tutor to pick: round photo (or a person glyph for the two stock voices) beside the name; the chosen one gets a green edge and a tick. */
@Composable
private fun TutorPickCard(label: String, imageUrl: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (selected) Ink.CtaGreen.copy(alpha = 0.14f) else Ink.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Ink.CtaGreen else Ink.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Ink.SurfaceHigh).border(1.dp, if (selected) Ink.CtaGreen else Ink.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = Ink.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(20.dp).clip(CircleShape).background(Ink.CtaGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Ink.OnCtaGreen, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** The three levels as one segmented track; the chosen segment is a solid green pill. */
@Composable
private fun LevelSegmented(current: String, onPick: (String) -> Unit) {
    val track = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(track)
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LEVELS.forEach { (key, label) ->
            val on = current == key
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Ink.CtaGreen else Color.Transparent)
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr(label),
                    color = if (on) Ink.OnCtaGreen else Ink.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Today's replies as a card with a progress bar, so the balance is felt rather than read. */
@Composable
private fun RepliesCard(text: String, fraction: Float, creditsText: String?) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Ink.CtaGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Ink.CtaGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.SurfaceHigh)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Ink.CtaGreenPressed, Ink.CtaGreen))),
            )
        }
        if (creditsText != null) {
            Spacer(Modifier.height(12.dp))
            Pill(creditsText, background = Ink.AmberSoft, foreground = Ink.Amber)
        }
    }
}

@Composable
private fun LanguageNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Translate, contentDescription = null, tint = Ink.TextMuted, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CharacterChoice(c: TutorCharacter, selected: Boolean, onClick: () -> Unit) {
    TutorPickCard(c.name, c.imageUrl, selected, onClick = onClick)
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Ink.TextPrimary else Ink.Surface)
            .border(1.dp, if (selected) Ink.TextPrimary else Ink.Hairline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Ink.Canvas else Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    InkCard {
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun untilReset(iso: String?): String {
    val at = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "24h"
    val d = Duration.between(OffsetDateTime.now(), at).coerceAtLeast(Duration.ZERO)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// ============================================================================== call

@Composable
private fun TutorCall(state: TutorUiState, vm: TutorViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StageTop, StageBottom))),
    ) {
        TutorAvatar(
            feed = vm.feed,
            modelFile = state.modelFile,
            accent = StageGreen,
            character = state.character,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 180.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.interrupt() },
            onModelFailed = vm::modelFailed,
        )

        // Top bar: who, how long, how many replies are left.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tutorNameText(state), color = StageText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(clock(state.elapsedSec), color = StageMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                trf(StrTutor.repliesLeftShort, state.remaining),
                color = StageText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(StagePanel).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Correction: what they said → the right form, with the rule in Arabic.
            val correction = state.lastCorrection
            AnimatedVisibility(
                // Shown once the tutor has finished talking (never over his voice), and hidden while the student speaks.
                visible = correction != null && !state.hearingSpeech &&
                    state.activity != TutorActivity.SPEAKING && state.activity != TutorActivity.THINKING,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 3 },
            ) {
                if (correction != null) CorrectionCard(correction.wrong, correction.right, correction.explainAr)
            }

            // "Replay last reply" sits beside the status pill whenever the tutor is neither talking nor thinking.
            val canReplayNow = state.canReplay && !state.connecting && !state.hearingSpeech &&
                state.activity != TutorActivity.SPEAKING && state.activity != TutorActivity.THINKING
            StatusPill(state, trailing = if (canReplayNow) ({ ReplayChip(tr(StrTutor.replayLast), onClick = vm::replayLast) }) else null)

            if (state.captions) Captions(state, onPickAlt = { vm.sendTyped(it) }, onEdit = { vm.editLastTranscript() })

            state.notice?.let { NoticeBar(it, state.canRetry, vm::retry) }

            var typing by remember { mutableStateOf(false) }
            // Tapping "edit" under the student's line opens the text box with that sentence, ready to fix.
            LaunchedEffect(state.editTick) { if (state.editDraft != null) typing = true }
            if (typing) TypeBar(enabled = state.activity != TutorActivity.THINKING, initial = state.editDraft.orEmpty(), resetKey = state.editTick) { vm.sendTyped(it) }

            Controls(state, vm, typing) { typing = !typing; if (!typing) vm.cancelEdit() }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CorrectionCard(wrong: String, right: String, explainAr: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFFBF5)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF0F7A5A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            LtrText {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(wrong, color = Color(0xFFA23955), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodyLarge)
                    Text("  →  ", color = Color(0xFF756B5F), style = MaterialTheme.typography.bodyLarge)
                    Text(right, color = Color(0xFF0F7A5A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (explainAr.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(explainAr, color = Color(0xFF4A4037), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusPill(state: TutorUiState, trailing: (@Composable () -> Unit)? = null) {
    val (label, color) = when {
        state.connecting -> tr(StrTutor.connecting) to StageMuted
        !state.micOn && state.activity == TutorActivity.IDLE -> tr(if (state.silenceHint) StrTutor.stillThere else StrTutor.paused) to StageMuted
        state.activity == TutorActivity.LISTENING -> tr(if (state.hearingSpeech) StrTutor.hearing else StrTutor.listening) to StageGreen
        state.activity == TutorActivity.THINKING -> tr(StrTutor.thinking) to Color(0xFFE0B35A)
        state.activity == TutorActivity.SPEAKING -> tr(StrTutor.speaking) to StageText
        else -> "" to StageMuted
    }
    if (label.isBlank() && trailing == null) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotBlank()) {
            Row(
                Modifier.weight(1f, fill = false).clip(RoundedCornerShape(16.dp)).background(StagePanel).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(label, color = StageText, style = MaterialTheme.typography.labelLarge)
            }
        }
        trailing?.invoke()
    }
}

/** "Replay last reply": same height as the status pill, so the row does not change size when it appears. */
@Composable
private fun ReplayChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(StagePanel).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Replay, contentDescription = null, tint = StageText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = StageText, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun Captions(state: TutorUiState, onPickAlt: ((String) -> Unit)? = null, onEdit: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.studentLine.isNotBlank() && state.activity != TutorActivity.LISTENING) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StagePanel.copy(alpha = 0.7f)).padding(12.dp)) {
                Text(tr(StrTutor.you), color = StageMuted, style = MaterialTheme.typography.labelSmall)
                AutoDirText(state.studentLine) { Text(state.studentLine, color = StageMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
                if (onEdit != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(tr(StrTutor.editText), color = StageGreen, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onEdit() })
                }
                if (state.studentConf != "high") {
                    Spacer(Modifier.height(6.dp))
                    Text(tr(StrTutor.heardUnsure), color = Color(0xFFE0B35A), style = MaterialTheme.typography.labelSmall)
                    if (state.studentAlt.isNotBlank() && onPickAlt != null) {
                        Spacer(Modifier.height(4.dp))
                        AutoDirText(state.studentAlt) {
                            Text(
                                tr(StrTutor.didYouMean) + " " + state.studentAlt,
                                color = StageText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().clickable { onPickAlt(state.studentAlt) },
                            )
                        }
                    }
                }
            }
        }
        if (state.tutorLine.isNotBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StagePanel).padding(14.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    AutoDirText(state.tutorLine) { Text(state.tutorLine, color = StageText, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), modifier = Modifier.fillMaxWidth()) }
                    if (state.tutorHint.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.tutorHint, color = StageMuted, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBar(notice: TutorFailure, canRetry: Boolean, onRetry: () -> Unit) {
    val text = when (notice) {
        TutorFailure.NO_SPEECH -> StrTutor.didntCatch
        TutorFailure.AUDIO_TOO_LONG -> StrTutor.tooLong
        TutorFailure.AUDIO_FORMAT -> StrTutor.aiError
        TutorFailure.NETWORK -> StrTutor.networkError
        TutorFailure.AI_FAILED -> StrTutor.aiError
        TutorFailure.UNAUTHORIZED -> StrTutor.sessionExpired
        TutorFailure.GLOBAL_LIMIT -> StrTutor.busyBody
        TutorFailure.DISABLED -> StrTutor.notReadyBody
        TutorFailure.USER_LIMIT -> StrTutor.limitTitle
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF45232A)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.WifiOff, null, tint = Color(0xFFF2A99A), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(tr(text), color = StageText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (canRetry) {
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(StageText).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Refresh, null, tint = StageBottom, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr(StrTutor.tryAgain), color = StageBottom, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Controls(state: TutorUiState, vm: TutorViewModel, typing: Boolean, onToggleTyping: () -> Unit) {
    val micActive = state.micOn
    val hearingScale by animateFloatAsState(if (state.hearingSpeech) 1.08f else 1f, tween(160), label = "mic")
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        RoundButton(
            icon = if (micActive) Icons.Default.Mic else Icons.Default.MicOff,
            label = tr(if (micActive) StrTutor.mute else StrTutor.unmute),
            background = if (micActive) StagePanel else StageText,
            tint = if (micActive) StageText else StageBottom,
            size = 60,
            modifier = Modifier.scale(hearingScale),
            onClick = vm::toggleMic,
        )
        RoundButton(
            icon = Icons.Default.Keyboard,
            label = tr(StrTutor.typeInstead),
            background = if (typing) StageText else StagePanel,
            tint = if (typing) StageBottom else StageText,
            size = 52,
            onClick = onToggleTyping,
        )
        RoundButton(Icons.Default.CallEnd, tr(StrTutor.endCall), StageRed, Color.White, 72, onClick = vm::endCall)
        RoundButton(
            icon = if (state.captions) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
            label = tr(if (state.captions) StrTutor.captionsOff else StrTutor.captionsOn),
            background = StagePanel,
            tint = StageText,
            size = 60,
            onClick = vm::toggleCaptions,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, background: Color, tint: Color, size: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size((size * 0.42f).dp))
    }
}

/** Typed turn for noisy places: English only, sent with the keyboard's Send key or the arrow. */
@Composable
private fun TypeBar(enabled: Boolean, initial: String = "", resetKey: Int = 0, onSend: (String) -> Unit) {
    var text by remember(resetKey) { mutableStateOf(initial) }
    fun submit() { if (enabled && text.isNotBlank()) { onSend(text); text = "" } }
    LtrText {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(StagePanel).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (text.isEmpty()) Text(tr(StrTutor.typeHint), color = StageMuted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(400) },
                    textStyle = TextStyle(color = StageText, fontSize = 16.sp),
                    cursorBrush = SolidColor(StageGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (enabled && text.isNotBlank()) StageGreen else StageBottom).clickable(enabled = enabled) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr(StrTutor.send), tint = if (enabled && text.isNotBlank()) Color(0xFF04211A) else StageMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** First call only: proves the microphone reaches the phone before the real conversation starts. */
@Composable
private fun MicCheckDialog(vm: TutorViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(0) } // 0 listening, 1 ok, 2 failed
    var level by remember { mutableFloatStateOf(0f) }
    fun run() {
        phase = 0
        scope.launch { phase = if (vm.micCheck()) 1 else 2 }
    }
    LaunchedEffect(Unit) { run() }
    LaunchedEffect(phase) {
        while (phase == 0) withFrameMillis { level += (vm.micLevel() - level) * 0.3f }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.Surface).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(StrTutor.micCheckTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                when (phase) { 1 -> tr(StrTutor.micCheckOk); 2 -> tr(StrTutor.micCheckFail); else -> tr(StrTutor.micCheckBody) },
                color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.SurfaceHigh)) {
                Box(
                    Modifier.fillMaxWidth(if (phase == 1) 1f else level.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (phase == 2) Ink.Coral else Ink.Teal),
                )
            }
            if (phase == 0) {
                Spacer(Modifier.height(8.dp))
                Text(tr(StrTutor.micCheckListening), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            when (phase) {
                1 -> PrimaryAction(tr(StrTutor.startNow), containerColor = Ink.Teal, contentColor = Ink.OnTeal, onClick = onDone)
                2 -> {
                    PrimaryAction(tr(StrTutor.tryAgain), containerColor = Ink.Teal, contentColor = Ink.OnTeal) { run() }
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(tr(StrTutor.skip), Modifier.fillMaxWidth(), onClick = onDone)
                }
                else -> SecondaryAction(tr(StrTutor.skip), Modifier.fillMaxWidth(), onClick = onDone)
            }
        }
    }
}

private fun tutorNameText(state: TutorUiState): String = state.character?.name ?: tr(tutorName(state))
private fun tutorName(state: TutorUiState) = if (state.voice == "female") StrTutor.tutorNameFemale else StrTutor.tutorName

/** Captions follow their own script: Arabic lines right-to-left, English lines left-to-right. */
@Composable
private fun AutoDirText(text: String, content: @Composable () -> Unit) {
    val arabic = text.count { it in '\u0600'..'\u06FF' } > text.count { it in 'A'..'Z' || it in 'a'..'z' }
    CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
}

/** English content stays left-to-right even inside the Arabic UI. */
@Composable
private fun LtrText(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

private fun clock(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)

// ============================================================================== summary

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorSummary(state: TutorUiState, onNewCall: () -> Unit, onDone: () -> Unit) {
    val exchanges = state.exchanges
    val correct = exchanges.count { it.corrections.isEmpty() }
    val fixes = exchanges.flatMap { it.corrections }
    val words = exchanges.flatMap { it.newWords }.distinctBy { it.lowercase() }.take(8)
    val pct = if (exchanges.isEmpty()) 0 else correct * 100 / exchanges.size

    // ---- the conversation: show it, copy it, or save it as TXT / PDF (a "Save as" picker: no storage permission needed)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { delay(2500); message = null } }
    val tutorLabel = tutorNameText(state)
    val youLabel = tr(StrTutor.you)
    val titleText = tr(StrTutor.exportTitle)
    val fixedTitle = tr(StrTutor.fixedMistakes)
    val wordsTitle = tr(StrTutor.newWords)
    val savedText = tr(StrTutor.chatSaved)
    val failedText = tr(StrTutor.chatSaveFailed)
    val copiedText = tr(StrTutor.chatCopied)
    fun buildDoc() = TranscriptDoc(
        title = titleText,
        subtitle = "$tutorLabel  ·  ${clock(state.elapsedSec)}  ·  ${java.time.LocalDate.now()}",
        tutorLabel = tutorLabel,
        youLabel = youLabel,
        lines = state.transcript,
        correctionsTitle = fixedTitle,
        corrections = fixes,
        newWordsTitle = wordsTitle,
        newWords = exchanges.flatMap { it.newWords }.distinctBy { it.lowercase() },
        footer = "7PRO",
    )
    var pendingPdf by remember { mutableStateOf(false) }
    val saver = rememberLauncherForActivityResult(CreateExportDocument()) { uri ->
        val asPdf = pendingPdf
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val doc = buildDoc()
                    val bytes = if (asPdf) TranscriptExport.pdfBytes(doc) else TranscriptExport.plainText(doc).toByteArray(Charsets.UTF_8)
                    TranscriptExport.write(context, uri, bytes)
                }.isSuccess
            }
            message = if (ok) savedText else failedText
        }
    }
    fun save(pdf: Boolean) {
        pendingPdf = pdf
        val name = "7PRO-conversation-${java.time.LocalDate.now()}.${if (pdf) "pdf" else "txt"}"
        saver.launch((if (pdf) "application/pdf" else "text/plain") to name)
    }
    fun copyAll() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("7PRO", TranscriptExport.plainText(buildDoc())))
        message = copiedText
    }

    LazyColumn(
        Modifier.fillMaxSize().appBackdrop().statusBarsPadding(),
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(trf(StrTutor.callEnded, clock(state.elapsedSec)), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(tr(StrTutor.summaryTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(exchanges.size.toString(), tr(StrTutor.statReplies), Ink.TextPrimary, Modifier.weight(1f))
                Stat("$pct%", tr(StrTutor.statCorrect), Ink.Teal, Modifier.weight(1f))
                Stat(fixes.size.toString(), tr(StrTutor.statFixed), Ink.Coral, Modifier.weight(1f))
            }
        }
        if (state.transcript.isNotEmpty()) {
            item { Text(tr(StrTutor.conversationTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction(tr(StrTutor.copyChat), Modifier.weight(1f)) { copyAll() }
                        SecondaryAction(tr(StrTutor.saveTxt), Modifier.weight(1f)) { save(false) }
                        SecondaryAction(tr(StrTutor.savePdf), Modifier.weight(1f)) { save(true) }
                    }
                    message?.let { Text(it, color = Ink.Teal, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            items(state.transcript) { line -> ConversationBubble(line, tutorLabel, youLabel) }
        }
        item { Text(tr(StrTutor.fixedMistakes), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (fixes.isEmpty()) {
            item { InkCard { Text(tr(StrTutor.noMistakes), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium) } }
        } else {
            items(fixes) { c ->
                InkCard(contentPadding = PaddingValues(14.dp)) {
                    LtrText {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.wrong, color = Ink.Coral, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodyLarge)
                            Text("  →  ", color = Ink.TextMuted)
                            Text(c.right, color = Ink.Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (c.explainAr.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(c.explainAr, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (words.isNotEmpty()) {
            item {
                Text(tr(StrTutor.newWords), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    words.forEach { Pill(it, background = Ink.TealSoft, foreground = Ink.Teal) }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            if (state.remaining > 0) {
                PrimaryAction(tr(StrTutor.newCall), containerColor = Ink.Teal, contentColor = Ink.OnTeal, onClick = onNewCall)
                Spacer(Modifier.height(10.dp))
            }
            SecondaryAction(tr(StrTutor.done), Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}

/** One line of the finished call: who said it and when, the words, the Arabic translation under a tutor line, and the corrected sentence under a student line. */
@Composable
private fun ConversationBubble(line: TranscriptLine, tutorLabel: String, youLabel: String) {
    val student = line.role == LineRole.STUDENT
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (student) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (student) Ink.TealSoft else Ink.Surface)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Text("${if (student) youLabel else tutorLabel}  ·  ${clock(line.atSec)}", color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            AutoDirText(line.text) { Text(line.text, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()) }
            if (line.arabic.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(line.arabic, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
            }
            if (line.corrected.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                LtrText { Text("✓ ${line.corrected}", color = Ink.Teal, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Ink.Surface).border(1.dp, Ink.Hairline, RoundedCornerShape(18.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
