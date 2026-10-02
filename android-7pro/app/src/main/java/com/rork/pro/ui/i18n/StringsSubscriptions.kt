package com.rork.pro.ui.i18n

/** Subscriptions feature strings. */
object StrSub {
    // Screen titles
    val subscriptions = Tr("Subscriptions", "الاشتراكات")
    val mySubscriptions = Tr("My subscriptions", "اشتراكاتي")
    val allSubscriptions = Tr("All subscriptions", "كل الاشتراكات")
    val newSubscription = Tr("New subscription", "اشتراك جديد")
    val editSubscription = Tr("Edit subscription", "تعديل الاشتراك")

    // Fields
    val groupName = Tr("Group name", "اسم المجموعة")
    val parentName = Tr("Parent name", "اسم ولي الأمر")
    val parentPhone = Tr("Parent phone", "هاتف ولي الأمر")
    val studentName = Tr("Student name", "اسم الطالب")
    val startDate = Tr("Start date", "تاريخ البداية")
    val monthlyAmount = Tr("Monthly amount", "المبلغ الشهري")
    val nextRenewalDate = Tr("Next renewal date", "تاريخ التجديد القادم")
    val status = Tr("Status", "الحالة")
    val notes = Tr("Notes", "ملاحظات")
    val currency = Tr("Currency", "العملة")
    val teacherId = Tr("Teacher ID", "معرّف المعلم")

    // Actions
    val level = Tr("Level", "المستوى")
    val renew = Tr("Renewed", "تم التجديد")
    val renewConfirm = Tr("Renew this subscription?", "هل تريد تجديد هذا الاشتراك؟")
    val deleteSubscription = Tr("Delete subscription", "حذف الاشتراك")
    val deleteConfirm = Tr("This action cannot be undone.", "لا يمكن التراجع عن هذا الإجراء.")
    val saveSubscription = Tr("Save subscription", "حفظ الاشتراك")
    val createSubscription = Tr("Create subscription", "إنشاء اشتراك")
    val renewalHistory = Tr("Renewal history", "سجل التجديدات")

    // Statuses
    val statusActive = Tr("Active", "نشط")
    val statusDue = Tr("Due", "مستحق")
    val statusOverdue = Tr("Overdue", "متأخر")
    val statusPaused = Tr("Paused", "متوقف")
    val statusAutoHint = Tr(
        "Active / due / overdue is set automatically from the renewal date.",
        "الحالة (نشط / مستحق / متأخر) تُحدَّد تلقائيًا من تاريخ التجديد.",
    )

    // Stats
    val totalSubscriptions = Tr("Total subscriptions", "إجمالي الاشتراكات")
    val dueThisWeek = Tr("Due this week", "مستحق هذا الأسبوع")
    val overdue = Tr("Overdue", "متأخر")
    val overdueCount = Tr("%d overdue", "%d متأخر")
    val totalMonthlyValue = Tr("Total monthly value", "القيمة الشهرية الإجمالية")

    // Search
    val searchPlaceholder = Tr("Search by student, parent, or group...", "بحث بالطالب أو ولي الأمر أو المجموعة...")

    // Empty states
    val noSubscriptions = Tr("No subscriptions yet", "لا توجد اشتراكات بعد")
    val noSubscriptionsBody = Tr("Add your first subscription to start tracking.", "أضف أول اشتراك للبدء في التتبع.")
    val noRenewals = Tr("No renewal history", "لا يوجد سجل تجديدات")
    val noRenewalsBody = Tr("Renewal records will appear here.", "ستظهر هنا سجلات التجديدات.")

    // Summary
    val perMonth = Tr("/ month", "/ شهريًا")
    val from = Tr("From", "من")
    val until = Tr("Until", "حتى")
    val renewedOn = Tr("Renewed on", "تم التجديد في")
    val previousDate = Tr("Previous date", "التاريخ السابق")
    val newDate = Tr("New date", "التاريخ الجديد")

    // Filters
    val allStatuses = Tr("All statuses", "كل الحالات")
    val activeOnly = Tr("Active", "نشط")
    val dueOnly = Tr("Due", "مستحق")
    val overdueOnly = Tr("Overdue", "متأخر")
    val pausedOnly = Tr("Paused", "متوقف")

    // Notifications / reminders
    val reminderTitle = Tr("Subscription renewal reminder", "تذكير بتجديد الاشتراك")
    val reminderBody7 = Tr("Subscription for %s is due in 7 days", "اشتراك %s مستحق خلال ٧ أيام")
    val reminderBody3 = Tr("Subscription for %s is due in 3 days", "اشتراك %s مستحق خلال ٣ أيام")
    val reminderBodyToday = Tr("Subscription for %s is due today", "اشتراك %s مستحق اليوم")

    // ── Earnings ───────────────────────────────────────────────────────────
    val subscriptionEarnings = Tr("Subscription earnings", "أرباح الاشتراكات")
    val mySubscriptionEarnings = Tr("My subscription earnings", "أرباح اشتراكاتي")
    val allSubscriptionEarnings = Tr("All subscription earnings", "أرباح كل الاشتراكات")
    val totalEarnings = Tr("Total earnings", "إجمالي الأرباح")
    val teacherShare = Tr("Teacher share", "حصة المعلم")
    val ownerShare = Tr("Owner share", "حصة المالك")
    val teacherPercentage = Tr("Teacher percentage", "نسبة المعلم")
    val ownerPercentage = Tr("Owner percentage", "نسبة المالك")
    val setPercentage = Tr("Set percentage", "تعيين النسبة")
    val percentageLocked = Tr("Locked at subscription creation", "مثبتة عند إنشاء الاشتراك")
    val byGroup = Tr("By group", "حسب المجموعة")
    val groupBreakdown = Tr("Group breakdown", "تفصيل المجموعات")
    val today = Tr("Today", "اليوم")
    val thisWeek = Tr("This week", "هذا الأسبوع")
    val thisMonth = Tr("This month", "هذا الشهر")
    val customPeriod = Tr("Custom period", "فترة مخصصة")
    val fromDate = Tr("From date", "من تاريخ")
    val toDate = Tr("To date", "إلى تاريخ")
    val apply = Tr("Apply", "تطبيق")
    val savedReports = Tr("Saved reports", "التقارير المحفوظة")
    val generateReport = Tr("Generate report", "إنشاء تقرير")
    val reportGenerated = Tr("Report generated for %s", "تم إنشاء التقرير لـ %s")
    val noEarnings = Tr("No earnings yet", "لا توجد أرباح بعد")
    val noEarningsBody = Tr("Earnings will appear when subscriptions are renewed.", "ستظهر الأرباح عند تجديد الاشتراك.")
    val noReports = Tr("No saved reports", "لا توجد تقارير محفوظة")
    val noReportsBody = Tr("Generate a report to save monthly earnings data.", "أنشئ تقريرًا لحفظ بيانات أرباح الشهر.")
    val earningsDetail = Tr("Earnings detail", "تفاصيل الأرباح")
    val subscriptionEarningsRate = Tr("Subscription earnings rate", "نسبة أرباح الاشتراك")
    val currentRate = Tr("Current rate", "النسبة الحالية")
    val percentageSymbol = Tr("%%", "%%")

    // ── Groups ─────────────────────────────────────────────────────────────
    val groups = Tr("Groups", "المجموعات")
    val myGroups = Tr("My groups", "مجموعاتي")
    val allGroups = Tr("All groups", "كل المجموعات")
    val groupDetails = Tr("Group details", "تفاصيل المجموعة")
    val groupMembers = Tr("members", "أعضاء")
    val noGroups = Tr("No groups yet", "لا توجد مجموعات بعد")
    val noGroupsBody = Tr("Create your first group to start managing subscriptions.", "أنشئ مجموعتك الأولى لإدارة الاشتراكات.")
    val newGroup = Tr("New group", "مجموعة جديدة")
    val createGroup = Tr("Create group", "إنشاء مجموعة")
    val deleteGroup = Tr("Delete group", "حذف المجموعة")
    val deleteGroupConfirm = Tr("Delete this group and all its subscriptions? This cannot be undone.", "هل تريد حذف هذه المجموعة وجميع اشتراكاتها؟ لا يمكن التراجع.")
    val createFirstPerson = Tr("Add first person", "أضف أول شخص")
    val addPerson = Tr("Add person", "إضافة شخص")
    val personName = Tr("Person name", "اسم الشخص")
    val upcomingRenewals = Tr("Upcoming renewals", "مواعيد التجديد القادمة")
    val upcomingRenewalsBody = Tr("Renewals sorted by nearest date", "التجديدات مرتبة من الأقرب")
    val noRenewalsDue = Tr("No upcoming renewals", "لا توجد تجديدات قادمة")
    val noRenewalsDueBody = Tr("All subscriptions are up to date.", "جميع الاشتراكات محدّثة.")
    val peopleInGroup = Tr("People in %s", "أعضاء في %s")
    val groupTotal = Tr("Group total", "إجمالي المجموعة")
    val totalPeople = Tr("People", "الأشخاص")
    val monthlyPerPerson = Tr("per person", "لكل شخص")
    val totalGroups = Tr("Total groups", "إجمالي المجموعات")
    val dueThisMonth = Tr("Due this month", "مستحق هذا الشهر")
    val searchGroup = Tr("Search for group", "بحث عن مجموعة")
    val tomorrow = Tr("Tomorrow", "غداً")
    val within7Days = Tr("Within 7 days", "خلال 7 أيام")
    val within30Days = Tr("Within 30 days", "خلال 30 يوم")
    val dueToday = Tr("Due today", "مستحق اليوم")
    val totalExpected = Tr("Total expected", "إجمالي المتوقع")
    val payNow = Tr("Pay now", "تسجيل الدفع")
val renewsInDays = Tr("Renews in %s days", "يتجدد بعد %s يوم")
val renewsOn = Tr("Ends and renews on", "ينتهي ويتجدد في")
val renewRuleNote = Tr(
    "Renewal opens on this date — never before — and always adds exactly one month. Renewing sets both the start and end date.",
    "التجديد بيفتح في الموعد ده مش قبله، ودايمًا بيضيف شهر واحد بس. وبعد التجديد بيتحدّث تاريخ البداية والانتهاء مع بعض.",
)
    val copyRenewalLink = Tr("Copy renewal link", "نسخ رابط التجديد")
    val renewalLinkCopied = Tr("Renewal link copied", "تم نسخ رابط التجديد")
    val renewalLinkNoAccount = Tr(
        "Link the student's account first (Edit) — the renewal link only works with the student's own account.",
        "اربط حساب الطالب الأول (تعديل) — رابط التجديد بيشتغل بحساب الطالب نفسه فقط.",
    )
    val daysLeft = Tr("days left", "أيام متبقية")
    val dayLeft = Tr("day left", "يوم متبق")
    val overdueDay = Tr("Overdue by %d day", "متأخر %d يوم")
    val overdueDays = Tr("Overdue by %d days", "متأخر %d أيام")
    val renewalDate = Tr("Renewal: %s", "التجديد: %s")
    val membersCount = Tr("%d members", "%d أعضاء")

    // ── Approval status badges (shown on the item itself) ─────────────────
    val inactiveLabel = Tr("Inactive", "غير مفعلة")
    val awaitingApproval = Tr("Awaiting approval", "في انتظار الموافقة")
    val approvedLabel = Tr("Active", "نشطة")
    val rejectedLabel = Tr("Rejected", "مرفوضة")

    // ── Group activation ───────────────────────────────────────────────────
    val activateGroup = Tr("Activate group", "تفعيل المجموعة")
    val editGroup = Tr("Edit group", "تعديل المجموعة")
    val activateGroupConfirm = Tr(
        "Send this group to the owner for approval? It will not count toward earnings until it is approved.",
        "هل تريد إرسال هذه المجموعة إلى المالك للموافقة؟ لن تحتسب في الأرباح حتى تتم الموافقة.",
    )
    val groupInactiveHint = Tr(
        "This group is inactive — it does not count toward earnings until the owner approves it.",
        "هذه المجموعة غير مفعلة — لا تحتسب في الأرباح حتى يوافق المالك عليها.",
    )
    val groupPendingHint = Tr(
        "This group is awaiting owner approval.",
        "هذه المجموعة في انتظار موافقة المالك.",
    )
    val groupRejectedHint = Tr(
        "This group was rejected. You can edit it and request activation again.",
        "تم رفض هذه المجموعة. يمكنك تعديلها وطلب التفعيل مرة أخرى.",
    )
    val groupNotActive = Tr("Group inactive", "المجموعة غير مفعلة")

    // ── Per-subscription activation ──────────────────────────────────────
    val activateSubscription = Tr("Activate", "تفعيل")
    val activateSubscriptionConfirm = Tr(
        "Send this subscription for owner approval? Earnings will not count until approved.",
        "هل تريد إرسال هذا الاشتراك للمالك للموافقة؟ لن تحتسب الأرباح حتى تتم الموافقة.",
    )
    val subscriptionPendingHint = Tr(
        "This subscription is awaiting owner approval.",
        "هذا الاشتراك في انتظار موافقة المالك.",
    )
    val subscriptionRejectedHint = Tr(
        "This subscription was rejected. You can request activation again.",
        "تم رفض هذا الاشتراك. يمكنك طلب التفعيل مرة أخرى.",
    )
    val subscriptionInactiveHint = Tr(
        "This subscription does not count toward earnings until the owner approves it.",
        "هذا الاشتراك لا تحتسب في الأرباح حتى يوافق المالك عليه.",
    )
    val pendingSubscriptions = Tr("Pending subscriptions", "اشتراكات معلقة")
    val pendingActivationHint = Tr(
        "Some subscriptions are awaiting approval. Earnings only count after approval.",
        "بعض الاشتراكات في انتظار الموافقة. الأرباح لا تحتسب إلا بعد الموافقة.",
    )

    val dashboard = Tr("Subscriptions dashboard", "لوحة الاشتراكات")
    val myDashboard = Tr("My unified dashboard", "لوحتي الموحّدة")

    val editTeacherRate = Tr("Edit this teacher's percentage", "تعديل نسبة هذا المعلم")
    val setTeacherRate = Tr("Set this teacher's percentage", "تحديد نسبة هذا المعلم")
    val teacherListFailed = Tr(
        "Couldn't load the teacher list. Pull down to retry.",
        "تعذّر تحميل قائمة المعلمين. اسحب للأسفل لإعادة المحاولة.",
    )

    // ── Teacher dashboard (was entirely hardcoded Arabic) ──────────────────
    val tabSubscriptions = Tr("Subscriptions", "الاشتراكات")
    val tabSubscriptionsSub = Tr("My subscriptions", "اشتراكاتي")
    val tabRequests = Tr("Requests", "الطلبات")
    val tabRequestsSub = Tr("My pending requests", "طلباتي المعلقة")
    // Used to only show subscription income, with a hint pointing elsewhere for course income —
    // but nothing ever actually showed course income anywhere else, so the tab now shows both,
    // each under its own clearly labeled section (see TeacherEarningsTab) instead of promising a
    // screen that didn't exist.
    val tabEarnings = Tr("Earnings", "الأرباح")
    val tabEarningsSub = Tr("Courses & subscriptions", "الدورات والاشتراكات")
    val courseEarningsSectionTitle = Tr("Course earnings", "أرباح الدورات")
    val subscriptionEarningsSectionTitle = Tr("Subscription earnings", "أرباح الاشتراكات")
    val tabPayouts = Tr("Payouts", "السحوبات")
    val tabPayoutsSub = Tr("My payouts", "سحوباتي")
    val dashboardTitle = Tr("Dashboard", "لوحة التحكم")
    val refreshAction = Tr("Refresh", "تحديث")

    val activeShort = Tr("Active", "نشط")
    val pendingShort = Tr("Pending", "قيد الانتظار")
    val awaitingReply = Tr("Awaiting reply", "ينتظر الرد")
    val monthlyIncome = Tr("Monthly income", "الدخل الشهري")
    val myAvailableBalance = Tr("My available balance", "رصيدي المتاح")
    val detailedReports = Tr("Detailed reports", "تقارير مفصّلة")
    val renewalDates = Tr("Renewal dates", "مواعيد التجديد")
    val manageGroups = Tr("Manage groups", "إدارة المجموعات")
    val yourRatePerSubscription = Tr("Your share of every subscription: %d%%", "نسبة أرباحك من كل اشتراك: %d%%")
    val detailsAction = Tr("Details", "تفاصيل")

    val searchStudentOrGroup = Tr("Search for a student or group", "ابحث عن طالب أو مجموعة")
    val noSubscriptionsTitle = Tr("No subscriptions", "لا توجد اشتراكات")
    val noSubscriptionsForSearch = Tr("No subscriptions match this search", "لا توجد اشتراكات لهذا البحث")
    val addFirstSubscriptionBody = Tr("Add your first student subscription and it will show up here", "أضف أول اشتراك لطلابك ليظهر هنا")
    val addNewSubscription = Tr("Add a new subscription", "إضافة اشتراك جديد")
    val renewAction = Tr("Renew", "تجديد")

    val noPendingRequestsTitle = Tr("No pending requests", "لا توجد طلبات معلقة")
    val noPendingRequestsBody2 = Tr("Your requests are processed or awaiting admin review", "طلباتك مُعالجَة أو في انتظار مراجعة الإدارة")
    val myPendingRequestsCount = Tr("My pending requests (%d)", "طلباتي المعلقة (%d)")
    val reqActivateSubscription = Tr("Activate subscription", "تفعيل اشتراك")
    val reqActivateGroup = Tr("Activate group", "تفعيل مجموعة")
    val reqAddSubscription = Tr("Add subscription", "إضافة اشتراك")
    val reqAddGroup = Tr("Add group", "إضافة مجموعة")
    val studentLabel = Tr("Student", "الطالب")
    val groupLabel = Tr("Group", "المجموعة")
    val groupWord = Tr("Group", "مجموعة")

    val myTotalEarnings = Tr("My total subscription earnings", "إجمالي أرباحي من الاشتراكات")
    val allPeriods = Tr("All periods", "كل الفترات")
    val platformTotal = Tr("Platform total (subscriptions)", "إجمالي المنصة من الاشتراكات")
    val platformNet = Tr("Platform net", "صافي المنصة")
    val earningsByPeriod = Tr("Earnings by period", "الأرباح حسب الفترة")
    val totalWord = Tr("Total", "الإجمالي")
    // Quick view on the dashboard now shows only the teacher's own week/month share (server-
    // computed, same source as the full report) — everything else lives on one dedicated
    // screen instead of being duplicated here, to keep the numbers consistent everywhere.
    val viewFullReport = Tr("View full earnings report", "عرض تقرير الأرباح الكامل")
    val noEarningsYet = Tr("No earnings yet", "لا توجد أرباح بعد")
    val noEarningsYetBody = Tr("Earnings appear once your first subscription is activated", "تبدأ الأرباح تظهر بعد تفعيل اول اشتراك")

    val availableBalance = Tr("Available balance", "الرصيد المتاح")
    val canWithdraw = Tr("Withdrawable", "يمكن السحب")
    val pendingPayouts = Tr("Pending payouts", "سحوبات معلقة")
    val totalPayouts = Tr("Total payouts", "إجمالي السحوبات")
    val transferred = Tr("Transferred", "تم تحويلها")
    val withdrawConfirmBody = Tr("Withdraw your available balance?", "هل تريد سحب الرصيد المتاح؟")
    val withdrawBalance = Tr("Withdraw balance", "سحب الرصيد")
    val minimumPayoutIs = Tr("Minimum payout %s", "الحد الأدنى للسحب %s")
    val payoutHistory = Tr("Payout history", "سجل السحوبات")
    val noPayouts = Tr("No payouts", "لا توجد سحوبات")
    val noPayoutsBody = Tr("You haven't requested any payouts yet", "لم تطلب أي سحوبات حتى الآن")
    val meWord = Tr("Me", "أنا")

    val payoutRequestTitle = Tr("Payout request", "طلب سحب")
    val availableBalanceIs = Tr("Available balance: %s", "الرصيد المتاح: %s")
    val minimumPayoutIsColon = Tr("Minimum payout: %s", "الحد الأدنى للسحب: %s")
    val amountWithCurrency = Tr("Amount (%s)", "المبلغ (%s)")
    val bankTransfer = Tr("Bank transfer", "تحويل بنكي")
    val wallet = Tr("Wallet", "محفظة")
    val accountOrWalletNumber = Tr("Account or wallet number", "رقم الحساب أو رقم المحفظة")
    val noteOptional = Tr("Note (optional)", "ملاحظة (اختياري)")
    val confirmWithdraw = Tr("Confirm withdrawal", "تأكيد السحب")
    val cancelAction = Tr("Cancel", "إلغاء")

    // Add-student account link (replaces manual "level" entry in the add-student form)
    val studentAccount = Tr("Student's app account", "حساب الطالب في التطبيق")
    val searchByEmailOrName = Tr("Search by email or name", "ابحث بالإيميل أو الاسم")
    val noAccountsFound = Tr("No matching accounts found", "لا يوجد حساب مطابق")
    val accountLinked = Tr("Linked account", "الحساب المرتبط")
    val accountNotLinkedYet = Tr("Not linked to an app account yet", "غير مرتبط بحساب في التطبيق بعد")
    val clearLinkedAccount = Tr("Clear", "إزالة")
}
