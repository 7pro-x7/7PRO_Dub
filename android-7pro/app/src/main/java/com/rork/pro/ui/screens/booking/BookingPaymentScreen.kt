package com.rork.pro.ui.screens.booking

import com.rork.pro.ui.i18n.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rork.pro.data.GroupBookingRepository
import com.rork.pro.data.ManualPaymentAccount
import com.rork.pro.data.BookingPaymentState
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.StrPay
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.payment.ManualTransferPanel
import com.rork.pro.ui.screens.payment.WalletBrand
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * The payment step of a booking, and the same screen a renewal goes through.
 *
 * It is deliberately the platform's existing wallet page rather than a new one: the same
 * wallets the owner published, the same transfer form, the same proof upload. Two things
 * differ from a course purchase, and both are on purpose:
 *
 *  - the amount is not quoted here, it is read from the seat the server already created at
 *    the owner's price, so what the student pays and what the teacher is later credited are
 *    the same number by construction;
 *  - it never touches the orders/gateway path. A group subscription is credited by
 *    _on_subscription_approved when the owner approves it, so charging it as an order too
 *    would pay the teacher twice for one seat.
 */
@Composable
fun BookingPaymentScreen(navController: NavHostController, subscriptionIds: List<String>) {
    // One transfer can cover several children (a booking for 2–5 students, or several renewals
    // that fell due together); what is actually owed comes from the server, per child.
    var state by remember { mutableStateOf<BookingPaymentState?>(null) }
    var wallets by remember { mutableStateOf<List<ManualPaymentAccount>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var selected by remember { mutableStateOf<ManualPaymentAccount?>(null) }
    var senderPhone by remember { mutableStateOf("") }
    var senderTouched by remember { mutableStateOf(false) }
    var proofPath by remember { mutableStateOf<String?>(null) }
    var proofName by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var sent by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(subscriptionIds) {
        loading = true
        state = runCatching { GroupBookingRepository.paymentState(subscriptionIds) }.getOrNull()
        wallets = runCatching { GroupBookingRepository.wallets() }.getOrDefault(emptyList())
        selected = wallets.firstOrNull()
        loading = false
    }

    val current = state
    val brand = selected?.let { account ->
        WalletBrand.entries.firstOrNull { it.name.equals(account.brand, ignoreCase = true) }
    }
    val accent = brand?.brandColor ?: Ink.Amber
    val amountLabel = current?.let { formatMoney(it.total, it.currency) }.orEmpty()

    val phoneOk = Regex("^01[0125]\\d{8}$").matches(senderPhone.filter(Char::isDigit))
    val canSubmit = selected != null && phoneOk && proofPath != null && !uploading

    BookingScaffold(
        title = tr(StrBooking.payTitle),
        step = 4,
        onBack = { navController.popBackStack() },
    ) {
        when {
            loading -> LoadingBlock(Modifier.fillMaxSize())

            sent -> BookingSubmittedPanel(
                teacherName = current?.teacherName.orEmpty(),
                groupName = current?.groupName.orEmpty(),
            ) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            }

            // The server is the only judge of whether anything is owed. If it says nothing
            // is, the screen says so too rather than collecting a transfer nobody asked for.
            current == null || !current.needsPayment ->
                BookingUnavailable(tr(StrBooking.errNothingToPay)) { navController.popBackStack() }

            wallets.isEmpty() ->
                BookingUnavailable(tr(StrBooking.walletsUnavailable)) { navController.popBackStack() }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenPadding),
                ) {
                    BookingNote(
                        String.format(
                            tr(if (current.isRenewal) StrBooking.payForRenewal else StrBooking.payForSeat),
                            current.teacherName,
                            current.groupName,
                        ),
                        accent = Ink.Teal,
                    )
                    if (current.count > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            trf(StrBooking.studentsLine, current.items.joinToString("، ") { it.studentName }),
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    AmountCard(amountLabel)

                    Spacer(Modifier.height(18.dp))
                    Text(
                        tr(StrBooking.chooseWallet),
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))

                    wallets.forEach { account ->
                        val rowBrand = WalletBrand.entries
                            .firstOrNull { it.name.equals(account.brand, ignoreCase = true) }
                        WalletRow(
                            account = account,
                            brand = rowBrand,
                            selected = selected?.brand == account.brand,
                        ) {
                            // Switching wallets clears the form: a screenshot must never end
                            // up filed against a wallet the money did not go to.
                            selected = account
                            senderPhone = ""
                            senderTouched = false
                            proofPath = null
                            proofName = ""
                            uploadError = null
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    selected?.let { account ->
                        Spacer(Modifier.height(6.dp))
                        ManualTransferPanel(
                            account = account,
                            brand = brand,
                            amountLabel = amountLabel,
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

                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr(StrBooking.afterPaymentNote),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    failure?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            it.message.orEmpty().ifBlank { tr(StrPay.paymentFailed) },
                            color = Ink.Coral,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenPadding, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    PrimaryAction(
                        tr(StrBooking.sendProof),
                        enabled = canSubmit,
                        loading = submitting,
                        containerColor = accent,
                        contentColor = Ink.OnAmber,
                    ) {
                        val account = selected ?: return@PrimaryAction
                        val path = proofPath ?: return@PrimaryAction
                        submitting = true
                        failure = null
                        scope.launch {
                            runCatching {
                                GroupBookingRepository.submitPayments(
                                    subscriptionIds = current.items.map { it.subscriptionId },
                                    brand = account.brand,
                                    senderPhone = senderPhone,
                                    proofPath = path,
                                    note = note,
                                )
                            }
                                .onSuccess { sent = true }
                                .onFailure { failure = it }
                            submitting = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountCard(amountLabel: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.AmberSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(StrBooking.amountDue),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            amountLabel,
            color = Ink.Amber,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WalletRow(
    account: ManualPaymentAccount,
    brand: WalletBrand?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = brand?.brandColor ?: Ink.Amber
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else Ink.Surface)
            .border(1.dp, if (selected) accent else Ink.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                brand?.let { tr(it.label) } ?: account.brand,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(account.phone, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
