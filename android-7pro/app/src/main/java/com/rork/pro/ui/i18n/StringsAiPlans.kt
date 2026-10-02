package com.rork.pro.ui.i18n

/** Strings for the AI Tutor's paid plans: the student's plans screen and the owner's price management. */
object StrAiPlans {
    // ---- student
    val title = Tr("AI Tutor replies", "ردود المدرّس الذكي")
    val intro = Tr(
        "Buy a pack of replies and talk with your tutor whenever you like. Replies stay valid for 30 days from the purchase, used or not. After that, renew or buy more.",
        "اشتري باقة ردود وتكلم مع مدرّسك وقت ما تحب. الردود صالحة 30 يوم من الشراء، استخدمتها أو لا. وبعدها جدّد أو اشتري ردود تانية.",
    )
    val activeTitle = Tr("Your replies", "رصيد ردودك")
    val activeLine = Tr("%s · active until %s", "%s · فعّالة لحد %s")
    val repliesLeftToday = Tr("%d of %d replies left today", "باقي %d من %d رد النهارده")
    val freeInfo = Tr("Everyone gets %d free replies a day.", "كل الطلاب بياخدوا %d رد مجاني في اليوم.")
    val paidOnlyInfo = Tr("The tutor is available with a plan.", "المدرّس متاح بالاشتراك في إحدى الباقات.")
    val paidOnlyTitle = Tr("Buy replies to talk with the tutor", "اشتري ردود عشان تتكلم مع المدرّس")
    val paidOnlyBody = Tr("Pick a pack and start talking right away.", "اختار باقة وابدأ كلام على طول.")
    val repliesCount = Tr("%d replies", "%d رد")
    val repliesPerDay = Tr("%d replies a day", "%d رد في اليوم")
    val forDays = Tr("for %d days", "لمدة %d يوم")
    val validFor = Tr("valid %d days", "صالحة %d يوم")
    val validNote = Tr("Counted from the purchase, used or not.", "من يوم الشراء، استخدمتها أو لا.")
    val perReply = Tr("about %s per reply", "حوالي %s للرد")
    val creditsLine = Tr("%d replies · until %s", "%d رد · لحد %s")
    val expiryNote = Tr("Unused replies expire with the pack. A new purchase starts its own 30 days.", "الردود اللي ما اتستخدمتش بتنتهي مع الباقة. وكل شراء جديد بيبدأ 30 يوم من جديد.")
    val expiresSoon = Tr("Your replies expire in %d days. Renew before they go.", "ردودك بتنتهي بعد %d يوم. جدّد قبل ما تروح.")
    val expiresToday = Tr("Your replies expire today. Renew before they go.", "ردودك بتنتهي النهارده. جدّد قبل ما تروح.")
    val freeLeftToday = Tr("%d free replies left today", "باقي %d رد مجاني النهارده")
    val creditsPill = Tr("%d replies · until %s", "%d رد · لحد %s")
    val subscribe = Tr("Buy replies", "اشتري الردود")
    val subscribeAgain = Tr("Buy more / renew", "اشتري تاني / جدّد")
    val saveBadge = Tr("Save %d%%", "وفّر %d%%")
    val offerEnds = Tr("Offer ends %s", "العرض ينتهي %s")
    val offerEndsLabel = Tr("Discount ends", "الخصم ينتهي")
    val free = Tr("Free", "مجانًا")
    val noPlans = Tr("No plans are available right now.", "مفيش باقات متاحة دلوقتي.")
    val activated = Tr("Your replies are ready. Enjoy!", "ردودك اتضافت. استمتع!")
    val checkoutFailed = Tr("Couldn't start the payment. Please try again.", "معرفناش نبدأ الدفع. جرّب تاني.")
    val quoteFailed = Tr("Couldn't get the price. Check your connection and try again.", "معرفناش نجيب السعر. اتأكد من النت وجرّب تاني.")

    // ---- redesigned plans screen (hook + trust)
    val heroTitle = Tr("Speak English.\nGet corrected on the spot.", "اتكلم إنجليزي…\nوالمدرّس يصححلك فورًا")
    val heroSub = Tr("Real speaking practice, any time you want it.", "تدريب حقيقي على الكلام، في أي وقت تحبه.")
    val demoTip = Tr("“went”, not “goed”. Say it again.", "نقول went مش goed. قولها تاني.")
    val tryFirst = Tr("Try it free first", "جرّب الأول ببلاش")
    val choosePack = Tr("Pick your pack", "اختار باقتك")
    val bestValue = Tr("Best value", "الأوفر")
    val repliesWord = Tr("replies", "رد")
    val perkLive = Tr("The tutor corrects you while you speak", "المدرّس بيصححلك وإنت بتتكلم")
    val perkAnytime = Tr("Available any time, no schedule", "متاح في أي وقت، من غير مواعيد")
    val payMethods = Tr("Pay by card, wallet or transfer", "ادفع بالكارت أو المحفظة أو التحويل")
    val repliesLeftLabel = Tr("replies left", "رد متبقي")
    val validUntil = Tr("Valid until %s", "صالحة لحد %s")

    // ---- lobby
    val seePlans = Tr("Plans & prices", "الباقات والأسعار")
    val getMore = Tr("Buy replies", "اشتري ردود")
    val subscribeToKeepTalking = Tr("Buy replies to keep talking", "اشتري ردود عشان تكمّل كلام")
    val planPill = Tr("%s · until %s", "%s · لحد %s")
    val managePrices = Tr("Prices", "الأسعار")

    // ---- owner
    val adminTitle = Tr("AI Tutor prices", "أسعار المدرّس الذكي")
    val adminTile = Tr("AI Tutor prices", "أسعار المدرّس الذكي")
    val subscribers = Tr("With replies left", "عندهم رصيد")
    val paidOrders = Tr("Paid orders", "طلبات مدفوعة")
    val revenue = Tr("Revenue", "الإيراد")
    val freeTitle = Tr("Free replies a day", "الردود المجانية يوميًا")
    val freeHint = Tr(
        "Every student gets this many free replies each day. Enter 0 to make the tutor paid-only.",
        "كل طالب بياخد العدد ده مجانًا كل يوم. اكتب 0 لو عايز المدرّس بالاشتراك بس.",
    )
    val freeSave = Tr("Save", "حفظ")
    val newPlan = Tr("New reply pack", "باقة ردود جديدة")
    val createPlan = Tr("Add the plan", "أضف الباقة")
    val editPlan = Tr("Edit", "تعديل")
    val savePlan = Tr("Save changes", "احفظ التعديلات")
    val activeLabel = Tr("Available to students", "متاحة للطلاب")
    val fName = Tr("Name (English)", "الاسم (إنجليزي)")
    val fNameAr = Tr("Name (Arabic)", "الاسم (عربي)")
    val fDesc = Tr("Description (English)", "الوصف (إنجليزي)")
    val fDescAr = Tr("Description (Arabic)", "الوصف (عربي)")
    val fPrice = Tr("Price", "السعر")
    val fSalePrice = Tr("Price after discount (optional)", "السعر بعد الخصم (اختياري)")
    val fSaleDays = Tr("Discount lasts (days, optional)", "مدة الخصم بالأيام (اختياري)")
    val fSaleDaysEdit = Tr("Discount lasts (days from now, 0 = no end, empty = unchanged)", "مدة الخصم من النهارده (0 = بدون نهاية، فاضي = زي ما هو)")
    val fCurrency = Tr("Currency", "العملة")
    val fPeriod = Tr("Valid for (days)", "صالحة لمدة (أيام)")
    val fReplies = Tr("Replies in the pack", "عدد الردود في الباقة")
    val packNote = Tr("Unused replies expire when the period ends, whether the student used them or not.", "الردود اللي ما اتستخدمتش بتنتهي بعد المدة، سواء الطالب استخدمها أو لا.")
    val repliesSold = Tr("Replies sold", "ردود مبيعة")
    val repliesUsed = Tr("Used", "استُخدم")
    val repliesExpired = Tr("Expired unused", "انتهى بدون استخدام")
    val fOrder = Tr("Order", "الترتيب")
    val saleNote = Tr(
        "With a discount price, students see the old price crossed out next to the new one.",
        "لو حطيت سعر بعد الخصم، الطالب بيشوف السعر القديم مشطوب جنب الجديد.",
    )
    val noPlansAdmin = Tr("No plans yet", "لسه مفيش باقات")
    val noPlansAdminBody = Tr("Add a plan above and students can start subscribing.", "أضف باقة فوق والطلاب يقدروا يشتركوا.")
    val deletePlan = Tr("Delete the plan", "حذف الباقة")
    val deleteConfirm = Tr(
        "A plan that already has orders can't be deleted: switch it off instead.",
        "الباقة اللي عليها طلبات ما تتحذفش: عطّلها بدل كده.",
    )
    val invalidSale = Tr("The discount price must be lower than the price.", "سعر الخصم لازم يكون أقل من السعر.")

    // ---- coupons
    val scopeTitle = Tr("The coupon works on", "الكوبون بيشتغل على")
    val scopeNone = Tr("Courses only", "الكورسات فقط")
    val scopeAlso = Tr("Courses + AI Tutor", "الكورسات والمدرّس الذكي")
    val scopeOnly = Tr("AI Tutor only", "المدرّس الذكي فقط")
}
