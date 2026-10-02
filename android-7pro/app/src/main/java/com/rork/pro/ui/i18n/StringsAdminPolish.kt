package com.rork.pro.ui.i18n

/** Labels added by the console-wide design pass (summaries, support thread, readable logs/settings). */
object StrAdminX {
    // Teacher subscribers
    val tsTile = Tr("Teacher subscribers", "مشتركو المعلمين")
    val tsTitle = Tr("Teacher subscribers", "مشتركو المعلمين")
    val tsSubscribers = Tr("Subscribers", "المشتركين")
    val tsStudents = Tr("People", "أشخاص")
    val tsMonthly = Tr("Monthly value", "القيمة الشهرية")
    val tsDueSoon = Tr("Renew soon", "تجديد قريب")
    val tsSearch = Tr("Search student, parent, phone, group, teacher or email…", "ابحث باسم الطالب أو ولي الأمر أو التليفون أو الجروب أو المعلم أو البريد…")
    val tsAllTeachers = Tr("All teachers", "كل المعلمين")
    val tsTeacherLabel = Tr("Teacher", "المعلم")
    val tsStateAll = Tr("All", "الكل")
    val tsStateActive = Tr("Active", "نشط")
    val tsStateDue = Tr("Due soon", "قرب التجديد")
    val tsStateOverdue = Tr("Overdue", "متأخر")
    val tsStatePaused = Tr("Paused", "موقوف")
    val tsStatePending = Tr("Pending", "بانتظار الموافقة")
    val tsStateRejected = Tr("Rejected", "مرفوض")
    val tsNone = Tr("No subscribers match", "مفيش مشتركين مطابقين")
    val tsParent = Tr("Parent", "ولي الأمر")
    val tsAccount = Tr("Account", "الحساب")
    val tsNoAccount = Tr("Not linked to an account", "غير مربوط بحساب")
    val tsStarted = Tr("Started %s", "بدأ %s")
    val tsRenews = Tr("Renews %s", "يتجدد %s")
    val tsDaysLeft = Tr("in %d days", "بعد %d يوم")
    val tsToday = Tr("today", "النهارده")
    val tsDaysLate = Tr("%d days late", "متأخر %d يوم")
    val tsRenew = Tr("Renew", "جدّد")
    val tsPause = Tr("Pause", "أوقف")
    val tsResume = Tr("Resume", "شغّل")
    val tsApprove = Tr("Approve", "موافقة")
    val tsReject = Tr("Reject", "رفض")
    val tsEditDates = Tr("Edit dates", "عدّل التواريخ")
    val tsTeacherView = Tr("Teacher's page", "صفحة المعلم")
    val tsDelete = Tr("Delete", "حذف")
    val tsDeleteTitle = Tr("Delete %s's subscription?", "حذف اشتراك %s؟")
    val tsDeleteBody = Tr("It is removed for good, and earnings recorded for it are reversed.", "بيتحذف نهائيًا، والأرباح المسجّلة عليه بتتعكس.")
    val tsStartDate = Tr("Start date (yyyy-mm-dd)", "تاريخ البداية (سنة-شهر-يوم)")
    val tsNextDate = Tr("Next renewal (yyyy-mm-dd)", "التجديد القادم (سنة-شهر-يوم)")
    val tsPlusMonth = Tr("+1 month", "+ شهر")
    val tsSave = Tr("Save", "حفظ")
    val tsBadDate = Tr("Use the format 2026-10-30, and the renewal can't be before the start.", "اكتب التاريخ بالشكل 2026-10-30، والتجديد مايكونش قبل البداية.")
    val tsDone = Tr("Done", "تم")
    val tsPersonTitle = Tr("Subscriber", "بيانات المشترك")
    val tsSubsCount = Tr("%d subscriptions", "%d اشتراك")
    val tsMore = Tr("More options", "المزيد")

    // Home content (banners, shortcuts, categories)
    // Ads screen
    val adsPolicyShort = Tr(
        "Ads show on free content only, never for students with an active paid subscription.",
        "الإعلانات بتظهر على المحتوى المجاني بس، ومابتظهرش لطلاب الاشتراك المدفوع النشط.",
    )
    val adsScreensTitle = Tr("Screens", "الشاشات")
    val adsNone = Tr("No ad yet", "مفيش إعلان")
    val adsAllOff = Tr("All switched off", "كلها متوقفة")
    val adsAddHere = Tr("Add an ad to this screen", "إضافة إعلان لهذه الشاشة")
    val adsAdvanced = Tr("Advanced options", "خيارات متقدمة")
    val adsCustomScreen = Tr("+ A screen that is not in the list", "+ شاشة مش في القايمة")
    val adsUnused = Tr("The app never asks for this one, so it can't show", "التطبيق مابيطلبش الموضع ده، فمش هيظهر أبدًا")
    val adsCleanup = Tr("Delete ads the app never asks for (%d)", "حذف الإعلانات اللي التطبيق مابيطلبهاش (%d)")
    val adsCleanupBody = Tr(
        "These placements exist but no screen requests them, so they can never show. Deleting them removes the saved ad unit ids too.",
        "المواضع دي موجودة لكن مفيش شاشة بتطلبها، فمش هتظهر أبدًا. الحذف بيمسح معرّفات الوحدات المحفوظة فيها كمان.",
    )
    val cmsEditBanner = Tr("Edit banner", "تعديل البانر")
    val cmsEditShortcut = Tr("Edit shortcut", "تعديل الاختصار")
    val cmsSave = Tr("Save", "حفظ")
    val cmsCancel = Tr("Cancel", "إلغاء")
    val cmsAddBanner = Tr("Add a banner", "إضافة بانر")
    val cmsAddShortcut = Tr("Add a shortcut", "إضافة اختصار")
    val cmsAddCategory = Tr("Add a category", "إضافة تصنيف")
    val cmsPickImage = Tr("Choose an image from your phone", "اختر صورة من الموبايل")
    val cmsChangeImage = Tr("Change image", "تغيير الصورة")
    val cmsRemoveImage = Tr("Remove", "إزالة")
    val cmsUploading = Tr("Uploading…", "جاري الرفع…")
    val cmsDestination = Tr("Where does it lead?", "بيودّي لفين؟")
    val cmsDestNone = Tr("Nowhere (display only)", "مفيش (للعرض بس)")
    val cmsDestCourses = Tr("All courses", "كل الكورسات")
    val cmsDestClassroom = Tr("Live classroom", "الفصل المباشر")
    val cmsDestTutor = Tr("AI tutor", "المدرس الذكي")
    val cmsDestDubbing = Tr("Dubbing", "الدبلجة")
    val cmsDestTest = Tr("Level test", "اختبار تحديد المستوى")
    val cmsDestProfile = Tr("My account", "حسابي")
    val cmsDestOrders = Tr("My orders", "طلباتي")
    val cmsDestCertificates = Tr("My certificates", "شهاداتي")
    val cmsDestSupport = Tr("Support", "الدعم")
    val cmsDestCourse = Tr("A specific course", "كورس معيّن")
    val cmsDestUrl = Tr("An external link", "رابط خارجي")
    val cmsPickCourse = Tr("Choose the course", "اختار الكورس")
    val cmsLinkHint = Tr("Link (https://…)", "الرابط (https://…)")
    val cmsPickIcon = Tr("Icon", "الأيقونة")
    val cmsLeadsTo = Tr("Leads to: %s", "بيودّي: %s")
    val cmsEdit = Tr("Edit", "تعديل")
    val cmsNeedDestination = Tr("Choose where it leads", "اختار بيودّي لفين")
    // Console tiles
    val tilePaidBuyers = Tr("Paid-course buyers", "مشتروا الكورسات")
    val tileSubscribers = Tr("Teacher subscribers", "مشتركو المعلمين")
    val tileSupportWaiting = Tr("Chats waiting", "محادثات مستنية")
    val tileOpenGrants = Tr("Courses opened by hand", "كورسات مفتوحة يدويًا")
    // Paid-course students
    val psTile = Tr("Paid-course students", "مشتركو الكورسات المدفوعة")
    val psTitle = Tr("Paid-course students", "مشتركو الكورسات المدفوعة")
    val psBuyers = Tr("Bought or subscribed", "اشتروا أو اشتركوا")
    val psStudents = Tr("All students", "كل الطلاب")
    val psPaidTotal = Tr("Paid in total", "إجمالي المدفوع")
    val psActive = Tr("Open now", "مفتوح دلوقتي")
    val psSearch = Tr("Search by name or email…", "ابحث بالاسم أو البريد…")
    val psAllCourses = Tr("All paid courses", "كل الكورسات المدفوعة")
    val psCourseLabel = Tr("Course", "الكورس")
    val psSourceAll = Tr("All", "الكل")
    val psSourcePaid = Tr("Paid", "دفع")
    val psSourceMonthly = Tr("Monthly", "شهري")
    val psSourceCoupon = Tr("Coupon", "كوبون")
    val psSourceGranted = Tr("Opened by hand", "منحة")
    val psSourceFree = Tr("Joined when free", "كان مجاني")
    val psStateAll = Tr("Any state", "أي حالة")
    val psStateActive = Tr("Open", "مفتوح")
    val psStateExpired = Tr("Expired", "منتهي")
    val psStateClosed = Tr("Closed", "مقفول")
    val psFreeEraHint = Tr("Joined while the course was free and never paid.", "دخل وقت ما الكورس كان مجاني ولم يدفع.")
    val psExpires = Tr("Ends %s", "ينتهي %s")
    val psEnrolled = Tr("Joined %s", "انضم %s")
    val psPaidLine = Tr("Paid %s", "دفع %s")
    val psCoursesCount = Tr("%d courses", "%d كورس")
    val psOpenCount = Tr("%d open", "%d مفتوح")
    val psPersonTitle = Tr("Student's courses", "كورسات الطالب")
    val psTotalPaid = Tr("Paid in total", "إجمالي ما دفعه")
    val psAccessOpen = Tr("Access is open", "الوصول مفتوح")
    val psAccessClosed = Tr("Access is closed", "الوصول مقفول")
    val psGrantCta = Tr("Open a course for someone", "فتح كورس لشخص")
    val psClose = Tr("Close access", "قفل الوصول")
    val psOpen = Tr("Open access", "فتح الوصول")
    val psExtend = Tr("Renew for one month", "تجديد شهر")
    val psExtendLocked = Tr("Renews on %s", "يتجدد في %s")
    val psExtendRule = Tr(
        "Renewal opens on the day the subscription ends — never before — and always adds exactly one month from that day.",
        "التجديد بيفتح يوم انتهاء الاشتراك مش قبله، ودايمًا بيضيف شهر واحد بس من اليوم ده.",
    )
    val psCloseTitle = Tr("Close this course for %s?", "قفل الكورس على %s؟")
    val psCloseBody = Tr("They lose access now and are notified. You can open it again any time.", "هيفقد الدخول دلوقتي وهيوصله إشعار. تقدر تفتحه له تاني في أي وقت.")
    val psNone = Tr("No students match", "مفيش طلاب مطابقين")
    val psDone = Tr("Done", "تم")
    val psOpenedNote = Tr("Opened", "اتفتح")
    val psClosedNote = Tr("Closed", "اتقفل")
    val psExtendedNote = Tr("Extended", "اتمدد")
    // Opening a paid course for a chosen person
    val grantTitle = Tr("Open a course for someone", "فتح كورس لمستخدم")
    val grantTile = Tr("Open courses for users", "فتح كورس لمستخدم")
    val grantIntro = Tr(
        "Pick a paid course, then find the person to open it for.",
        "اختار الكورس المدفوع، وبعدين دوّر على الشخص اللي عايز تفتحه له.",
    )
    val grantStepPerson = Tr("Person", "الشخص")
    val grantPickCourseFirst = Tr("Choose a course first", "اختار الكورس الأول")
    val grantSearchTitle = Tr("Search for a person", "دوّر على شخص")
    val grantDoneFor = Tr("Course opened for %s", "اتفتح الكورس لـ %s")
    val grantNoneYetBody = Tr(
        "People you open this course for will appear here, and you can close it for them any time.",
        "الناس اللي هتفتح لهم الكورس ده هتظهر هنا، وتقدر تقفله عليهم في أي وقت.",
    )
    val grantPickCourse = Tr("Course", "الكورس")
    val grantChooseCourse = Tr("Choose a paid course", "اختر كورسًا مدفوعًا")
    val grantSearchStaff = Tr("Search by name or email…", "ابحث بالاسم أو البريد…")
    val grantSearchTeacher = Tr("Search your students by name or email…", "ابحث في طلابك بالاسم أو البريد…")
    val grantSearchHintStaff = Tr("Type at least 2 letters of a name or email.", "اكتب حرفين على الأقل من الاسم أو البريد.")
    val grantNoUsers = Tr("No matching user", "لا يوجد مستخدم مطابق")
    val grantOpen = Tr("Open", "افتح")
    val grantClose = Tr("Close", "اقفل")
    val grantHasCourse = Tr("Has the course", "معه الكورس")
    val grantOpenedByHand = Tr("Opened by hand", "مفتوح يدويًا")
    val grantOpenedList = Tr("Course opened for", "الكورس مفتوح لهؤلاء")
    val grantNoneYet = Tr("Nobody yet", "لا أحد حتى الآن")
    val grantByLine = Tr("Opened by %s", "فتحه %s")
    val grantCloseTitle = Tr("Close this course for them?", "قفل الكورس عليه؟")
    val grantCloseBody = Tr("%s will lose access to this course. You can open it again any time.", "%s هيفقد الدخول للكورس ده. تقدر تفتحه له تاني في أي وقت.")
    val grantNoAccessTitle = Tr("Not available", "غير متاح")
    val grantNoAccessBody = Tr("The owner has not switched this on for you, or no course was assigned to you yet.", "المالك لم يفعّل هذه الخاصية لك، أو لم يحدد لك كورسات بعد.")
    val grantAlreadyHas = Tr("This person already has the course through a purchase or subscription.", "المستخدم ده معاه الكورس بالفعل عن طريق شراء أو اشتراك.")
    val grantDone = Tr("Opened", "تم الفتح")
    val grantClosedDone = Tr("Closed", "تم القفل")
    // Owner: per-teacher permission
    val grantTeacherToggle = Tr("Can open paid courses for their students", "يقدر يفتح كورسات مدفوعة لطلابه")
    val grantTeacherCourses = Tr("Courses they may open", "الكورسات المسموح له بفتحها")
    val grantTeacherNone = Tr("No courses chosen — nothing can be opened", "لم تحدد كورسات — لن يستطيع فتح أي كورس")
    val grantTeacherCount = Tr("%d courses selected", "تم اختيار %d كورس")
    val grantTeacherSave = Tr("Save courses", "حفظ الكورسات")
    // Coupon ↔ course link and search
    val couponCourseTitle = Tr("Applies to", "يُطبَّق على")
    val couponAllCourses = Tr("All courses", "كل الكورسات")
    val couponPickCourse = Tr("Choose a course", "اختر كورس")
    val couponSearchCourse = Tr("Search courses…", "ابحث عن كورس…")
    val couponSearchCoupons = Tr("Search by code, course or value…", "ابحث بالكود أو الكورس أو القيمة…")
    val couponNoCourseMatch = Tr("No matching course", "لا يوجد كورس مطابق")
    val couponLinkedNoTutor = Tr("A coupon linked to a course does not apply to the AI Tutor.", "الكوبون المربوط بكورس لا يعمل على المعلّم الذكي.")
    val couponManyCourses = Tr("%d courses", "%d كورسات")
    val couponUnknownCourse = Tr("Unknown course", "كورس غير معروف")
    // Summaries
    val teachersTotal = Tr("Teachers", "المعلمون")
    val activeNow = Tr("Active", "نشط")
    val suspendedNow = Tr("Suspended", "موقوف")
    val studentsTotal = Tr("Students", "الطلاب")
    val paidRevenue = Tr("Paid sales", "مبيعات مدفوعة")
    val platformNet = Tr("Platform share", "نصيب المنصة")
    val refundedTotal = Tr("Refunded", "المسترد")
    val ordersPaid = Tr("Paid orders", "طلبات مدفوعة")
    val awaitingApproval = Tr("Awaiting approval", "بانتظار الموافقة")
    val awaitingPayment = Tr("Approved, not paid", "معتمدة ولم تُصرف")
    val pendingRequests = Tr("Pending requests", "طلبات معلّقة")
    val activeSubs = Tr("Active subscriptions", "اشتراكات نشطة")
    val totalSubs = Tr("Subscriptions", "الاشتراكات")
    val openTickets = Tr("Open", "مفتوحة")
    val urgentTickets = Tr("Urgent", "عاجلة")
    val activeCoupons = Tr("Active coupons", "كوبونات نشطة")
    val totalUses = Tr("Total uses", "مرات الاستخدام")

    // Teacher console
    val chooseTeacher = Tr("Choose a teacher", "اختر المعلم")
    val chooseTeacherHint = Tr(
        "Approvals, subscriptions, earnings and withdrawals open for the teacher you pick.",
        "الموافقات والاشتراكات والأرباح والسحوبات بتفتح للمعلم اللي تختاره.",
    )
    val toolsForTeacher = Tr("Teacher tools", "أدوات المعلم")
    val tApprovals = Tr("Approval requests", "طلبات الموافقة")
    val tSubs = Tr("Subscriptions & groups", "الاشتراكات والجروبات")
    val tEarnings = Tr("Subscription earnings", "أرباح الاشتراكات")
    val tPayouts = Tr("Withdrawals", "السحوبات")
    val fullProfile = Tr("Full profile", "الملف الكامل")
    val change = Tr("Change", "تغيير")
    val withPending = Tr("With pending requests", "لديهم طلبات معلّقة")
    val allTeachers = Tr("All teachers", "كل المعلمين")

    // Approvals / detail
    val rejectReason = Tr("Reason (optional)", "سبب الرفض (اختياري)")
    val nothingToApprove = Tr("Nothing waiting — you're all caught up.", "مفيش طلبات معلّقة — كله تمام.")
    val deletionRequests = Tr("Deletion requests", "طلبات حذف")
    val needsDecision = Tr("Needs a decision", "يحتاج قرار")

    // Booking
    val groupsOpenOf = Tr("%1\$d of %2\$d groups open", "%1\$d من %2\$d جروب مفتوح")
    val openForBooking = Tr("Open for booking", "متاح للحجز")
    val closedForBooking = Tr("Not bookable", "غير متاح للحجز")
    val noPriceGroups = Tr("%d without a price", "%d بدون سعر")

    // Showcase
    val orderSection = Tr("Order", "الترتيب")

    // Payments
    val queueClear = Tr("No transfers waiting", "لا توجد تحويلات بانتظار المراجعة")
    val queueClearBody = Tr("New course and subscription transfers show up here.", "أي تحويل جديد لدورة أو اشتراك هيظهر هنا.")

    // Support thread
    val conversation = Tr("Conversation", "المحادثة")
    val replyHint = Tr("Write a reply…", "اكتب ردك…")
    val send = Tr("Send", "إرسال")
    val noMessages = Tr("No messages on this ticket yet.", "لا توجد رسائل في هذه التذكرة بعد.")
    val supportSide = Tr("Support", "الدعم")
    val userSide = Tr("User", "المستخدم")
    val urgent = Tr("Urgent", "عاجل")

    // Settings
    val settingsHidden = Tr(
        "App-update keys are managed in the card above.",
        "مفاتيح تحديث التطبيق بتتدار من البطاقة اللي فوق.",
    )
    // ── Round: admin screens polish ──
    val accountSettings = Tr("Account & settings", "الحساب والإعدادات")
    val bookingShowcaseTitle = Tr("Teacher order & badges", "ترتيب المعلمين وشاراتهم")
    val bookingShowcaseSub = Tr("Who appears first on the booking screen", "مين يظهر الأول في شاشة الحجز")
    val groupsTotal = Tr("Groups", "الجروبات")
    val openGroups = Tr("Open groups", "جروبات مفتوحة")
    val noRefunds = Tr("No refunds yet", "لا توجد مستردات")
    val noRefundsBody = Tr("Refunds and chargebacks you process will be listed here.", "أي استرداد أو منازعة تتم معالجتها ستظهر هنا.")
    val noBanners = Tr("No banners yet", "لا توجد بانرات")
    val noBannersBody = Tr("Add a banner to feature it at the top of the home screen.", "أضف بانر ليظهر أعلى الشاشة الرئيسية.")
    val noCategories = Tr("No categories yet", "لا توجد تصنيفات")
    val noCategoriesBody = Tr("Categories group courses on the home screen.", "التصنيفات بتجمّع الدورات في الشاشة الرئيسية.")
    val noShortcuts = Tr("No shortcuts yet", "لا توجد اختصارات")
    val noShortcutsBody = Tr("Add a shortcut so students can jump straight to a screen from Home.", "أضف اختصارًا ليقدر الطلاب يفتحوا شاشة معينة مباشرة من الرئيسية.")

}

/**
 * Readable names for the platform's setting keys. Anything not listed falls back to a tidied
 * version of the key, so a new setting still shows — just in its raw form until named here.
 */
private val SETTING_LABELS: Map<String, Pair<Tr, Tr?>> = mapOf(
    "ads.enabled" to (Tr("Show ads", "تشغيل الإعلانات") to null),
    "ads.free_content_only" to (Tr("Ads on free content only", "الإعلانات على المحتوى المجاني فقط") to null),
    "ads.global_enabled" to (Tr("AdMob master switch", "المفتاح الرئيسي لإعلانات AdMob") to null),
    "certificate.min_percent" to (Tr("Certificate pass mark (%)", "نسبة الحصول على الشهادة (%)") to Tr("Completion needed for a certificate", "نسبة الإكمال المطلوبة لإصدار الشهادة")),
    "courses.teacher_direct_publish" to (Tr("Teachers publish directly", "المعلم ينشر دوراته مباشرة") to Tr("Off = every course goes to review first", "لو مقفول، كل دورة تروح للمراجعة الأول")),
    "design.light.canvas" to (Tr("Light theme background", "لون خلفية الوضع الفاتح") to Tr("Hex colour, e.g. #F7FCFC", "كود لون، مثال: #F7FCFC")),
    "earnings.release_days" to (Tr("Earnings hold (days)", "مدة تعليق الأرباح (أيام)") to Tr("Before pending earnings become withdrawable", "قبل ما الأرباح المعلّقة تبقى متاحة للسحب")),
    "maintenance.enabled" to (Tr("Maintenance mode", "وضع الصيانة") to Tr("Blocks the app for everyone except staff", "بيقفل التطبيق على الكل ما عدا الإدارة")),
    "maintenance.message" to (Tr("Maintenance message", "رسالة الصيانة") to null),
    "payments.paymob_enabled" to (Tr("Paymob gateway", "بوابة Paymob") to Tr("Off = transfers only", "لو مقفولة، الدفع بالتحويل فقط")),
    "payout.currency" to (Tr("Payout currency", "عملة السحب") to null),
    "payout.minimum_amount" to (Tr("Minimum withdrawal", "أقل مبلغ للسحب") to null),
    "payout.release_note" to (Tr("Earnings note for teachers", "ملاحظة الأرباح للمعلمين") to Tr("Shown on the teacher earnings screen", "بتظهر في شاشة أرباح المعلم")),
    "platform.commission_rate" to (Tr("Default teacher share", "نسبة المعلم الافتراضية") to Tr("0.30 = 30% of each sale", "0.30 يعني 30% من كل عملية بيع")),
    "pricing.base_currency" to (Tr("Base currency", "العملة الأساسية") to null),
    "pricing.default_country" to (Tr("Default country", "الدولة الافتراضية") to Tr("Used when location is unknown", "بتُستخدم لو الموقع غير معروف")),
    "pricing.group_subscription_default" to (Tr("Default group price (monthly)", "سعر الجروب الافتراضي (شهريًا)") to Tr("For groups without their own price", "للجروبات اللي ملهاش سعر")),
    "subscription.renewal_window_days" to (Tr("Renewal reminder (days before)", "تنبيه التجديد (قبل بأيام)") to null),
    "support.email" to (Tr("Support email", "بريد الدعم") to null),
)

private val SETTING_GROUPS: Map<String, Tr> = mapOf(
    "ads" to Tr("Ads", "الإعلانات"),
    "certificate" to Tr("Certificates", "الشهادات"),
    "courses" to Tr("Courses", "الدورات"),
    "design" to Tr("Appearance", "المظهر"),
    "earnings" to Tr("Earnings", "الأرباح"),
    "maintenance" to Tr("Maintenance", "الصيانة"),
    "payments" to Tr("Payments", "المدفوعات"),
    "payout" to Tr("Withdrawals", "السحوبات"),
    "platform" to Tr("Platform", "المنصة"),
    "pricing" to Tr("Pricing", "التسعير"),
    "subscription" to Tr("Subscriptions", "الاشتراكات"),
    "support" to Tr("Support", "الدعم"),
    "app" to Tr("App updates", "تحديثات التطبيق"),
)

fun settingTitle(key: String): String =
    SETTING_LABELS[key]?.first?.let { tr(it) }
        ?: key.substringAfter('.').replace('_', ' ').replace(".", " · ").replaceFirstChar { it.uppercase() }

fun settingHint(key: String): String? = SETTING_LABELS[key]?.second?.let { tr(it) }

fun settingGroupTitle(prefix: String): String =
    SETTING_GROUPS[prefix]?.let { tr(it) } ?: prefix.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Readable audit actions. Unknown codes are tidied rather than hidden. */
private val AUDIT_LABELS: Map<String, Tr> = mapOf(
    "settings.update" to Tr("Setting changed", "تعديل إعداد"),
    "groups.booking_settings" to Tr("Group booking updated", "تعديل حجز جروب"),
    "course.status" to Tr("Course status changed", "تغيير حالة دورة"),
    "course.delete" to Tr("Course deleted", "حذف دورة"),
    "teacher.flags" to Tr("Teacher options changed", "تعديل خيارات معلم"),
    "teacher.create" to Tr("Teacher created", "إضافة معلم"),
    "teacher.approve" to Tr("Teacher approved", "اعتماد معلم"),
    "teacher.suspend" to Tr("Teacher suspended", "إيقاف معلم"),
    "teacher.reactivate" to Tr("Teacher reactivated", "إعادة تفعيل معلم"),
    "teacher.delete" to Tr("Teacher deleted", "حذف معلم"),
    "teacher.badge" to Tr("Teacher badge changed", "تعديل شارة معلم"),
    "teachers.headline" to Tr("Teacher headline changed", "تعديل وصف معلم"),
    "teachers.reorder" to Tr("Teachers reordered", "إعادة ترتيب المعلمين"),
    "teachers.booking_availability" to Tr("Booking availability changed", "تغيير إتاحة الحجز"),
    "set_group_photo" to Tr("Group photo changed", "تغيير صورة جروب"),
    "subscriptions.payment_review" to Tr("Subscription transfer reviewed", "مراجعة تحويل اشتراك"),
    "payments.manual_review" to Tr("Course transfer reviewed", "مراجعة تحويل دورة"),
    "user.role" to Tr("User role changed", "تغيير دور مستخدم"),
    "user.status" to Tr("User status changed", "تغيير حالة مستخدم"),
    "admin.permissions" to Tr("Admin permissions changed", "تعديل صلاحيات مشرف"),
    "payout.request" to Tr("Withdrawal requested", "طلب سحب"),
    "payout.approve" to Tr("Withdrawal approved", "اعتماد سحب"),
    "payout.paid" to Tr("Withdrawal paid", "صرف سحب"),
    "payout.reject" to Tr("Withdrawal rejected", "رفض سحب"),
    "subscription_request_add_group" to Tr("Request: add group", "طلب: إضافة جروب"),
    "subscription_request_delete_group" to Tr("Request: delete group", "طلب: حذف جروب"),
    "subscription_request_add_subscription" to Tr("Request: add subscription", "طلب: إضافة اشتراك"),
    "subscription_request_delete_subscription" to Tr("Request: delete subscription", "طلب: حذف اشتراك"),
    "subscription_request_renew_subscription" to Tr("Request: renew subscription", "طلب: تجديد اشتراك"),
    "subscription_request_rejected" to Tr("Request rejected", "رفض طلب"),
    "group_delete_approved" to Tr("Group deletion approved", "اعتماد حذف جروب"),
    "live.publish" to Tr("Live published", "نشر بث"),
)

fun auditActionLabel(action: String): String =
    AUDIT_LABELS[action.lowercase(java.util.Locale.US)]?.let { tr(it) }
        ?: action.replace('_', ' ').replace(".", " · ").lowercase().replaceFirstChar { it.uppercase() }

private val AUDIT_TARGETS: Map<String, Tr> = mapOf(
    "setting" to Tr("Setting", "إعداد"),
    "group" to Tr("Group", "جروب"),
    "teacher_group" to Tr("Group", "جروب"),
    "course" to Tr("Course", "دورة"),
    "teacher" to Tr("Teacher", "معلم"),
    "user" to Tr("User", "مستخدم"),
    "payout" to Tr("Withdrawal", "سحب"),
    "subscription" to Tr("Subscription", "اشتراك"),
    "renewal" to Tr("Renewal", "تجديد"),
    "subscription_payment_request" to Tr("Subscription transfer", "تحويل اشتراك"),
    "manual_payment_request" to Tr("Course transfer", "تحويل دورة"),
    "live" to Tr("Live", "بث"),
)

fun auditTargetLabel(target: String): String =
    AUDIT_TARGETS[target.lowercase(java.util.Locale.US)]?.let { tr(it) } ?: codeLabel(target)
