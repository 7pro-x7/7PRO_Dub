package com.rork.pro.dubbing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.toAppError
import com.rork.pro.ui.i18n.StrDubbing
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.screens.courses.CheckoutLauncher
import com.rork.pro.ui.screens.courses.CheckoutUiState
import com.rork.pro.ui.screens.payment.PayRequest
import com.rork.pro.ui.screens.payment.PaymentSheet
import com.rork.pro.ui.screens.payment.PaymentSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the dubbing screen needs beyond what [DubbingScreen] already tracks internally:
 * whether the feature is on right now, and where the dubbing-server lives (`dub_server_url`,
 * owner-configured, same idea as the AI Tutor worker URL).
 */
data class DubbingHostState(
    val loading: Boolean = true,
    val status: DubbingStatus? = null,
    val error: AppError? = null,
    /** Set once payment clears and processing has actually started, until DONE/FAILED. */
    val processingJobId: String? = null,
    val processingStatus: String? = null,
    val outputStoragePath: String? = null,
    val outputUrl: String? = null,
)

class DubbingViewModel : ViewModel() {
    private val _state = MutableStateFlow(DubbingHostState())
    val state: StateFlow<DubbingHostState> = _state.asStateFlow()

    private val _checkout = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val checkout: StateFlow<CheckoutUiState> = _checkout.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { DubbingRepository.settings() }
                .onSuccess { s -> _state.update { it.copy(loading = false, status = s, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toAppError()) } }
        }
    }

    /** Coupons are not wired for DUBBING (see the migration) — [request] is only ever manual or gateway. */
    fun buy(jobId: String, request: PayRequest) {
        if (_checkout.value is CheckoutUiState.Working) return
        viewModelScope.launch {
            _checkout.value = CheckoutUiState.Working
            runCatching {
                if (request.isManual) {
                    DubbingRepository.submitManualPayment(
                        jobId, brand = request.manualBrand.orEmpty(),
                        senderPhone = request.senderPhone.orEmpty(), proofPath = request.proofPath.orEmpty(),
                    )
                } else {
                    DubbingRepository.startPaidCheckout(jobId, methodId = request.methodId, walletPhone = request.walletPhone)
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
                        else -> CheckoutUiState.Error(
                            AppError("CHECKOUT_FAILED", Tr("Could not complete the payment.", "تعذر إتمام الدفع."), true),
                        )
                    }
                }
                .onFailure { _checkout.value = CheckoutUiState.Error(it.toAppError()) }
        }
    }

    fun resetCheckout() { _checkout.value = CheckoutUiState.Idle }

    /** Payment cleared (job is QUEUED server-side): kick off processing, then poll until it settles. */
    fun startProcessing(jobId: String) {
        val baseUrl = _state.value.status?.dubServerUrl.orEmpty()
        if (baseUrl.isBlank()) {
            _state.update {
                it.copy(
                    error = AppError(
                        "DUB_SERVER_NOT_CONFIGURED",
                        Tr("The dubbing server is not configured yet.", "خادم الدبلجة غير مهيّأ بعد."),
                        true,
                    ),
                )
            }
            return
        }
        _state.update { it.copy(processingJobId = jobId, processingStatus = "PROCESSING") }
        viewModelScope.launch {
            runCatching { DubbingRepository.start(baseUrl, jobId, youtubeUrl = null) }
                .onFailure { e -> _state.update { it.copy(error = e.toAppError()) } }

            // Poll every 5s until the dubbing-server reports a terminal state.
            while (true) {
                delay(5_000)
                val job = runCatching { DubbingRepository.jobStatus(baseUrl, jobId) }.getOrNull() ?: continue
                _state.update { it.copy(processingStatus = job.status) }
                if (job.status == "DONE") {
                    val signed = job.outputStoragePath?.let { path ->
                        runCatching { DubbingRepository.signedOutputUrl(path) }.getOrNull()
                    }
                    _state.update { it.copy(outputStoragePath = job.outputStoragePath, outputUrl = signed) }
                    break
                }
                if (job.status == "FAILED" || job.status == "CANCELLED") break
            }
        }
    }
}

/**
 * Wires [DubbingScreen] to the same checkout every other item type in this app uses (Paymob
 * card/wallet, or a manual transfer the owner reviews), then starts + tracks the actual dub
 * once the order is paid. Register at [com.rork.pro.ui.navigation.Routes.DUBBING].
 */
@Composable
fun DubbingHostScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: DubbingViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val checkout by vm.checkout.collectAsStateWithLifecycle()

    var paying by remember { mutableStateOf<Pair<String, PaymentSummary>?>(null) }

    paying?.let { (jobId, summary) ->
        PaymentSheet(
            summary = summary,
            working = checkout is CheckoutUiState.Working,
            onDismiss = { paying = null },
            onPay = { request -> paying = null; vm.buy(jobId, request) },
            // Coupons are intentionally not offered for DUBBING (see 20260927000000_dubbing_feature.sql).
            quoteLoader = { null },
        )
    }

    CheckoutLauncher(
        state = checkout,
        onHandled = { vm.resetCheckout() },
        onGranted = { paying?.first?.let(vm::startProcessing) },
    )

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Text(state.error!!.message, color = MaterialTheme.colorScheme.error)
            state.status?.isEnabled != true -> Text(tr(StrDubbing.disabled))
            state.processingJobId != null -> ProcessingCard(state)
            else -> DubbingScreen(
                dubServerBaseUrl = state.status!!.dubServerUrl,
                onPay = { jobId, amount, currency ->
                    paying = jobId to PaymentSummary(title = tr(StrDubbing.title), listPrice = amount, discount = 0.0, total = amount, currency = currency)
                },
            )
        }
    }
}

@Composable
private fun ProcessingCard(state: DubbingHostState) {
    when (state.processingStatus) {
        "DONE" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(StrDubbing.ready))
            if (state.outputUrl != null) {
                Button(onClick = {
                    // Hand the signed URL to whatever this app already uses to play a course
                    // video (CoursePlayerScreen's player), or open it externally.
                }) { Text(tr(StrDubbing.playDubbed)) }
            } else {
                Text(tr(StrDubbing.preparingLink))
            }
        }
        "FAILED", "CANCELLED" -> Text(tr(StrDubbing.failed), color = MaterialTheme.colorScheme.error)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(tr(StrDubbing.inProgress))
        }
    }
}
