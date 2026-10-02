package com.rork.pro.ui.i18n

/** Profile hub, orders, certificates, notifications, support, teacher application, edit profile. */
object StrProfile {
    val profile = Tr("Profile", "حسابي")
    val learner = Tr("Learner", "متعلم")
    val editProfile = Tr("Edit profile", "تعديل الملف الشخصي")
    val profilePhoto = Tr("Profile photo", "صورة الحساب")
    val photoHint = Tr(
        "Pick a picture from your phone. It is saved to your account.",
        "اختر صورة من هاتفك. سيتم حفظها في حسابك.",
    )
    val changePhoto = Tr("Change photo", "تغيير الصورة")
    val addPhoto = Tr("Add photo", "إضافة صورة")
    val removePhoto = Tr("Remove", "إزالة")
    val uploadingPhoto = Tr("Uploading…", "جارٍ الرفع…")
    val photoSaved = Tr("Photo updated.", "تم تحديث الصورة.")
    val noLevelYet = Tr("No level yet", "لا يوجد مستوى بعد")
    val student = Tr("Student", "طالب")
    val maintenanceOn = Tr("Maintenance mode is on", "وضع الصيانة مفعّل")
    val learning = Tr("Learning", "التعلّم")
    val placementTest = Tr("Placement test", "اختبار تحديد المستوى")
    val placementTestSub = Tr("Take or retake your level test", "ابدأ أو أعد اختبار المستوى")
    val certificates = Tr("Certificates", "الشهادات")
    val certificatesSub = Tr("Your issued 7PRO certificates", "شهادات 7PRO الصادرة لك")
    val paymentsOrders = Tr("Payments & orders", "المدفوعات والطلبات")
    val paymentsOrdersSub = Tr("Your purchase history", "سجل مشترياتك")
    val notifications = Tr("Notifications", "الإشعارات")
    val notificationSettings = Tr("Notification settings", "إعدادات الإشعارات")
    val notificationSettingsSub = Tr("Choose what appears on your phone", "اختر أنواع الإشعارات التي تظهر على هاتفك")
    val notificationSettingsBody = Tr("Turn categories on or off. Changes apply to this account on every device.", "فعّل أو عطّل الأنواع. تُطبّق التغييرات على هذا الحساب في كل الأجهزة.")
    val alertSound = Tr("Alert sound", "صوت التنبيهات")
    val alertSoundBody = Tr("Play a sound for new messages and alerts on this device. Notifications still appear when it is off.", "تشغيل صوت للرسائل والتنبيهات الجديدة على هذا الجهاز. تظل الإشعارات تظهر حتى لو أوقفت الصوت.")
    val notificationKindLabels = mapOf(
        "PAYMENT" to Tr("Payments", "المدفوعات"),
        "PAYMENT_REMINDER" to Tr("Payment reminders", "تذكيرات الدفع"),
        "ORDER" to Tr("Orders", "الطلبات"),
        "ORDER_PAID" to Tr("Paid orders", "الطلبات المدفوعة"),
        "SUBSCRIPTION" to Tr("Subscriptions", "الاشتراكات"),
        "SUBSCRIPTION_REQUEST" to Tr("Subscription requests", "طلبات الاشتراك"),
        "RENEWAL_REQUEST" to Tr("Renewal requests", "طلبات التجديد"),
        "ACTIVATION_REQUEST" to Tr("Activation requests (sound alert)", "طلبات التفعيل (تنبيه صوتي)"),
        "RENEWAL_REMINDER" to Tr("Renewal reminders", "تذكيرات التجديد"),
        "ENROLLMENT" to Tr("Enrollments", "الالتحاقات"),
        "COURSE" to Tr("Courses", "الدورات"),
        "COURSE_REVIEW" to Tr("Course reviews", "مراجعات الدورات"),
        "CERTIFICATE" to Tr("Certificates", "الشهادات"),
        "SECURITY" to Tr("Security", "الأمان"),
        "ACCOUNT" to Tr("Account", "الحساب"),
        "ANNOUNCEMENT" to Tr("Announcements", "الإعلانات"),
        "TEST_RESULT" to Tr("Test results", "نتائج الاختبارات"),
        "ADMIN" to Tr("Administration", "الإدارة"),
        "TEACHER" to Tr("Teacher updates", "تحديثات المعلم"),
        "PAYOUT" to Tr("Payouts", "السحوبات"),
        "PAYOUT_REQUEST" to Tr("Payout requests", "طلبات السحب"),
        "SUPPORT" to Tr("Support", "الدعم"),
        "CLASSROOM" to Tr("Classroom", "الفصل الافتراضي"),
    )
    val allCaughtUp = Tr("All caught up", "لا جديد لديك")
    val support = Tr("Support", "الدعم")
    val supportSub = Tr("Open a ticket with the academy", "افتح تذكرة مع الأكاديمية")
    val teaching = Tr("Teaching", "التدريس")
    val teacherStudio = Tr("Teacher Studio", "استوديو المعلم")
    val teacherStudioSub = Tr("Your courses and earnings", "دوراتك وأرباحك")
    val ownerConsole = Tr("Owner Console", "لوحة المالك")
    val adminConsole = Tr("Admin Console", "لوحة الإدارة")
    val consoleSub = Tr("Platform control centre", "مركز التحكم بالمنصة")
    val appearance = Tr("Appearance", "المظهر")
    val appearanceSubtitle = Tr("Choose how 7PRO looks on this device.", "اختر شكل 7PRO على هذا الجهاز.")
    val themeSystem = Tr("Match device", "حسب الجهاز")
    val themeLight = Tr("Light", "فاتح")
    val themeDark = Tr("Dark", "داكن")
    val signOut = Tr("Sign out", "تسجيل الخروج")
    val noPaymentsYet = Tr("No payments yet", "لا توجد مدفوعات بعد")
    val noPaymentsBody = Tr("Your course purchases will be listed here.", "ستظهر هنا مشترياتك من الدورات.")
    val coursePurchase = Tr("Course purchase", "شراء دورة")
    val amount = Tr("Amount", "المبلغ")
    val discount = Tr("Discount", "الخصم")
    val refunded = Tr("Refunded", "مسترد")
    val country = Tr("Country", "الدولة")
    val date = Tr("Date", "التاريخ")
    val noCertificatesYet = Tr("No certificates yet", "لا توجد شهادات بعد")
    val noCertificatesBody = Tr("Finish a course to earn your first 7PRO certificate.", "أكمل دورة لتحصل على أول شهادة من 7PRO.")
    val serial = Tr("Serial", "الرقم التسلسلي")
    val verificationCode = Tr("Verification code", "رمز التحقق")
    val teacherLabel = Tr("Teacher", "المعلم")
    val score = Tr("Score", "الدرجة")
    val issued = Tr("Issued", "تاريخ الإصدار")
    val nothingHereYet = Tr("Nothing here yet", "لا يوجد شيء هنا بعد")
    val notificationsEmptyBody = Tr("Payments, approvals and class updates will appear here.", "ستظهر هنا المدفوعات والموافقات وتحديثات الحصص.")
    val openTicket = Tr("Open a ticket", "فتح تذكرة")
    val subject = Tr("Subject", "الموضوع")
    val howCanWeHelp = Tr("How can we help?", "كيف يمكننا مساعدتك؟")
    val sendTicket = Tr("Send ticket", "إرسال التذكرة")
    val yourTickets = Tr("Your tickets", "تذاكرك")
    val noTicketsYet = Tr("No tickets yet.", "لا توجد تذاكر بعد.")
    val teachOnSevenPro = Tr("Teach on 7PRO", "درّس على 7PRO")
    val teachIntro = Tr("Tell the academy about your teaching. Every application is reviewed by the owner before you can publish.", "أخبر الأكاديمية عن خبرتك في التدريس. يراجع المالك كل طلب قبل السماح لك بالنشر.")
    val headlineField = Tr("Headline (e.g. Secondary-school math teacher)", "العنوان المهني (مثال: مدرس رياضيات للمرحلة الثانوية)")
    val aboutTeaching = Tr("About your teaching", "نبذة عن تدريسك")
    val yearsExperience = Tr("Years of experience", "سنوات الخبرة")
    val subjectsField = Tr("Subjects (comma separated)", "المواد (افصل بينها بفاصلة)")
    val languagesField = Tr("Languages (comma separated)", "اللغات (افصل بينها بفاصلة)")
    val sampleUrlField = Tr("Sample lesson link (optional)", "رابط درس تجريبي (اختياري)")
    val submitApplication = Tr("Submit application", "إرسال الطلب")
    val fullName = Tr("Full name", "الاسم الكامل")
    val phone = Tr("Phone", "رقم الهاتف")
    val countryCodeField = Tr("Country code (e.g. EG)", "رمز الدولة (مثال: EG)")
    val billingCountryNote = Tr("Your billing country is always re-checked on the server at checkout.", "يتم دائمًا التحقق من دولة الفوترة على الخادم عند الدفع.")
    val profileSaved = Tr("Profile saved.", "تم حفظ الملف الشخصي.")
    val saveChanges = Tr("Save changes", "حفظ التغييرات")
    val unreadCount = Tr("%d unread", "%d غير مقروء")
    val awardedTo = Tr("Awarded to %s", "ممنوحة إلى %s")
    val clearAll = Tr("Clear all", "مسح الكل")
    val deleteNotification = Tr("Delete", "حذف")
    val clearAllNotificationsTitle = Tr("Clear all notifications?", "مسح كل الإشعارات؟")
    val clearAllNotificationsBody = Tr(
        "This removes every notification from your account. This can't be undone.",
        "سيتم حذف كل إشعاراتك نهائيًا من حسابك. لا يمكن التراجع عن هذا الإجراء.",
    )
}
