package com.rork.pro.ui.screens.payment

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.rork.pro.data.CommerceRepository
import com.rork.pro.data.ManualPaymentAccount
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.PaymentMethod
import com.rork.pro.data.toAppError
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.StrPay
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Checkout's own palette, in a light and a dark version that follow the app's appearance switch
 * ([com.rork.pro.ui.theme.AppAppearance.dark]). Drawn from the owner's checkout design: a deep
 * navy night sky with blue accents in dark mode, a bright day sky with white cards in light mode,
 * brand orange for the "7" and the price in both.
 */
internal object Pay {
    private val dark: Boolean get() = com.rork.pro.ui.theme.AppAppearance.dark
    private fun pick(light: Long, darkValue: Long) = Color(if (dark) darkValue else light)

    val isDark: Boolean get() = dark

    // Page
    val bg0: Color get() = pick(0xFF8AD6FE, 0xFF011837)
    val bg1: Color get() = pick(0xFFD3F0FD, 0xFF031634)
    val bg2: Color get() = pick(0xFFF4FAFE, 0xFF010F26)
    val cloud: Color get() = if (dark) Color(0xFF0C2C5E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f)

    // Cards
    val surface: Color get() = pick(0xFFFFFFFF, 0xFF061D3D)
    val surfaceElevated: Color get() = pick(0xFFF2FAFF, 0xFF0B2A52)
    val section: Color get() = if (dark) Color(0xFF041A37) else Color.White.copy(alpha = 0.55f)
    val heroCard: Color get() = pick(0xFFE2F5FE, 0xFF011837)
    val border: Color get() = pick(0xFFD7EAF6, 0xFF17365F)
    val borderStrong: Color get() = pick(0xFFA9CBE6, 0xFF2A4D80)

    /** Faint wash for fields and empty states: white on the night sky, navy on the day sky. */
    val tint: Color get() = if (dark) Color.White else Color(0xFF0B3B7A)

    // Text
    val textPrimary: Color get() = pick(0xFF0B1B45, 0xFFFFFFFF)
    val textSecondary: Color get() = pick(0xFF3B5378, 0xFF9BBEF2)
    val textMuted: Color get() = pick(0xFF56739E, 0xFF7F9CC9)

    // Accents
    val blue: Color get() = pick(0xFF1E78F0, 0xFF2F8CFF)
    val blueText: Color get() = pick(0xFF1F6FE0, 0xFF4DA3FF)
    val blueSoft: Color get() = pick(0xFFD2ECFC, 0xFF1B3358)
    val gold = Color(0xFFFD8233)
    val goldDeep = Color(0xFFE96308)
    val price = Color(0xFFF7A928)
    val onGold = Color(0xFFFFFFFF)
    val green = Color(0xFF22C55E)
    val teal: Color get() = pick(0xFF0F8A6A, 0xFF4FC0A0)
    val sky: Color get() = pick(0xFF1F6FE0, 0xFF8EC1FF)
    val danger: Color get() = pick(0xFFC0392B, 0xFFF0B5BC)
}

/** The checkout's page: its sky gradient with soft clouds in the two bottom corners. */
internal fun Modifier.payBackground(): Modifier = this.drawBehind {
    drawRect(Brush.verticalGradient(listOf(Pay.bg0, Pay.bg1, Pay.bg2)))
    val c = Pay.cloud
    val w = size.width
    val h = size.height
    val u = 22.dp.toPx()
    // bottom-left
    drawCircle(c, radius = u * 2.2f, center = androidx.compose.ui.geometry.Offset(-u * 0.4f, h + u * 0.6f))
    drawCircle(c, radius = u * 1.5f, center = androidx.compose.ui.geometry.Offset(u * 1.6f, h + u * 0.9f))
    // bottom-right
    drawCircle(c, radius = u * 2.0f, center = androidx.compose.ui.geometry.Offset(w + u * 0.3f, h + u * 0.4f))
    drawCircle(c, radius = u * 1.4f, center = androidx.compose.ui.geometry.Offset(w - u * 1.7f, h + u * 1.0f))
}

/** A control's own press feedback: a small, snappy scale-down while held. Shared by every custom
 *  interactive surface in this screen so pressing anything here feels like the same material. */
@Composable
private fun rememberPressScale(pressed: Boolean, target: Float = 0.96f): Float {
    val scale by animateFloatAsState(
        if (pressed) target else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

/** A dashed rounded-rect border — the universal "drop something here" affordance, drawn by hand
 *  since Compose has no built-in dashed [border]. */
private fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp) = this.drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
    )
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx(), radius.toPx()))
}

/** What the learner is about to pay for, as priced by the server. */
data class PaymentSummary(
    val title: String,
    val listPrice: Double,
    val discount: Double,
    val total: Double,
    val currency: String,
)

/**
 * Everything the course screen needs to act on what the learner chose.
 *
 * One object rather than a handful of nullable arguments, because a wallet paid through the
 * gateway and a wallet paid by hand carry different fields and must not be confused for each
 * other on the way out of this sheet.
 */
data class PayRequest(
    /** Gateway integration to charge, when Paymob is doing the charging. */
    val methodId: String? = null,
    val couponCode: String? = null,
    /** Number to bill for a gateway wallet charge. */
    val walletPhone: String? = null,
    /** Wallet the learner transferred to by hand: VODAFONE / ORANGE / ETISALAT / WE. */
    val manualBrand: String? = null,
    /** Number the learner says the money left from. */
    val senderPhone: String? = null,
    /** Storage key of the uploaded transfer screenshot. */
    val proofPath: String? = null,
    val note: String? = null,
) {
    val isManual: Boolean get() = manualBrand != null
}

private data class MethodLook(val icon: ImageVector, val name: Tr, val sub: Tr)

/**
 * The four Egyptian mobile-wallet operators, each with its own brand colour and name.
 *
 * The colours are the operators' own so a payer recognises their wallet at a glance instead of
 * reading a generic row. All four are always on the sheet — what changes with the owner's
 * gateway switch is only how they are paid, never whether they are there.
 */
enum class WalletBrand(val brandColor: Color, val label: Tr) {
    VODAFONE(Color(0xFFE60000), StrPay.walletVodafone),
    ORANGE(Color(0xFFFF7900), StrPay.walletOrange),
    ETISALAT(Color(0xFF00A651), StrPay.walletEtisalat),
    WE(Color(0xFF7B2D8E), StrPay.walletWe),
}

/**
 * The operator that issued an Egyptian mobile number, by prefix: 010 Vodafone · 011 Etisalat ·
 * 012 Orange · 015 WE.
 *
 * Resolves as soon as three digits are typed, so the field takes on the payer's own wallet colour
 * while they are still entering the number.
 */
fun walletBrandFromPrefix(raw: String): WalletBrand? {
    val digits = raw.filter(Char::isDigit)
    if (digits.length < 3 || !digits.startsWith("01")) return null
    return when (digits.take(3)) {
        "010" -> WalletBrand.VODAFONE
        "011" -> WalletBrand.ETISALAT
        "012" -> WalletBrand.ORANGE
        "015" -> WalletBrand.WE
        else -> null
    }
}

/**
 * The brand to present a wallet option with: what the gateway account says it is, falling back to
 * the number the payer has typed so far.
 */
fun walletBrandOf(method: PaymentMethod, typedPhone: String): WalletBrand? =
    method.walletBrand?.let { brand -> WalletBrand.entries.firstOrNull { it.name == brand } }
        ?: walletBrandFromPrefix(typedPhone)

/**
 * True for an Egyptian mobile-wallet number Paymob can bill.
 *
 * 010 Vodafone · 011 Etisalat · 012 Orange · 015 WE. The server checks this again before an
 * order is opened; the copy here only spares the learner a pointless round trip.
 */
fun isWalletNumber(raw: String): Boolean = Regex("^01[0125]\\d{8}$").matches(raw.filter(Char::isDigit))

/**
 * True when a complete number really belongs to the wallet the payer picked.
 *
 * Picking the Vodafone row and typing an Orange number is the single most likely mistake here,
 * and it is one the payer can only discover much later — after a failed charge, or after money
 * has been transferred to the wrong wallet. So it is refused in the row itself.
 */
private fun matchesBrand(raw: String, brand: WalletBrand?): Boolean {
    if (brand == null) return true
    return walletBrandFromPrefix(raw) == brand
}

private fun lookOf(kind: String): MethodLook? = when (kind) {
    "CARD" -> MethodLook(Icons.Default.CreditCard, StrPay.methodCard, StrPay.methodCardSub)
    "WALLET" -> MethodLook(Icons.Default.AccountBalanceWallet, StrPay.methodWallet, StrPay.methodWalletSub)
    "INSTAPAY" -> MethodLook(Icons.Default.AccountBalance, StrPay.methodInstapay, StrPay.methodInstapaySub)
    "MEEZA" -> MethodLook(Icons.Default.Payments, StrPay.methodMeeza, StrPay.methodMeezaSub)
    "KIOSK" -> MethodLook(Icons.Default.Storefront, StrPay.methodKiosk, StrPay.methodKioskSub)
    "INSTALLMENT" -> MethodLook(Icons.Default.Schedule, StrPay.methodInstallment, StrPay.methodInstallmentSub)
    else -> null
}

/**
 * One row on the payment sheet, whichever way it is actually paid.
 *
 * A wallet row is the same row to the learner — same name, same colour — whether it is charged
 * through the gateway or transferred to by hand. Only [account] and [integrationId] differ, and
 * they decide what happens when the button at the bottom is pressed.
 */
private data class PayOption(
    val key: String,
    val kind: String,
    val brand: WalletBrand? = null,
    val label: String? = null,
    /** Gateway integration to charge; null on a wallet that is transferred to by hand. */
    val integrationId: String? = null,
    /** The published number to transfer to; null when the gateway is doing the charging. */
    val account: ManualPaymentAccount? = null,
    /** False for a wallet the owner has published no number for while the gateway is off. */
    val enabled: Boolean = true,
) {
    val isManual: Boolean get() = account != null
}

/**
 * Builds the rows the learner sees.
 *
 * The four wallets are always present, in a fixed order, with their own names and colours. What
 * the owner's switch changes is the route behind each one:
 *  - gateway on  → the wallet integration the merchant account exposes (per-operator when it has
 *                  one, otherwise the single unified rail every operator shares);
 *  - gateway off → the number the owner published for that operator, paid by hand.
 * A wallet with neither is still shown, plainly marked as unavailable, rather than quietly
 * vanishing and leaving the learner wondering where their wallet went.
 */
private fun buildOptions(
    gatewayMethods: List<PaymentMethod>,
    paymobEnabled: Boolean,
    accounts: List<ManualPaymentAccount>,
): List<PayOption> {
    val options = mutableListOf<PayOption>()

    if (paymobEnabled) {
        gatewayMethods.filter { it.kind != "WALLET" }.forEach { method ->
            options += PayOption(
                key = "GW:${method.id}",
                kind = method.kind,
                label = method.label,
                integrationId = method.id,
            )
        }
    }

    val walletMethods = if (paymobEnabled) gatewayMethods.filter { it.kind == "WALLET" } else emptyList()

    WalletBrand.entries.forEach { brand ->
        // A rail Paymob named after this operator is preferred; otherwise the account's single
        // unified wallet rail, which bills every operator, serves this row too.
        val gateway = walletMethods.firstOrNull { it.walletBrand == brand.name }
            ?: walletMethods.firstOrNull { it.walletBrand == null }
        val account = accounts.firstOrNull { it.brand.equals(brand.name, ignoreCase = true) && it.isUsable }

        options += when {
            gateway != null -> PayOption(
                key = "GW:${gateway.id}:${brand.name}",
                kind = "WALLET",
                brand = brand,
                integrationId = gateway.id,
            )

            account != null -> PayOption(
                key = "MANUAL:${brand.name}",
                kind = "WALLET",
                brand = brand,
                account = account,
            )

            else -> PayOption(
                key = "OFF:${brand.name}",
                kind = "WALLET",
                brand = brand,
                enabled = false,
            )
        }
    }

    return options
}

/**
 * The 7PRO payment sheet.
 *
 * Card, InstaPay and Meeza appear only while the gateway is on, because only the gateway can
 * charge them. The four wallets are always here: with Paymob on they are charged on the payer's
 * phone, and with Paymob off the same row asks for a transfer to the platform's own number and
 * hands the proof to the owner for approval. Nothing on this screen ever unlocks a course — that
 * is the server's decision either way.
 */
@Composable
fun PaymentSheet(
    summary: PaymentSummary,
    working: Boolean,
    onDismiss: () -> Unit,
    onPay: (PayRequest) -> Unit,
    quoteLoader: suspend (String?) -> PaymentSummary? = { null },
) {
    var options by remember { mutableStateOf<List<PayOption>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var walletPhone by remember { mutableStateOf("") }
    var walletTouched by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    var couponStatus by remember { mutableStateOf<CouponStatus>(CouponStatus.Idle) }
    var currentSummary by remember(summary) { mutableStateOf(summary) }

    // Manual transfer state, kept per selected wallet: switching rows starts a clean form so a
    // screenshot can never be filed against the wrong wallet.
    var senderPhone by remember { mutableStateOf("") }
    var senderTouched by remember { mutableStateOf(false) }
    var proofPath by remember { mutableStateOf<String?>(null) }
    var proofName by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    // Checkout UX: coupon field tucked behind a link (it pulls attention away from paying), and
    // the sheet glides to the transfer form the moment a wallet is tapped.
    var showCoupon by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var formTop by remember { mutableStateOf(0) }
    var userPicked by remember { mutableStateOf(0) }
    LaunchedEffect(userPicked) {
        if (userPicked > 0) {
            delay(120)
            scrollState.animateScrollTo(formTop, animationSpec = tween(350))
        }
    }

    LaunchedEffect(summary.currency) {
        loading = true
        val methods = runCatching { CommerceRepository.paymentMethods(summary.currency) }.getOrNull()
        val accounts = CommerceRepository.manualAccounts()
        val built = buildOptions(
            gatewayMethods = methods?.methods.orEmpty(),
            paymobEnabled = methods?.paymobEnabled ?: false,
            accounts = accounts,
        )
        options = built
        selectedKey = built.firstOrNull { it.kind == "CARD" }?.key
            ?: built.firstOrNull { it.enabled }?.key
        loading = false
    }

    LaunchedEffect(couponStatus) {
        if (couponStatus is CouponStatus.Loading && couponCode.isNotBlank()) {
            val newSummary = quoteLoader(couponCode)
            if (newSummary != null) {
                currentSummary = newSummary
                couponStatus = CouponStatus.Applied
            } else {
                couponStatus = CouponStatus.Invalid
            }
        }
    }

    val selected = options.firstOrNull { it.key == selectedKey }

    /**
     * Nothing is owed — either a 100%-off coupon was just applied here, or the item was free to
     * begin with. Everything about choosing and validating a payment method is skipped: there is
     * no charge to route, and asking someone to pick a rail, type a wallet number or upload a
     * transfer screenshot for a total of zero is asking them to prove a payment that must never
     * happen. It also used to be a dead end — with the gateway off and no transfer account
     * published there was no selectable method at all, so the confirm button stayed disabled and
     * a fully-discounted coupon could not be redeemed by any route.
     */
    val nothingToPay = currentSummary.total <= 0.0

    // A one-time reveal for the whole sheet — a quiet fade-and-rise rather than the content just
    // being there the instant the dialog opens, which is what makes a checkout feel considered
    // rather than assembled from stock components.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .payBackground()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            CheckoutHeader(onDismiss = onDismiss)

            AnimatedVisibility(
                visible = revealed,
                modifier = Modifier.weight(1f),
                enter = fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 14 },
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.screenPadding),
            ) {
                // Value first: what they are getting and what it costs, before any form.
                Spacer(Modifier.height(4.dp))
                CheckoutValueHero(currentSummary)

                val manualFlow = options.isNotEmpty() && options.none { it.enabled && !it.isManual }
                if (!nothingToPay && manualFlow) {
                    Spacer(Modifier.height(14.dp))
                    CheckoutProgress(
                        step = when {
                            selected == null -> 0
                            !isWalletNumber(senderPhone) -> 1
                            proofPath == null -> 2
                            else -> 3
                        },
                    )
                }

                // All payment methods live together as one group right under the title, so the
                // learner picks a rail first — nothing about it is scattered further down the
                // sheet, and the order summary and per-method form follow only after the choice.
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Pay.section)
                        .then(if (Pay.isDark) Modifier.border(1.dp, Color(0xFF13325A), RoundedCornerShape(26.dp)) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                if (!nothingToPay) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = Pay.blue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr(StrPay.chooseMethod),
                            color = Pay.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when {
                    nothingToPay -> Text(
                        tr(StrPay.couponCoversAll),
                        color = Pay.teal,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Pay.gold)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr(StrPay.loadingMethods),
                            color = Pay.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    options.isEmpty() -> Text(
                        tr(StrPay.securedBy),
                        color = Pay.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.filter { it.enabled }.forEach { option ->
                            MethodRow(option, option.key == selectedKey) {
                                if (!option.enabled) return@MethodRow
                                // Only the manual-transfer form (several fields, worth bringing into
                                // view) auto-scrolls; a gateway wallet's one short field stays where
                                // it is so tapping a row never moves the screen unexpectedly.
                                if (option.key != selectedKey && option.isManual) userPicked++
                                selectedKey = option.key
                                // Each row starts its own clean form.
                                walletPhone = ""
                                walletTouched = false
                                senderPhone = ""
                                senderTouched = false
                                proofPath = null
                                proofName = ""
                                uploadError = null
                                note = ""
                            }
                        }
                        // Wallets with no published number: one quiet line instead of dead rows
                        // that push the working options and the form off the screen.
                        val off = options.filter { !it.enabled }.mapNotNull { it.brand?.let { b -> tr(b.label) } }
                        if (off.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Pay.blue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    trf(StrPay.unavailableWallets, off.joinToString(" • ")),
                                    color = Pay.blueText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                }

                Spacer(Modifier.height(20.dp))
                SummaryCard(currentSummary)

                Spacer(Modifier.height(16.dp))
                if (showCoupon || couponStatus != CouponStatus.Idle || couponCode.isNotBlank()) {
                    CouponRow(
                        code = couponCode,
                        onCodeChange = {
                            couponCode = it
                            couponStatus = CouponStatus.Idle
                            currentSummary = summary
                        },
                        status = couponStatus,
                        loading = working,
                        onApply = { couponStatus = CouponStatus.Loading },
                    )
                } else {
                    Text(
                        tr(StrPay.haveCoupon),
                        color = Pay.textSecondary,
                        style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showCoupon = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).onGloballyPositioned { formTop = it.positionInParent().y.toInt() })

                // Detail form for whichever method is currently selected — kept below the
                // summary rather than wedged inside the method list above.
                selected?.takeIf { !nothingToPay }?.let { option ->
                    if (option.kind == "WALLET" && !option.isManual) {
                        Spacer(Modifier.height(20.dp))
                        WalletNumberField(
                            value = walletPhone,
                            onValueChange = {
                                walletPhone = it.filter(Char::isDigit).take(11)
                                walletTouched = true
                            },
                            showError = walletTouched && walletPhone.length >= 11 &&
                                (!isWalletNumber(walletPhone) || !matchesBrand(walletPhone, option.brand)),
                            brand = option.brand ?: walletBrandFromPrefix(walletPhone),
                            errorText = if (
                                walletTouched && walletPhone.length >= 11 &&
                                isWalletNumber(walletPhone) && !matchesBrand(walletPhone, option.brand)
                            ) {
                                option.brand?.let { trf(StrPay.senderNumberWrongBrand, tr(it.label)) }
                            } else {
                                null
                            },
                        )
                    }

                    // A wallet with the gateway off: the money has already been sent by hand,
                    // and what is collected here is what proves it.
                    option.account?.let { account ->
                        Spacer(Modifier.height(20.dp))
                        ManualTransferPanel(
                            account = account,
                            brand = option.brand,
                            amountLabel = formatMoney(currentSummary.total, currentSummary.currency),
                            senderPhone = senderPhone,
                            onSenderPhoneChange = {
                                senderPhone = it.filter(Char::isDigit).take(11)
                                senderTouched = true
                            },
                            senderTouched = senderTouched,
                            proofAttached = proofPath != null,
                            proofName = proofName,
                            uploading = uploading,
                            uploadError = uploadError,
                            note = note,
                            onNoteChange = { note = it },
                            onProofPicked = { path, name ->
                                proofPath = path
                                proofName = name
                            },
                            onUploadingChange = { uploading = it },
                            onUploadError = { uploadError = it },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 14.dp),
            ) {
                val amountLabel = formatMoney(currentSummary.total, currentSummary.currency)
                val isWallet = selected?.kind == "WALLET"
                val isManual = selected?.isManual == true

                // Only a complete, correct form can be sent — a wallet charge needs a number for
                // the right operator, a transfer needs that plus a screenshot.
                val ready = when {
                    // Zero owed: the only thing left to send is the coupon itself.
                    nothingToPay -> true
                    selected == null || !selected.enabled -> false
                    // A manual transfer is proved by its screenshot, not by which network the
                    // money moved on — it can be sent from any wallet, or via InstaPay, to the
                    // number shown, so the sender's own operator is never checked here.
                    isManual -> isWalletNumber(senderPhone) && proofPath != null && !uploading
                    isWallet -> isWalletNumber(walletPhone) && matchesBrand(walletPhone, selected.brand)
                    else -> true
                }

                // Says exactly what is still missing (or that everything is ready), so a disabled
                // button is never a dead end — shown as the design's course banner.
                if (!nothingToPay && !loading && (isManual || selected == null)) {
                    val (hint, ok) = when {
                        selected == null -> tr(StrPay.missingMethod) to false
                        !isWalletNumber(senderPhone) -> tr(StrPay.missingSender) to false
                        proofPath == null || uploading -> tr(StrPay.missingProof) to false
                        else -> tr(StrPay.readyToSend) to true
                    }
                    HintBanner(currentSummary.title.split(" · ").first(), hint, ok)
                    Spacer(Modifier.height(10.dp))
                }

                // The confirm button carries the chosen wallet's own colour, so the payer's last
                // action visibly belongs to the rail they picked rather than a generic button —
                // checkout gets its own bespoke button rather than the app-wide PrimaryAction, so
                // it can carry a gradient, a colour-matched shadow and its own press feel.
                val payBrand = selected?.brand
                CheckoutPayButton(
                    label = when {
                        nothingToPay -> tr(StrPay.enrollFree)
                        isManual -> trf(StrPay.sendRequestAmount, amountLabel)
                        isWallet -> trf(StrPay.walletPayNow, amountLabel)
                        else -> trf(StrPay.payNow, amountLabel)
                    },
                    brandColor = payBrand?.brandColor,
                    enabled = !loading && ready,
                    loading = working,
                ) {
                    val code = couponCode.takeIf { it.isNotBlank() }
                    if (nothingToPay) {
                        // No method, no wallet number, no proof — the server grants access off
                        // the zero total alone, and it recomputes that total from the coupon
                        // itself rather than trusting anything sent from here.
                        onPay(PayRequest(couponCode = code))
                        return@CheckoutPayButton
                    }
                    val option = selected ?: return@CheckoutPayButton
                    onPay(
                        if (option.isManual) {
                            PayRequest(
                                couponCode = code,
                                manualBrand = option.brand?.name,
                                senderPhone = senderPhone,
                                proofPath = proofPath,
                                note = note.takeIf { it.isNotBlank() },
                            )
                        } else {
                            PayRequest(
                                methodId = option.integrationId,
                                couponCode = code,
                                walletPhone = walletPhone.takeIf { option.kind == "WALLET" },
                            )
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Pay.blue,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // With the gateway off there is no card processor in the picture, so
                        // claiming one would be untrue.
                        tr(
                            when {
                                nothingToPay -> StrPay.couponCoversAll
                                isManual -> StrPay.privacyShort
                                else -> StrPay.securedBy
                            },
                        ),
                        color = Pay.blueText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The rounded course banner above the button: the course on top, what is still missing (or that
 * everything is ready) under it, the book-and-cap illustration on the start side.
 */
@Composable
private fun HintBanner(course: String, hint: String, ok: Boolean) {
    val dark = Pay.isDark
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(if (dark) 0.dp else 10.dp, shape, ambientColor = Pay.blue.copy(alpha = 0.4f), spotColor = Pay.blue.copy(alpha = 0.4f))
            .clip(shape)
            .background(
                if (dark) Brush.horizontalGradient(listOf(Color(0xFF0B2A5C), Color(0xFF0A2147)))
                else Brush.horizontalGradient(listOf(Color(0xFF2F8CF7), Color(0xFF1668E8))),
            )
            .then(if (dark) Modifier.border(1.5.dp, Color(0xFF1F5FBF), shape) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                if (dark) com.rork.pro.R.drawable.pay_book_dark else com.rork.pro.R.drawable.pay_book_light,
            ),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxHeight().aspectRatio(if (dark) 252f / 102f else 220f / 108f),
        )
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                course,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (ok) Pay.green else Color(0xFFF7B731),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    hint,
                    color = if (dark) Color(0xFF6FB4FF) else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = if (dark) Color(0xFF3B9CFF) else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(end = 16.dp, start = 4.dp).size(24.dp),
        )
    }
}

/**
 * Checkout's confirm button, as in the design: a blue gradient pill with a lock, the action and
 * the amount. While something is still missing it stays visible but quiet — a softer blue in
 * dark mode, a pale sky pill with blue text in light mode — so it is never a dead grey block.
 */
@Composable
private fun CheckoutPayButton(
    label: String,
    @Suppress("UNUSED_PARAMETER") brandColor: Color?,
    enabled: Boolean,
    loading: Boolean,
    /** Leading glyph — a lock for every charge/transfer action (the default), swappable for a
     *  button that isn't itself a payment, like the result screen's "Start learning". */
    icon: ImageVector? = Icons.Default.Lock,
    onClick: () -> Unit,
) {
    val dark = Pay.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ctaPress",
    )
    val shape = RoundedCornerShape(30.dp)
    val pale = !enabled && !dark
    val content = if (pale) Color(0xFF1F5FBF) else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(58.dp)
            .scale(scale)
            .alpha(if (!enabled && dark) 0.8f else 1f)
            .shadow(
                elevation = if (enabled) 16.dp else 0.dp,
                shape = shape,
                ambientColor = Pay.blue.copy(alpha = 0.6f),
                spotColor = Pay.blue.copy(alpha = 0.6f),
            )
            .clip(shape)
            .background(
                if (pale) Brush.verticalGradient(listOf(Color(0xFFDDF1FD), Color(0xFFCFEAFB)))
                else Brush.verticalGradient(listOf(Color(0xFF2E86FF), Color(0xFF1A5FE6))),
            )
            .border(1.5.dp, if (pale) Color(0xFFBCDDF5) else Color(0xFF6FB4FF).copy(alpha = 0.6f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = loading, label = "ctaContent") { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = content)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        label,
                        color = content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        if (dark && !loading) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(16.dp),
            )
        }
    }
}

/**
 * The premium checkout's own top bar — a press-scale close button, the "7PRO" wordmark, and a
 * small "secure checkout" badge — shared by every screen in the payment journey (picking a
 * method, a pending manual transfer, a wallet approval wait, the hosted gateway page) so the
 * whole flow reads as one considered surface instead of reverting to the app's ordinary
 * light/dark chrome the moment the learner leaves the method-picker.
 */
@Composable
private fun CheckoutHeader(onDismiss: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val closeInteraction = remember { MutableInteractionSource() }
        val closePressed by closeInteraction.collectIsPressedAsState()
        Box(
            Modifier
                .size(46.dp)
                .scale(rememberPressScale(closePressed))
                .shadow(if (Pay.isDark) 0.dp else 6.dp, CircleShape, ambientColor = Pay.blue.copy(alpha = 0.25f), spotColor = Pay.blue.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(if (Pay.isDark) Color(0xFF0A2347) else Color.White)
                .then(if (Pay.isDark) Modifier.border(1.dp, Color(0xFF1E3E6B), CircleShape) else Modifier)
                .clickable(interactionSource = closeInteraction, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = tr(StrPay.cancelPayment),
                tint = Pay.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Pay.gold, fontWeight = FontWeight.Black)) { append("7") }
                    withStyle(SpanStyle(color = Pay.textPrimary, fontWeight = FontWeight.Black)) { append("PRO") }
                },
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp,
            )
            subtitle?.let {
                Text(it, color = Pay.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (Pay.isDark) Color(0xFF07263F) else Color.White)
                .then(if (Pay.isDark) Modifier.border(1.dp, Color(0xFF1B5268), RoundedCornerShape(999.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = if (Pay.isDark) Color(0xFF2DD4BF) else Color(0xFF16A34A),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                tr(StrPay.checkoutTitle),
                color = Pay.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The outline counterpart to [CheckoutPayButton] — used for a secondary action on a status screen
 * ("open the wallet page", "try again"), so a screen with only one real choice does not have to
 * borrow the app's ordinary (light/dark, [Ink]-toned) [SecondaryAction] and break the checkout's
 * otherwise fully self-contained dark palette.
 */
@Composable
private fun CheckoutSecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(rememberPressScale(pressed, target = 0.98f))
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Pay.borderStrong, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Pay.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * The transfer instructions and the proof form, shown under the wallet the learner picked.
 *
 * The number to send to comes from the owner's own settings and is copyable in one tap, because
 * a wallet number typed by hand from a screen is a wallet number sent to a stranger.
 */
/**
 * The transfer form itself: where to send the money, which number it left from, and the
 * screenshot proving it. Shared with the group-booking flow so a seat and a course are paid
 * for on visibly the same screen — the difference is only which server call files the proof.
 */
@Composable
internal fun ManualTransferPanel(
    account: ManualPaymentAccount,
    brand: WalletBrand?,
    amountLabel: String,
    senderPhone: String,
    onSenderPhoneChange: (String) -> Unit,
    senderTouched: Boolean,
    proofAttached: Boolean,
    proofName: String,
    uploading: Boolean,
    uploadError: String?,
    note: String,
    onNoteChange: (String) -> Unit,
    onProofPicked: (String, String) -> Unit,
    onUploadingChange: (Boolean) -> Unit,
    onUploadError: (String?) -> Unit,
) {
    val accent = brand?.brandColor ?: Pay.gold
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .background(Pay.surfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        val senderOk = isWalletNumber(senderPhone)
        StepHeader(
            1,
            buildAnnotatedString {
                val full = trf(StrPay.manualStep1, amountLabel)
                val at = full.indexOf(amountLabel)
                if (at < 0) append(full) else {
                    append(full.substring(0, at))
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Black)) { append(amountLabel) }
                    append(full.substring(at + amountLabel.length))
                }
            },
            done = senderOk,
            accent = accent,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Pay.tint.copy(alpha = 0.04f))
                .border(1.dp, Pay.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                account.phone,
                color = accent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f),
            )
            val copyInteraction = remember { MutableInteractionSource() }
            val copyPressed by copyInteraction.collectIsPressedAsState()
            Row(
                Modifier
                    .scale(rememberPressScale(copyPressed))
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(interactionSource = copyInteraction, indication = null) {
                        clipboard.setText(AnnotatedString(account.phone))
                        copied = true
                    }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(targetState = copied, label = "copyIcon") { done ->
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    tr(if (copied) StrPay.transferCopied else StrPay.transferCopy),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        account.holderName?.takeIf { it.isNotBlank() }?.let { holder ->
            Spacer(Modifier.height(8.dp))
            Text(
                "${tr(StrPay.transferHolder)}: $holder",
                color = Pay.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        account.instructions?.takeIf { it.isNotBlank() }?.let { extra ->
            Spacer(Modifier.height(8.dp))
            Text(extra, color = Pay.textMuted, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Pay.sky.copy(alpha = 0.10f))
                .border(1.dp, Pay.sky.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Pay.sky, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(tr(StrPay.instapaySupported), color = Pay.sky, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pay.border))
        Spacer(Modifier.height(18.dp))
        StepHeader(2, AnnotatedString(tr(StrPay.manualStep2)), done = senderOk, accent = accent)
        Spacer(Modifier.height(10.dp))
        val senderBad = senderTouched && senderPhone.length >= 11 && !isWalletNumber(senderPhone)
        OutlinedTextField(
            value = senderPhone,
            onValueChange = onSenderPhoneChange,
            label = { Text(tr(StrPay.senderNumber), color = Pay.textMuted) },
            placeholder = { Text(tr(StrPay.walletNumberHint), color = Pay.textMuted) },
            singleLine = true,
            isError = senderBad,
            leadingIcon = {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = if (senderBad) Pay.danger else accent,
                    modifier = Modifier.size(20.dp),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = accent.copy(alpha = 0.35f),
                errorBorderColor = Pay.danger,
                cursorColor = accent,
                focusedTextColor = Pay.textPrimary,
                unfocusedTextColor = Pay.textPrimary,
                focusedContainerColor = Pay.tint.copy(alpha = 0.03f),
                unfocusedContainerColor = Pay.tint.copy(alpha = 0.03f),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (senderBad) tr(StrPay.walletNumberInvalid) else tr(StrPay.senderNumberHelp),
            color = if (senderBad) Pay.danger else Pay.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pay.border))
        Spacer(Modifier.height(18.dp))
        StepHeader(3, AnnotatedString(tr(StrPay.manualStep3)), done = proofAttached && !uploading, accent = accent)
        Spacer(Modifier.height(10.dp))
        val pickProof: () -> Unit = {
            onUploadError(null)
            picker.pick("image/*") { uri ->
                scope.launch {
                    onUploadingChange(true)
                    runCatching {
                        val file = MediaRepository.read(context, uri, MediaRepository.MAX_PROOF_BYTES)
                        MediaRepository.uploadPaymentProof(file) to file.name
                    }
                        .onSuccess { (path, name) -> onProofPicked(path, name) }
                        .onFailure { failure ->
                            val error = failure.toAppError()
                            onUploadError(
                                if (error.code == "FILE_TOO_LARGE") tr(StrPay.proofTooLarge) else error.message,
                            )
                        }
                    onUploadingChange(false)
                }
            }
        }
        val attached = proofAttached && !uploading
        val zoneColor = if (attached) Pay.teal else accent
        val zoneInteraction = remember { MutableInteractionSource() }
        val zonePressed by zoneInteraction.collectIsPressedAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .scale(rememberPressScale(zonePressed, target = 0.985f))
                .clip(RoundedCornerShape(18.dp))
                .background(zoneColor.copy(alpha = if (attached) 0.12f else 0.05f))
                .let { m -> if (attached) m.border(1.5.dp, zoneColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp)) else m.dashedBorder(zoneColor.copy(alpha = 0.55f), 18.dp, 1.5.dp) }
                .clickable(interactionSource = zoneInteraction, indication = null, enabled = !uploading, onClick = pickProof)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(zoneColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = if (uploading) "up" else if (attached) "ok" else "add", label = "proofIcon") { st ->
                    when (st) {
                        "up" -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = zoneColor)
                        "ok" -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = zoneColor, modifier = Modifier.size(24.dp))
                        else -> Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = zoneColor, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        uploading -> tr(StrPay.proofUploading)
                        attached -> tr(StrPay.proofAttached)
                        else -> tr(StrPay.proofDrop)
                    },
                    color = if (attached) Pay.teal else Pay.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (attached) proofName.ifBlank { tr(StrPay.proofReplace) } else tr(StrPay.proofDropSub),
                    color = Pay.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (attached) {
                Text(
                    tr(StrPay.proofReplace),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        uploadError?.let { message ->
            Spacer(Modifier.height(6.dp))
            Text(message, color = Pay.danger, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text(tr(StrPay.noteOptional), color = Pay.textMuted) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = Pay.border,
                cursorColor = accent,
                focusedTextColor = Pay.textPrimary,
                unfocusedTextColor = Pay.textPrimary,
                focusedContainerColor = Pay.tint.copy(alpha = 0.03f),
                unfocusedContainerColor = Pay.tint.copy(alpha = 0.03f),
            ),
        )
    }
}

/**
 * Confirms a transfer has been filed and is now waiting on a person.
 *
 * The order is watched while this is open, so a learner whose transfer is approved straight away
 * lands on the same success panel a card payment would — without having to leave and come back.
 */
@Composable
fun ManualPending(orderId: String, onDone: (Boolean) -> Unit) {
    var status by remember { mutableStateOf("PENDING") }

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) return@LaunchedEffect
        // Approval is a human decision and may take hours, so this watches only while the panel
        // is on screen — the learner is told by notification either way.
        while (status == "PENDING") {
            delay(5000)
            status = runCatching { CommerceRepository.order(orderId) }.getOrNull()?.status ?: status
        }
    }

    Dialog(
        onDismissRequest = { onDone(status == "PAID") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .payBackground()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CheckoutHeader(onDismiss = { onDone(status == "PAID") })

            Box(Modifier.weight(1f)) {
                if (status == "PAID") {
                    ResultPanel(success = true, onPrimary = { onDone(true) })
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.screenPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Pay.gold.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Pay.gold,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            tr(StrPay.manualSentTitle),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Pay.textPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr(StrPay.manualSentBody),
                            color = Pay.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(26.dp))
                        CheckoutPayButton(
                            label = tr(StrPay.manualDone),
                            brandColor = null,
                            enabled = true,
                            loading = false,
                        ) { onDone(false) }
                    }
                }
            }
        }
    }
}

/**
 * Warm reassurance strip at the top of the checkout sheet — sets the expectation that this is
 * the very last step, matching the same "almost there" framing used on the group-booking form.
 */
/**
 * The hook at the top of checkout, drawn after the owner's design: the learner illustration on
 * the end side, "your course is waiting / one step and you're in" with the course and plan on the
 * start side, the price in its own pill, and the three reassurances underneath. In light mode the
 * whole block sits on a pale card; in dark mode it floats on the night sky.
 */
@Composable
private fun CheckoutValueHero(summary: PaymentSummary) {
    val dark = Pay.isDark
    // "Course · Monthly" arrives as one title; the plan gets its own line, as in the design.
    val parts = summary.title.split(" · ")
    val courseTitle = parts.first()
    val planLine = parts.drop(1).joinToString(" · ").takeIf { it.isNotBlank() }

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier
                else Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFD9F1FE), Color(0xFFEAF8FE))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(26.dp))
                    .padding(start = 14.dp, end = 0.dp, top = 12.dp, bottom = 14.dp),
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The illustration: end side (left in Arabic), bleeding to the edge.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (dark) com.rork.pro.R.drawable.pay_hero_dark else com.rork.pro.R.drawable.pay_hero_light,
                ),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(if (dark) 0.56f else 0.5f)
                    .aspectRatio(if (dark) 530f / 446f else 436f / 405f),
            )
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(0.6f)) {
                    // "Your course is waiting"
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (dark) Color(0xFF0A2550) else Color(0xFFF9C23C))
                            .then(if (dark) Modifier.border(1.dp, Color(0xFF2A5799), RoundedCornerShape(999.dp)) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (dark) Icons.Default.Star else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (dark) Color(0xFFF7B731) else Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tr(StrPay.heroKicker),
                            color = if (dark) Color(0xFFF7B731) else Color(0xFF0B1B45),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // "One step and you're in", with the hand-drawn swoosh under its middle word.
                    val swoosh = if (dark) Color(0xFF2F8CFF) else Color(0xFFF5A623)
                    Text(
                        tr(StrPay.heroHeadline),
                        color = Pay.textPrimary,
                        fontSize = 27.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.drawBehind {
                            val y = size.height - 2.dp.toPx()
                            val x0 = size.width * 0.30f
                            val x1 = size.width * 0.66f
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x0, y)
                                quadraticTo((x0 + x1) / 2f, y - 7.dp.toPx(), x1, y - 2.dp.toPx())
                            }
                            drawPath(
                                path,
                                swoosh,
                                style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Course line: book · "Level 3" in blue · "| Course name" · a small accent.
                Row(Modifier.fillMaxWidth(0.98f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFFF5A623), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val split = courseTitle.indexOf('|')
                    Text(
                        buildAnnotatedString {
                            if (split > 0) {
                                withStyle(SpanStyle(color = Pay.blueText, fontWeight = FontWeight.Bold)) { append(courseTitle.substring(0, split).trim()) }
                                withStyle(SpanStyle(color = Pay.textPrimary)) { append("  |  " + courseTitle.substring(split + 1).trim()) }
                            } else {
                                withStyle(SpanStyle(color = Pay.blueText, fontWeight = FontWeight.Bold)) { append(courseTitle) }
                            }
                            append(if (dark) "  ✦" else "  🇬🇧")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                planLine?.let { plan ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF7B731), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(plan, color = Pay.blueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Price on the start side, the three reassurances on the end side just below the
        // illustration — overlapping in height the way the design staggers them.
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth(0.42f)) { PricePill(summary) }
            BenefitStrip(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.6f)
                    .padding(top = 44.dp),
            )
        }
    }
}

/** "You pay" over "EGP 49" — currency in the text colour, the amount in gold, read left to right. */
@Composable
private fun PricePill(summary: PaymentSummary) {
    val dark = Pay.isDark
    Column(
        Modifier
            .shadow(if (dark) 0.dp else 10.dp, RoundedCornerShape(30.dp), ambientColor = Pay.blue.copy(alpha = 0.2f), spotColor = Pay.blue.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(30.dp))
            .background(if (dark) Color(0xFF071F40) else Color.White.copy(alpha = 0.92f))
            .then(if (dark) Modifier.border(1.5.dp, Color(0xFF244A7E), RoundedCornerShape(30.dp)) else Modifier)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(tr(StrPay.heroYouPay), color = Pay.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (summary.total <= 0.0) {
            Text(tr(StrPay.enrollFree), color = Pay.price, fontSize = 30.sp, fontWeight = FontWeight.Black)
        } else {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Pay.textPrimary, fontSize = 24.sp)) { append(summary.currency.uppercase() + " ") }
                        withStyle(SpanStyle(color = Pay.price, fontSize = 32.sp)) { append(plainAmount(summary.total)) }
                    },
                    fontWeight = FontWeight.Black,
                    lineHeight = 38.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (summary.discount > 0 && summary.listPrice > summary.total) {
            Text(
                formatMoney(summary.listPrice, summary.currency),
                color = Pay.textMuted,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
            )
        }
    }
}

private fun plainAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)

@Composable
private fun BenefitStrip(modifier: Modifier = Modifier) {
    val dark = Pay.isDark
    Row(
        modifier
            .shadow(if (dark) 0.dp else 8.dp, RoundedCornerShape(24.dp), ambientColor = Pay.blue.copy(alpha = 0.18f), spotColor = Pay.blue.copy(alpha = 0.18f))
            .clip(RoundedCornerShape(24.dp))
            .background(if (dark) Color(0xFF061D3D) else Color.White)
            .then(if (dark) Modifier.border(1.dp, Color(0xFF1A3A63), RoundedCornerShape(24.dp)) else Modifier)
            .padding(if (dark) 8.dp else 6.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(if (dark) 8.dp else 0.dp),
    ) {
        HeroBenefit(Icons.Default.Bolt, tr(StrPay.benefitUnlockShort), if (dark) Color(0xFF34D399) else Pay.blue, if (dark) Color(0xFF0E3B3A) else Color(0xFFEAF5FE), Modifier.weight(1f))
        if (!dark) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Color(0xFFE3EEF8)))
        HeroBenefit(Icons.Default.Schedule, tr(StrPay.benefitAnytimeShort), if (dark) Color(0xFFA78BFA) else Pay.blue, if (dark) Color(0xFF2A2563) else Color(0xFFEAF5FE), Modifier.weight(1f))
        if (!dark) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Color(0xFFE3EEF8)))
        HeroBenefit(Icons.Default.SupportAgent, tr(StrPay.benefitSupportShort), if (dark) Color(0xFF60A5FA) else Pay.blue, if (dark) Color(0xFF123D7A) else Color(0xFFEAF5FE), Modifier.weight(1f))
    }
}

/** One reassurance: an icon in a soft circle over a two-or-three-word label. */
@Composable
private fun HeroBenefit(icon: ImageVector, text: String, accent: Color, circle: Color, modifier: Modifier = Modifier) {
    val dark = Pay.isDark
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF0A2650) else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            color = Pay.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Four-step progress for the transfer flow. Seeing how close they are to done is what carries a
 * payer through the last, most tedious step (the screenshot) instead of abandoning it.
 */
@Composable
private fun CheckoutProgress(step: Int) {
    val dark = Pay.isDark
    val labels = listOf(StrPay.stepPick, StrPay.stepTransfer, StrPay.stepProof, StrPay.stepSend)
    val futureLine = if (dark) Color(0xFF1E3A63) else Color(0xFFBFE3F7)
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (dark) Color(0xFF05203F) else Color.White.copy(alpha = 0.5f))
            .then(if (dark) Modifier.border(1.dp, Color(0xFF15335C), RoundedCornerShape(22.dp)) else Modifier)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        labels.forEachIndexed { i, label ->
            val done = i < step
            val current = i == step
            val nodeColor by animateColorAsState(
                when {
                    done -> Pay.green
                    current -> Pay.blue
                    else -> if (dark) Color(0xFF1B3358) else Color(0xFFCDEBFB)
                },
                tween(300), label = "stepNode",
            )
            // Line segments either side of the node, expressed as (start side, end side) so the
            // green→blue hand-off reads correctly in Arabic and English alike.
            val clear = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            val lineBefore: Brush = when {
                i == 0 -> clear
                i < step -> seg(Pay.green, Pay.green, rtl)
                i == step -> seg(Pay.green, Pay.blue, rtl)
                else -> seg(futureLine, futureLine, rtl)
            }
            val lineAfter: Brush = when {
                i == labels.lastIndex -> clear
                i < step - 1 -> seg(Pay.green, Pay.green, rtl)
                i == step - 1 -> seg(Pay.green, Pay.blue, rtl)
                i == step -> seg(Pay.blue, futureLine, rtl)
                else -> seg(futureLine, futureLine, rtl)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(lineBefore))
                    Box(
                        Modifier
                            .size(36.dp)
                            .shadow(if (current) 8.dp else 0.dp, CircleShape, ambientColor = Pay.blue, spotColor = Pay.blue)
                            .clip(CircleShape)
                            .background(nodeColor)
                            .then(if (current) Modifier.border(2.dp, Color.White.copy(alpha = if (dark) 0.25f else 0.9f), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(targetState = done, label = "stepIcon") { isDone ->
                            if (isDone) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    "${i + 1}",
                                    color = when {
                                        current -> Color.White
                                        dark -> Color(0xFF9BBEF2)
                                        else -> Pay.blue
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f).height(3.dp).background(lineAfter))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(label),
                    color = when {
                        current -> Pay.textPrimary
                        done -> Pay.blueText
                        else -> Pay.textMuted
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (current) FontWeight.Black else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A line segment coloured from its start side to its end side, whichever way the layout runs. */
private fun seg(startSide: Color, endSide: Color, rtl: Boolean): Brush =
    Brush.horizontalGradient(if (rtl) listOf(endSide, startSide) else listOf(startSide, endSide))

/** Numbered header for each step inside the transfer form. */
@Composable
private fun StepHeader(number: Int, text: AnnotatedString, done: Boolean, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (done) Pay.teal else accent),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = done, label = "stepHeaderIcon") { isDone ->
                if (isDone) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                } else {
                    Text("$number", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Pay.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryCard(summary: PaymentSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Pay.surface)
            .border(1.dp, Pay.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Pay.gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Pay.gold, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(StrPay.orderSummary), color = Pay.textMuted, style = MaterialTheme.typography.bodySmall)
                Text(summary.title, color = Pay.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(14.dp))
        AmountRow(tr(StrPay.listPrice), formatMoney(summary.listPrice, summary.currency), Pay.textSecondary)
        if (summary.discount > 0) {
            Spacer(Modifier.height(6.dp))
            AmountRow(tr(StrPay.discount), "− ${formatMoney(summary.discount, summary.currency)}", Pay.teal)
            Spacer(Modifier.height(8.dp))
            Text(
                trf(StrPay.youSave, formatMoney(summary.discount, summary.currency)),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Pay.teal.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Pay.teal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pay.border))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(StrPay.totalDue), color = Pay.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                formatMoney(summary.total, summary.currency),
                color = Pay.gold,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Pay.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MethodRow(
    option: PayOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val dark = Pay.isDark
    val look = lookOf(option.kind)
    val brand = option.brand
    val dim = if (option.enabled) 1f else 0.45f

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = rememberPressScale(pressed, target = 0.985f)
    val cardColor by animateColorAsState(
        when {
            selected && dark -> Color(0xFF0B2A57)
            selected -> Color.White
            dark -> Color(0xFF061D3D)
            else -> Color.White.copy(alpha = 0.75f)
        },
        tween(220), label = "methodBg",
    )
    val borderColor by animateColorAsState(
        when {
            selected -> Pay.blue
            dark -> Color(0xFF17345C)
            else -> Color(0xFFE0EEF8)
        },
        tween(220), label = "methodBorder",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .shadow(
                if (selected) 12.dp else 0.dp,
                RoundedCornerShape(20.dp),
                ambientColor = Pay.blue.copy(alpha = 0.45f),
                spotColor = Pay.blue.copy(alpha = 0.45f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = option.enabled, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .alpha(dim),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletLogo(brand, look?.icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                brand?.let { tr(it.label) } ?: look?.let { tr(it.name) } ?: option.label.orEmpty(),
                color = Pay.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    !option.enabled -> tr(StrPay.walletUnavailable)
                    option.isManual -> tr(StrPay.manualRowSub)
                    else -> look?.let { tr(it.sub) } ?: option.label.orEmpty()
                },
                color = if (option.enabled) Pay.blueText else Pay.danger,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        // Empty ring → filled blue check.
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) Pay.blue else Color.Transparent)
                .border(2.dp, if (selected) Pay.blue else if (dark) Color(0xFF3A5A86) else Color(0xFF9DB8D8), CircleShape)
                .alpha(if (option.enabled) 1f else 0f),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/** The wallet's own logo tile (Vodafone, Etisalat), or its colour with a wallet glyph. */
@Composable
private fun WalletLogo(brand: WalletBrand?, fallback: ImageVector?) {
    val shape = RoundedCornerShape(14.dp)
    val logo = when (brand) {
        WalletBrand.VODAFONE -> com.rork.pro.R.drawable.pay_logo_vodafone
        WalletBrand.ETISALAT -> com.rork.pro.R.drawable.pay_logo_etisalat
        else -> null
    }
    if (logo != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(logo),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(50.dp).clip(shape),
        )
    } else {
        Box(
            Modifier.size(50.dp).clip(shape).background(brand?.brandColor ?: Pay.blue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (brand != null) Icons.Default.AccountBalanceWallet else (fallback ?: Icons.Default.Payments),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * Asks for the mobile number a wallet payment will be billed to.
 *
 * Only digits are accepted and the length is capped, so the number cannot be mistyped into a
 * shape the gateway would simply reject.
 */
@Composable
private fun WalletNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    showError: Boolean,
    /** Operator recognised so far — from the third digit onward, or from the chosen integration. */
    brand: WalletBrand? = null,
    /** Overrides the generic "wrong shape" message when the real problem is a different one. */
    errorText: String? = null,
) {
    // The field takes the operator's own colour the moment it can be told which one it is,
    // so the payer gets confirmation they typed their own wallet before finishing the number.
    val accent = brand?.brandColor ?: Pay.gold
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(20.dp))
            .background(Pay.surfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(tr(StrPay.walletNumber), color = Pay.textMuted) },
            placeholder = { Text(tr(StrPay.walletNumberHint), color = Pay.textMuted) },
            singleLine = true,
            isError = showError,
            leadingIcon = {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = if (showError) Pay.danger else accent,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = brand?.let { detected ->
                {
                    Text(
                        tr(detected.label),
                        color = detected.brandColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = if (brand != null) accent.copy(alpha = 0.5f) else Pay.border,
                errorBorderColor = Pay.danger,
                cursorColor = accent,
                focusedTextColor = Pay.textPrimary,
                unfocusedTextColor = Pay.textPrimary,
                focusedContainerColor = Pay.tint.copy(alpha = 0.03f),
                unfocusedContainerColor = Pay.tint.copy(alpha = 0.03f),
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            errorText ?: tr(if (showError) StrPay.walletNumberInvalid else StrPay.walletNumberHelp),
            color = if (showError) Pay.danger else Pay.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Waits while a mobile-wallet payment is approved on the payer's own phone.
 *
 * Nothing here can unlock a course: the screen simply watches the order until the server, and
 * only the server, reports it paid. If the request is never approved it ends as a clean failure
 * rather than an endless spinner.
 */
@Composable
fun WalletApproval(
    orderId: String,
    phone: String,
    walletUrl: String?,
    onDone: (Boolean) -> Unit,
) {
    var status by remember { mutableStateOf("PENDING") }
    var expired by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) return@LaunchedEffect
        // Wallet approvals are short lived; five minutes is well past the operator's own window.
        repeat(100) {
            delay(3000)
            val order = runCatching { CommerceRepository.order(orderId) }.getOrNull()
            status = order?.status ?: status
            if (status != "PENDING") return@LaunchedEffect
        }
        expired = true
    }

    Dialog(
        onDismissRequest = { onDone(status == "PAID") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .payBackground()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CheckoutHeader(onDismiss = { onDone(status == "PAID") })

            Box(Modifier.weight(1f)) {
                when {
                    status == "PAID" -> ResultPanel(success = true, onPrimary = { onDone(true) })

                    status != "PENDING" -> ResultPanel(success = false, onPrimary = { onDone(false) })

                    expired -> ResultPanel(
                        success = false,
                        onPrimary = { onDone(false) },
                        title = StrPay.walletTimedOut,
                        body = StrPay.walletTimedOutBody,
                    )

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.screenPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // A soft breathing ring behind the icon says "waiting" the instant the eye
                        // lands on it, before it even reaches the spinner and copy below.
                        val ring = rememberInfiniteTransition(label = "walletRing")
                        val ringScale by ring.animateFloat(
                            1f, 1.35f,
                            infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
                            label = "walletRingScale",
                        )
                        val ringAlpha by ring.animateFloat(
                            0.35f, 0f,
                            infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
                            label = "walletRingAlpha",
                        )
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(88.dp)
                                    .scale(ringScale)
                                    .alpha(ringAlpha)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Pay.gold),
                            )
                            Box(
                                Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Pay.gold.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Pay.gold,
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            tr(StrPay.walletApprovalTitle),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Pay.textPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            trf(StrPay.walletApprovalBody, phone),
                            color = Pay.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(22.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Pay.gold.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Pay.gold)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                tr(StrPay.walletWaiting),
                                color = Pay.gold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            tr(StrPay.walletKeepOpen),
                            color = Pay.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        if (!walletUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(18.dp))
                            CheckoutSecondaryButton(tr(StrPay.walletOpenPage)) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, walletUrl.toUri()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The gateway's own payment page, hosted inside 7PRO.
 *
 * Payment is only ever confirmed by the server: the page is watched purely for progress, while
 * the order itself is polled until the verified webhook marks it paid. That keeps a page that
 * merely *looks* successful from unlocking anything.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HostedCheckout(
    url: String,
    orderId: String,
    onDone: (Boolean) -> Unit,
) {
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("PENDING") }
    val context = LocalContext.current

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) return@LaunchedEffect
        while (status == "PENDING") {
            delay(3000)
            val order = runCatching { CommerceRepository.order(orderId) }.getOrNull()
            status = order?.status ?: status
        }
    }

    Dialog(
        onDismissRequest = { onDone(status == "PAID") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .payBackground()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Same header every other screen in the flow uses — the gateway's own WebView page
            // used to break back out into the app's ordinary light/dark chrome right in the
            // middle of checkout; now it stays inside the same dark, considered surface.
            CheckoutHeader(onDismiss = { onDone(status == "PAID") }, subtitle = tr(StrPay.doNotClose))

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Pay.gold,
                    trackColor = Pay.border,
                )
            }

            Box(Modifier.weight(1f)) {
                when (status) {
                    "PAID" -> ResultPanel(
                        success = true,
                        onPrimary = { onDone(true) },
                    )

                    "PENDING" -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                }
                                with(settings) {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                loadUrl(url)
                            }
                        },
                        onRelease = { view ->
                            view.loadUrl("about:blank")
                            view.destroy()
                        },
                    )

                    else -> ResultPanel(success = false, onPrimary = { onDone(false) })
                }
            }

            if (status == "PENDING") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Pay.gold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr(StrPay.waitingConfirmation),
                        color = Pay.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr(StrPay.openInBrowser),
                        color = Pay.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The closing screen of the checkout journey — paid or not. Rendered inside the same dark [Pay]
 * canvas as every other step (see [CheckoutHeader]'s doc), rather than the app's ordinary
 * light/dark [Ink] chrome: this used to be the one screen in the flow that broke back out into
 * whatever appearance the device was in, which on a light device meant near-black text landing on
 * this screen's dark background — unreadable, not just inconsistent.
 *
 * The icon badge pops in once, on arrival — a single deliberate moment for the one thing this
 * screen exists to say, rather than a decoration.
 */
@Composable
private fun ResultPanel(
    success: Boolean,
    onPrimary: () -> Unit,
    title: Tr? = null,
    body: Tr? = null,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val badgeScale by animateFloatAsState(
        if (entered) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "resultBadgeScale",
    )
    val badgeAlpha by animateFloatAsState(if (entered) 1f else 0f, animationSpec = tween(250), label = "resultBadgeAlpha")
    val accent = if (success) Pay.teal else Pay.danger

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .scale(badgeScale)
                .alpha(badgeAlpha)
                .clip(RoundedCornerShape(28.dp))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            tr(title ?: if (success) StrPay.paymentConfirmed else StrPay.paymentFailed),
            style = MaterialTheme.typography.headlineSmall,
            color = Pay.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(body ?: if (success) StrPay.accessUnlocked else StrPay.paymentFailedBody),
            color = Pay.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        if (success) {
            CheckoutPayButton(
                label = tr(StrPay.startLearning),
                brandColor = Pay.teal,
                enabled = true,
                loading = false,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = onPrimary,
            )
        } else {
            CheckoutSecondaryButton(tr(StrPay.tryAgain), onClick = onPrimary)
        }
    }
}

private sealed interface CouponStatus {
    data object Idle : CouponStatus
    data object Loading : CouponStatus
    data object Applied : CouponStatus
    data object Invalid : CouponStatus
}

@Composable
private fun CouponRow(
    code: String,
    onCodeChange: (String) -> Unit,
    status: CouponStatus,
    loading: Boolean,
    onApply: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = { Text(tr(StrPay.couponHint), color = Pay.textMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) onApply() }),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Pay.gold,
                unfocusedBorderColor = Pay.border,
                cursorColor = Pay.gold,
                focusedTextColor = Pay.textPrimary,
                unfocusedTextColor = Pay.textPrimary,
                focusedContainerColor = Pay.tint.copy(alpha = 0.03f),
                unfocusedContainerColor = Pay.tint.copy(alpha = 0.03f),
            ),
        )
        val applyInteraction = remember { MutableInteractionSource() }
        val applyPressed by applyInteraction.collectIsPressedAsState()
        Box(
            Modifier
                .height(54.dp)
                .scale(rememberPressScale(applyPressed))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (code.isNotBlank() && !loading) Brush.verticalGradient(listOf(Pay.gold, Pay.goldDeep))
                    else Brush.verticalGradient(listOf(Pay.tint.copy(alpha = 0.06f), Pay.tint.copy(alpha = 0.06f))),
                )
                .clickable(
                    interactionSource = applyInteraction,
                    indication = null,
                    enabled = code.isNotBlank() && !loading,
                    onClick = onApply,
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status is CouponStatus.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Pay.onGold,
                )
            } else {
                Text(
                    tr(StrPay.couponApply),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (code.isNotBlank()) Pay.onGold else Pay.textMuted,
                )
            }
        }
    }
    when (status) {
        is CouponStatus.Applied -> {
            Spacer(Modifier.height(4.dp))
            Text(tr(StrPay.couponApplied), color = Pay.teal, style = MaterialTheme.typography.bodySmall)
        }
        is CouponStatus.Invalid -> {
            Spacer(Modifier.height(4.dp))
            Text(tr(StrPay.couponInvalid), color = Pay.danger, style = MaterialTheme.typography.bodySmall)
        }
        else -> Unit
    }
}
