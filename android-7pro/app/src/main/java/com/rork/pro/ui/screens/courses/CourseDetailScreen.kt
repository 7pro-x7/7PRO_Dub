package com.rork.pro.ui.screens.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AppError
import com.rork.pro.data.Async
import com.rork.pro.data.CatalogRepository
import com.rork.pro.data.CommerceRepository
import com.rork.pro.data.Course
import com.rork.pro.data.CourseSection
import com.rork.pro.data.Enrollment
import com.rork.pro.data.ExerciseRepository
import com.rork.pro.data.LearningRepository
import com.rork.pro.data.ManualPaymentRequest
import com.rork.pro.data.Lesson
import com.rork.pro.data.PlacementTest
import com.rork.pro.data.PriceQuote
import com.rork.pro.data.Review
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.RatingRow
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.levelName
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.payment.HostedCheckout
import com.rork.pro.ui.screens.payment.ManualPending
import com.rork.pro.ui.screens.payment.PayRequest
import com.rork.pro.ui.screens.payment.PaymentSheet
import com.rork.pro.ui.screens.payment.WalletApproval
import com.rork.pro.ui.screens.payment.PaymentSummary
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.StrCourses
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.StrPay
import com.rork.pro.ui.i18n.Str

private object CheckoutFailed {
    val phrase = Tr("Checkout could not be started.", "تعذّر بدء عملية الدفع.")
}

data class CourseDetailData(
    val course: Course,
    val lessons: List<Lesson>,
    val sections: List<CourseSection>,
    val enrollment: Enrollment?,
    val reviews: List<Review>,
    val quote: PriceQuote?,
    val quoteError: AppError?,
    /** Price of the monthly subscription, when the course offers one and the learner can buy it. */
    val monthlyQuote: PriceQuote? = null,
    /** An enrollment that no longer grants access (course turned paid / subscription ended). */
    val lapsed: Enrollment? = null,
    /** The learner's own transfer for this course, when they paid by hand. */
    val manualRequest: ManualPaymentRequest? = null,
    /** Published exercises linked into this course, each with the unit it sits in. Everyone sees
     *  the list; opening one needs an active enrollment (paid) or the course being free. */
    val courseExercises: List<com.rork.pro.data.CourseExerciseItem> = emptyList(),
)

class CourseDetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<CourseDetailData>>(Async.Loading)
    val state: StateFlow<Async<CourseDetailData>> = _state.asStateFlow()

    private val _checkout = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val checkout: StateFlow<CheckoutUiState> = _checkout.asStateFlow()

    private val _coupon = MutableStateFlow<CouponUiState>(CouponUiState.Idle)
    val coupon: StateFlow<CouponUiState> = _coupon.asStateFlow()

    /** True while the learner has "monthly subscription" selected instead of the one-time price. */
    private val _monthly = MutableStateFlow(false)
    val monthly: StateFlow<Boolean> = _monthly.asStateFlow()

    private fun itemType() = if (_monthly.value) "COURSE_MONTHLY" else "COURSE"

    /** The code the server accepted, replayed when the order is finally created. */
    var appliedCouponCode: String? = null
        private set

    fun load(courseId: String) {
        viewModelScope.launch {
            _state.value = Async.Loading
            runCatching {
                val course = CatalogRepository.course(courseId) ?: error("COURSE_NOT_AVAILABLE")
                val anyState = LearningRepository.enrollmentAnyState(courseId)
                val enrollment = anyState?.takeIf { it.hasAccess }
                val lapsed = anyState?.takeIf { it.needsPayment }
                var quote: PriceQuote? = null
                var quoteError: AppError? = null
                var monthlyQuote: PriceQuote? = null
                if (enrollment == null && !course.isFreeCourse && !course.isMonthlyOnly) {
                    runCatching { CommerceRepository.quote("COURSE", courseId = courseId) }
                        .onSuccess { quote = it }
                        .onFailure { quoteError = it.toAppError() }
                }
                // Offered to anyone without access, and to a current monthly subscriber who wants to renew.
                // A current subscriber only gets the quote (and the renew button) once renewal is due.
                if (course.hasMonthly && (enrollment == null || enrollment.isRenewalDue())) {
                    runCatching { CommerceRepository.quote("COURSE_MONTHLY", courseId = courseId) }
                        .onSuccess { monthlyQuote = it }
                }
                // Subscription-only courses have no one-time price: the monthly quote is the price shown.
                if (course.isMonthlyOnly && enrollment == null) {
                    quote = monthlyQuote
                    if (monthlyQuote == null) quoteError = AppError("COURSE_NOT_AVAILABLE", StrCourses.monthlyOnlyUnavailable, true)
                }
                // A one-time buyer never sees a subscription; a course without one never shows the switch.
                if (course.isMonthlyOnly) _monthly.value = true else if (monthlyQuote == null) _monthly.value = false
                CatalogRepository.trackCourseView(courseId)
                // Someone who transferred by hand is waiting on a person, not a gateway, so the
                // screen has to be able to say where that stands.
                val manualRequest = if (enrollment == null) {
                    CommerceRepository.myManualRequest(courseId)
                } else {
                    null
                }
                CourseDetailData(
                    course = course,
                    lessons = CatalogRepository.lessons(courseId),
                    sections = CatalogRepository.courseSections(courseId),
                    enrollment = enrollment,
                    reviews = CatalogRepository.reviewsFor(courseId = courseId, limit = 5),
                    quote = quote,
                    quoteError = quoteError,
                    monthlyQuote = monthlyQuote,
                    lapsed = lapsed,
                    manualRequest = manualRequest,
                    courseExercises = runCatching { com.rork.pro.data.CourseContentRepository.panel(courseId).items }
                        .getOrDefault(emptyList())
                        .filter { it.status == "PUBLISHED" },
                )
            }.onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
    }

    /** Keeps the buy bar in step with a coupon the learner just applied. */
    fun applyQuote(quote: PriceQuote) {
        val current = _state.value
        if (current is Async.Success) {
            _state.value = Async.Success(
                if (quote.itemType == "COURSE_MONTHLY") {
                    current.value.copy(monthlyQuote = quote)
                } else {
                    current.value.copy(quote = quote, quoteError = null)
                },
            )
        }
    }

    /** Remembers a code the server has already accepted, so the order is created with it. */
    fun noteCoupon(code: String?) {
        appliedCouponCode = code?.takeIf { it.isNotBlank() }
        _coupon.value = if (appliedCouponCode == null) CouponUiState.Idle else CouponUiState.Applied(false)
    }

    /** Switches between the one-time price and the monthly subscription; a code applies to one plan only. */
    fun selectPlan(courseId: String, monthly: Boolean) {
        if (_monthly.value == monthly) return
        _monthly.value = monthly
        val had = appliedCouponCode != null
        appliedCouponCode = null
        _coupon.value = CouponUiState.Idle
        if (!had) return
        viewModelScope.launch {
            runCatching { CommerceRepository.quote(itemType(), courseId = courseId) }
                .onSuccess { applyQuote(it) }
        }
    }

    /** Drops an applied code and restores the plain, undiscounted price. */
    fun couponChanged(courseId: String) {
        val had = appliedCouponCode != null
        appliedCouponCode = null
        _coupon.value = CouponUiState.Idle
        if (!had) return
        viewModelScope.launch {
            runCatching { CommerceRepository.quote(itemType(), courseId = courseId) }
                .onSuccess { applyQuote(it) }
        }
    }

    /**
     * Checks a coupon with the server and, when it covers the whole price, enrolls at once.
     *
     * A code is only ever judged by the backend. If it wipes the total out, the learner is
     * granted the course immediately — the payment page is never opened for a zero amount.
     */
    fun applyCoupon(courseId: String, code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank() || _coupon.value is CouponUiState.Checking) return
        viewModelScope.launch {
            _coupon.value = CouponUiState.Checking
            runCatching { CommerceRepository.quote(itemType(), courseId = courseId, couponCode = trimmed) }
                .onSuccess { quote ->
                    if (quote.coupon?.valid != true) {
                        appliedCouponCode = null
                        _coupon.value = CouponUiState.Invalid
                        return@onSuccess
                    }
                    appliedCouponCode = trimmed
                    applyQuote(quote)
                    val coversAll = quote.totalAmount <= 0.0
                    _coupon.value = CouponUiState.Applied(coversAll)
                    if (coversAll) buy(courseId, PayRequest(couponCode = trimmed))
                }
                .onFailure {
                    appliedCouponCode = null
                    _coupon.value = CouponUiState.Invalid
                }
        }
    }

    /**
     * Starts a purchase.
     *
     * @param walletPhone set when the learner pays with a mobile wallet: the charge is sent to
     *   that number and approved on their phone, so no payment page is opened.
     */
    fun buy(courseId: String, request: PayRequest = PayRequest()) {
        if (_checkout.value is CheckoutUiState.Working) return
        viewModelScope.launch {
            _checkout.value = CheckoutUiState.Working
            runCatching {
                // A transfer and a gateway charge are two different server calls: one files a
                // proof for review, the other opens a payment. Mixing them would let a transfer
                // reach the gateway path and be quietly abandoned there.
                if (request.isManual) {
                    CommerceRepository.submitManualPayment(
                        itemType(),
                        courseId = courseId,
                        couponCode = request.couponCode,
                        brand = request.manualBrand.orEmpty(),
                        senderPhone = request.senderPhone.orEmpty(),
                        proofPath = request.proofPath.orEmpty(),
                        note = request.note,
                    )
                } else {
                    CommerceRepository.startCheckout(
                        itemType(),
                        courseId = courseId,
                        couponCode = request.couponCode,
                        methodId = request.methodId,
                        walletPhone = request.walletPhone,
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

                        session.checkoutUrl != null ->
                            CheckoutUiState.Redirect(session.checkoutUrl, session.orderId.orEmpty())
                        else -> CheckoutUiState.Error(AppError("CHECKOUT_FAILED", CheckoutFailed.phrase, true))
                    }
                }
                .onFailure { failure ->
                    val error = failure.toAppError()
                    Log.w("Checkout", "course checkout failed: ${error.code}")
                    // The grant may have landed even though the reply did not: if access exists
                    // now, treat it as success instead of showing a dead end.
                    // A renewal is made while access already exists, so "has access" proves nothing
                    // there: only the server's explicit ALREADY_ENROLLED counts for a subscription.
                    val alreadyHasAccess = error.code == "ALREADY_ENROLLED" ||
                        (!_monthly.value && runCatching { LearningRepository.enrollment(courseId) }.getOrNull() != null)
                    _checkout.value =
                        if (alreadyHasAccess) CheckoutUiState.Granted else CheckoutUiState.Error(error)
                }
        }
    }

    fun resetCheckout() {
        _checkout.value = CheckoutUiState.Idle
    }
}

/** How the coupon field on the course screen is doing. */
sealed interface CouponUiState {
    data object Idle : CouponUiState
    data object Checking : CouponUiState

    /** @param coversAll true when the discount leaves nothing to pay. */
    data class Applied(val coversAll: Boolean) : CouponUiState
    data object Invalid : CouponUiState
}

sealed interface CheckoutUiState {
    data object Idle : CheckoutUiState
    data object Working : CheckoutUiState
    data class Redirect(val url: String, val orderId: String) : CheckoutUiState

    /** A wallet charge is on its way to [phone] and is waiting to be approved there. */
    data class WalletPending(val orderId: String, val phone: String, val url: String?) : CheckoutUiState

    /** A transfer proof was filed and an owner or admin now has to confirm it. */
    data class ManualPending(val orderId: String) : CheckoutUiState
    data object Granted : CheckoutUiState
    data class Error(val error: AppError) : CheckoutUiState
}

/**
 * Shows the gateway's payment page inside 7PRO and waits for the result.
 *
 * Access is only ever granted by the verified server-side webhook: this screen watches the
 * order until the server itself reports it paid, so a page that merely looks successful
 * unlocks nothing.
 */
@Composable
fun CheckoutLauncher(
    state: CheckoutUiState,
    onHandled: () -> Unit,
    onGranted: () -> Unit,
) {
    when (state) {
        is CheckoutUiState.Redirect -> HostedCheckout(state.url, state.orderId) { paid ->
            onHandled()
            if (paid) onGranted()
        }

        is CheckoutUiState.WalletPending -> WalletApproval(state.orderId, state.phone, state.url) { paid ->
            onHandled()
            if (paid) onGranted()
        }

        is CheckoutUiState.ManualPending -> ManualPending(state.orderId) { paid ->
            onHandled()
            if (paid) onGranted()
        }

        is CheckoutUiState.Granted -> LaunchedEffect(state) {
            onGranted()
            onHandled()
        }

        else -> Unit
    }
}

@Composable
fun CourseDetailScreen(navController: NavHostController, courseId: String, canCopyLink: Boolean = false) {
    val vm: CourseDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val checkout by vm.checkout.collectAsStateWithLifecycle()
    val couponState by vm.coupon.collectAsStateWithLifecycle()
    val monthly by vm.monthly.collectAsStateWithLifecycle()

    // Set while the branded payment sheet is open, so the learner picks a method before paying.
    var paying by remember { mutableStateOf<PaymentSummary?>(null) }
    // What the learner has typed into the coupon field on the course screen itself.
    var couponCode by remember { mutableStateOf("") }

    LaunchedEffect(courseId) { vm.load(courseId) }

    val copyCourseLink = rememberCourseLinkCopier(canCopyLink)
    // Only a published course has a public page; a draft opened by its teacher has nothing to link to.
    // Copying the link is limited to teachers, admins and the owner.
    val linkable = canCopyLink &&
        (state as? Async.Success<*>)?.value.let { it is CourseDetailData && it.course.status == "PUBLISHED" }

    paying?.let { summary ->
        PaymentSheet(
            summary = summary,
            working = checkout is CheckoutUiState.Working,
            onDismiss = { paying = null },
            onPay = { request ->
                paying = null
                vm.buy(courseId, request)
            },
            quoteLoader = label@{ code ->
                runCatching {
                    val q = CommerceRepository.quote(if (monthly) "COURSE_MONTHLY" else "COURSE", courseId = courseId, couponCode = code)
                    // The server is the only judge of a coupon: an unknown, expired or already
                    // used code comes back as not valid, and the sheet must say so instead of
                    // pretending the price changed.
                    if (q.coupon?.valid != true) return@label null
                    val p = paying ?: return@label null
                    val newSummary = PaymentSummary(
                        title = p.title,
                        listPrice = q.listPrice,
                        discount = q.discountAmount,
                        total = q.totalAmount,
                        currency = q.currency,
                    )
                    // Remember the code and reflect the new price on the course screen behind.
                    vm.noteCoupon(code)
                    vm.applyQuote(q)
                    if (q.totalAmount <= 0.0) {
                        // Nothing left to pay: enroll right here and close the sheet, so the
                        // gateway page is never opened for a zero amount.
                        vm.buy(courseId, PayRequest(couponCode = code?.takeIf { it.isNotBlank() }))
                        paying = null
                    } else {
                        paying = newSummary
                    }
                    newSummary
                }.getOrNull()
            },
        )
    }

    CheckoutLauncher(
        state = checkout,
        onHandled = {
            // Closing the transfer panel leaves a request the screen has not read yet, so the
            // bar would still offer "Buy" until the learner navigated away and back.
            if (checkout is CheckoutUiState.ManualPending) vm.load(courseId)
            vm.resetCheckout()
        },
        onGranted = {
            // Access granted — whether the gateway charged the card or a coupon brought the
            // total to zero — so refresh this screen and open the course right away.
            vm.load(courseId)
            navController.navigate(Routes.coursePlayer(courseId))
        },
    )

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(
            tr(StrCourses.courseTitle),
            onBack = { navController.popBackStack() },
            trailing = if (linkable) {
                {
                    androidx.compose.material3.IconButton(onClick = { copyCourseLink(courseId) }) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = tr(com.rork.pro.ui.i18n.StrCourseLink.copyOne),
                            tint = Ink.Amber,
                        )
                    }
                }
            } else null,
        )

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load(courseId) }
            is Async.Success -> {
                val data = s.value
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(bottom = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                            ) {
                                CoverImage(data.course.thumbnailUrl, Modifier.fillMaxSize(), data.course.displayTitle)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Ink.Canvas.copy(alpha = 0.88f)),
                                            ),
                                        ),
                                )
                            }
                        }

                        item {
                            Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
                                Text(
                                    data.course.displayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Ink.TextPrimary,
                                )
                                if (!data.course.displaySubtitle.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        data.course.displaySubtitle,
                                        color = Ink.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RatingRow(data.course.ratingAvg, data.course.ratingCount)
                                    data.course.level?.let { Pill(levelName(it)) }
                                    Pill(
                                        trf(StrCourses.learnersCount, data.course.enrollmentsCount),
                                        background = Ink.NeutralSoft,
                                        foreground = Ink.TextSecondary,
                                    )
                                }
                            }
                        }

                        item {
                            InkCard(Modifier.padding(horizontal = Dimens.screenPadding)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Avatar(data.course.teacher?.avatarUrl, data.course.teacher?.fullName, 46.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            data.course.teacher?.fullName ?: tr(StrCourses.sevenProTeacher),
                                            color = Ink.TextPrimary,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                            }
                        }

                        // Free courses may carry a banner here; paid ones never do (server-decided).
                        item {
                            AdBanner(
                                "COURSE_DETAIL",
                                Modifier.padding(horizontal = Dimens.screenPadding),
                                courseId = data.course.id,
                            )
                        }

                        if (!data.course.displayDescription.isNullOrBlank()) {
                            item {
                                Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
                                    SectionHeader(tr(StrCourses.aboutThisCourse))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        data.course.displayDescription,
                                        color = Ink.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        // Linked exercises open for an active enrollment (paid), or for everyone on a
                        // free course — same as its lessons. The server enforces the same rule.
                        val exercisesOpen = data.enrollment != null || data.course.isFreeCourse
                        item(key = "lessons-section") {
                            SectionHeader(
                                trf(StrCourses.lessonsCount, data.lessons.size),
                                Modifier.padding(horizontal = Dimens.screenPadding),
                            )
                        }

                        groupLessons(data.sections, data.lessons).forEach { group ->
                            group.section?.let { section ->
                                item(key = "section-${section.id}") {
                                    Text(
                                        section.displayTitle,
                                        color = Ink.Amber,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(
                                            start = Dimens.screenPadding,
                                            end = Dimens.screenPadding,
                                            top = 6.dp,
                                        ),
                                    )
                                }
                            }
                            items(group.lessons, key = { it.id }) { lesson ->
                                LessonRow(
                                    lesson = lesson,
                                    unlocked = data.enrollment != null || lesson.isPreview || data.course.isFreeCourse,
                                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                ) {
                                    if (data.enrollment != null || lesson.isPreview || data.course.isFreeCourse) {
                                        navController.navigate(Routes.coursePlayer(courseId, lesson.id))
                                    }
                                }
                            }
                            // This unit's exercises sit right under its lessons.
                            val unitExercises = group.section?.let { section ->
                                data.courseExercises.filter { it.sectionId == section.id }.sortedBy { it.sortOrder }
                            }.orEmpty()
                            items(unitExercises, key = { "unit-exercise-${it.exerciseId}" }) { item ->
                                ExerciseEntryRow(
                                    exercise = item.asPlacementTest(),
                                    unlocked = exercisesOpen,
                                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                ) {
                                    if (exercisesOpen) navController.navigate(Routes.testRun(item.exerciseId))
                                }
                            }
                        }

                        // Exercises not filed under one of the course's units.
                        val courseLevelExercises = data.courseExercises.filter { item ->
                            item.sectionId == null || data.sections.none { it.id == item.sectionId }
                        }.sortedBy { it.sortOrder }
                        if (courseLevelExercises.isNotEmpty()) {
                            item(key = "exercises-section") {
                                SectionHeader(
                                    trf(StrCourses.exercisesCount, courseLevelExercises.size),
                                    Modifier.padding(horizontal = Dimens.screenPadding, vertical = 4.dp),
                                )
                            }
                            items(courseLevelExercises, key = { "exercise-${it.exerciseId}" }) { item ->
                                ExerciseEntryRow(
                                    exercise = item.asPlacementTest(),
                                    unlocked = exercisesOpen,
                                    modifier = Modifier.padding(horizontal = Dimens.screenPadding),
                                ) {
                                    if (exercisesOpen) navController.navigate(Routes.testRun(item.exerciseId))
                                }
                            }
                        }

                        if (data.reviews.isNotEmpty()) {
                            item {
                                SectionHeader(tr(StrCourses.verifiedReviews), Modifier.padding(horizontal = Dimens.screenPadding))
                            }
                            items(data.reviews, key = { it.id }) { review ->
                                ReviewRow(review, Modifier.padding(horizontal = Dimens.screenPadding))
                            }
                        }
                    }

                    BuyBar(
                        data = data,
                        monthly = monthly,
                        onSelectPlan = { vm.selectPlan(courseId, it) },
                        working = checkout is CheckoutUiState.Working,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onOpen = { navController.navigate(Routes.coursePlayer(courseId)) },
                        onRenew = {
                            val offer = data.monthlyQuote
                            if (offer != null) {
                                vm.selectPlan(courseId, true)
                                paying = PaymentSummary(
                                    title = data.course.displayTitle + " · " + tr(StrCourses.monthlyPlan),
                                    listPrice = offer.listPrice,
                                    discount = offer.discountAmount,
                                    total = offer.totalAmount,
                                    currency = offer.currency,
                                )
                            }
                        },
                        // Gated on enrollment alone: the button's own loading state (driven by
                        // checkout being Working) already disables re-taps, so nothing needs to
                        // be flipped — and nothing needs to be persisted — before the grant is
                        // actually confirmed. Flipping it early used to hide this button forever
                        // after any hiccup, leaving the learner stuck on a generic "Enroll"
                        // label instead of ever reaching the free grant.
                        showStartFree = data.course.isFreeCourse,
                        onStartFree = { vm.buy(courseId) },
                        couponCode = couponCode,
                        couponState = couponState,
                        onCouponChange = {
                            couponCode = it
                            vm.couponChanged(courseId)
                        },
                        onApplyCoupon = { vm.applyCoupon(courseId, couponCode) },
                        checkoutError = (checkout as? CheckoutUiState.Error)?.error,
                        onBuy = {
                            val quote = if (monthly) data.monthlyQuote ?: data.quote else data.quote
                            when {
                                // Free courses are granted straight away.
                                data.course.isFreeCourse || quote == null -> vm.buy(courseId)
                                // A coupon already covers the whole price: enroll without ever
                                // opening the payment page.
                                quote.totalAmount <= 0.0 ->
                                    vm.buy(courseId, PayRequest(couponCode = vm.appliedCouponCode))
                                // Anything still owed goes through the payment sheet.
                                else -> paying = PaymentSummary(
                                    title = if (monthly) {
                                        data.course.displayTitle + " · " + tr(StrCourses.monthlyPlan)
                                    } else {
                                        data.course.displayTitle
                                    },
                                    listPrice = quote.listPrice,
                                    discount = quote.discountAmount,
                                    total = quote.totalAmount,
                                    currency = quote.currency,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BuyBar(
    data: CourseDetailData,
    monthly: Boolean,
    onSelectPlan: (Boolean) -> Unit,
    working: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRenew: () -> Unit = {},
    showStartFree: Boolean = false,
    onStartFree: () -> Unit = {},
    couponCode: String = "",
    couponState: CouponUiState = CouponUiState.Idle,
    onCouponChange: (String) -> Unit = {},
    onApplyCoupon: () -> Unit = {},
    checkoutError: AppError? = null,
    onBuy: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            // A solid bar with a hairline on top instead of a fade: the price and the buy button
            // sit on a clear surface, and the lesson list scrolls under a crisp edge.
            .background(Ink.Surface)
            .drawBehind {
                drawRect(Ink.Hairline, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = Dimens.screenPadding, vertical = 14.dp)
            .navigationBarsPadding(),
    ) {
        // The course used to be open to this learner and is not any more: say why, plainly.
        if (data.enrollment == null && data.lapsed != null) {
            Text(
                if (data.lapsed.status == "PAYMENT_REQUIRED") {
                    if (data.course.isMonthlyOnly) tr(StrCourses.monthlyOnlyBanner) else tr(StrCourses.nowPaidBanner)
                } else {
                    tr(StrCourses.subscriptionEndedBanner)
                },
                color = Ink.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        // A failed enrolment must say why: silence makes the button look dead.
        if (data.enrollment == null) {
            checkoutError?.let { failure ->
                Text(
                    failure.message,
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            // A refused transfer has to say so here, where the learner is about to pay again.
            // Without it the button looks like nothing ever happened and the natural response is
            // to transfer a second time.
            if (data.manualRequest?.status == "REJECTED") {
                Text(
                    tr(StrPay.manualRejectedBanner),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                data.manualRequest.reviewNote?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        reason,
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        when {
            data.enrollment != null -> {
                Text(
                    trf(StrCourses.youOwnThisCourse, data.enrollment.progressPercent.toInt()),
                    color = Ink.Teal,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (data.enrollment.isMonthly) {
                    data.enrollment.expiresDay?.let { day ->
                        Text(
                            trf(StrCourses.subscriptionEndsOn, day),
                            color = Ink.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                PrimaryAction(if (data.enrollment.progressPercent > 0) tr(StrCourses.continueLearning) else tr(StrCourses.startLearning), onClick = onOpen)
                if (data.enrollment.isRenewalDue() && data.monthlyQuote != null) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(
                        trf(
                            StrCourses.renewFor,
                            formatMoney(data.monthlyQuote.totalAmount, data.monthlyQuote.currency),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working,
                        onClick = onRenew,
                    )
                }
            }

            // Money has already been sent and is waiting on a person. Offering "Buy" again here
            // is how a learner ends up paying twice for one course, so the bar reports the wait
            // instead.
            data.manualRequest?.status == "PENDING" -> {
                Text(
                    tr(StrPay.manualPendingBanner),
                    color = Ink.Amber,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PrimaryAction(tr(StrPay.manualUnderReview), enabled = false) {}
            }

            showStartFree -> PrimaryAction(tr(StrCourses.startForFree), loading = working, onClick = onStartFree)
            data.quoteError != null -> {
                Text(
                    data.quoteError.message,
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PrimaryAction(tr(Str.retry), loading = working, onClick = onBuy)
            }
            data.quote != null -> {
                val monthlyOffer = data.monthlyQuote
                val monthlyOnly = data.course.isMonthlyOnly
                val useMonthly = (monthly || monthlyOnly) && monthlyOffer != null
                val quote = if (useMonthly) monthlyOffer!! else data.quote
                val coversAll = quote.totalAmount <= 0.0

                // Two ways to pay for the same course: once, or month by month.
                if (monthlyOffer != null && !monthlyOnly) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlanChip(
                            label = tr(StrCourses.oneTimePlan),
                            price = formatMoney(data.quote.listPrice, data.quote.currency),
                            selected = !useMonthly,
                            modifier = Modifier.weight(1f),
                        ) { onSelectPlan(false) }
                        PlanChip(
                            label = tr(StrCourses.monthlyPlan),
                            price = trf(StrCourses.perMonth, formatMoney(monthlyOffer.listPrice, monthlyOffer.currency)),
                            selected = useMonthly,
                            modifier = Modifier.weight(1f),
                        ) { onSelectPlan(true) }
                    }
                }

                when {
                    coversAll -> Text(
                        tr(StrPay.couponCoversAll),
                        color = Ink.Teal,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    quote.discountAmount > 0 -> Text(
                        "${formatMoney(quote.listPrice, quote.currency)} → ${formatMoney(quote.totalAmount, quote.currency)}",
                        color = Ink.Teal,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    else -> Text(
                        trf(StrCourses.countryPricingSecure, quote.countryCode),
                        color = Ink.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                PrimaryAction(
                    if (coversAll) {
                        tr(StrPay.enrollFree)
                    } else if (useMonthly) {
                        trf(StrCourses.subscribeFor, formatMoney(quote.totalAmount, quote.currency))
                    } else {
                        trf(StrCourses.buyFor, formatMoney(quote.totalAmount, quote.currency))
                    },
                    loading = working,
                    onClick = onBuy,
                )
            }
            else -> PrimaryAction(tr(StrCourses.enroll), loading = working, onClick = onBuy)
        }
    }
}

/** One of the two ways to pay for a course; the selected one is outlined in amber. */
@Composable
private fun PlanChip(
    label: String,
    price: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = if (selected) Ink.AmberSoft else Ink.Surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) Ink.Amber else Ink.Hairline,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                color = if (selected) Ink.Amber else Ink.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                price,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * Coupon entry on the course screen itself.
 *
 * Redeeming here means a code that covers the whole price never sends the learner through a
 * payment page for an amount of zero — the course simply unlocks.
 */
@Composable
private fun CouponField(
    code: String,
    state: CouponUiState,
    onCodeChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    val checking = state is CouponUiState.Checking
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                placeholder = { Text(tr(StrPay.couponHint), color = Ink.TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) onApply() }),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Amber,
                    unfocusedBorderColor = Ink.Hairline,
                    cursorColor = Ink.Amber,
                    focusedTextColor = Ink.TextPrimary,
                    unfocusedTextColor = Ink.TextPrimary,
                ),
            )
            Button(
                onClick = onApply,
                enabled = code.isNotBlank() && !checking,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Amber,
                    contentColor = Ink.OnAmber,
                    disabledContainerColor = Ink.SurfaceHigh,
                    disabledContentColor = Ink.TextMuted,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Ink.OnAmber,
                    )
                } else {
                    Text(tr(StrPay.couponApply), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        when (state) {
            is CouponUiState.Applied -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(StrPay.couponApplied),
                    color = Ink.Teal,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is CouponUiState.Invalid -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(StrPay.couponInvalid),
                    color = Ink.Coral,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, unlocked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    InkCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (unlocked) Ink.AmberSoft else Ink.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        !unlocked -> Icons.Default.Lock
                        lesson.kind == "QUIZ" -> Icons.Default.Quiz
                        lesson.kind == "DOCUMENT" -> Icons.Default.Description
                        else -> Icons.Default.PlayArrow
                    },
                    null,
                    tint = if (unlocked) Ink.Amber else Ink.TextMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(lesson.kind.lowercase().replaceFirstChar { it.uppercase() })
                        if (lesson.durationSeconds > 0) append(" · " + trf(StrCourses.minutes, lesson.durationSeconds / 60))
                        if (lesson.isPreview) append(tr(StrCourses.freePreview))
                    },
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ExerciseEntryRow(
    exercise: PlacementTest,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    InkCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (unlocked) Ink.TealSoft else Ink.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (unlocked) Icons.Default.Quiz else Icons.Default.Lock,
                    null,
                    tint = if (unlocked) Ink.Teal else Ink.TextMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.displayTitle,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (unlocked) {
                        trf(StrEx.chipMinutes, exercise.timeLimitSeconds / 60)
                    } else {
                        tr(StrCourses.enrollToUnlockExercise)
                    },
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun ReviewRow(review: Review, modifier: Modifier = Modifier) {
    InkCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(review.author?.avatarUrl, review.author?.fullName, 32.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    review.author?.fullName ?: tr(StrCourses.verifiedStudent),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(formatDate(review.createdAt), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Pill("★ ${review.rating}")
        }
        if (!review.body.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(review.body, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A curriculum section together with the lessons filed under it. */
data class LessonGroup(val section: CourseSection?, val lessons: List<Lesson>)

/**
 * Groups a course's lessons by section for display.
 *
 * Courses created before sections existed — or that simply never used them — return a single
 * unlabelled group, so the list looks exactly as it always did.
 */
fun groupLessons(sections: List<CourseSection>, lessons: List<Lesson>): List<LessonGroup> {
    if (sections.isEmpty()) return listOf(LessonGroup(null, lessons))
    return buildList {
        sections.forEach { section ->
            val inSection = lessons.filter { it.sectionId == section.id }
            if (inSection.isNotEmpty()) add(LessonGroup(section, inSection))
        }
        val loose = lessons.filter { it.sectionId == null }
        if (loose.isNotEmpty()) add(LessonGroup(null, loose))
    }
}
