package com.rork.pro.ui.screens.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.i18n.StrTutor
import com.rork.pro.ui.theme.surfaceSheenBrush
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AiTutorPlan
import com.rork.pro.data.AiTutorPlansRepository
import com.rork.pro.data.AppError
import com.rork.pro.data.CommerceRepository
import com.rork.pro.data.PriceQuote
import com.rork.pro.data.toAppError
import com.rork.pro.tutor.TutorApi
import com.rork.pro.tutor.TutorStatus
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAiPlans
import com.rork.pro.ui.i18n.isRtl
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.courses.CheckoutLauncher
import com.rork.pro.ui.screens.courses.CheckoutUiState
import com.rork.pro.ui.screens.payment.PayRequest
import com.rork.pro.ui.screens.payment.PaymentSheet
import com.rork.pro.ui.screens.payment.PaymentSummary
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiPlansState(
    val loading: Boolean = true,
    val plans: List<AiTutorPlan> = emptyList(),
    val status: TutorStatus? = null,
    val error: AppError? = null,
    val justActivated: Boolean = false,
)

/**
 * The student's AI Tutor plans. Prices, sales and coupons are all decided by the owner and judged by
 * the server; this only shows them and hands the purchase to the same checkout every course uses
 * (Paymob card / wallet, or a manual transfer the owner approves).
 */
class AiTutorPlansViewModel : ViewModel() {
    private val _state = MutableStateFlow(AiPlansState())
    val state: StateFlow<AiPlansState> = _state.asStateFlow()

    private val _checkout = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val checkout: StateFlow<CheckoutUiState> = _checkout.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.plans.isEmpty() && it.status == null) }
            runCatching {
                val plans = AiTutorPlansRepository.activePlans()
                val status = runCatching { TutorApi.status() }.getOrNull()
                plans to status
            }
                .onSuccess { (plans, status) -> _state.update { it.copy(loading = false, plans = plans, status = status, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toAppError()) } }
        }
    }

    fun buy(planId: String, request: PayRequest) {
        if (_checkout.value is CheckoutUiState.Working) return
        viewModelScope.launch {
            _checkout.value = CheckoutUiState.Working
            runCatching {
                // A transfer and a gateway charge are different server calls (see the course screen).
                if (request.isManual) {
                    CommerceRepository.submitManualPayment(
                        "AI_TUTOR",
                        couponCode = request.couponCode,
                        brand = request.manualBrand.orEmpty(),
                        senderPhone = request.senderPhone.orEmpty(),
                        proofPath = request.proofPath.orEmpty(),
                        note = request.note,
                        planId = planId,
                    )
                } else {
                    CommerceRepository.startCheckout(
                        "AI_TUTOR",
                        couponCode = request.couponCode,
                        methodId = request.methodId,
                        walletPhone = request.walletPhone,
                        planId = planId,
                    )
                }
            }
                .onSuccess { session ->
                    _checkout.value = when {
                        session.free -> CheckoutUiState.Granted
                        session.manualPending -> CheckoutUiState.ManualPending(session.orderId.orEmpty())
                        session.walletPending -> CheckoutUiState.WalletPending(
                            orderId = session.orderId.orEmpty(),
                            phone = session.walletPhone ?: request.walletPhone.orEmpty(),
                            url = session.checkoutUrl,
                        )
                        session.checkoutUrl != null -> CheckoutUiState.Redirect(session.checkoutUrl, session.orderId.orEmpty())
                        else -> CheckoutUiState.Error(AppError("CHECKOUT_FAILED", StrAiPlans.checkoutFailed, true))
                    }
                }
                .onFailure { _checkout.value = CheckoutUiState.Error(it.toAppError()) }
        }
    }

    fun resetCheckout() { _checkout.value = CheckoutUiState.Idle }

    /** The purchase went through: reload so the new plan and its replies show up straight away. */
    fun activated() {
        _state.update { it.copy(justActivated = true) }
        load()
    }
}

private fun planTitle(plan: AiTutorPlan): String = if (isRtl && plan.nameAr.isNotBlank()) plan.nameAr else plan.name
private fun planBody(plan: AiTutorPlan): String = if (isRtl && plan.descriptionAr.isNotBlank()) plan.descriptionAr else plan.description

/**
 * What the payment sheet shows for a quote: the plan's normal price, everything knocked off it (the
 * owner's discount price and any coupon together), and what is actually charged.
 */
private fun summaryOf(plan: AiTutorPlan, q: PriceQuote): PaymentSummary {
    val base = if (q.basePrice > 0.0) q.basePrice else q.listPrice
    return PaymentSummary(
        title = planTitle(plan),
        listPrice = base,
        discount = (base - q.totalAmount).coerceAtLeast(0.0),
        total = q.totalAmount,
        currency = q.currency.ifBlank { plan.currency },
    )
}

@Composable
fun AiTutorPlansScreen(navController: NavHostController) {
    val vm: AiTutorPlansViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val checkout by vm.checkout.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var paying by remember { mutableStateOf<Pair<AiTutorPlan, PaymentSummary>?>(null) }
    var quoting by remember { mutableStateOf<String?>(null) }
    var quoteFailed by remember { mutableStateOf(false) }

    paying?.let { (plan, summary) ->
        PaymentSheet(
            summary = summary,
            working = checkout is CheckoutUiState.Working,
            onDismiss = { paying = null },
            onPay = { request ->
                paying = null
                vm.buy(plan.id, request)
            },
            quoteLoader = label@{ code ->
                runCatching {
                    val q = CommerceRepository.quote("AI_TUTOR", couponCode = code, planId = plan.id)
                    // The server alone judges a coupon: an unknown, expired or used code is simply "not valid".
                    if (q.coupon?.valid != true) return@label null
                    val updated = summaryOf(plan, q)
                    if (q.totalAmount <= 0.0) {
                        // Nothing left to pay: activate right here, no payment page for a zero amount.
                        vm.buy(plan.id, PayRequest(couponCode = code?.takeIf { it.isNotBlank() }))
                        paying = null
                    } else {
                        paying = plan to updated
                    }
                    updated
                }.getOrNull()
            },
        )
    }

    CheckoutLauncher(
        state = checkout,
        onHandled = { vm.resetCheckout() },
        onGranted = { vm.activated() },
    )

    // Display aids only: the plan that costs least per reply gets the "best value" badge (when there is
    // a choice), and the lead plan carries the featured treatment. Amounts themselves stay server-side.
    val bestId = remember(state.plans) {
        if (state.plans.size > 1) {
            state.plans.minByOrNull { if (it.replies > 0) it.effectivePrice() / it.replies else Double.MAX_VALUE }?.id
        } else null
    }
    val featuredId = bestId ?: state.plans.firstOrNull()?.id

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrAiPlans.title), onBack = { navController.popBackStack() })

        when {
            state.loading -> LoadingBlock(Modifier.fillMaxSize())
            state.error != null && state.plans.isEmpty() -> ErrorBlock(state.error!!, Modifier.fillMaxSize()) { vm.load() }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.justActivated) {
                    item { Notice(tr(StrAiPlans.activated), good = true) }
                }
                if (checkout is CheckoutUiState.Error) {
                    item { Notice((checkout as CheckoutUiState.Error).error.message, good = false) }
                }
                if (quoteFailed) {
                    item { Notice(tr(StrAiPlans.quoteFailed), good = false) }
                }

                val status = state.status
                val hasActivePlan = (status?.credits ?: 0) > 0
                if (status != null && hasActivePlan) {
                    item { ActivePlanCard(status) }
                } else {
                    // The hook: show what a reply actually feels like before showing a price.
                    item { TutorHero() }
                    if (status != null) item { FreeStrip(status) }
                }

                if (state.plans.isEmpty()) {
                    item { Notice(tr(StrAiPlans.noPlans), good = false) }
                } else {
                    item {
                        Text(
                            tr(StrAiPlans.choosePack),
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                items(state.plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        featured = plan.id == featuredId,
                        bestValue = plan.id == bestId,
                        hasActivePlan = hasActivePlan,
                        busy = quoting == plan.id || checkout is CheckoutUiState.Working,
                        onSubscribe = {
                            quoteFailed = false
                            quoting = plan.id
                            scope.launch {
                                // The server prices it (sale + any active discount): the app never computes an amount.
                                val quote = runCatching { CommerceRepository.quote("AI_TUTOR", planId = plan.id) }.getOrNull()
                                quoting = null
                                when {
                                    quote == null -> quoteFailed = true
                                    quote.totalAmount <= 0.0 -> vm.buy(plan.id, PayRequest())
                                    else -> paying = plan to summaryOf(plan, quote)
                                }
                            }
                        },
                    )
                }
                item { TrustFooter() }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Visual layer. Everything below is presentation only: no prices, quotes or entitlements are
// computed here (the server owns those). The only derived numbers are display aids: which plan is
// cheapest per reply, and the per-reply figure shown under a price.
// ---------------------------------------------------------------------------------------------

private val TealAmberBrush: Brush get() = Brush.linearGradient(listOf(Ink.Teal, Ink.Amber))

/** Ambient motion is skipped when the user has switched animations off in system settings. */
@Composable
private fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

/** A soft highlight that sweeps across once every few seconds: the one loop that says "tap me". */
private fun Modifier.shimmerSweep(enabled: Boolean): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "cta-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3400
                0f at 0 using LinearEasing
                1f at 1100 using LinearEasing
                1f at 3400
            },
        ),
        label = "cta-shimmer-progress",
    )
    drawWithContent {
        drawContent()
        val w = size.width
        val x = -w * 0.4f + progress * w * 1.8f
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + w * 0.32f, size.height),
            ),
        )
    }
}

/** Live-audio bars, tallest in the middle. Static when animations are off. */
@Composable
private fun VoiceWave(modifier: Modifier = Modifier, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "wave-phase",
    )
    val p = if (animate) phase else 1.2f
    val from = Ink.Teal
    val to = Ink.Amber
    Canvas(modifier) {
        val bars = 13
        val slot = size.width / bars
        val barWidth = slot * 0.46f
        for (i in 0 until bars) {
            val envelope = sin(PI.toFloat() * (i + 0.5f) / bars)
            val h = size.height * (0.18f + 0.82f * abs(sin(p + i * 0.55f))) * (0.35f + 0.65f * envelope)
            drawRoundRect(
                color = lerp(from, to, i / (bars - 1f)),
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun TutorHero() {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .background(Brush.linearGradient(listOf(Ink.TealSoft, Color.Transparent, Ink.AmberSoft)))
            .border(1.dp, Ink.Hairline, shape)
            .padding(20.dp),
    ) {
        Text(tr(StrAiPlans.heroTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(tr(StrAiPlans.heroSub), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        DemoCall()
    }
}

/** A frozen moment of a real session: a slip, the correction, and the tutor's voice. */
@Composable
private fun DemoCall() {
    val motion = rememberMotionEnabled()
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Canvas.copy(alpha = 0.55f))
            .border(1.dp, Ink.Hairline, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(TealAmberBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, null, tint = Ink.OnAmber, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(StrTutor.tutorName), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Ink.CtaGreen))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(StrTutor.screenTitle), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            VoiceWave(Modifier.width(84.dp).height(34.dp), animate = motion)
        }
        Spacer(Modifier.height(14.dp))

        // What the student said (the slip is struck through)...
        Text(
            buildAnnotatedString {
                append("I ")
                withStyle(SpanStyle(color = Ink.Coral, textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold)) { append("goed") }
                append(" to school yesterday.")
            },
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.Start)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.CoralSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
        // ...and what the tutor answers.
        Row(
            Modifier
                .align(Alignment.End)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.TealSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("I ")
                    withStyle(SpanStyle(color = Ink.Teal, fontWeight = FontWeight.Bold)) { append("went") }
                    append(" to school yesterday.")
                },
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.CheckCircle, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(tr(StrAiPlans.demoTip), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** The free daily allowance as dots that empty through the day: a reason to try before paying. */
@Composable
private fun FreeStrip(status: TutorStatus) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.perUserDaily <= 0) {
            Icon(Icons.Rounded.Lock, null, tint = Ink.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(tr(StrAiPlans.paidOnlyInfo), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            return@Row
        }
        val total = status.perUserDaily.coerceAtMost(10)
        val filled = status.freeRemaining.coerceIn(0, total)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(total) { i ->
                    Box(Modifier.size(11.dp).clip(CircleShape).background(if (i < filled) Ink.Teal else Ink.Hairline))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                trf(StrAiPlans.freeInfo, status.perUserDaily),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (status.freeRemaining < status.perUserDaily) trf(StrAiPlans.freeLeftToday, status.freeRemaining) else tr(StrAiPlans.tryFirst),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Notice(text: String, good: Boolean) {
    InkCard(color = if (good) Ink.TealSoft else Ink.CoralSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (good) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                null,
                tint = if (good) Ink.Teal else Ink.Coral,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text, color = if (good) Ink.Teal else Ink.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivePlanCard(status: TutorStatus) {
    val shape = RoundedCornerShape(Dimens.cardRadius + 4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .background(Brush.linearGradient(listOf(Ink.TealSoft, Color.Transparent)))
            .border(1.dp, Ink.Teal.copy(alpha = 0.45f), shape)
            .padding(18.dp),
    ) {
        Text(tr(StrAiPlans.activeTitle), color = Ink.Teal, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${status.credits}",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 50.sp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr(StrAiPlans.repliesLeftLabel),
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(trf(StrAiPlans.validUntil, formatDate(status.creditsUntil)), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (status.freeRemaining > 0) {
            Spacer(Modifier.height(4.dp))
            Text(trf(StrAiPlans.freeLeftToday, status.freeRemaining), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        // Replies expire whether or not they were used: warn while there is still time to renew.
        val daysLeft = runCatching {
            java.time.Duration.between(java.time.OffsetDateTime.now(), java.time.OffsetDateTime.parse(status.creditsUntil)).toDays().toInt()
        }.getOrNull()
        if (daysLeft != null && daysLeft <= 5) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .background(Ink.CoralSoft)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Timer, null, tint = Ink.Coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (daysLeft <= 0) tr(StrAiPlans.expiresToday) else trf(StrAiPlans.expiresSoon, daysLeft),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(tr(StrAiPlans.expiryNote), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PerkRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Ink.TealSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Ink.Teal, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlanCard(
    plan: AiTutorPlan,
    featured: Boolean,
    bestValue: Boolean,
    hasActivePlan: Boolean,
    busy: Boolean,
    onSubscribe: () -> Unit,
) {
    val onSale = plan.saleActive()
    val price = plan.effectivePrice()
    val shape = RoundedCornerShape(Dimens.cardRadius + 4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .background(if (featured) Brush.verticalGradient(listOf(Ink.TealSoft, Color.Transparent)) else surfaceSheenBrush())
            .then(
                if (featured) Modifier.border(1.5.dp, TealAmberBrush, shape)
                else Modifier.border(1.dp, Ink.Hairline, shape),
            )
            .padding(18.dp),
    ) {
        // Title + the two reasons to act now.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                planTitle(plan),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (bestValue) {
                Pill(tr(StrAiPlans.bestValue), background = Ink.AmberSoft, foreground = Ink.Amber)
                Spacer(Modifier.width(6.dp))
            }
            if (onSale) Pill(trf(StrAiPlans.saveBadge, plan.salePercent()), background = Ink.CoralSoft, foreground = Ink.Coral)
        }
        val body = planBody(plan)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))

        // The offer: how much you get on one side, what it costs on the other.
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${plan.replies}",
                        color = Ink.Teal,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 50.sp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tr(StrAiPlans.repliesWord),
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(trf(StrAiPlans.validFor, plan.periodDays), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (onSale) {
                    Text(
                        formatMoney(plan.price, plan.currency),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
                Text(
                    if (price <= 0.0) tr(StrAiPlans.free) else formatMoney(price, plan.currency),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        if (plan.replies > 0 && price > 0.0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .background(Ink.AmberSoft)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bolt, null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    trf(StrAiPlans.perReply, formatMoney(price / plan.replies, plan.currency)),
                    color = Ink.Amber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PerkRow(tr(StrAiPlans.perkLive))
            PerkRow(tr(StrAiPlans.perkAnytime))
            PerkRow(tr(StrAiPlans.validNote))
        }

        if (onSale && plan.saleEndsAt != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = Ink.Coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    trf(StrAiPlans.offerEnds, formatDate(plan.saleEndsAt)),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PlanCta(
            label = if (hasActivePlan) tr(StrAiPlans.subscribeAgain) else tr(StrAiPlans.subscribe),
            busy = busy,
            hero = featured,
            onClick = onSubscribe,
        )
    }
}

/** The buy button. The featured plan gets the vivid "go" green and the sweep; the rest stay calm teal. */
@Composable
private fun PlanCta(label: String, busy: Boolean, hero: Boolean, onClick: () -> Unit) {
    val motion = rememberMotionEnabled()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(110), label = "cta-press")
    val shape = RoundedCornerShape(18.dp)
    val top = if (hero) Ink.CtaGreen else Ink.Teal
    val bottom = if (hero) Ink.CtaGreenPressed else Ink.Teal
    val content = if (hero) Ink.OnCtaGreen else Ink.OnTeal
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(scale)
            .clip(shape)
            .background(if (busy) Brush.verticalGradient(listOf(Ink.SurfaceHigh, Ink.SurfaceHigh)) else Brush.verticalGradient(listOf(top, bottom)))
            .shimmerSweep(enabled = hero && !busy && motion)
            .clickable(interactionSource = interaction, indication = null, enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.2.dp, color = Ink.TextMuted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = content, style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp))
            }
        }
    }
}

/** Where the money goes and how long replies last, said once, quietly, at the bottom. */
@Composable
private fun TrustFooter() {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Ink.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr(StrAiPlans.payMethods), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Text(tr(StrAiPlans.intro), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
