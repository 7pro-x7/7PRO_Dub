package com.rork.pro.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rork.pro.data.GroupBookingRepository
import com.rork.pro.data.SubscriptionState
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.HomeInk
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * The home banner for the group-subscription funnel.
 *
 * Its state is never decided here. One server call returns which state this student is in —
 * computed from their real subscription, its open approval request and the transfer attached
 * to it — and this composable only paints it. That is the whole point: a local "subscribed"
 * flag would eventually disagree with the backend, and the banner is exactly where such a
 * disagreement would be most visible and most damaging.
 *
 * "جدد اشتراكك الآن" therefore appears only when the server says the renewal window is open,
 * never on a date the app worked out for itself.
 */
@Composable
fun SubscriptionHomeBanner(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    // Optional full-width card drawn above the banner row (the AI tutor).
    tutor: (@Composable (Modifier) -> Unit)? = null,
    // Optional second cell drawn beside the banner (teacher exercises). When the server has no
    // banner to show for this student it simply takes the full row.
    side: (@Composable (Modifier) -> Unit)? = null,
    // Optional painter for the banner cell. Home passes its own so the tile matches its design;
    // when null the standard [MiniBannerCard] is used.
    card: (@Composable (BannerSpec, Modifier) -> Unit)? = null,
) {
    var state by remember { mutableStateOf<SubscriptionState?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        state = runCatching { GroupBookingRepository.state() }.getOrNull()
    }

    LaunchedEffect(Unit) { refresh() }

    val s = state
    // One compact spec per server state. Which state applies is still decided only by the
    // server; this only decides how it is painted.
    val spec: BannerSpec? = if (s == null) null else when (s.state) {
        "NONE" -> BannerSpec(
            kind = BannerKind.BRAND,
            icon = Icons.Default.Groups,
            title = tr(StrBooking.bannerSubscribeTitle),
            body = tr(StrBooking.tileSubscribeSub),
            accent = HomeInk.Brand,
            featured = true,
            onClick = { navController.navigate(Routes.BOOKING_TEACHERS) },
        )
        "AWAITING_PAYMENT" -> BannerSpec(
            kind = BannerKind.BRAND,
            icon = Icons.Default.Payments,
            title = tr(StrBooking.bannerAwaitingPaymentTitle),
            body = trf(StrBooking.bannerAwaitingPaymentBody, s.groupName) + studentsSuffix(s),
            accent = HomeInk.Brand,
            featured = true,
            onClick = { if (s.actionIds.isNotEmpty()) navController.navigate(Routes.bookingPay(s.actionIds)) },
        )
        "PENDING_REVIEW" -> BannerSpec(
            kind = BannerKind.INFO,
            icon = Icons.Default.HourglassTop,
            title = tr(StrBooking.bannerPendingTitle),
            body = tr(StrBooking.bannerPendingBody),
            accent = HomeInk.Sky,
        )
        "ACTIVE" -> BannerSpec(
            kind = BannerKind.SUCCESS,
            icon = Icons.Default.CheckCircle,
            title = tr(StrBooking.bannerActiveTitle),
            body = String.format(
                tr(StrBooking.bannerActiveBody),
                s.groupName,
                s.teacherName,
                s.nextRenewalDate.orEmpty(),
            ),
            accent = HomeInk.Green,
        )
        "RENEWAL_DUE" -> BannerSpec(
            kind = BannerKind.BRAND,
            icon = Icons.Default.NotificationsActive,
            title = tr(StrBooking.bannerRenewTitle),
            body = trf(StrBooking.bannerRenewBody, s.groupName) + studentsSuffix(s),
            accent = HomeInk.Brand,
            featured = true,
            busy = working,
            onClick = {
                val ids = s.actionIds
                if (ids.isNotEmpty() && !working) {
                    working = true
                    scope.launch {
                        // Opening the renewal request and paying for it are one tap to the
                        // student but two server steps — the first is safe to repeat, so a
                        // double tap cannot leave two renewals behind. Every child that fell due
                        // together gets its request, and they are paid with one transfer.
                        val opened = ids.filter { id -> runCatching { GroupBookingRepository.requestRenewal(id) }.isSuccess }
                        if (opened.isNotEmpty()) navController.navigate(Routes.bookingPay(opened))
                        working = false
                        refresh()
                    }
                }
            },
        )
        "RENEWAL_AWAITING_PAYMENT" -> BannerSpec(
            kind = BannerKind.BRAND,
            icon = Icons.Default.Payments,
            title = tr(StrBooking.bannerRenewPayTitle),
            body = trf(StrBooking.bannerRenewBody, s.groupName),
            accent = HomeInk.Brand,
            featured = true,
            onClick = { if (s.actionIds.isNotEmpty()) navController.navigate(Routes.bookingPay(s.actionIds)) },
        )
        "RENEWAL_PENDING_REVIEW" -> BannerSpec(
            kind = BannerKind.INFO,
            icon = Icons.Default.HourglassTop,
            title = tr(StrBooking.bannerRenewPendingTitle),
            body = tr(StrBooking.bannerPendingBody),
            accent = HomeInk.Sky,
        )
        "REJECTED" -> BannerSpec(
            kind = BannerKind.DANGER,
            icon = Icons.Default.Groups,
            title = tr(StrBooking.bannerRejectedTitle),
            body = s.reviewNote?.takeIf { it.isNotBlank() } ?: tr(StrBooking.bannerRejectedBody),
            accent = HomeInk.Danger,
            onClick = { navController.navigate(Routes.BOOKING_TEACHERS) },
        )
        else -> null
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tutor?.invoke(Modifier)

        if (spec != null || side != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (spec != null && side != null) {
                    val m = Modifier.weight(1f).fillMaxHeight()
                    if (card != null) card(spec, m) else MiniBannerCard(spec, m)
                    side(Modifier.weight(1f).fillMaxHeight())
                } else if (spec != null) {
                    if (card != null) card(spec, Modifier.fillMaxWidth()) else MiniBannerCard(spec, Modifier.fillMaxWidth())
                } else {
                    side?.invoke(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** " · 3 students" when one tap handles several children of the same group. */
private fun studentsSuffix(s: SubscriptionState): String =
    if (s.actionIds.size > 1) " · " + trf(StrBooking.studentsCountSuffix, s.actionIds.size) else ""

/** Which family of colour a banner state belongs to, so a custom painter can pick its own. */
enum class BannerKind { BRAND, INFO, SUCCESS, DANGER }

/** What one state of the subscription banner shows. See [MiniBannerCard]. */
class BannerSpec(
    val kind: BannerKind,
    val icon: ImageVector,
    val title: String,
    val body: String,
    val accent: Color,
    val featured: Boolean = false,
    val busy: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * The compact banner: a solid accent icon tile and a chevron on top, the title and one short
 * line under them, on a soft tint of the state's accent. Half the width of the screen so the
 * teacher-exercises tile can sit beside it.
 */
@Composable
private fun MiniBannerCard(spec: BannerSpec, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val clickable = spec.onClick != null && !spec.busy
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(spec.accent.copy(alpha = if (spec.featured) 0.18f else 0.12f))
            .border(1.dp, spec.accent.copy(alpha = 0.40f), shape)
            .then(if (clickable) Modifier.clickable { spec.onClick?.invoke() } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(spec.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spec.icon, contentDescription = null, tint = HomeInk.OnAccent, modifier = Modifier.size(20.dp))
            }
            if (spec.busy) {
                CircularProgressIndicator(color = spec.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else if (spec.onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = spec.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Column {
            Text(
                spec.title,
                color = HomeInk.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                spec.body,
                color = HomeInk.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The "اشترك في أقرب جروب" entry point — the one tap that starts the whole funnel for a
 * student with no subscription yet, so it is deliberately louder than every other banner
 * state: a solid [Ink.CtaGreen] fill instead of a wash, and a trailing arrow bubble that
 * breathes with a slow glow to keep the eye coming back to it without being a distraction.
 */
/**
 * One state of the subscription banner: an icon, a title, a short body and — when [onClick] is
 * non-null — a chevron that says the whole card is tappable. [featured] adds a soft [accent]
 * gradient for the states that need the student's attention; [working] swaps the chevron for a
 * spinner and blocks taps while a server call is in flight.
 */
@Composable
private fun BannerCard(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    working: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val clickable = onClick != null && !working

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(
                if (featured) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.06f)))
                else com.rork.pro.ui.theme.surfaceSheenBrush(),
            )
            .border(1.dp, accent.copy(alpha = if (featured) 0.45f else 0.28f), shape)
            .then(if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (working) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SubscribeBanner(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val infinite = rememberInfiniteTransition(label = "subscribeGlow")
    val glow by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrowScale",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.CtaGreen)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Ink.OnCtaGreen.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = Ink.OnCtaGreen, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(StrBooking.bannerSubscribeTitle),
                color = Ink.OnCtaGreen,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tr(StrBooking.bannerSubscribeBody),
                color = Ink.OnCtaGreen.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.OnCtaGreen.copy(alpha = 0.18f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Ink.OnCtaGreen, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    tr(StrBooking.bannerSubscribeHint),
                    color = Ink.OnCtaGreen,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(38.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Ink.OnCtaGreen.copy(alpha = glow)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Ink.CtaGreen,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The subscribe entry point as a half-width tile, drawn next to the tutor tile. The brand amber
 * (the 7PRO colour) makes it the one filled tile on the screen; the tutor tile beside it stays a
 * quiet surface tile, so the pair reads as "main action + companion" instead of two colours
 * shouting at each other.
 */
@Composable
private fun SubscribeTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    HomeActionTile(
        modifier = modifier,
        icon = Icons.Default.Groups,
        title = tr(StrBooking.bannerSubscribeTitle),
        subtitle = tr(StrBooking.tileSubscribeSub),
        accent = Ink.Amber,
        onAccent = Ink.OnAmber,
        solid = true,
        onClick = onClick,
    )
}

/**
 * One of the home screen's two side-by-side entry points (talk to Mr. Adam / join the nearest
 * group). The whole tile is the button: a rounded-square icon at the top, the title, a one-line
 * subtitle, and a small chevron in the bottom corner. [solid] fills it with [accent] (text in
 * [onAccent]); otherwise it is a surface tile with a faint [accent] edge.
 *
 * [pulse] is kept for source compatibility and no longer animates anything: a tile that breathes
 * forever competes with everything else on the screen.
 */
@Composable
fun HomeActionTile(
    icon: ImageVector,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onAccent: Color = Ink.OnCtaGreen,
    solid: Boolean = false,
    @Suppress("UNUSED_PARAMETER") pulse: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val fg = if (solid) onAccent else Ink.TextPrimary
    val sub = if (solid) onAccent.copy(alpha = 0.78f) else Ink.TextSecondary

    Column(
        modifier
            .heightIn(min = 152.dp)
            .shadow(
                elevation = if (solid) 10.dp else 4.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = accent.copy(alpha = 0.35f),
            )
            .clip(shape)
            .then(
                if (solid) Modifier.background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.82f))))
                else Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.06f)))),
            )
            .border(1.dp, if (solid) accent.copy(alpha = 0.6f) else accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (solid) onAccent.copy(alpha = 0.16f) else accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (solid) onAccent else accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        Text(
            title,
            color = fg,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = MaterialTheme.typography.titleMedium.fontSize * 1.15,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                subtitle.orEmpty(),
                color = sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (solid) onAccent.copy(alpha = 0.16f) else accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (solid) onAccent else accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
