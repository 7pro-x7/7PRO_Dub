package com.rork.pro.ui.i18n

/** Admin console screens: applications, teachers, review, pricing, coupons, finance, payouts, users, CMS, ads, settings, tests, moderation, support, audit. */
object StrAdmin {
    val dismiss = Tr("Dismiss", "إغلاق")
    val changeEmail = Tr("Change email", "تغيير الإيميل")
    val changePassword = Tr("Change password", "تغيير كلمة المرور")
    val newEmail = Tr("New email", "الإيميل الجديد")
    val newPassword = Tr("New password", "كلمة المرور الجديدة")
    val changePasswordHint = Tr(
        "At least 8 characters. Tell the user yourself — a saved password can't be viewed again.",
        "٨ أحرف على الأقل. أبلغ بها المستخدم بنفسك، فلا يمكن عرضها بعد الحفظ.",
    )
    val changeEmailHint = Tr(
        "The user will sign in with this address from now on.",
        "سيسجل المستخدم الدخول بهذا الإيميل من الآن.",
    )
    val headline = Tr("Headline", "العنوان المهني")
    val approve = Tr("Approve", "موافقة")
    val reject = Tr("Reject", "رفض")
    val teachers = Tr("Teachers", "المعلمون")
    val teachersPolicy = Tr("Only the owner and admins create teaching accounts. Each teacher keeps the share you set here and can only manage their own courses.", "المالك والمشرفون فقط ينشئون حسابات المعلمين. يحتفظ كل معلم بالنسبة التي تحددها هنا ولا يدير سوى دوراته.")
    val noTeachers = Tr("No teachers", "لا يوجد معلمون")
    val noTeachersBody = Tr("Create a teaching account for one of your members.", "أنشئ حساب تدريس لأحد أعضائك.")
    val teacherLabel = Tr("Teacher", "المعلم")
    val openForNewStudents = Tr("Open for new students", "مفتوح للطلاب الجدد")
    val canManageTests = Tr("Can manage placement tests", "يمكنه إدارة اختبارات المستوى")
    val canManageCourseExercises = Tr("Can add exercises inside their courses", "يقدر يضيف تمارين داخل دوراته")
    // Course commission specifically — kept distinct from subscriptionRatePercent below so
    // admins never confuse "how much the teacher keeps per course sale" with "how much they
    // keep per subscription renewal". These are two separate rates in two separate tables.
    val teacherSharePct = Tr("Teacher share from courses %", "نسبة المعلم من الدورات %")
    val save = Tr("Save", "حفظ")
    val contentReview = Tr("Content review", "مراجعة المحتوى")
    val coursesTitle = Tr("Courses", "الدورات")
    val noCourses = Tr("No courses", "لا توجد دورات")
    val noCoursesBody = Tr("Teacher courses will appear here.", "ستظهر هنا دورات المعلمين.")
    val featuredOnHome = Tr("Featured on home", "مميّزة في الرئيسية")
    val reviewNote = Tr("Review note", "ملاحظة المراجعة")
    val publish = Tr("Publish", "نشر")
    val suspend = Tr("Suspend", "إيقاف")
    val republish = Tr("Re-publish", "إعادة النشر")
    val deleteCourse = Tr("Delete course", "حذف الدورة")
    val deleteCourseConfirm = Tr(
        "Delete this course for good? Its lessons, enrolments, progress and reviews are removed. Paid orders stay in your financial records. This cannot be undone.",
        "هل تريد حذف هذه الدورة نهائيًا؟ ستُحذف دروسها والتحاق الطلاب وتقدمهم وتقييماتهم. تبقى الطلبات المدفوعة في سجلاتك المالية. لا يمكن التراجع عن هذا الإجراء.",
    )
    val countryPricing = Tr("Country pricing", "التسعير حسب الدولة")
    val addUpdateCountry = Tr("Add / update a country", "إضافة / تحديث دولة")
    val pricingPolicy = Tr("Base catalogue prices are multiplied for each country. The buyer's country is detected on the server.", "تُضرب أسعار الكتالوج الأساسية لكل دولة. يتم تحديد دولة المشتري على الخادم.")
    val country = Tr("Country", "الدولة")
    val currency = Tr("Currency", "العملة")
    val rateField = Tr("× rate", "× المعامل")
    val roundTo = Tr("Round to", "التقريب إلى")
    val discPct = Tr("Disc %", "خصم %")
    val saveCountryPricing = Tr("Save country pricing", "حفظ تسعير الدولة")
    val noCountryRules = Tr("No country rules", "لا توجد قواعد دول")
    val noCountryRulesBody = Tr("Without a rule, every country pays the base catalogue price.", "بدون قاعدة، تدفع كل دولة السعر الأساسي.")
    val remove = Tr("Remove", "حذف")
    val multiplier = Tr("Multiplier", "المعامل")
    val roundedTo = Tr("Rounded to", "مقرّب إلى")
    val discount = Tr("Discount", "الخصم")
    val coupons = Tr("Coupons", "كوبونات الخصم")
    val newCoupon = Tr("New coupon", "كوبون جديد")
    val code = Tr("Code", "الكود")
    val amount = Tr("Amount", "القيمة")
    val maxUses = Tr("Max uses", "أقصى عدد استخدامات")
    val createCoupon = Tr("Create coupon", "إنشاء كوبون")
    val noCoupons = Tr("No coupons", "لا توجد كوبونات")
    val noCouponsBody = Tr("Create a coupon to run a promotion.", "أنشئ كوبونًا لتشغيل عرض ترويجي.")
    val used = Tr("Used", "المستخدم")
    val expires = Tr("Expires", "ينتهي في")
    val activeLabel = Tr("Active", "نشط")
    val ordersRefunds = Tr("Orders & refunds", "الطلبات والمستردات")
    val orders = Tr("Orders", "الطلبات")
    val noOrders = Tr("No orders", "لا توجد طلبات")
    val noOrdersBody = Tr("Verified payments will appear here.", "ستظهر هنا المدفوعات المؤكدة.")
    val type = Tr("Type", "النوع")
    val teacherShare = Tr("Teacher share", "حصة المعلم")
    val platformShare = Tr("Platform share", "حصة المنصة")
    val commissionUsed = Tr("Commission used", "العمولة المطبقة")
    val date = Tr("Date", "التاريخ")
    val refunded = Tr("Refunded", "مسترد")
    val reason = Tr("Reason", "السبب")
    val cancel = Tr("Cancel", "إلغاء")
    val refundChargeback = Tr("Refund / chargeback", "استرداد / منازعة")
    val refundHistory = Tr("Refund history", "سجل المستردات")
    val withdrawals = Tr("Withdrawals", "السحوبات")
    val noWithdrawals = Tr("No withdrawals", "لا توجد سحوبات")
    val noWithdrawalsBody = Tr("Teacher withdrawal requests appear here.", "تظهر هنا طلبات سحب المعلمين.")
    val method = Tr("Method", "الطريقة")
    val destination = Tr("Destination", "الوجهة")
    val requested = Tr("Requested", "تاريخ الطلب")
    val decisionNote = Tr("Decision note", "ملاحظة القرار")
    val transferReference = Tr("Transfer reference", "مرجع التحويل")
    val markAsPaid = Tr("Mark as paid", "تأكيد الصرف")
    val reference = Tr("Reference", "المرجع")
    val users = Tr("Users", "المستخدمون")
    val noUsers = Tr("No users", "لا يوجد مستخدمون")
    val noUsersBody = Tr("Registered accounts will appear here.", "ستظهر هنا الحسابات المسجلة.")
    val statusLabel = Tr("Status", "الحالة")
    val levelLabel = Tr("Level", "المستوى")
    val reactivate = Tr("Reactivate", "إعادة التفعيل")
    val changedFromConsole = Tr("Changed from admin console", "تم التغيير من لوحة الإدارة")
    val permissions = Tr("Permissions", "الصلاحيات")
    val permissionsPolicy = Tr("Grant only what this admin needs. The owner always has everything.", "امنح المشرف ما يحتاجه فقط. المالك يملك كل الصلاحيات دائمًا.")
    val savePermissions = Tr("Save permissions", "حفظ الصلاحيات")
    val homeContent = Tr("Home content", "محتوى الرئيسية")
    val newHomeBanner = Tr("New home banner", "بانر جديد للرئيسية")
    val subtitle = Tr("Subtitle", "العنوان الفرعي")
    val imageUrl = Tr("Image URL", "رابط الصورة")
    val buttonLabel = Tr("Button label", "نص الزر")
    val bannerTarget = Tr("Target (test, course:<id>)", "الوجهة (test أو course:<id>)")
    val publishBanner = Tr("Publish banner", "نشر البانر")
    val shortcuts = Tr("Shortcuts", "الاختصارات")
    val shortcutsHelp = Tr(
        "Shortcuts appear as tiles on the student's Home screen. Up to 6 active shortcuts are recommended.",
        "الاختصارات بتظهر كمربعات في الشاشة الرئيسية للطالب. يُفضّل ألا يزيد عدد الاختصارات المفعّلة عن 6.",
    )
    val newShortcut = Tr("New shortcut", "اختصار جديد")
    val shortcutTitle = Tr("Shortcut title", "اسم الاختصار")
    val shortcutTarget = Tr("Target (classroom, courses, tutor, profile, orders, certificates, support, test, course:<id>, url:<link>)", "الوجهة (classroom أو courses أو tutor أو profile أو orders أو certificates أو support أو test أو course:<id> أو url:<رابط>)")
    val addShortcut = Tr("Add shortcut", "إضافة اختصار")
    val delete = Tr("Delete", "حذف")
    val categories = Tr("Categories", "التصنيفات")
    val newCategory = Tr("New category", "تصنيف جديد")
    val categoryName = Tr("Category name", "اسم التصنيف")
    val categoryIcon = Tr("Icon (emoji, optional)", "أيقونة (إيموجي، اختياري)")
    val addCategory = Tr("Add category", "إضافة تصنيف")
    val published = Tr("Published", "منشور")
    val admob = Tr("AdMob", "إعلانات AdMob")
    val adsPolicyTitle = Tr("Ads policy", "سياسة الإعلانات")
    val adsPolicyBody = Tr("Ads only ever render on free content, and never for students with an active paid subscription. Eligibility is decided on the server.", "تظهر الإعلانات فقط على المحتوى المجاني، ولا تظهر أبدًا للطلاب أصحاب الاشتراك المدفوع النشط. يتم تحديد الأهلية على الخادم.")
    val newPlacement = Tr("New placement", "موضع إعلاني جديد")
    val adUnitId = Tr("AdMob ad unit id", "معرّف وحدة إعلان AdMob")
    val frequency = Tr("Frequency", "التكرار")
    val displayInterval = Tr("Interval (sec)", "المدة (ثانية)")
    val addPlacement = Tr("Add placement", "إضافة موضع")
    val customScreen = Tr("Or type any screen key", "أو اكتب اسم أي شاشة")
    val sectionLabel = Tr("Slot name (for a second banner on the same screen)", "اسم الموضع (لبانر إضافي في نفس الشاشة)")
    val sectionHelp = Tr(
        "Leave empty for the screen's main banner. To add another banner on the same screen, give this one a slot name such as TOP or BOTTOM, then place it in that screen.",
        "اتركه فارغًا للبانر الرئيسي للشاشة. لإضافة بانر آخر في نفس الشاشة، امنحه اسم موضع مثل TOP أو BOTTOM ثم ضعه في تلك الشاشة.",
    )
    val customScreenHelp = Tr(
        "Use the exact key the app asks for, in CAPITALS. Anything not in the list above still works — the screen just has to request it.",
        "استخدم نفس المفتاح الذي يطلبه التطبيق بحروف كبيرة. أي شاشة خارج القائمة أعلاه تعمل أيضًا — يكفي أن تطلبها الشاشة.",
    )
    val screenAlreadyConfigured = Tr(
        "This screen and format already exist below — adding will update it.",
        "هذه الشاشة وهذا النوع موجودان بالأسفل — الإضافة ستحدّثهما.",
    )
    val liveScreensTitle = Tr("App screens", "شاشات التطبيق")
    val liveScreensBody = Tr(
        "Every screen a user browses. ● has a live ad, ○ has none yet — pick a screen below to add one.",
        "كل الشاشات اللي المستخدم بيتصفحها. ● فيها إعلان شغّال، ○ لسه مفيهاش إعلان. اختار الشاشة من تحت وضيف لها إعلان.",
    )
    val adsExcludedNote = Tr(
        "Not offered: sign-in, payment, a test in progress and a live class — ads there get tapped by mistake and annoy people at the wrong moment.",
        "مش موجودة هنا: تسجيل الدخول والدفع والاختبار الجاري والحصة المباشرة، لأن الإعلان هناك بيتضغط بالغلط ويضايق المستخدم في وقت غلط.",
    )

    // Global switches — these override every placement below them.
    val adsGlobalTitle = Tr("Master controls", "التحكم العام")
    val adsGloballyEnabled = Tr("Ads enabled across the app", "تفعيل الإعلانات في التطبيق")
    val adsGloballyEnabledHelp = Tr(
        "Turning this off hides every ad everywhere, whatever the placements below say.",
        "إيقاف هذا يخفي كل الإعلانات في كل مكان مهما كانت إعدادات المواضع بالأسفل.",
    )
    val adsFreeOnly = Tr("Never show ads on paid courses", "لا تعرض الإعلانات في الدورات المدفوعة أبدًا")
    val adsFreeOnlyHelp = Tr(
        "A master override. While it is on, paid courses stay ad-free no matter what any placement below allows. Turn it off to decide per placement.",
        "تجاوز عام. أثناء تفعيله تبقى الدورات المدفوعة بلا إعلانات مهما سمحت المواضع بالأسفل. أوقفه لتتحكم في كل موضع على حدة.",
    )

    // Per-placement editing
    val editPlacement = Tr("Edit", "تعديل")
    val closeEdit = Tr("Close", "إغلاق")
    val maxImpressions = Tr("Max per session", "الحد لكل جلسة")
    val placementFreeOnly = Tr("Free content only", "المحتوى المجاني فقط")
    val placementFreeOnlyHelp = Tr(
        "On: this placement appears on free content only. Off: it appears on paid courses too. Ignored while the master override above is on.",
        "مفعّل: يظهر هذا الموضع في المحتوى المجاني فقط. متوقف: يظهر في الدورات المدفوعة أيضًا. يُتجاهل أثناء تفعيل التجاوز العام بالأعلى.",
    )
    val deletePlacementConfirm = Tr(
        "Delete this placement? Ads will stop on that screen immediately.",
        "حذف هذا الموضع؟ ستتوقف الإعلانات في تلك الشاشة فورًا.",
    )
    val noPlacements = Tr("No placements", "لا توجد مواضع")
    val noPlacementsBody = Tr("Add a placement, then enable it when you are ready.", "أضف موضعًا ثم فعّله عندما تكون جاهزًا.")
    val enabledLabel = Tr("Enabled", "مفعّل")
    val platformSettings = Tr("Platform settings", "إعدادات المنصة")
    val settingsPolicy = Tr("Every value here applies instantly across the app — no new build required.", "كل قيمة هنا تُطبّق فورًا في التطبيق — دون الحاجة إلى إصدار جديد.")
    val valueLabel = Tr("Value", "القيمة")
    val placementTests = Tr("Placement tests", "اختبارات تحديد المستوى")
    val recentPlacementResults = Tr("Recent level placements", "تحديدات المستوى الأخيرة")
    val recentPlacementResultsBody = Tr("Students who completed a placement test today or yesterday. Older records are removed automatically.", "الطلاب الذين أنهوا اختبار تحديد المستوى اليوم أو أمس. السجلات الأقدم تُحذف تلقائيًا.")
    val noRecentPlacementResults = Tr("No recent placements", "لا توجد تحديدات مستوى حديثة")
    val scoreLabel = Tr("Score", "النتيجة")
    val newTest = Tr("New test", "اختبار جديد")
    val titleField = Tr("Title", "العنوان")
    val descriptionField = Tr("Description", "الوصف")
    val questions = Tr("Questions", "الأسئلة")
    val minutes = Tr("Minutes", "الدقائق")
    val createAdaptiveTest = Tr("Create adaptive test", "إنشاء اختبار تكيفي")
    val noTests = Tr("No tests", "لا توجد اختبارات")
    val noTestsBody = Tr("Create a test and add questions before publishing it.", "أنشئ اختبارًا وأضف الأسئلة قبل نشره.")
    val questionsInBank = Tr("Questions in bank", "أسئلة في البنك")
    val askedPerAttempt = Tr("Asked per attempt", "تُطرح لكل محاولة")
    val adaptive = Tr("Adaptive", "تكيفي")
    val yes = Tr("Yes", "نعم")
    val noWord = Tr("No", "لا")
    val question = Tr("Question", "السؤال")
    val optionsCommaSeparated = Tr("Options (comma separated)", "الخيارات (افصل بينها بفاصلة)")
    val correctOptionNumber = Tr("Correct option number (1-based)", "رقم الخيار الصحيح (يبدأ من ١)")
    val difficultyRange = Tr("Difficulty 1-6 (A1→C2)", "الصعوبة ١-٦ (A1→C2)")
    val addQuestion = Tr("Add question", "إضافة سؤال")
    val close = Tr("Close", "إغلاق")
    val unpublish = Tr("Unpublish", "إلغاء النشر")
    val reviews = Tr("Reviews", "التقييمات")
    val noReviews = Tr("No reviews", "لا توجد تقييمات")
    val noReviewsBody = Tr("Verified student reviews will appear here.", "ستظهر هنا تقييمات الطلاب الموثقة.")
    val student = Tr("Student", "طالب")
    val restore = Tr("Restore", "استعادة")
    val hide = Tr("Hide", "إخفاء")
    val moderated = Tr("Moderated", "تمت الإدارة")
    val supportTickets = Tr("Support tickets", "تذاكر الدعم")
    val noTickets = Tr("No tickets", "لا توجد تذاكر")
    val noTicketsBody = Tr("Student and teacher tickets appear here.", "تظهر هنا تذاكر الطلاب والمعلمين.")
    val assignToMe = Tr("Assign to me", "إسناد لي")
    val resolve = Tr("Resolve", "إغلاق التذكرة")
    val auditLog = Tr("Audit log", "سجل التدقيق")
    val nothingLogged = Tr("Nothing logged yet", "لا يوجد تسجيل بعد")
    val nothingLoggedBody = Tr("Sensitive actions are recorded here automatically.", "تُسجَّل الإجراءات الحساسة هنا تلقائيًا.")
    val yearsShort = Tr("%d yrs", "%d سنة")
    val studentsCount = Tr("%d students", "%d طالب")
    val plansAndGroups = Tr("%1${'$'}d plans · %2${'$'}d groups", "%1${'$'}d خطة · %2${'$'}d مجموعة")
    val processKind = Tr("Process %s", "تنفيذ %s")
    val systemActor = Tr("System", "النظام")

    // Suspended teachers
    val suspendedTeacherNote = Tr(
        "This teacher is suspended. Reactivating restores their teaching account; removing it archives their courses and returns them to a learner account.",
        "هذا المعلم موقوف. إعادة التفعيل تستعيد حسابه كمعلم؛ والحذف يؤرشف دوراته وخدماته المباشرة ويعيده حساب طالب.",
    )
    val reactivateTeacher = Tr("Reactivate teacher", "إعادة تفعيل المعلم")
    val deleteTeacher = Tr("Delete teacher", "حذف المعلم")
    val deleteTeacherConfirm = Tr(
        "Remove this teaching account? Their courses are archived and their earnings history is kept. The person keeps a learner account.",
        "هل تريد حذف حساب التدريس؟ ستُؤرشف دوراته وخدماته المباشرة مع الاحتفاظ بسجل الأرباح، ويبقى له حساب طالب.",
    )
    val ownerOnlyAction = Tr("Owner only", "للمالك فقط")

    // New teaching accounts
    val newTeacher = Tr("New teaching account", "حساب تدريس جديد")
    val newTeacherPolicy = Tr(
        "Find a registered member, set the share they keep, and turn their account into a teaching account.",
        "ابحث عن عضو مسجّل، وحدّد النسبة التي يحصل عليها، ثم حوّل حسابه إلى حساب تدريس.",
    )
    val searchMember = Tr("Search by name", "ابحث بالاسم")
    val noMatches = Tr("No matching member", "لا يوجد عضو مطابق")
    val makeTeacher = Tr("Make teacher", "تعيين معلمًا")

    // Announcements
    val announcements = Tr("Announcements", "الإشعارات العامة")
    val announcementPolicy = Tr(
        "Send only what matters. Everyone in the audience gets one notification, and it is kept in their history.",
        "أرسل ما يهم فقط. يصل إشعار واحد لكل من في الفئة المختارة ويُحفظ في سجل إشعاراتهم.",
    )
    val audience = Tr("Audience", "الفئة")
    val audienceAll = Tr("Everyone", "الجميع")
    val audienceStudents = Tr("Students", "الطلاب")
    val audienceTeachers = Tr("Teachers", "المعلمون")
    val audienceStaff = Tr("Owner & admins", "المالك والمشرفون")
    val announcementTitle = Tr("Title", "العنوان")
    val announcementBody = Tr("Message", "الرسالة")
    val sendAnnouncement = Tr("Send announcement", "إرسال الإشعار")
    val announcementSent = Tr("Sent to %d people.", "تم الإرسال إلى %d شخصًا.")

    // Question media
    val questionMedia = Tr("Media (optional)", "وسائط (اختياري)")
    val questionImageUrl = Tr("Image URL", "رابط صورة")
    val questionAudioUrl = Tr("Audio URL", "رابط صوت")
    val questionVideoUrl = Tr("Video URL", "رابط فيديو")
    val questionExplanation = Tr("Explanation shown after answering", "شرح يظهر بعد الإجابة")
    val questionKind = Tr("Answer type", "نوع الإجابة")
    val correctAnswerText = Tr("Accepted answers (comma separated)", "الإجابات المقبولة (افصل بفاصلة)")
    val publishTestFirst = Tr("Add at least one question, then publish.", "أضف سؤالًا واحدًا على الأقل ثم انشر.")
    val editTest = Tr("Edit test", "تعديل الاختبار")
    val passingScore = Tr("Passing score %", "نسبة النجاح %")

    // Coupons
    val editCoupon = Tr("Edit coupon", "تعديل الكوبون")
    val saveCoupon = Tr("Save coupon", "حفظ الكوبون")
    val deleteCoupon = Tr("Delete coupon", "حذف الكوبون")
    val deleteCouponConfirm = Tr(
        "Delete this coupon? It stops working immediately and its redemption records are removed. Paid orders keep their discount.",
        "هل تريد حذف هذا الكوبون؟ سيتوقف فورًا وتُحذف سجلات استخدامه، وتحتفظ الطلبات المدفوعة بخصمها.",
    )

    // Admins
    val adminAccess = Tr("Admin access", "صلاحيات الإدارة")
    val makeAdmin = Tr("Make admin", "تعيين مشرفًا")
    val removeAdmin = Tr("Remove admin", "إلغاء الإشراف")
    val adminsPolicy = Tr(
        "Each admin only sees what you switch on here, and you can change or revoke it at any time.",
        "يرى كل مشرف ما تفعّله له هنا فقط، ويمكنك تغييره أو إلغاؤه في أي وقت.",
    )
    val permissionsCount = Tr("%d permissions", "%d صلاحية")
    val selectAll = Tr("Select all", "تحديد الكل")
    val clearAll = Tr("Clear all", "مسح الكل")

    // Forced update
    val appUpdates = Tr("App updates", "تحديثات التطبيق")
    val forceUpdate = Tr("Force everyone to update", "إلزام الجميع بالتحديث")
    val forceUpdatePolicy = Tr(
        "While this is on, anyone running an older build sees a full-screen update notice and cannot continue until they install the new version.",
        "عند تفعيله، يرى كل من يستخدم إصدارًا أقدم شاشة تحديث كاملة ولا يمكنه المتابعة حتى يثبّت الإصدار الجديد.",
    )
    val minimumBuild = Tr("Minimum build number", "أقل رقم إصدار مسموح")
    val thisBuild = Tr("This device runs build %s", "هذا الجهاز يشغل الإصدار %s")
    val updateMessageField = Tr("Message shown to users", "الرسالة المعروضة للمستخدمين")
    val updateLinkField = Tr("Store link", "رابط المتجر")
    val saveUpdateRule = Tr("Save update rule", "حفظ قاعدة التحديث")

    // Permission labels
    val permUsersManage = Tr("Manage users & account status", "إدارة المستخدمين وحالة الحساب")
    val permTeachersManage = Tr("Approve teachers & availability", "الموافقة على المعلمين وإتاحتهم")
    val permCoursesManage = Tr("Review & publish courses", "مراجعة ونشر الدورات")
    val permNotificationsManage = Tr("Send announcements", "إرسال الإعلانات")
    val permPricingManage = Tr("Country pricing & overrides", "الأسعار حسب الدولة والاستثناءات")
    val permCouponsManage = Tr("Coupons & promotions", "الكوبونات والعروض الترويجية")
    val permStudentsManage = Tr("Manage & transfer teachers' students", "إدارة طلاب المعلمين ونقلهم")
    val permAiTutorManage = Tr("AI Tutor plans & sales", "خطط المعلّم الذكي ومبيعاته")
    val permCoursesGrant = Tr("Open paid courses for chosen users", "فتح الكورسات المدفوعة لمستخدمين محددين")
    val permFinanceRead = Tr("View financial records", "عرض السجلات المالية")
    val permFinanceManage = Tr("Refunds & chargebacks", "الاسترداد والمطالبات")
    val permPayoutsManage = Tr("Approve & pay withdrawals", "الموافقة على السحوبات وتنفيذها")
    val permReviewsManage = Tr("Moderate reviews", "إدارة التقييمات")
    val permSupportManage = Tr("Live support chat", "الدردشة المباشرة مع العملاء")
    val permTestsManage = Tr("Placement tests", "اختبارات المستوى")
    val permClassroomManage = Tr("Virtual classroom", "الفصول الافتراضية")
    val permCmsManage = Tr("Home content & banners", "محتوى الرئيسية والبانرات")
    val permAdsManage = Tr("AdMob configuration", "إعدادات AdMob")
    val permSettingsManage = Tr("Platform settings", "إعدادات المنصة")
    val permAnalyticsRead = Tr("Analytics dashboards", "لوحات التحليلات")
    val permAuditRead = Tr("Audit logs", "سجلات التدقيق")

    // ── Teacher dashboard / teacher detail (subscription control) ──────────
    // These two screens shipped with every label hardcoded in Arabic, so they
    // stayed Arabic even with the app set to English. Moved here so they obey
    // the same language switch as the rest of the app.
    val teacherDashboardTitle = Tr("Teacher & subscriptions console", "لوحة المعلم والاشتراكات")
    val netSubscriptionEarnings30 = Tr("Net subscription earnings (30 days)", "صافي أرباح الاشتراكات (٣٠ يومًا)")
    val netCourseEarnings30 = Tr("Net course earnings (30 days)", "صافي أرباح الدورات (٣٠ يومًا)")
    val netSubscriptionEarningsWeek = Tr("Weekly subscription earnings", "صافي أرباح الاشتراكات الأسبوعية")
    val netCourseEarningsWeek = Tr("Weekly course earnings", "صافي أرباح الدورات الأسبوعية")
    val step1PickTeacher = Tr("1. Choose a teacher", "١. اختر معلمًا")
    val step2SubscriptionSystem = Tr("2. Subscription system for this teacher", "٢. نظام الاشتراكات لهذا المعلم")
    val selectedTeacher = Tr("Selected teacher", "المعلم المحدد")
    val fullProfile = Tr("Full profile", "الملف الكامل")
    val changeAction = Tr("Change", "تغيير")
    val approvalRequests = Tr("Approval requests", "طلبات الموافقة")
    val approvalRequestsSub = Tr(
        "Approve activation of existing groups and subscribers only",
        "اعتماد تفعيل المجموعات والمشتركين الحاليين فقط",
    )
    val allSubsAndGroups = Tr("All subscriptions and groups", "كل الاشتراكات والجروبات")
    val allSubsAndGroupsSub = Tr(
        "View this teacher's groups and students with full control",
        "عرض جروبات وطلاب هذا المعلم مع تحكم كامل",
    )
    val subscriptionEarningsTitle = Tr("Subscription earnings", "أرباح الاشتراكات")
    val subscriptionEarningsSub = Tr(
        "This teacher's earnings and their subscription percentage",
        "أرباح هذا المعلم ونسبته من الاشتراكات",
    )
    val payoutsForThisTeacher = Tr("Withdrawal requests for this teacher only", "طلبات سحب هذا المعلم فقط")
    val pickTeacherFirst = Tr("Choose a teacher first", "اختر المعلم أولاً")
    val pickTeacherFirstBody = Tr(
        "Once selected, you'll see this teacher's approval requests, subscriptions and groups, earnings, and withdrawals.",
        "بعد الاختيار ستظهر طلبات الموافقة، الاشتراكات والجروبات، الأرباح، والسحوبات الخاصة بهذا المعلم فقط.",
    )
    val searchTeacher = Tr("Search for a teacher", "ابحث عن معلم")
    val noTeachersForSearch = Tr("No teachers", "لا يوجد معلمون")
    val noTeachersForSearchBody = Tr("No teachers matched your search", "لم يتم العثور على معلمين مطابقين للبحث")
    val teacherWord = Tr("Teacher", "معلم")
    val generalTools = Tr("General tools", "أدوات عامة")
    val pendingRequestsLabel = Tr("Pending requests", "طلبات معلقة")

    val teacherProfileTitle = Tr("Teacher profile", "ملف المعلم")
    val subscriptionsLabel = Tr("Subscriptions", "الاشتراكات")
    val noSubscriptions = Tr("No subscriptions", "لا توجد اشتراكات")
    val noSubscriptionsBody = Tr("This teacher hasn't added any subscriptions yet", "لم يقم المعلم بإضافة أي اشتراكات بعد")
    val pendingApprovalRequests = Tr("Pending approval requests", "طلبات الموافقة المعلقة")
    val noPendingRequests = Tr("No pending requests", "لا توجد طلبات معلقة")
    val noPendingRequestsBody = Tr(
        "There are no approval requests awaiting a decision right now",
        "لا توجد طلبات موافقة تنتظر القرار حالياً",
    )
    val amountLabel = Tr("Amount", "المبلغ")
    val nextRenewalLabel = Tr("Next renewal", "التجديد القادم")
    val detailsLabel = Tr("Details", "التفاصيل")
    val dateLabel = Tr("Date", "التاريخ")
    val reviewNoteLabel = Tr("Review note", "ملاحظة المراجعة")
    // Only ever bound to the teacher's SUBSCRIPTION rate (see AdminTeacherDetailScreen) — the
    // label used to say "Teacher earnings percentage" with no qualifier, which read as the
    // teacher's overall/course rate. Renamed so it can't be misread as that.
    val teacherRateLabel = Tr("Subscription earnings percentage", "نسبة أرباح المعلم من الاشتراكات")
    val courseCommissionLabel = Tr("Course earnings percentage", "نسبة أرباح المعلم من الدورات")
    val approveAction = Tr("Approve", "موافقة")
    val rejectAction = Tr("Reject", "رفض")
    val activateAction = Tr("Activate", "تفعيل")
    val rejectRequestTitle = Tr("Reject request", "رفض الطلب")
    val activateSubscriptionTitle = Tr("Activate subscription", "تفعيل الاشتراك")
    val rejectSubscriptionTitle = Tr("Reject subscription", "رفض الاشتراك")

    val actAddSubscription = Tr("Add subscription", "إضافة اشتراك")
    val actDeleteSubscription = Tr("Delete subscription", "حذف اشتراك")
    val actEditSubscription = Tr("Edit subscription", "تعديل اشتراك")
    val actAddGroup = Tr("Add group", "إضافة مجموعة")
    val actDeleteGroup = Tr("Delete group", "حذف مجموعة")
    val actRenewSubscription = Tr("Renew subscription", "تجديد اشتراك")
    val actActivateGroup = Tr("Activate group", "تفعيل مجموعة")
    val actActivateSubscription = Tr("Activate subscription", "تفعيل اشتراك")
    val groupPrefix = Tr("Group: %s", "مجموعة: %s")
    val amountEgp = Tr("%s EGP", "%s ج.م")
    val confirmRejectRequest = Tr("Reject the \"%s\" request?", "هل تريد رفض طلب %s؟")
    val shareSplit = Tr("Teachers %s · Platform %s", "نصيب المعلمين %s · نصيب المنصة %s")
    val pendingCount = Tr("%d requests", "%d طلب")
    val dueToday = Tr("Due today", "مستحق اليوم")
    val teacherEarningsOf = Tr("Teacher earnings: %s", "أرباح المعلم: %s")
    val teacherRatePercent = Tr("Teacher earnings percentage %", "نسبة أرباح المعلم %")
    val percentField = Tr("Percentage (0-100)", "النسبة (0-100)")
    val teachersShareLabel = Tr("Teachers' share", "نصيب المعلمين")
    val platformShareLabel = Tr("Platform share", "نصيب المنصة")
    val last30Days = Tr("Last 30 days", "آخر ٣٠ يومًا")
    val stepOne = Tr("Step 1", "الخطوة ١")
    val stepTwo = Tr("Step 2", "الخطوة ٢")
    val needsReview = Tr("Needs review", "بحاجة لمراجعة")
    val approvalsForTeacher = Tr("Approval requests – %s", "طلبات الموافقة – %s")
    val totalPendingForTeacher = Tr("Total pending requests for this teacher", "إجمالي الطلبات المعلقة لهذا المعلم")
    val totalPendingAll = Tr("Total pending requests", "إجمالي الطلبات المعلقة")
    val requestsLabel = Tr("Requests", "الطلبات")
    val noPendingRequestsFound = Tr(
        "No pending approval requests were found right now",
        "لم يتم العثور على أي طلبات موافقة معلقة حالياً",
    )
    val teacherDashboardSub = Tr(
        "Teachers, approvals, subscriptions and earnings — all in one place",
        "إدارة المعلمين، الموافقات، الاشتراكات، والأرباح — كل شيء في مكان واحد",
    )
    val subscriptionRatePercent = Tr("Subscription earnings percentage %", "نسبة أرباح الاشتراكات %")

    // ---- Payments: the gateway switch, the wallet numbers, and the review queue ----
    val payments = Tr("Payments", "المدفوعات")
    val paymentsSub = Tr("Gateway switch, transfer numbers, approvals", "تشغيل البوابة، أرقام التحويل، الموافقات")
    val paymobSwitchTitle = Tr("Paymob gateway", "بوابة باي موب")
    val paymobEnabled = Tr("Accept payments through Paymob", "استقبال المدفوعات عبر باي موب")
    val paymobEnabledHelp = Tr(
        "On: cards and wallets are charged automatically. Off: the four wallets stay on the payment screen, but learners transfer by hand and you approve each one.",
        "مفعّل: البطاقات والمحافظ بتتخصم أوتوماتيك. مقفول: الأربع محافظ تفضل موجودة في شاشة الدفع، لكن الطالب بيحوّل يدويًا وإنت بتوافق على كل عملية.",
    )
    val paymobOffWarning = Tr(
        "Paymob is off. Publish a number for at least one wallet below, or nobody can pay.",
        "باي موب مقفول دلوقتي. حط رقم لمحفظة واحدة على الأقل تحت، وإلا مش هيقدر حد يدفع.",
    )
    val transferNumbersTitle = Tr("Transfer numbers", "أرقام التحويل")
    val transferNumbersHelp = Tr(
        "The numbers learners send money to. A wallet with no number is never shown to them.",
        "الأرقام اللي الطلاب هيحوّلوا عليها. أي محفظة من غير رقم مش بتظهر للطالب خالص.",
    )
    val transferNumberField = Tr("Wallet number", "رقم المحفظة")
    val transferHolderField = Tr("Account holder name", "اسم صاحب المحفظة")
    val transferInstructionsField = Tr("Note for learners (optional)", "ملاحظة للطالب (اختياري)")
    val transferShown = Tr("Shown to learners", "تظهر للطلاب")
    val transferNeedsNumber = Tr("Add a number to publish this wallet", "حط رقم علشان المحفظة تظهر")
    val transferSave = Tr("Save", "حفظ")

    val reviewQueueTitle = Tr("Transfers to review", "تحويلات للمراجعة")
    val reviewQueueEmpty = Tr("Nothing waiting", "مفيش حاجة مستنية")
    val reviewQueueEmptyBody = Tr(
        "Manual transfers appear here the moment a learner sends one.",
        "التحويلات اليدوية هتظهر هنا أول ما أي طالب يبعت واحد.",
    )
    val reviewSender = Tr("Sent from", "محوَّل من")
    val reviewViewProof = Tr("View screenshot", "عرض صورة التحويل")
    val reviewProofFailed = Tr("Could not open the screenshot", "تعذّر فتح الصورة")
    val reviewNoteField = Tr("Note (shown to the learner if refused)", "ملاحظة (بتظهر للطالب لو رفضت)")
    val reviewApprove = Tr("Approve and open the course", "موافقة وفتح الدورة")
    val reviewReject = Tr("Refuse", "رفض")
    val reviewApproveConfirm = Tr(
        "Approving unlocks the course and credits the teacher. Check the amount and the sender number first.",
        "الموافقة هتفتح الدورة وتحسب نصيب المعلم. راجع المبلغ ورقم المُرسِل الأول.",
    )
}
