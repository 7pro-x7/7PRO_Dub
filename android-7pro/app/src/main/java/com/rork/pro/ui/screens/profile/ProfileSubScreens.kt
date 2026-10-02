package com.rork.pro.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.data.AlertSoundPrefs
import com.rork.pro.data.AppError
import com.rork.pro.data.AppNotification
import com.rork.pro.data.Async
import com.rork.pro.data.Certificate
import com.rork.pro.data.CommerceRepository
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.Order
import com.rork.pro.data.SessionRepository
import com.rork.pro.data.SupportTicket
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.StrProfile
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.statusLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf

// ---------------------------------------------------------------- Orders

class OrdersViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<Order>>>(Async.Loading)
    val state: StateFlow<Async<List<Order>>> = _state.asStateFlow()

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
            runCatching { CommerceRepository.myOrders() }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

@Composable
fun OrdersScreen(navController: NavHostController) {
    val vm: OrdersViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrProfile.paymentsOrders), onBack = { navController.popBackStack() })
        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                if (s.value.isEmpty()) {
                EmptyBlock(tr(StrProfile.noPaymentsYet), tr(StrProfile.noPaymentsBody))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { AdBanner("ORDERS") }
                    items(s.value, key = { it.id }) { order ->
                        InkCard {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tr(StrProfile.coursePurchase),
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                StatusPill(order.status)
                            }
                            Spacer(Modifier.height(10.dp))
                            KeyValueRow(tr(StrProfile.amount), formatMoney(order.totalAmount, order.currency), valueColor = Ink.Amber)
                            if (order.discountAmount > 0) {
                                KeyValueRow(
                                    tr(StrProfile.discount),
                                    "−${formatMoney(order.discountAmount, order.currency)}",
                                    valueColor = Ink.Teal,
                                )
                            }
                            if (order.refundedAmount > 0) {
                                KeyValueRow(
                                    tr(StrProfile.refunded),
                                    formatMoney(order.refundedAmount, order.currency),
                                    valueColor = Ink.Coral,
                                )
                            }
                            KeyValueRow(tr(StrProfile.country), order.countryCode)
                            KeyValueRow(tr(StrProfile.date), formatDate(order.paidAt ?: order.createdAt))
                            if (!order.failureReason.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(order.failureReason, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ---------------------------------------------------------------- Certificates

class CertificatesViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<Certificate>>>(Async.Loading)
    val state: StateFlow<Async<List<Certificate>>> = _state.asStateFlow()

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
            runCatching { CommerceRepository.myCertificates() }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

@Composable
fun CertificatesScreen(navController: NavHostController) {
    val vm: CertificatesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrProfile.certificates), onBack = { navController.popBackStack() })
        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                if (s.value.isEmpty()) {
                EmptyBlock(tr(StrProfile.noCertificatesYet), tr(StrProfile.noCertificatesBody))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { AdBanner("CERTIFICATES") }
                    items(s.value, key = { it.id }) { certificate ->
                        InkCard(borderColor = Ink.Amber.copy(alpha = 0.3f)) {
                            Text(
                                certificate.courseTitle,
                                color = Ink.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                trf(StrProfile.awardedTo, certificate.studentName),
                                color = Ink.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Divider()
                            Spacer(Modifier.height(10.dp))
                            KeyValueRow(tr(StrProfile.serial), certificate.serial)
                            KeyValueRow(tr(StrProfile.verificationCode), certificate.verifyCode)
                            certificate.teacherName?.let { KeyValueRow(tr(StrProfile.teacherLabel), it) }
                            certificate.scorePercent?.let { KeyValueRow(tr(StrProfile.score), "${it.toInt()}%") }
                            KeyValueRow(tr(StrProfile.issued), formatDate(certificate.issuedAt))
                            if (certificate.revoked) {
                                Spacer(Modifier.height(6.dp))
                                Pill(statusLabel("REVOKED"), background = Ink.CoralSoft, foreground = Ink.Coral)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ---------------------------------------------------------------- Notifications

private val notificationSettingKinds = listOf(
    "PAYMENT", "PAYMENT_REMINDER", "ORDER", "ORDER_PAID", "SUBSCRIPTION", "SUBSCRIPTION_REQUEST",
    "RENEWAL_REQUEST", "ACTIVATION_REQUEST", "RENEWAL_REMINDER", "ENROLLMENT", "COURSE", "COURSE_REVIEW", "CERTIFICATE",
    "SECURITY", "ACCOUNT", "ANNOUNCEMENT", "TEST_RESULT", "ADMIN", "TEACHER", "PAYOUT",
    "PAYOUT_REQUEST", "SUPPORT", "CLASSROOM",
)

@Composable
fun NotificationSettingsScreen(navController: NavHostController, session: SessionViewModel) {
    val state by session.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var soundOn by remember { mutableStateOf(AlertSoundPrefs.isEnabled(context)) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrProfile.notificationSettings), onBack = { navController.popBackStack() })
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    tr(StrProfile.notificationSettingsBody),
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                InkCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr(StrProfile.alertSound), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(tr(StrProfile.alertSoundBody), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = soundOn,
                            onCheckedChange = {
                                soundOn = it
                                AlertSoundPrefs.setEnabled(context, it)
                            },
                        )
                    }
                }
            }
            items(notificationSettingKinds) { kind ->
                NotificationSettingRow(
                    label = tr(StrProfile.notificationKindLabels[kind] ?: Tr(kind.replace('_', ' '), kind.replace('_', ' '))),
                    enabled = state.notificationPreferences[kind] != false,
                    onEnabledChange = { session.setNotificationPreference(kind, it) },
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingRow(label: String, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    InkCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
fun NotificationsScreen(navController: NavHostController, session: SessionViewModel) {
    val state by session.state.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    var confirmingClearAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { session.refreshNotifications() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(
            tr(StrProfile.notifications),
            onBack = { navController.popBackStack() },
            trailing = {
                if (state.notifications.isNotEmpty()) {
                    IconButton(onClick = { confirmingClearAll = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = tr(StrProfile.clearAll),
                            tint = Ink.TextPrimary,
                        )
                    }
                }
            },
        )
        Refreshable(
            refreshing,
            {
                refreshing = true
                session.refreshNotifications()
                scope.launch {
                    delay(600)
                    refreshing = false
                }
            },
        ) {
            if (state.notifications.isEmpty()) {
                EmptyBlock(tr(StrProfile.nothingHereYet), tr(StrProfile.notificationsEmptyBody))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item { AdBanner("NOTIFICATIONS") }
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationRow(
                            notification,
                            onOpen = {
                                session.markRead(notification.id)
                                navController.navigate(notification.deepLink)
                            },
                            onDelete = { session.deleteNotification(notification.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmingClearAll) {
        ConfirmDialog(
            title = tr(StrProfile.clearAllNotificationsTitle),
            body = tr(StrProfile.clearAllNotificationsBody),
            confirmLabel = tr(StrProfile.clearAll),
            destructive = true,
            onDismiss = { confirmingClearAll = false },
            onConfirm = {
                confirmingClearAll = false
                session.clearNotifications()
            },
        )
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onOpen: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onOpen, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (notification.readAt == null) Ink.Amber else Ink.Hairline),
            )
            Column(Modifier.weight(1f)) {
                Text(notification.title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                if (!notification.body.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(notification.body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(5.dp))
                Text(formatDate(notification.createdAt), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = tr(StrProfile.deleteNotification),
                    tint = Ink.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Support

class SupportViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<List<SupportTicket>>>(Async.Loading)
    val state: StateFlow<Async<List<SupportTicket>>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching { SessionRepository.myTickets() }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    fun create(subject: String, message: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { SessionRepository.createTicket(subject, "GENERAL", message) }
                .onSuccess { onDone(); load() }
                .onFailure { _error.value = it.toAppError() }
            _busy.value = false
        }
    }
}

@Composable
fun SupportScreen(navController: NavHostController) {
    val vm: SupportViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(tr(StrProfile.support), onBack = { navController.popBackStack() })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InkCard {
                Text(tr(StrProfile.openTicket), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                InkField(subject, { subject = it }, tr(StrProfile.subject))
                Spacer(Modifier.height(10.dp))
                InkField(message, { message = it }, tr(StrProfile.howCanWeHelp), singleLine = false, minLines = 4)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryAction(
                    tr(StrProfile.sendTicket),
                    enabled = subject.isNotBlank() && message.isNotBlank(),
                    loading = busy,
                ) {
                    vm.create(subject, message) { subject = ""; message = "" }
                }
            }

            Text(tr(StrProfile.yourTickets), color = Ink.TextPrimary, style = MaterialTheme.typography.titleLarge)

            when (val s = state) {
                is Async.Loading -> LoadingBlock()
                is Async.Failure -> ErrorBlock(s.error) { vm.load() }
                is Async.Success -> if (s.value.isEmpty()) {
                    Text(tr(StrProfile.noTicketsYet), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    s.value.forEach { ticket ->
                        InkCard {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ticket.subject,
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(ticket.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                formatDate(ticket.createdAt),
                                color = Ink.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------------------------------------------------------- Edit profile

@Composable
fun EditProfileScreen(navController: NavHostController, session: SessionViewModel) {
    val state by session.state.collectAsStateWithLifecycle()
    val profile = state.profile

    var name by remember(profile?.id) { mutableStateOf(profile?.fullName.orEmpty()) }
    var phone by remember(profile?.id) { mutableStateOf(profile?.phone.orEmpty()) }
    var country by remember(profile?.id) { mutableStateOf(profile?.countryCode.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val context = LocalContext.current
    val picker = rememberFilePicker()
    var photoBusy by remember { mutableStateOf(false) }
    var photoSaved by remember { mutableStateOf(false) }

    /** Uploads and stores in one step, so the picture survives leaving this screen. */
    fun setPhoto(url: String?) {
        scope.launch {
            photoBusy = true
            error = null
            photoSaved = false
            runCatching { SessionRepository.updateAvatar(url) }
                .onSuccess {
                    photoSaved = true
                    session.loadEverything()
                }
                .onFailure { error = it.toAppError() }
            photoBusy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        DetailHeader(tr(StrProfile.editProfile), onBack = { navController.popBackStack() })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InkCard {
                Text(tr(StrProfile.profilePhoto), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(profile?.avatarUrl, profile?.displayName, 66.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (photoBusy) tr(StrProfile.uploadingPhoto) else tr(StrProfile.photoHint),
                            color = if (photoBusy) Ink.Amber else Ink.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryAction(
                                if (profile?.avatarUrl.isNullOrBlank()) tr(StrProfile.addPhoto) else tr(StrProfile.changePhoto),
                                enabled = !photoBusy,
                            ) {
                                picker.pick("image/*") { uri ->
                                    scope.launch {
                                        photoBusy = true
                                        error = null
                                        photoSaved = false
                                        val uploaded = runCatching {
                                            val file = MediaRepository.read(context, uri, MediaRepository.MAX_AVATAR_BYTES)
                                            MediaRepository.uploadAvatar(file)
                                        }.onFailure { error = it.toAppError() }.getOrNull()
                                        photoBusy = false
                                        if (uploaded != null) setPhoto(uploaded)
                                    }
                                }
                            }
                            if (!profile?.avatarUrl.isNullOrBlank() && !photoBusy) {
                                SecondaryAction(tr(StrProfile.removePhoto), tint = Ink.Coral) { setPhoto(null) }
                            }
                        }
                    }
                }
                if (photoSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(StrProfile.photoSaved), color = Ink.Teal, style = MaterialTheme.typography.bodySmall)
                }
            }

            InkCard {
                InkField(name, { name = it }, tr(StrProfile.fullName))
                Spacer(Modifier.height(10.dp))
                InkField(phone, { phone = it }, tr(StrProfile.phone), keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(10.dp))
                InkField(country, { country = it.uppercase().take(2) }, tr(StrProfile.countryCodeField))
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(StrProfile.billingCountryNote),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.message, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
                }
                if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(StrProfile.profileSaved), color = Ink.Teal, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryAction(tr(StrProfile.saveChanges), enabled = name.isNotBlank(), loading = busy) {
                    scope.launch {
                        busy = true
                        error = null
                        saved = false
                        runCatching {
                            SessionRepository.updateProfile(name, phone.ifBlank { null }, country.ifBlank { null })
                        }.onSuccess {
                            saved = true
                            session.loadEverything()
                        }.onFailure { error = it.toAppError() }
                        busy = false
                    }
                }
            }
        }
    }
}
