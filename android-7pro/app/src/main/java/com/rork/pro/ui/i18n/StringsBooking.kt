package com.rork.pro.ui.i18n

/**
 * "احجز في أقرب جروب" — the student-facing booking funnel, its home banner, and the
 * owner screen that sets the prices and availability behind it.
 */
object StrBooking {

    // ── Entry points ──────────────────────────────────────────────────────
    val bookNearestGroup = Tr("Book the nearest group", "احجز في أقرب جروب")
    val browseCourses = Tr("Browse courses", "تصفح الدورات")
    val bookNearestGroupSub = Tr(
        "Live lessons with a teacher, in a group at your level",
        "دروس مباشرة مع معلم، في جروب على مستواك",
    )
    val whatNext = Tr("What would you like to do next?", "ما الخطوة التالية؟")

    // ── Banner ────────────────────────────────────────────────────────────
    val bannerSubscribeTitle = Tr("Subscribe to the nearest group", "اشترك في أقرب جروب")
    val bannerSubscribeBody = Tr(
        "Pick your teacher and join a live group today",
        "اختر معلمك وانضم إلى جروب مباشر اليوم",
    )
    val bannerSubscribeHint = Tr("Takes under a minute", "يستغرق أقل من دقيقة")
    val tileSubscribeSub = Tr("Live classes with a real teacher", "حصص لايف مع مدرّس حقيقي")
    val bannerAwaitingPaymentTitle = Tr("Complete your payment", "أكمل عملية الدفع")
    val bannerAwaitingPaymentBody = Tr(
        "Your seat in %s is held until you pay",
        "مقعدك في %s محجوز حتى تُتم الدفع",
    )
    val bannerPendingTitle = Tr("Your booking is under review", "حجزك قيد المراجعة")
    val bannerPendingBody = Tr(
        "We're confirming your transfer — you'll be notified once it's approved",
        "نراجع التحويل الآن، وسنُخبرك فور الموافقة",
    )
    val bannerActiveTitle = Tr("You're subscribed", "أنت مشترك")
    val bannerActiveBody = Tr(
        "%1\$s with %2\$s · renews on %3\$s",
        "%1\$s مع %2\$s · التجديد في %3\$s",
    )
    val bannerRenewTitle = Tr("Renew your subscription now", "جدد اشتراكك الآن")
    val bannerRenewBody = Tr(
        "Your seat in %s ends soon — keep it active",
        "اشتراكك في %s على وشك الانتهاء — حافظ على مكانك",
    )
    val bannerRenewPayTitle = Tr("Pay to complete your renewal", "ادفع لإتمام التجديد")
    val bannerRenewPendingTitle = Tr("Renewal under review", "التجديد قيد المراجعة")
    val bannerRejectedTitle = Tr("Your booking wasn't confirmed", "لم يتم تأكيد حجزك")
    val bannerRejectedBody = Tr("Tap to book again", "اضغط للحجز من جديد")

    // ── Step 1 — teacher ──────────────────────────────────────────────────
    val title = Tr("Book a group", "حجز جروب")
    val stepTeacher = Tr("Choose your teacher", "اختر المعلم")
    val stepGroup = Tr("Choose your group", "اختر الجروب")
    val stepDetails = Tr("Your details", "بياناتك")
    val stepPay = Tr("Payment", "الدفع")
    val recommendedForYou = Tr("Recommended for you", "المقترح لك")
    val availableTeachers = Tr("Available teachers", "المعلمون المتاحون")
    val noTeachers = Tr("No teachers are open for booking", "لا يوجد معلمون متاحون للحجز")
    val noTeachersBody = Tr(
        "New groups open regularly — please check back soon.",
        "تُفتح جروبات جديدة باستمرار، حاول لاحقًا.",
    )
    val groupsCount = Tr("%d groups", "%d جروب")
    val studentsCount = Tr("%d students", "%d طالب")

    // ── Step 2 — group ────────────────────────────────────────────────────
    val noGroups = Tr("No open groups for this teacher", "لا توجد جروبات مفتوحة لهذا المعلم")
    val noGroupsBody = Tr("Try another teacher.", "جرّب معلمًا آخر.")
    val seatsLeft = Tr("%d seats left", "باقي %d مقعد")
    val groupFull = Tr("Full", "مكتمل")
    val members = Tr("%d enrolled", "%d ملتحق")
    val perMonth = Tr("%s / month", "%s / شهريًا")

    // ── Step 3 — details ──────────────────────────────────────────────────
    val detailsIntro = Tr(
        "The teacher needs these to add the student to the group.",
        "يحتاج المعلم هذه البيانات لإضافة الطالب إلى الجروب.",
    )
    val detailsHeroTitle = Tr("One step away from a seat", "خطوة واحدة تفصلك عن حجز المكان")
    val detailsHeroBody = Tr(
        "Fill in the details below and the seat is requested in under a minute.",
        "املأ البيانات دي وهيتم طلب حجز المكان في أقل من دقيقة.",
    )
    val trustSecure = Tr("Your data stays private", "بياناتك محفوظة وسرية")
    val trustFast = Tr("Instant seat request", "حجز فوري للمكان")
    val trustQualified = Tr("Certified teachers", "معلمون معتمدون")
    val parentName = Tr("Parent's name", "اسم ولي الأمر")
    val parentPhone = Tr("Parent's phone number", "رقم هاتف ولي الأمر")
    val studentName = Tr("Student's name", "اسم الطالب")
    val studentNameN = Tr("Student %s's name", "اسم الطالب %s")
    val addStudent = Tr("Add another student", "إضافة طالب آخر")
    val removeStudent = Tr("Remove this student", "إزالة الطالب ده")
    val studentsSummary = Tr("%1\$s students × %2\$s", "%1\$s طلاب × %2\$s")
    val studentsTotal = Tr("Total", "الإجمالي")
    val seatsLimit = Tr("Only %s seats are left in this group.", "المتاح في الجروب %s مقاعد بس.")
    val studentsLine = Tr("Students: %s", "الطلاب: %s")
    val studentsCountSuffix = Tr("%s students", "%s طلاب")
    val errDuplicateStudent = Tr("Two of the names are the same. Write each student once.", "فيه اسمين متكررين. اكتب كل طالب مرة واحدة.")
    val errTooMany = Tr("You can book up to 5 students at a time.", "تقدر تحجز لحد 5 طلاب في المرة.")
    val errPartialFull = Tr("There aren't enough seats left for all these students.", "الأماكن المتاحة مش كفاية لكل الطلاب دول.")
    val phoneHint = Tr("01xxxxxxxxx", "01xxxxxxxxx")
    val phoneInvalid = Tr("Enter a valid Egyptian mobile number", "أدخل رقم موبايل مصري صحيح")
    val fieldRequired = Tr("Required", "مطلوب")
    val noteOptional = Tr("Anything else the teacher should know (optional)", "أي ملاحظة للمعلم (اختياري)")
    val continueToPayment = Tr("Continue to payment", "المتابعة إلى الدفع")
    val payNextHint = Tr("Next: secure payment", "الخطوة التالية: الدفع الآمن")

    // ── Step 4 — payment ──────────────────────────────────────────────────
    val payTitle = Tr("Pay for your seat", "ادفع قيمة اشتراكك")
    val payForSeat = Tr(
        "You're paying to book the nearest group with %1\$s — %2\$s.",
        "أنت تدفع لحجز أقرب جروب مع المعلم %1\$s — %2\$s.",
    )
    val payForRenewal = Tr(
        "You're paying to renew your seat with %1\$s — %2\$s.",
        "أنت تدفع لتجديد اشتراكك مع المعلم %1\$s — %2\$s.",
    )
    val chooseWallet = Tr("Choose a wallet", "اختر المحفظة")
    val walletsUnavailable = Tr(
        "No wallet is published for transfers yet. Please contact support.",
        "لا توجد محفظة متاحة للتحويل حاليًا. تواصل مع الدعم.",
    )
    val amountDue = Tr("Amount due", "المبلغ المستحق")
    val sendProof = Tr("Send transfer proof", "إرسال إثبات التحويل")
    val afterPaymentNote = Tr(
        "Your seat is confirmed once the owner approves the transfer.",
        "يتم تأكيد مقعدك فور موافقة الإدارة على التحويل.",
    )

    // ── Result ────────────────────────────────────────────────────────────
    val sentTitle = Tr("Transfer sent for review", "تم إرسال التحويل للمراجعة")
    val sentBody = Tr(
        "The owner will confirm it shortly. You'll be added to %1\$s with %2\$s as soon as it's approved.",
        "ستتم مراجعته قريبًا. وسيتم إضافتك إلى %1\$s مع %2\$s فور الموافقة.",
    )
    val backHome = Tr("Back to home", "العودة للرئيسية")

    // ── Errors ────────────────────────────────────────────────────────────
    val errAlreadySubscribed = Tr(
        "One of these students is already booked in this group.",
        "واحد من الطلاب دول محجوز بالفعل في الجروب ده.",
    )
    val errGroupUnavailable = Tr("This group is no longer open.", "هذا الجروب لم يعد متاحًا.")
    val errTeacherUnavailable = Tr("This teacher is not available right now.", "هذا المعلم غير متاح حاليًا.")
    val errGroupFull = Tr("This group is full.", "هذا الجروب مكتمل.")
    val errPriceNotSet = Tr(
        "No price has been set for this group yet.",
        "لم يتم تحديد سعر لهذا الجروب بعد.",
    )
    val errNothingToPay = Tr("There's nothing to pay for right now.", "لا يوجد مبلغ مستحق حاليًا.")
    val errNotDueYet = Tr("Your renewal isn't due yet.", "لم يحن موعد التجديد بعد.")

    // ── Owner / admin ─────────────────────────────────────────────────────
    val adminTitle = Tr("Booking & prices", "الحجز والأسعار")
    val adminSubtitle = Tr(
        "Set what a seat costs and who is open for booking.",
        "حدد سعر المقعد ومَن المتاح للحجز.",
    )
    val adminTeacherAvailable = Tr("Available for booking", "متاح للحجز")
    val adminGroupOpen = Tr("Open for booking", "مفتوح للحجز")
    val startsFrom = Tr("Starts from", "تبدأ من")
    val adminPrice = Tr("Price per cycle", "سعر الدورة")
    val adminCurrency = Tr("Currency", "العملة")
    val adminCapacity = Tr("Capacity (0 = unlimited)", "السعة (0 = غير محدودة)")
    val adminSchedule = Tr("Schedule (shown to students)", "المواعيد (تظهر للطلاب)")
    val adminSave = Tr("Save", "حفظ")
    val adminSaved = Tr("Saved", "تم الحفظ")
    val adminNoPrice = Tr("No price set", "بدون سعر")
    val adminDefaultPrice = Tr("Default price for unpriced groups", "السعر الافتراضي للجروبات بدون سعر")
    val adminNoGroups = Tr("No groups yet", "لا توجد جروبات بعد")
    val adminNoGroupsBody = Tr(
        "Groups created by teachers and approved by you appear here.",
        "تظهر هنا الجروبات التي ينشئها المعلمون وتوافق عليها.",
    )
    val adminPriceChangeNote = Tr(
        "Changing a price affects new bookings only; approved subscriptions keep the price they were approved at.",
        "تغيير السعر يؤثر على الحجوزات الجديدة فقط؛ الاشتراكات المعتمدة تحتفظ بسعرها.",
    )

    // ── Admin — transfer review ───────────────────────────────────────────
    val adminSubTransfers = Tr("Group subscription transfers", "تحويلات اشتراكات الجروبات")
    val adminSubTransfersEmpty = Tr("No subscription transfers waiting", "لا توجد تحويلات اشتراكات بانتظار المراجعة")
    val adminKindNew = Tr("New booking", "حجز جديد")
    val adminKindRenewal = Tr("Renewal", "تجديد")
    val adminApproveHint = Tr(
        "Approving adds the student to the teacher's group and records the earnings.",
        "الموافقة تضيف الطالب إلى جروب المعلم وتُسجّل الأرباح تلقائيًا.",
    )

    // The course queue's own approve copy talks about unlocking a course, which is not what
    // this decision does — so this queue gets wording that matches what it really acts on.
    val adminApproveSeat = Tr("Approve and seat the student", "موافقة وإضافة الطالب للجروب")
    val adminApproveSeatConfirm = Tr(
        "Approving seats the student with this teacher and records the subscription earning. Check the amount and the sender number first.",
        "الموافقة هتضيف الطالب لجروب المعلم وتسجّل ربح الاشتراك. راجع المبلغ ورقم المُرسِل الأول.",
    )

    // ── Admin — how teachers are presented on the booking screen ──────────
    val showcaseTitle = Tr("Teacher display order", "ترتيب المعلمين")
    val showcaseSubtitle = Tr(
        "Arrange who students see first, award the premium badge, and set each teacher's picture.",
        "رتّب من يظهر أولًا للطلاب، وامنح شعار premium، واضبط صورة كل معلم.",
    )
    val showcaseMoveUp = Tr("Move up", "تحريك لأعلى")
    val showcaseMoveDown = Tr("Move down", "تحريك لأسفل")
    val showcaseMoveTop = Tr("Move to top", "نقل للأول")
    val showcaseMoveBottom = Tr("Move to bottom", "نقل للآخر")
    val showcaseSaveOrder = Tr("Save order", "حفظ الترتيب")
    val showcaseUnsaved = Tr("Order not saved yet", "الترتيب لم يُحفظ بعد")
    val showcaseOrderSaved = Tr("Order saved", "تم حفظ الترتيب")
    val showcasePosition = Tr("#%d", "#%d")
    val showcasePremium = Tr("Premium badge", "شعار premium")
    val showcaseBadgeLabel = Tr("Badge text (optional)", "نص الشعار (اختياري)")
    val showcaseBadgeHint = Tr(
        "Leave empty to show the default PREMIUM badge.",
        "اتركه فارغًا ليظهر شعار PREMIUM الافتراضي.",
    )
    val showcaseBadgeDefault = Tr("PREMIUM", "PREMIUM")
    val showcaseHidden = Tr("Not shown to students", "لا يظهر للطلاب")
    val showcaseHiddenWhy = Tr(
        "A teacher appears only with an open group and availability switched on.",
        "المعلم يظهر فقط عند وجود جروب مفتوح وتفعيل الإتاحة للحجز.",
    )
    val showcaseEditPhoto = Tr("Change photo", "تغيير الصورة")
    val showcaseGroupPhotos = Tr("Group pictures", "صور الجروبات")
    val showcaseGroupPhoto = Tr("Group picture", "صورة الجروب")

    /** Default wording on the student-facing premium badge when the owner sets no custom text. */
    val premiumBadge = Tr("PREMIUM", "PREMIUM")
}
