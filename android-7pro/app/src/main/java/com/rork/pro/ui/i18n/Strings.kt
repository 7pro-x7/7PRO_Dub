package com.rork.pro.ui.i18n

/** Shared vocabulary: navigation, actions, states and labels reused across every screen. */
object Str {
    // Navigation
    val home = Tr("Home", "الرئيسية")
    val courses = Tr("Courses", "الدورات")

    val profile = Tr("Profile", "حسابي")
    val back = Tr("Back", "رجوع")

    // Core actions
    val save = Tr("Save", "حفظ")
    val saveChanges = Tr("Save changes", "حفظ التغييرات")
    val cancel = Tr("Cancel", "إلغاء")
    val close = Tr("Close", "إغلاق")
    val delete = Tr("Delete", "حذف")
    val edit = Tr("Edit", "تعديل")
    val create = Tr("Create", "إنشاء")
    val add = Tr("Add", "إضافة")
    val send = Tr("Send", "إرسال")
    val submit = Tr("Submit", "إرسال")
    val confirm = Tr("Confirm", "تأكيد")
    val approve = Tr("Approve", "موافقة")
    val reject = Tr("Reject", "رفض")
    val refresh = Tr("Refresh", "تحديث")
    val retry = Tr("Try again", "حاول مرة أخرى")
    val search = Tr("Search", "بحث")
    val filter = Tr("Filter", "تصفية")
    val all = Tr("All", "الكل")
    val viewAll = Tr("View all", "عرض الكل")
    val seeAll = Tr("See all", "عرض الكل")
    val done = Tr("Done", "تم")
    val next = Tr("Next", "التالي")
    val previous = Tr("Previous", "السابق")
    val continueLabel = Tr("Continue", "متابعة")
    val open = Tr("Open", "فتح")
    val copy = Tr("Copy", "نسخ")
    val copied = Tr("Copied", "تم النسخ")
    val optional = Tr("Optional", "اختياري")

    // Earnings matrix — shared by the owner console and the teacher studio so both read the
    // same way. "Weekly" is always the monthly figure divided by four (see EarningsMatrixCard).
    val earningsTitle = Tr("Earnings", "الأرباح")
    val monthlyLabel = Tr("Monthly", "شهري")
    val weeklyLabel = Tr("Weekly", "أسبوعي")
    val coursesStream = Tr("Courses", "الدورات")
    val subscriptionsStream = Tr("Teacher subscriptions", "اشتراكات المعلمين")
    val ownerNetStream = Tr("Owner net earnings", "صافي أرباح المالك")
    val myCoursesStream = Tr("My courses", "دوراتي")
    val mySubscriptionsStream = Tr("My subscriptions", "اشتراكاتي")
    val last30DaysShort = Tr("Last 30 days", "آخر ٣٠ يومًا")
    val loading = Tr("Loading", "جارٍ التحميل")
    val saving = Tr("Saving", "جارٍ الحفظ")
    val saved = Tr("Saved", "تم الحفظ")
    val none = Tr("None", "لا يوجد")
    val yes = Tr("Yes", "نعم")
    val no = Tr("No", "لا")
    val free = Tr("Free", "مجاني")
    val play = Tr("Play", "تشغيل")
    val listen = Tr("Listen", "استمع")
    val new = Tr("New", "جديد")
    val enabled = Tr("Enabled", "مفعّل")
    val disabled = Tr("Disabled", "معطّل")
    val active = Tr("Active", "نشط")
    val inactive = Tr("Inactive", "غير نشط")
    val comingSoon = Tr("Coming soon", "قريبًا")
    val notAvailable = Tr("Not available", "غير متاح")
    val nothingYet = Tr("Nothing here yet", "لا يوجد شيء هنا بعد")
    val me = Tr("Me", "أنا")
    val somethingWrong = Tr("Something went wrong", "حدث خطأ ما")

    // Language
    val language = Tr("Language", "اللغة")
    val languageSubtitle = Tr("Choose how 7PRO speaks to you", "اختر اللغة التي يتحدث بها 7PRO معك")
    val followDevice = Tr("Following your device language", "يتبع لغة جهازك")
    val english = Tr("English", "الإنجليزية")
    val arabic = Tr("Arabic", "العربية")

    // Levels
    val levelA1 = Tr("A1 · Beginner", "A1 · مبتدئ")
    val levelA2 = Tr("A2 · Elementary", "A2 · أساسي")
    val levelB1 = Tr("B1 · Intermediate", "B1 · متوسط")
    val levelB2 = Tr("B2 · Upper-Intermediate", "B2 · فوق المتوسط")
    val levelC1 = Tr("C1 · Advanced", "C1 · متقدم")
    val levelC2 = Tr("C2 · Proficient", "C2 · متمكن")
    val level = Tr("Level", "المستوى")
    val allLevels = Tr("All levels", "كل المستويات")

    // Domain nouns
    val course = Tr("Course", "دورة")
    val lesson = Tr("Lesson", "درس")
    val lessons = Tr("Lessons", "الدروس")
    val section = Tr("Section", "قسم")
    val teacher = Tr("Teacher", "معلم")
    val teachers = Tr("Teachers", "المعلمون")
    val student = Tr("Student", "طالب")
    val students = Tr("Students", "الطلاب")
    val group = Tr("Group", "مجموعة")
    val groups = Tr("Groups", "المجموعات")
    val price = Tr("Price", "السعر")
    val total = Tr("Total", "الإجمالي")
    val order = Tr("Order", "طلب")
    val orders = Tr("Orders", "الطلبات")
    val payment = Tr("Payment", "الدفع")
    val subscription = Tr("Subscription", "الاشتراك")
    val certificate = Tr("Certificate", "الشهادة")
    val certificates = Tr("Certificates", "الشهادات")
    val notifications = Tr("Notifications", "الإشعارات")
    val support = Tr("Support", "الدعم")
    val settings = Tr("Settings", "الإعدادات")
    val rating = Tr("Rating", "التقييم")
    val reviews = Tr("Reviews", "التقييمات")
    val category = Tr("Category", "التصنيف")
    val status = Tr("Status", "الحالة")
    val date = Tr("Date", "التاريخ")
    val name = Tr("Name", "الاسم")
    val email = Tr("Email", "البريد الإلكتروني")
    val phone = Tr("Phone", "رقم الهاتف")
    val description = Tr("Description", "الوصف")
    val title = Tr("Title", "العنوان")
    val notes = Tr("Notes", "ملاحظات")
    val amount = Tr("Amount", "المبلغ")
    val currency = Tr("Currency", "العملة")
    val country = Tr("Country", "الدولة")

    // Status labels
    val statusActive = Tr("Active", "نشط")
    val statusPending = Tr("Pending", "قيد الانتظار")
    val statusPendingReview = Tr("Pending review", "قيد المراجعة")
    val statusApproved = Tr("Approved", "معتمد")
    val statusRejected = Tr("Rejected", "مرفوض")
    val statusPaid = Tr("Paid", "مدفوع")
    val statusFailed = Tr("Failed", "فشل")
    val statusCancelled = Tr("Cancelled", "ملغي")
    val statusRefunded = Tr("Refunded", "مسترد")
    val statusExpired = Tr("Expired", "منتهي")
    val statusExpiring = Tr("Expiring", "على وشك الانتهاء")
    val statusDraft = Tr("Draft", "مسودة")
    val statusPublished = Tr("Published", "منشور")
    val statusArchived = Tr("Archived", "مؤرشف")
    val statusSuspended = Tr("Suspended", "موقوف")
    val statusAvailable = Tr("Available", "متاح")
    val statusWaiting = Tr("Waiting", "في الانتظار")
    val statusOffered = Tr("Offered", "تم العرض")
    val statusDueSoon = Tr("Due soon", "مستحق قريبًا")
    val statusOverdue = Tr("Overdue", "متأخر")
    val statusChargeback = Tr("Chargeback", "رد مالي")
    val statusCompleted = Tr("Completed", "مكتمل")
    val statusInProgress = Tr("In progress", "قيد التنفيذ")
    val statusScheduled = Tr("Scheduled", "مجدول")
    val statusPresent = Tr("Present", "حاضر")
    val statusAbsent = Tr("Absent", "غائب")
    val statusLate = Tr("Late", "متأخر")
    val statusExcused = Tr("Excused", "بعذر")
    val statusOpen = Tr("Open", "مفتوح")
    val statusClosed = Tr("Closed", "مغلق")
    val statusFull = Tr("Full", "مكتمل العدد")
    val statusPaidOut = Tr("Paid out", "تم الصرف")
    val statusProcessing = Tr("Processing", "قيد المعالجة")
    val statusMatured = Tr("Matured", "مستحق")
    val statusReversed = Tr("Reversed", "معكوس")
    val statusHeld = Tr("Held", "محجوز")
    val statusRevoked = Tr("Revoked", "ملغاة")
    val statusPaused = Tr("Paused", "متوقف")
    val statusDue = Tr("Due", "مستحق")
}

private val STATUS_LABELS: Map<String, Tr> = mapOf(
    "ACTIVE" to Str.statusActive,
    "INACTIVE" to Str.inactive,
    "PENDING" to Str.statusPending,
    "PENDING_REVIEW" to Str.statusPendingReview,
    "APPROVED" to Str.statusApproved,
    "REJECTED" to Str.statusRejected,
    "PAID" to Str.statusPaid,
    "FAILED" to Str.statusFailed,
    "CANCELLED" to Str.statusCancelled,
    "REFUNDED" to Str.statusRefunded,
    "EXPIRED" to Str.statusExpired,
    "EXPIRING" to Str.statusExpiring,
    "DRAFT" to Str.statusDraft,
    "PUBLISHED" to Str.statusPublished,
    "ARCHIVED" to Str.statusArchived,
    "SUSPENDED" to Str.statusSuspended,
    "AVAILABLE" to Str.statusAvailable,
    "AVAILABLE_BALANCE" to Str.statusAvailable,
    "WAITING" to Str.statusWaiting,
    "WAITLISTED" to Str.statusWaiting,
    "OFFERED" to Str.statusOffered,
    "DUE_SOON" to Str.statusDueSoon,
    "OVERDUE" to Str.statusOverdue,
    "CHARGEBACK" to Str.statusChargeback,
    "COMPLETED" to Str.statusCompleted,
    "IN_PROGRESS" to Str.statusInProgress,
    "SCHEDULED" to Str.statusScheduled,
    "PRESENT" to Str.statusPresent,
    "ABSENT" to Str.statusAbsent,
    "LATE" to Str.statusLate,
    "EXCUSED" to Str.statusExcused,
    "OPEN" to Str.statusOpen,
    "CLOSED" to Str.statusClosed,
    "FULL" to Str.statusFull,
    "PAID_OUT" to Str.statusPaidOut,
    "PROCESSING" to Str.statusProcessing,
    "MATURED" to Str.statusMatured,
    "REVERSED" to Str.statusReversed,
    "HELD" to Str.statusHeld,
    "REVOKED" to Str.statusRevoked,
    "PAUSED" to Str.statusPaused,
    "DUE" to Str.statusDue,
    "ASSIGNED" to Tr("Assigned", "قيد المتابعة"),
    "RESOLVED" to Tr("Resolved", "تم الحل"),
    "PARTIALLY_REFUNDED" to Tr("Partly refunded", "مسترد جزئيًا"),
    "FREE" to Str.free,
    "NONE" to Str.none,
)

/** Human label for a backend status code, translated when we know it. */
fun statusLabel(code: String?): String {
    if (code.isNullOrBlank()) return "—"
    val key = code.uppercase(java.util.Locale.US)
    return STATUS_LABELS[key]?.let { tr(it) } ?: code.replace('_', ' ')
}

/** Human label for a CEFR level code. */
fun levelLabel(code: String?): String = when (code?.uppercase(java.util.Locale.US)) {
    "A1" -> tr(Str.levelA1)
    "A2" -> tr(Str.levelA2)
    "B1" -> tr(Str.levelB1)
    "B2" -> tr(Str.levelB2)
    "C1" -> tr(Str.levelC1)
    "C2" -> tr(Str.levelC2)
    else -> code.orEmpty()
}

private val CODE_LABELS: Map<String, Tr> = mapOf(
    // Lesson kinds
    "VIDEO" to Tr("Video", "فيديو"),
    "DOCUMENT" to Tr("Document", "مستند"),
    "TEXT" to Tr("Text", "نص"),
    "QUIZ" to Tr("Quiz", "اختبار"),
    // Coupon kinds
    "PERCENT" to Tr("Percent", "نسبة"),
    "FIXED" to Tr("Fixed", "مبلغ ثابت"),
    // Refund kinds
    "REFUND" to Tr("Refund", "استرداد"),
    "CHARGEBACK" to Tr("Chargeback", "منازعة"),
    // Billing periods
    "DAY" to Tr("Day", "يوم"),
    "WEEK" to Tr("Week", "أسبوع"),
    "MONTH" to Tr("Month", "شهر"),
    "YEAR" to Tr("Year", "سنة"),
    // Ad placement screens
    "HOME" to Tr("Home", "الرئيسية"),
    "COURSE_LIST" to Tr("Course list", "قائمة الدورات"),
    "COURSE_DETAILS" to Tr("Course details", "تفاصيل الدورة"),
    "COURSE_DETAIL" to Tr("Course details", "تفاصيل الدورة"),
    "TESTS" to Tr("Tests", "الاختبارات"),
    "EXERCISES" to Tr("Exercises", "التمارين"),
    "PROFILE" to Tr("Profile", "الملف الشخصي"),
    "FREE_LESSON" to Tr("Free lesson", "درس مجاني"),
    "RESULT" to Tr("Test result", "نتيجة الاختبار"),
    "BOOKING" to Tr("Group booking", "حجز المجموعات"),
    "BOOKING_GROUPS" to Tr("Teacher's groups", "مجموعات المعلم"),
    "ORDERS" to Tr("My orders", "طلباتي"),
    "CERTIFICATES" to Tr("Certificates", "الشهادات"),
    "NOTIFICATIONS" to Tr("Notifications", "الإشعارات"),
    "SUPPORT" to Tr("Support", "مركز الدعم"),
    "SEARCH" to Tr("Search", "البحث"),
    "CATEGORIES" to Tr("Categories", "التصنيفات"),
    // Ad formats
    "BANNER" to Tr("Banner", "بانر"),
    "NATIVE" to Tr("Native", "مدمج"),
    "INTERSTITIAL" to Tr("Interstitial", "بيني"),
    "REWARDED" to Tr("Rewarded", "مكافأة"),
    // Test skills
    "GRAMMAR" to Tr("Grammar", "القواعد"),
    "VOCABULARY" to Tr("Vocabulary", "المفردات"),
    "READING" to Tr("Reading", "القراءة"),
    "LISTENING" to Tr("Listening", "الاستماع"),
    "WRITING" to Tr("Writing", "الكتابة"),
    "SPEAKING" to Tr("Speaking", "المحادثة"),
    // Payout methods & order kinds
    "MANUAL" to Tr("Manual", "يدوي"),
    "BANK" to Tr("Bank transfer", "تحويل بنكي"),
    "WALLET" to Tr("Wallet", "محفظة"),
    "COURSE" to Tr("Course", "دورة"),
    "SUBSCRIPTION" to Tr("Subscription", "اشتراك"),
    "BOOKING" to Tr("Booking", "حجز"),
    "BUNDLE" to Tr("Bundle", "باقة"),
    "SINGLE" to Tr("Single choice", "اختيار واحد"),
)

/** Human label for a fixed option code shown in pickers and chips. */
fun codeLabel(code: String): String =
    CODE_LABELS[code.uppercase(java.util.Locale.US)]?.let { tr(it) } ?: code.replace('_', ' ')
