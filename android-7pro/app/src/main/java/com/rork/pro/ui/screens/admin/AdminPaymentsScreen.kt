package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.ManualPaymentAccount
import com.rork.pro.data.ManualPaymentRequest
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.SubscriptionPaymentRequest
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatDateTime
import com.rork.pro.ui.i18n.StrApprovalX
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.screens.payment.WalletBrand
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink

/** Everything the payments console needs, read in one pass. */
data class PaymentsAdminData(
    val paymobEnabled: Boolean,
    val accounts: List<ManualPaymentAccount>,
    val pending: List<ManualPaymentRequest>,
    /** Group-seat and renewal transfers, which unlock a subscription rather than a course. */
    val subscriptionPending: List<SubscriptionPaymentRequest>,
)

class PaymentsVm : AdminListViewModel<PaymentsAdminData>({
    val settings = runCatching { AdminRepository.settings() }.getOrDefault(emptyList())
    val paymobEnabled = settings.firstOrNull { it.key == "payments.paymob_enabled" }
        ?.value?.toString()?.trim('"')
        ?.let { !it.equals("false", true) } ?: true
    PaymentsAdminData(
        paymobEnabled = paymobEnabled,
        accounts = AdminRepository.manualPaymentAccounts(),
        pending = runCatching { AdminRepository.manualPaymentRequests("PENDING") }.getOrDefault(emptyList()),
        subscriptionPending = runCatching {
            AdminRepository.subscriptionPaymentRequests("PENDING")
        }.getOrDefault(emptyList()),
    )
})

/**
 * The wallets 7PRO knows how to brand, in the order they are shown to a learner.
 *
 * A wallet the owner has not filled in yet still gets a card here — that is the only place it
 * can be given a number — while the payment sheet keeps it hidden until it is really payable.
 */
private val WALLET_ORDER = listOf("VODAFONE", "ORANGE", "ETISALAT", "WE")

private fun brandOf(code: String): WalletBrand? =
    WalletBrand.entries.firstOrNull { it.name == code.uppercase() }

/**
 * Owner / admin payments console.
 *
 * Three things live here, in the order they matter: whether Paymob is charging at all, the
 * numbers learners transfer to when it is not, and the transfers waiting to be confirmed.
 */
@Composable
fun AdminPaymentsScreen(navController: NavHostController) {
    val vm: PaymentsVm = viewModel()
    // Both queues open the same viewer, keyed by the storage path rather than by which
    // table the request came from.
    var proof by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("QUEUE") }

    proof?.let { (id, path, senderPhone) -> ProofViewer(id, path, senderPhone) { proof = null } }

    AdminScaffold(tr(StrAdmin.payments), navController, vm) { data ->
        // The queue is what blocks learners, so it opens first; the gateway switch and the
        // published numbers change rarely and live on their own tab.
        item {
            AdminTabs(
                listOf(
                    "QUEUE" to tr(StrAdmin.reviewQueueTitle),
                    "SETUP" to tr(StrAdmin.transferNumbersTitle),
                ),
                tab,
                { tab = it },
                counts = mapOf("QUEUE" to data.pending.size + data.subscriptionPending.size),
            )
        }

        if (tab == "SETUP") {
            item {
                InkCard(borderColor = if (data.paymobEnabled) null else Ink.Amber.copy(alpha = 0.4f)) {
                    Text(
                        tr(StrAdmin.paymobSwitchTitle),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(tr(StrAdmin.paymobEnabled), data.paymobEnabled) { value ->
                        vm.act { AdminRepository.saveSetting("payments.paymob_enabled", value.toString()) }
                    }
                    Text(
                        tr(StrAdmin.paymobEnabledHelp),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // With the gateway off and no number published, the payment sheet has nothing to
                    // offer at all — worth saying plainly rather than letting learners discover it.
                    if (!data.paymobEnabled && data.accounts.none { it.isUsable }) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr(StrAdmin.paymobOffWarning),
                            color = Ink.Coral,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                InkCard {
                    Text(
                        tr(StrAdmin.transferNumbersTitle),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(StrAdmin.transferNumbersHelp),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val ordered = WALLET_ORDER.mapNotNull { code ->
                data.accounts.firstOrNull { it.brand.equals(code, true) }
            } + data.accounts.filter { account -> WALLET_ORDER.none { it.equals(account.brand, true) } }

            items(ordered, key = { it.brand }) { account ->
                WalletAccountCard(account) { phone, holder, instructions, enabled ->
                    vm.act {
                        AdminRepository.saveManualPaymentAccount(
                            account.brand,
                            phone,
                            holder,
                            instructions,
                            enabled,
                        )
                    }
                }
            }
        } else {
            item { AdminSectionLabel(tr(StrAdmin.reviewQueueTitle), data.pending.size) }
            if (data.pending.isEmpty()) {
                item { AdminEmpty(tr(StrAdmin.reviewQueueEmpty), tr(StrAdmin.reviewQueueEmptyBody)) }
            }
            items(data.pending, key = { it.id }) { request ->
                ReviewCard(
                    request = request,
                    onViewProof = { proof = Triple(request.id, request.proofPath, request.senderPhone) },
                    onDecide = { approve, note ->
                        vm.act { AdminRepository.reviewManualPayment(request.id, approve, note) }
                    },
                )
            }

            // Group subscriptions are a separate queue on purpose: approving one seats a student
            // with a teacher and records a subscription earning — a different money path.
            item {
                AdminSectionLabel(tr(StrBooking.adminSubTransfers), data.subscriptionPending.size)
                Spacer(Modifier.height(4.dp))
                AdminNote(tr(StrBooking.adminApproveHint))
            }
            if (data.subscriptionPending.isEmpty()) {
                item { AdminEmpty(tr(StrBooking.adminSubTransfersEmpty), tr(StrAdmin.reviewQueueEmptyBody)) }
            }
            items(data.subscriptionPending, key = { it.id }) { request ->
                SubscriptionReviewCard(
                    request = request,
                    onViewProof = { proof = Triple(request.id, request.proofPath, request.senderPhone) },
                    onDecide = { approve, note ->
                        vm.act { AdminRepository.reviewSubscriptionPayment(request.id, approve, note) }
                    },
                )
            }
        }
    }
}

/**
 * One group-subscription transfer awaiting a decision.
 *
 * Approving here does not credit anything directly: it runs the same approval the teacher's
 * own hand-entered students go through, which is what seats the student in the group and
 * records the earning. So the card shows exactly what that approval will act on — who, which
 * group, which teacher, and for how much.
 */
@Composable
private fun SubscriptionReviewCard(
    request: SubscriptionPaymentRequest,
    onViewProof: () -> Unit,
    onDecide: (approve: Boolean, note: String) -> Unit,
) {
    val brand = brandOf(request.brand)
    val accent = brand?.brandColor ?: Ink.Amber
    val clipboard = LocalClipboardManager.current
    var note by remember(request.id) { mutableStateOf("") }
    var confirming by remember(request.id) { mutableStateOf(false) }

    InkCard(borderColor = Ink.Teal.copy(alpha = 0.3f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    request.student?.fullName ?: "—",
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${request.groupName} · ${request.teacher?.fullName.orEmpty()}",
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            // A plain Pill rather than StatusPill: this badge carries what kind of request
            // this is, not a status, and StatusPill would try to translate it as one.
            Pill(
                tr(if (request.isRenewal) StrBooking.adminKindRenewal else StrBooking.adminKindNew),
                background = if (request.isRenewal) Ink.SkySoft else Ink.TealSoft,
                foreground = if (request.isRenewal) Ink.Sky else Ink.Teal,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatMoney(request.amount, request.currency),
                color = Ink.Amber,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                brand?.let { tr(it.label) } ?: request.brand,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(tr(StrAdmin.reviewSender), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(request.senderPhone, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(request.senderPhone)) }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Ink.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        request.createdAt?.let { at ->
            Text(
                "${tr(StrApprovalX.transferredAt)}: ${formatDateTime(at)}",
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        request.note?.takeIf { it.isNotBlank() }?.let { studentNote ->
            Spacer(Modifier.height(6.dp))
            Text(studentNote, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        PaymentProofPreview(
            proofId = request.id,
            proofPath = request.proofPath,
            senderPhone = request.senderPhone,
            whenText = request.createdAt?.let { formatDateTime(it) },
        )

        Spacer(Modifier.height(10.dp))
        InkField(note, { note = it }, tr(StrAdmin.reviewNoteField), singleLine = false, minLines = 2)

        Spacer(Modifier.height(10.dp))
        if (confirming) {
            Text(
                tr(StrBooking.adminApproveSeatConfirm),
                color = Ink.Amber,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminButton(
                tr(StrBooking.adminApproveSeat),
                Modifier.weight(1f),
                Icons.Default.Check,
                if (confirming) AdminTone.Primary else AdminTone.Success,
            ) {
                if (confirming) onDecide(true, note) else confirming = true
            }
            AdminButton(
                tr(StrAdmin.reviewReject),
                Modifier.weight(1f),
                Icons.Default.Close,
                AdminTone.Danger,
            ) { onDecide(false, note) }
        }
    }
}

/**
 * One wallet's published details, edited in place.
 *
 * Edits are staged locally and only sent on Save, so a half-typed number is never published to
 * learners mid-keystroke.
 */
@Composable
private fun WalletAccountCard(
    account: ManualPaymentAccount,
    onSave: (phone: String, holder: String, instructions: String, enabled: Boolean) -> Unit,
) {
    val brand = brandOf(account.brand)
    val accent = brand?.brandColor ?: Ink.Amber

    var phone by remember(account.brand, account.phone) { mutableStateOf(account.phone) }
    var holder by remember(account.brand) { mutableStateOf(account.holderName.orEmpty()) }
    var instructions by remember(account.brand) { mutableStateOf(account.instructions.orEmpty()) }
    var enabled by remember(account.brand, account.isEnabled) { mutableStateOf(account.isEnabled) }

    val dirty = phone != account.phone ||
        holder != account.holderName.orEmpty() ||
        instructions != account.instructions.orEmpty() ||
        enabled != account.isEnabled

    InkCard(borderColor = accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    brand?.let { tr(it.label) } ?: account.brand,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(if (account.isUsable) StrAdmin.transferShown else StrAdmin.transferNeedsNumber),
                    color = if (account.isUsable) Ink.Teal else Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        InkField(
            phone,
            { phone = it.filter(Char::isDigit).take(11) },
            tr(StrAdmin.transferNumberField),
            keyboardType = KeyboardType.Phone,
        )
        Spacer(Modifier.height(8.dp))
        InkField(holder, { holder = it }, tr(StrAdmin.transferHolderField))
        Spacer(Modifier.height(8.dp))
        InkField(
            instructions,
            { instructions = it },
            tr(StrAdmin.transferInstructionsField),
            singleLine = false,
            minLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        ToggleRow(tr(StrAdmin.transferShown), enabled) { enabled = it }

        Spacer(Modifier.height(10.dp))
        PrimaryAction(
            tr(StrAdmin.transferSave),
            enabled = dirty,
            containerColor = accent,
            contentColor = Color.White,
        ) {
            onSave(phone, holder, instructions, enabled)
        }
    }
}

/**
 * One transfer awaiting a decision.
 *
 * Approving is the moment access is granted and the teacher is credited, so the amount, the
 * sender's number and the screenshot are all on the card before either button is reachable.
 */
@Composable
private fun ReviewCard(
    request: ManualPaymentRequest,
    onViewProof: () -> Unit,
    onDecide: (approve: Boolean, note: String) -> Unit,
) {
    val brand = brandOf(request.brand)
    val accent = brand?.brandColor ?: Ink.Amber
    val clipboard = LocalClipboardManager.current
    var note by remember(request.id) { mutableStateOf("") }
    var confirming by remember(request.id) { mutableStateOf(false) }

    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    request.student?.fullName ?: "—",
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                request.course?.title?.let { title ->
                    Text(title, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            StatusPill(request.status)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatMoney(request.amount, request.currency),
                color = Ink.Amber,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                brand?.let { tr(it.label) } ?: request.brand,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))
        // The sender's number is what the owner matches against their own wallet history, so it
        // is one tap to copy rather than something to retype.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(tr(StrAdmin.reviewSender), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    request.senderPhone,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(request.senderPhone)) }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Ink.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        request.createdAt?.let { at ->
            Text(
                "${tr(StrApprovalX.transferredAt)}: ${formatDateTime(at)}",
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        request.note?.takeIf { it.isNotBlank() }?.let { learnerNote ->
            Spacer(Modifier.height(6.dp))
            Text(learnerNote, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        PaymentProofPreview(
            proofId = request.id,
            proofPath = request.proofPath,
            senderPhone = request.senderPhone,
            whenText = request.createdAt?.let { formatDateTime(it) },
        )

        Spacer(Modifier.height(10.dp))
        InkField(note, { note = it }, tr(StrAdmin.reviewNoteField), singleLine = false, minLines = 2)

        Spacer(Modifier.height(10.dp))
        if (confirming) {
            Text(
                tr(StrAdmin.reviewApproveConfirm),
                color = Ink.Amber,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminButton(
                tr(StrAdmin.reviewApprove),
                Modifier.weight(1f),
                Icons.Default.Check,
                if (confirming) AdminTone.Primary else AdminTone.Success,
            ) {
                if (confirming) onDecide(true, note) else confirming = true
            }
            AdminButton(
                tr(StrAdmin.reviewReject),
                Modifier.weight(1f),
                Icons.Default.Close,
                AdminTone.Danger,
            ) { onDecide(false, note) }
        }
    }
}

/**
 * The transfer screenshot, full screen.
 *
 * The proofs bucket is private, so the image is fetched through a short-lived signed link that
 * is created only when a reviewer actually opens it.
 */
@Composable
internal fun ProofViewer(
    proofId: String,
    proofPath: String,
    senderPhone: String,
    whenText: String? = null,
    onDismiss: () -> Unit,
) {
    var url by remember(proofId) { mutableStateOf<String?>(null) }
    var failed by remember(proofId) { mutableStateOf(false) }
    LaunchedEffect(proofId) {
        runCatching { MediaRepository.paymentProofUrl(proofPath) }
            .onSuccess { url = it }
            .onFailure { failed = true }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink.CanvasDeep)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Ink.TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${tr(StrAdmin.reviewSender)} · $senderPhone",
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    whenText?.let {
                        Text(it, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    failed -> Text(
                        tr(StrAdmin.reviewProofFailed),
                        color = Ink.Coral,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Dimens.screenPadding),
                    )

                    url == null -> CircularProgressIndicator(color = Ink.Amber, strokeWidth = 2.5.dp)

                    else -> AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.screenPadding)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Ink.Hairline, RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
    }
}
