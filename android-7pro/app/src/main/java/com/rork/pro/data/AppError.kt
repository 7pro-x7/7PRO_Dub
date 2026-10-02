package com.rork.pro.data

import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import java.io.IOException

/**
 * UI-safe representation of a failure. Never leaks internals to the screen.
 *
 * The text is stored as a bilingual [Tr] and resolved on read, so an error that is already
 * on screen re-renders in the new language the moment the user switches.
 */
data class AppError(
    val code: String,
    val phrase: Tr,
    val retryable: Boolean = true,
    val detail: String? = null,
) {
    val message: String
        get() = if (!detail.isNullOrBlank()) "${tr(phrase)} ($detail)" else tr(phrase)
}

object ErrorText {
    val offline = Tr(
        "You appear to be offline. Check your connection and try again.",
        "يبدو أنك غير متصل بالإنترنت. تحقق من اتصالك وحاول مرة أخرى.",
    )
    val unknown = Tr("Something went wrong. Please try again.", "حدث خطأ ما. يرجى المحاولة مرة أخرى.")
    val invalidRate = Tr(
        "The commission percentage must be between 0 and 100.",
        "يجب أن تكون نسبة العمولة بين 0 و 100.",
    )
    val badCredentials = Tr("That email or password is not correct.", "البريد الإلكتروني أو كلمة المرور غير صحيحة.")
    val emailTaken = Tr("An account with this email already exists.", "يوجد حساب بهذا البريد الإلكتروني بالفعل.")
    val emailUnconfirmed = Tr(
        "Please confirm your email address, then sign in.",
        "يرجى تأكيد بريدك الإلكتروني ثم تسجيل الدخول.",
    )
    val googleUnavailable = Tr(
        "Google sign-in is not switched on yet. The platform owner needs to connect Google in the backend.",
        "لم يتم تفعيل تسجيل الدخول عبر Google بعد. يجب على مالك المنصة ربط Google في الخادم.",
    )
    val signInCancelled = Tr("Sign-in was cancelled.", "تم إلغاء تسجيل الدخول.")
    val weakPassword = Tr(
        "Please choose a longer password (at least 6 characters).",
        "يرجى اختيار كلمة مرور أطول (٦ أحرف على الأقل).",
    )
    val duplicate = Tr("That record already exists.", "هذا السجل موجود بالفعل.")
    val forbidden = Tr("You do not have permission to do that.", "ليس لديك صلاحية للقيام بذلك.")
    val notConfigured = Tr(
        "The backend is not configured for this build.",
        "لم يتم إعداد الخادم لهذه النسخة من التطبيق.",
    )
    val sessionExpired = Tr(
        "Your session expired. Please sign in again.",
        "انتهت جلستك. يرجى تسجيل الدخول مرة أخرى.",
    )
    val actionUnavailable = Tr(
        "This action is not available in this version of the app. Please update to the latest version.",
        "هذا الإجراء غير متاح في هذه النسخة من التطبيق. يرجى التحديث إلى أحدث نسخة.",
    )
    val offlineKeepWorking = Tr(
        "You are offline. Showing your saved data — some actions will not work until you reconnect.",
        "أنت غير متصل بالإنترنت. نعرض بياناتك المحفوظة — بعض الإجراءات لن تعمل حتى تعود الاتصال.",
    )
    val belowMinimum = Tr("You need at least %s to withdraw.", "تحتاج إلى %s على الأقل للسحب.")
    val insufficientBalance = Tr("Your available balance is only %s.", "رصيدك المتاح هو %s فقط.")
}

private val FRIENDLY: Map<String, Tr> = mapOf(
    // Meeting servers (classroom_conference_server_* RPCs)
    "ACTIVE_NEEDS_DOMAIN" to com.rork.pro.ui.i18n.StrMeetingServers.errActiveNeedsDomain,
    "DOMAIN_NOT_SUPPORTED" to com.rork.pro.ui.i18n.StrMeetingServers.errDomainNotSupported,
    "DOMAIN_ALREADY_USED" to com.rork.pro.ui.i18n.StrMeetingServers.errDomainUsed,
    "DOMAIN_REQUIRED" to com.rork.pro.ui.i18n.StrMeetingServers.errDomainRequired,
    "INVALID_DOMAIN" to com.rork.pro.ui.i18n.StrMeetingServers.errDomain,
    "JWT_SECRET_REQUIRED" to com.rork.pro.ui.i18n.StrMeetingServers.errSecretRequired,
    "JWT_APP_ID_REQUIRED" to com.rork.pro.ui.i18n.StrMeetingServers.errAppIdRequired,
    "CANNOT_DELETE_ACTIVE" to com.rork.pro.ui.i18n.StrMeetingServers.errDeleteActive,
    "LABEL_REQUIRED" to com.rork.pro.ui.i18n.StrMeetingServers.errLabel,
    "COURSE_NOT_AVAILABLE" to Tr("This course is no longer available.", "هذه الدورة لم تعد متاحة."),
    "TEACHER_NOT_ACCEPTING_STUDENTS" to Tr(
        "This teacher is not accepting new students right now.",
        "هذا المعلم لا يقبل طلابًا جددًا في الوقت الحالي.",
    ),
    "GROUP_FULL" to Tr(
        "This group is full. Join the waitlist to be notified when a seat opens.",
        "هذه المجموعة ممتلئة. انضم إلى قائمة الانتظار ليصلك إشعار عند توفر مقعد.",
    ),
    "GROUP_HAS_SEATS" to Tr(
        "This group still has seats — you can join directly.",
        "لا تزال هناك مقاعد في هذه المجموعة — يمكنك الانضمام مباشرة.",
    ),
    "GROUP_NOT_FOUND" to Tr("That group could not be found.", "تعذر العثور على هذه المجموعة."),
    "ALREADY_ENROLLED" to Tr("You already have access to this course.", "لديك بالفعل إمكانية الوصول إلى هذه الدورة."),
    "CHAT_CLOSED" to Tr("This chat has ended. Start a new one if you need more help.", "المحادثة دي انتهت. ابدأ محادثة جديدة لو محتاج مساعدة تانية."),
    "ALREADY_COPIED" to Tr("You already copied this exercise.", "نسخت التدريب ده قبل كده."),
    "CHAT_NOT_FOUND" to Tr("That chat could not be found.", "المحادثة دي مش موجودة."),
    "ALREADY_CLAIMED" to Tr("Someone else on the team already took this chat.", "حد تاني من الفريق استلم المحادثة دي."),
    "ASSIGNED_TO_OTHER" to Tr("This chat is with another team member. Take it over first to reply.", "المحادثة دي مع عضو تاني في الفريق. استلمها الأول عشان ترد."),
    "TOO_MANY_MESSAGES" to Tr("You're sending too fast. Wait a moment and try again.", "بتبعت بسرعة كبيرة. استنى لحظة وجرّب تاني."),
    "MESSAGE_TOO_LONG" to Tr("That message is too long.", "الرسالة طويلة جدًا."),
    "ALREADY_RATED" to Tr("You already rated this chat.", "إنت قيّمت المحادثة دي قبل كده."),
    "SUBSCRIPTION_EXPIRED_EXTEND_INSTEAD" to Tr("This monthly subscription has ended. Extend it instead of just opening it.", "الاشتراك الشهري انتهى. مدّده بدل ما تفتحه بس."),
    "NOT_MONTHLY" to Tr("Only monthly subscriptions can be extended.", "التمديد للاشتراك الشهري بس."),
    "ENROLLMENT_NOT_FOUND" to Tr("That enrollment could not be found.", "التسجيل ده مش موجود."),
    "RENEWAL_BEFORE_START" to Tr("The renewal date can't be before the start date.", "تاريخ التجديد مايكونش قبل تاريخ البداية."),
    "INVALID_DATE" to Tr("That date isn't valid.", "التاريخ ده مش صحيح."),
    "COURSE_IS_FREE" to Tr("This course is free, so there is nothing to open.", "الكورس ده مجاني، مفيش حاجة تتفتح."),
    "USER_NOT_FOUND" to Tr("That user could not be found.", "تعذر العثور على هذا المستخدم."),
    "ACCOUNT_NOT_ACTIVE" to Tr(
        "Your account is suspended. Contact support for help.",
        "حسابك موقوف. تواصل مع الدعم للمساعدة.",
    ),
    "GATEWAY_NOT_CONFIGURED" to Tr(
        "Payments are not switched on yet. The platform owner needs to connect the payment gateway.",
        "لم يتم تفعيل المدفوعات بعد. يجب على مالك المنصة ربط بوابة الدفع.",
    ),
    "GATEWAY_UNAVAILABLE" to Tr(
        "The payment page could not be opened. Please try again.",
        "تعذر فتح صفحة الدفع. يرجى المحاولة مرة أخرى.",
    ),
    "WALLET_PHONE_REQUIRED" to Tr(
        "Enter the mobile number your wallet is registered on.",
        "أدخل رقم الهاتف المسجّلة عليه محفظتك.",
    ),
    "WALLET_PHONE_INVALID" to Tr(
        "That wallet number is not valid. Enter an 11-digit number starting with 010, 011, 012 or 015.",
        "رقم المحفظة غير صالح. أدخل رقمًا من ١١ رقمًا يبدأ بـ ٠١٠ أو ٠١١ أو ٠١٢ أو ٠١٥.",
    ),
    "WALLET_PHONE_BRAND_MISMATCH" to Tr(
        "That number belongs to a different operator. Pick the matching wallet, or enter a number for the one you chose.",
        "هذا الرقم يخص شبكة أخرى. اختر المحفظة المطابقة، أو أدخل رقمًا يخص الشبكة التي اخترتها.",
    ),
    "WALLET_DECLINED" to Tr(
        "The wallet refused the request. Check the number and your balance, then try again.",
        "رفضت المحفظة الطلب. تأكد من الرقم ومن رصيدك ثم حاول مرة أخرى.",
    ),
    "WALLET_UNAVAILABLE" to Tr(
        "The wallet request could not be sent right now. Please try again.",
        "تعذر إرسال طلب المحفظة الآن. يرجى المحاولة مرة أخرى.",
    ),
    // Manual wallet transfers. Without these the server's precise reason — a wallet the owner
    // just switched off, a proof that never uploaded — would reach the learner as "something
    // went wrong", which tells them nothing about what to do next.
    "PAYMOB_DISABLED" to Tr(
        "Card payments are switched off right now. Pay by transferring to one of the wallet numbers shown.",
        "الدفع بالبطاقة متوقف حاليًا. ادفع عن طريق التحويل إلى أحد أرقام المحافظ الظاهرة.",
    ),
    "MANUAL_METHOD_UNAVAILABLE" to Tr(
        "That wallet is not accepting transfers right now. Pick another one.",
        "هذه المحفظة لا تستقبل تحويلات حاليًا. اختر محفظة أخرى.",
    ),
    "MANUAL_BRAND_REQUIRED" to Tr(
        "Choose the wallet you transferred to.",
        "اختر المحفظة التي حوّلت إليها.",
    ),
    "SENDER_PHONE_REQUIRED" to Tr(
        "Enter the number you transferred from.",
        "أدخل الرقم الذي حوّلت منه.",
    ),
    "SENDER_PHONE_INVALID" to Tr(
        "That number is not valid. Enter an 11-digit number starting with 010, 011, 012 or 015.",
        "هذا الرقم غير صالح. أدخل رقمًا من ١١ رقمًا يبدأ بـ ٠١٠ أو ٠١١ أو ٠١٢ أو ٠١٥.",
    ),
    "PROOF_REQUIRED" to Tr(
        "Attach a screenshot of the transfer so it can be confirmed.",
        "أرفق صورة من عملية التحويل حتى يمكن تأكيدها.",
    ),
    "PROOF_INVALID" to Tr(
        "That screenshot could not be read. Attach it again.",
        "تعذّرت قراءة الصورة. أرفقها مرة أخرى.",
    ),
    "ORDER_ALREADY_PAID" to Tr(
        "This course is already paid for.",
        "تم دفع قيمة هذه الدورة بالفعل.",
    ),
    "REQUEST_ALREADY_APPROVED" to Tr(
        "Your transfer has already been approved.",
        "تمت الموافقة على تحويلك بالفعل.",
    ),
    "REQUEST_ALREADY_REVIEWED" to Tr(
        "Someone has already decided on this request.",
        "تم البت في هذا الطلب بالفعل.",
    ),
    "UNAUTHORIZED" to Tr("Please sign in again to continue.", "يرجى تسجيل الدخول مرة أخرى للمتابعة."),
    "FORBIDDEN" to ErrorText.forbidden,
    "NO_COURSE_ACCESS" to Tr("You need to purchase this course first.", "عليك شراء هذه الدورة أولاً."),
    "REVIEW_REQUIRES_PURCHASE" to Tr(
        "Only students who purchased this course can review it.",
        "يمكن فقط للطلاب الذين اشتروا هذه الدورة تقييمها.",
    ),
    "REVIEW_REQUIRES_RELATIONSHIP" to Tr(
        "You can review a teacher after studying with them.",
        "يمكنك تقييم المعلم بعد الدراسة معه.",
    ),
    "ATTEMPT_LIMIT_REACHED" to Tr(
        "You have used all attempts for this test.",
        "لقد استخدمت جميع محاولاتك في هذا الاختبار.",
    ),
    "TEST_NOT_AVAILABLE" to Tr("This test is not published yet.", "لم يتم نشر هذا الاختبار بعد."),
    "EXERCISE_NOT_STUDENT" to Tr(
        "Only this teacher's students can take this exercise.",
        "المشاركة في هذا التدريب لطلاب هذا المعلم فقط.",
    ),
    "TEST_LOCKED" to Tr(
        "Your teacher has locked this exercise for now.",
        "قفل المعلم هذا التمرين مؤقتًا.",
    ),
    "EXERCISE_SECTION_NOT_YOURS" to Tr(
        "That level belongs to another teacher.",
        "هذا الليفل يخص معلمًا آخر.",
    ),
    "TEST_HAS_NO_QUESTIONS" to Tr(
        "This test has no questions yet. The academy needs to add questions before it can be taken.",
        "لا توجد أسئلة في هذا الاختبار بعد. على الأكاديمية إضافة الأسئلة قبل إمكانية خوضه.",
    ),
    "ATTEMPT_NOT_FOUND" to Tr(
        "That attempt could not be found. Start the test again.",
        "تعذر العثور على هذه المحاولة. ابدأ الاختبار من جديد.",
    ),
    "ATTEMPT_CLOSED" to Tr(
        "This attempt is already finished. Open your result to see the score.",
        "انتهت هذه المحاولة بالفعل. افتح نتيجتك للاطلاع على الدرجة.",
    ),
    "PAYOUT_ALREADY_OPEN" to Tr("You already have a withdrawal in review.", "لديك بالفعل طلب سحب قيد المراجعة."),
    "CANNOT_MODIFY_OWNER" to Tr("The platform owner account cannot be modified.", "لا يمكن تعديل حساب مالك المنصة."),
    "NOT_DUE_YET" to Tr(
        "It isn't renewal time yet. Renewal opens on the renewal date and can't be done earlier.",
        "لسه ما حانش موعد التجديد. التجديد بيفتح في يوم الموعد بس، ومينفعش قبله.",
    ),
    "ALREADY_RENEWED" to Tr(
        "This subscription was already renewed for this month.",
        "الاشتراك ده اتجدد بالفعل للشهر ده.",
    ),
    "RENEWAL_STALE" to Tr(
        "This subscription was renewed or edited after the request was opened, so it can't be applied. Reject it and ask the student to open a new request.",
        "الاشتراك اتجدد أو اتعدّل بعد ما الطلب اتفتح، فمينفعش يتطبّق. ارفضه وخلّي الطالب يفتح طلب جديد.",
    ),
    "CANNOT_RENEW_PENDING" to Tr("This subscription is still pending approval. It must be approved before it can be renewed.", "هذا الاشتراك لا يزال بانتظار الموافقة. يجب الموافقة عليه قبل تجديده."),
    "GROUP_NOT_ACTIVE" to Tr(
        "Activate this group before recording payments — it only counts toward earnings after approval.",
        "فعّل هذه المجموعة قبل تسجيل الدفعات — لا تحتسب في الأرباح إلا بعد الموافقة.",
    ),
    "GROUP_ALREADY_PENDING" to Tr(
        "This group is already awaiting approval.",
        "هذه المجموعة بانتظار الموافقة بالفعل.",
    ),
    "CANNOT_RENEW_REJECTED" to Tr("This subscription was rejected. It cannot be renewed.", "تم رفض هذا الاشتراك. لا يمكن تجديده."),
    "GROUP_NAME_EXISTS" to Tr("A group with this name already exists. Choose a different name.", "توجد مجموعة بهذا الاسم بالفعل. اختر اسمًا مختلفًا."),
    "GROUP_ALREADY_EXISTS" to Tr("A group with this name already exists. Choose a different name.", "توجد مجموعة بهذا الاسم بالفعل. اختر اسمًا مختلفًا."),
    "DUPLICATE_KEY" to Tr("A record with the same key already exists.", "يوجد سجل بنفس المفتاح بالفعل."),
    "REFUND_EXCEEDS_ORDER" to Tr(
        "The refund is larger than the original payment.",
        "قيمة الاسترداد أكبر من قيمة الدفعة الأصلية.",
    ),
    "ORDER_NOT_REFUNDABLE" to Tr("This order cannot be refunded.", "لا يمكن استرداد قيمة هذا الطلب."),
    "TARGET_NOT_TEACHER" to Tr("The selected account is not an approved teacher.", "الحساب المحدد ليس معلمًا معتمدًا."),
    "COURSE_NEEDS_TITLE" to Tr(
        "Give the course a title before publishing it.",
        "أضف عنوانًا للدورة قبل نشرها.",
    ),
    "COURSE_NEEDS_LESSON" to Tr(
        "Add at least one lesson before publishing this course.",
        "أضف درسًا واحدًا على الأقل قبل نشر هذه الدورة.",
    ),
    "COURSE_HAS_STUDENTS" to Tr(
        "Students are enrolled in this course, so it cannot be deleted. Archive it instead.",
        "يوجد طلاب ملتحقون بهذه الدورة، لذلك لا يمكن حذفها. أرشفها بدلاً من ذلك.",
    ),
    "COURSE_HAS_ORDERS" to Tr(
        "This course has paid orders, so it cannot be deleted. Archive it instead.",
        "لهذه الدورة طلبات مدفوعة، لذلك لا يمكن حذفها. أرشفها بدلاً من ذلك.",
    ),
    "COURSE_NOT_FOUND" to Tr("That course could not be found.", "تعذر العثور على هذه الدورة."),
    "FILE_TOO_LARGE" to Tr(
        "That file is larger than the 50 MB upload limit. Paste a link instead.",
        "حجم هذا الملف أكبر من حد الرفع البالغ ٥٠ ميجابايت. الصق رابطًا بدلاً من ذلك.",
    ),
    "FILE_UNREADABLE" to Tr(
        "That file could not be read. Please pick another one.",
        "تعذر قراءة هذا الملف. يرجى اختيار ملف آخر.",
    ),
    "INVALID_WINDOW" to Tr(
        "The end time must be after the start time.",
        "يجب أن يكون وقت الانتهاء بعد وقت البدء.",
    ),
    "SESSION_NOT_FOUND" to Tr("This session could not be found.", "تعذر العثور على هذه الحصة."),
    "SESSION_CANCELED" to Tr("This session was canceled.", "تم إلغاء هذه الحصة."),
    "SESSION_ENDED" to Tr("This session has already ended.", "انتهت هذه الحصة بالفعل."),
    "NOT_AUTHORIZED" to Tr("You are not invited to this session.", "أنت غير مدعو لهذه الحصة."),
    "TOO_EARLY" to Tr("This session hasn't opened for joining yet.", "لم يفتح باب الانضمام لهذه الحصة بعد."),
    "SESSION_WINDOW_PASSED" to Tr("This session's time window has passed.", "انتهى الوقت المخصص لهذه الحصة."),
    "SESSION_EXPIRED" to Tr(
        "This session expired because it never started. Ask your teacher to schedule a new one.",
        "انتهت صلاحية هذه الحصة لأنها لم تبدأ في موعدها. اطلب من معلمك جدولة حصة جديدة.",
    ),
    "SESSION_FULL" to Tr("This session is full right now.", "هذه الحصة ممتلئة حاليًا."),
    "INVALID_TYPE" to Tr("That file type is not supported.", "نوع هذا الملف غير مدعوم."),
)

/** Maps any throwable into a stable, human-readable error. */
fun Throwable.toAppError(): AppError {
    val raw = message ?: "Something went wrong."

    if (this is IOException || raw.contains("Unable to resolve host") || raw.contains("timeout", true) ||
        raw.contains("Failed to connect", true) || raw.contains("Network", true)
    ) {
        return AppError("OFFLINE", ErrorText.offline, true)
    }

    FRIENDLY.keys.firstOrNull { raw.contains(it) }?.let { key ->
        val extra = raw.substringAfter("$key:", "").takeWhile { it != '"' && it != '\n' }.trim()
        return AppError(
            code = key,
            phrase = FRIENDLY.getValue(key),
            retryable = key !in setOf("FORBIDDEN", "ALREADY_ENROLLED"),
            detail = extra.takeIf { it.isNotBlank() },
        )
    }

    if (raw.contains("BELOW_MINIMUM")) {
        val min = raw.substringAfter("BELOW_MINIMUM:").takeWhile { it.isDigit() || it == '.' }
        return AppError("BELOW_MINIMUM", formatted(ErrorText.belowMinimum, min), false)
    }
    if (raw.contains("INSUFFICIENT_BALANCE")) {
        val bal = raw.substringAfter("INSUFFICIENT_BALANCE:").takeWhile { it.isDigit() || it == '.' }
        return AppError("INSUFFICIENT_BALANCE", formatted(ErrorText.insufficientBalance, bal), false)
    }
    if (raw.contains("Invalid login credentials", true)) {
        return AppError("BAD_CREDENTIALS", ErrorText.badCredentials, false)
    }
    if (raw.contains("User already registered", true)) {
        return AppError("EMAIL_TAKEN", ErrorText.emailTaken, false)
    }
    if (raw.contains("Email not confirmed", true)) {
        return AppError("EMAIL_UNCONFIRMED", ErrorText.emailUnconfirmed, false)
    }
    // Supabase answers this way when the Google provider has not been configured on the project.
    if (raw.contains("provider is not enabled", true) || raw.contains("Unsupported provider", true)) {
        return AppError("GOOGLE_UNAVAILABLE", ErrorText.googleUnavailable, false)
    }
    if (raw.contains("Password should be", true)) {
        return AppError("WEAK_PASSWORD", ErrorText.weakPassword, false)
    }
    if (raw.contains("duplicate key", true)) {
        return AppError("DUPLICATE", ErrorText.duplicate, false)
    }
    if (raw.contains("row-level security", true) || raw.contains("permission denied", true)) {
        return AppError("FORBIDDEN", ErrorText.forbidden, false)
    }
    // PostgreSQL foreign-key violation
    if (raw.contains("foreign key constraint", true) || raw.contains("violates foreign key", true)) {
        return AppError("UNKNOWN", ErrorText.unknown, true)
    }
    // PostgreSQL NOT NULL constraint — often the root cause when audit_logs.actor_id is missing
    if (raw.contains("violates not-null constraint", true) || raw.contains("null value in column", true)) {
        return AppError("UNKNOWN", ErrorText.unknown, true)
    }
    // The server rejected the call shape itself (renamed or re-signed function).
    // Map to a retriable unknown error so the user can retry instead of seeing
    // a misleading "update required" message.
    if (raw.contains("PGRST202") || raw.contains("schema cache", true)) {
        return AppError("SCHEMA_STALE", ErrorText.unknown, true)
    }
    return AppError("UNKNOWN", ErrorText.unknown, true)
}

/** Builds a bilingual phrase with a runtime value substituted into both translations. */
private fun formatted(phrase: Tr, value: String): Tr =
    Tr(phrase.en.replace("%s", value), phrase.ar.replace("%s", value))

/** Simple async state holder used across every screen. */
sealed interface Async<out T> {
    data object Loading : Async<Nothing>
    data class Success<T>(val value: T) : Async<T>
    data class Failure(val error: AppError) : Async<Nothing>
}

inline fun <T> Async<T>.valueOrNull(): T? = (this as? Async.Success<T>)?.value
