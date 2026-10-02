package com.rork.pro.ui.i18n

/** The 7PRO payment sheet and the hosted gateway checkout it opens. */
object StrPay {
    val title = Tr("Complete your payment", "أكمل عملية الدفع")
    val heroTitle = Tr("One last step to unlock your course", "خطوة أخيرة لفتح دورتك")
    val heroBody = Tr(
        "Finish the payment below and start learning right away.",
        "أكمل الدفع تحت وابدأ التعلم على طول.",
    )
    val trustSecure = Tr("100% secure payment", "دفع آمن ١٠٠٪")
    val trustInstant = Tr("Quick activation", "تفعيل سريع")
    val trustSupport = Tr("Support if you need it", "دعمنا معاك لو احتجت")
    val youSave = Tr("You save %s", "بتوفّر %s")
    val orderSummary = Tr("Order summary", "ملخص الطلب")
    val listPrice = Tr("Price", "السعر")
    val discount = Tr("Discount", "الخصم")
    val totalDue = Tr("Total due", "الإجمالي المستحق")
    val chooseMethod = Tr("Choose how to pay", "اختر طريقة الدفع")
    val loadingMethods = Tr("Loading payment methods…", "جارٍ تحميل طرق الدفع…")
    val payNow = Tr("Pay %s securely", "ادفع %s بأمان")
    val securedBy = Tr(
        "Encrypted and processed by Paymob. 7PRO never sees your card details.",
        "مشفّر ومعالَج عبر Paymob. لا يطّلع 7PRO على بيانات بطاقتك إطلاقًا.",
    )
    val checkoutTitle = Tr("Secure checkout", "دفع آمن")
    val waitingConfirmation = Tr("Confirming your payment…", "جارٍ تأكيد عملية الدفع…")
    val doNotClose = Tr("Keep this screen open until the payment is confirmed.", "أبقِ هذه الشاشة مفتوحة حتى يتم تأكيد الدفع.")
    val paymentConfirmed = Tr("Payment confirmed", "تم تأكيد الدفع")
    val accessUnlocked = Tr("Your access is unlocked. Enjoy your lessons!", "تم تفعيل وصولك. استمتع بدروسك!")
    val startLearning = Tr("Start learning", "ابدأ التعلم")
    val paymentFailed = Tr("The payment did not go through", "لم تكتمل عملية الدفع")
    val paymentFailedBody = Tr(
        "No money was taken. You can try again with another method.",
        "لم يتم خصم أي مبلغ. يمكنك المحاولة مرة أخرى بطريقة أخرى.",
    )
    val tryAgain = Tr("Try again", "حاول مرة أخرى")
    val openInBrowser = Tr("Open in browser instead", "الفتح في المتصفح بدلاً من ذلك")
    val cancelPayment = Tr("Cancel payment", "إلغاء الدفع")

    // Method families
    val methodCard = Tr("Card", "بطاقة بنكية")
    val methodCardSub = Tr("Visa, Mastercard", "فيزا، ماستركارد")
    // The generic wallet row, shown when the gateway account has one unified wallet rail rather
    // than a separate integration per operator. It is no longer named after a single operator —
    // that wrongly suggested Orange, Etisalat and WE customers could not use it.
    val methodWallet = Tr("Mobile wallet", "محفظة إلكترونية")
    val methodWalletSub = Tr("Vodafone, Orange, Etisalat and WE", "فودافون وأورنج واتصالات ووي")

    // Each operator by name, for an account that exposes them as separate wallet integrations
    // and for the live badge shown as the number is typed.
    val walletVodafone = Tr("Vodafone Cash", "فودافون كاش")
    val walletOrange = Tr("Orange Money", "أورنج موني")
    val walletEtisalat = Tr("Etisalat Cash", "اتصالات كاش")
    val walletWe = Tr("WE Pay", "وي باي")
    val walletBrandMismatch = Tr(
        "That number belongs to a different operator. Pick the matching wallet, or enter a number for this one.",
        "هذا الرقم يخص شبكة أخرى. اختر المحفظة المطابقة، أو أدخل رقمًا يخص هذه الشبكة.",
    )
    val methodInstapay = Tr("InstaPay", "إنستاباي")
    val methodInstapaySub = Tr("Instant bank transfer", "تحويل بنكي فوري")
    val methodMeeza = Tr("Meeza", "ميزة")
    val methodMeezaSub = Tr("Egyptian national card", "البطاقة الوطنية المصرية")
    val methodKiosk = Tr("Cash at a kiosk", "دفع نقدي من منفذ")
    val methodKioskSub = Tr("Aman, Masary and partners", "أمان ومصاري ومنافذ أخرى")
    val methodInstallment = Tr("Pay in instalments", "الدفع بالتقسيط")
    val methodInstallmentSub = Tr("Subject to provider approval", "حسب موافقة جهة التقسيط")
    val couponHint = Tr("Enter coupon code", "أدخل كود الخصم")
    val couponApply = Tr("Apply", "تطبيق")
    val couponApplied = Tr("Coupon applied!", "تم تطبيق الكود!")
    val couponInvalid = Tr("Invalid coupon", "الكود غير صالح")
    val haveCoupon = Tr("Have a coupon?", "لديك كود خصم؟")
    val couponCoversAll = Tr(
        "This coupon covers the full price — unlocking your course now.",
        "هذا الكود يغطي السعر بالكامل — جارٍ فتح الدورة الآن.",
    )
    val enrollFree = Tr("Enroll for free", "التحق مجانًا")

    // Mobile wallet (Vodafone Cash) — charged against the number, approved on the phone
    val walletNumber = Tr("Wallet mobile number", "رقم المحفظة")
    val walletNumberHint = Tr("01X XXXX XXXX", "01X XXXX XXXX")
    val walletNumberHelp = Tr(
        "Enter the number your wallet is registered on. A payment request is sent straight to it.",
        "أدخل الرقم المسجّلة عليه محفظتك. سيصلك طلب الدفع على هذا الرقم مباشرة.",
    )
    val walletNumberInvalid = Tr(
        "Enter an 11-digit number starting with 010, 011, 012 or 015.",
        "أدخل رقمًا من ١١ رقمًا يبدأ بـ ٠١٠ أو ٠١١ أو ٠١٢ أو ٠١٥.",
    )
    val walletPayNow = Tr("Send request for %s", "أرسل طلب دفع %s")
    val walletApprovalTitle = Tr("Approve it on your phone", "أكّد العملية من هاتفك")
    val walletApprovalBody = Tr(
        "We sent a payment request to %s. Open your wallet app — or dial *9# — and enter your PIN to approve it.",
        "أرسلنا طلب دفع إلى %s. افتح تطبيق المحفظة — أو اطلب #9* — وأدخل رقمك السري لتأكيد العملية.",
    )
    val walletWaiting = Tr("Waiting for your approval…", "في انتظار تأكيدك…")
    val walletOpenPage = Tr("Open the wallet page", "فتح صفحة المحفظة")
    val walletKeepOpen = Tr(
        "Keep this screen open — your course unlocks the moment the payment is confirmed.",
        "أبقِ هذه الشاشة مفتوحة — ستُفتح دورتك فور تأكيد الدفع.",
    )
    val walletTimedOut = Tr("The request expired", "انتهت صلاحية الطلب")
    val walletTimedOutBody = Tr(
        "No money was taken. The wallet request was not approved in time — you can send a new one.",
        "لم يتم خصم أي مبلغ. لم يتم تأكيد طلب المحفظة في الوقت المحدد — يمكنك إرسال طلب جديد.",
    )

    // ---- Manual wallet transfer ----
    // Used when the owner has switched the gateway off: the four wallets stay on the sheet,
    // but paying means transferring by hand and waiting for the platform to confirm it.
    val transferTitle = Tr("Transfer to this number", "حوّل على الرقم ده")
    val transferHolder = Tr("Account name", "اسم صاحب المحفظة")
    val transferCopied = Tr("Number copied", "تم نسخ الرقم")
    val transferCopy = Tr("Copy", "نسخ")
    val transferSteps = Tr(
        "Send exactly %s to the number above — from any wallet or bank, on any network.",
        "حوّل مبلغ %s بالظبط على الرقم اللي فوق — من أي محفظة أو بنك، وعلى أي شبكة.",
    )
    val instapaySupported = Tr(
        "InstaPay transfers to this number are accepted too.",
        "التحويل عبر إنستاباي على نفس الرقم مقبول برضه.",
    )
    val senderNumber = Tr("The number you transferred from", "الرقم اللي حوّلت منه")
    val senderNumberHelp = Tr(
        "We match your transfer against this number — enter the number the money left from, whichever network or app it was on.",
        "بنراجع التحويل على الرقم ده، فاكتب الرقم اللي خرج منه المبلغ، من أي شبكة أو تطبيق حوّلت بيه.",
    )
    val senderNumberWrongBrand = Tr(
        "That number is on another network. Enter a %s number, or pick the wallet that matches it.",
        "الرقم ده على شبكة تانية. اكتب رقم %s، أو اختر المحفظة اللي بتخصه.",
    )
    val proofTitle = Tr("Transfer screenshot", "صورة التحويل")
    val proofHelp = Tr(
        "Attach the confirmation screen from your wallet. Only you and the review team can see it.",
        "ارفق صورة شاشة تأكيد التحويل من محفظتك. مفيش حد يشوفها غيرك وغير فريق المراجعة.",
    )
    val proofAttach = Tr("Attach screenshot", "ارفق الصورة")
    val proofReplace = Tr("Change screenshot", "غيّر الصورة")
    val proofAttached = Tr("Screenshot attached", "تم إرفاق الصورة")
    val proofUploading = Tr("Uploading…", "جارٍ الرفع…")
    val proofTooLarge = Tr("That image is too large — 5 MB maximum.", "الصورة كبيرة جدًا — الحد الأقصى ٥ ميجا.")
    val noteOptional = Tr("Anything else we should know? (optional)", "أي ملاحظة تحب تضيفها؟ (اختياري)")
    val sendRequest = Tr("Send payment request", "أرسل طلب الدفع")
    val walletUnavailable = Tr("Not available right now", "غير متاح حاليًا")

    // After sending
    val manualSentTitle = Tr("Request sent", "تم إرسال طلبك")
    val manualSentBody = Tr(
        "The team is checking your transfer. Your course opens as soon as it is approved — you will get a notification.",
        "الفريق بيراجع تحويلك دلوقتي. هتتفتح دورتك أول ما تتم الموافقة — وهيوصلك إشعار.",
    )
    val manualDone = Tr("Got it", "تمام")
    val manualPendingBanner = Tr(
        "Your transfer is under review. The course opens once it is approved.",
        "تحويلك تحت المراجعة. هتتفتح الدورة بعد الموافقة عليه.",
    )
    val manualRejectedBanner = Tr(
        "Your transfer was not confirmed. You can send the request again with a clearer screenshot.",
        "لم يتم تأكيد تحويلك. تقدر تبعت الطلب تاني بصورة أوضح.",
    )
    val manualUnderReview = Tr("Under review", "تحت المراجعة")
    val manualRowSub = Tr("Transfer, then we confirm it", "حوّل، وإحنا نأكّد التحويل")

    // ---- Checkout hook (value-first hero, progress, guided CTA) ----
    val heroKicker = Tr("Your course is waiting", "دورتك مستنياك")
    val heroHeadline = Tr("One step and you're in", "خطوة واحدة وتبدأ")
    val heroYouPay = Tr("You pay", "هتدفع")
    val benefitUnlock = Tr(
        "Lessons unlock for you as soon as the payment is confirmed",
        "الدروس تتفتح لك أول ما الدفع يتأكد",
    )
    val benefitAnytime = Tr(
        "Learn from your phone, any time, at your own pace",
        "اتعلم من موبايلك في أي وقت وبالسرعة اللي تناسبك",
    )
    val benefitSupport = Tr(
        "The 7PRO team has your back if anything goes wrong",
        "فريق 7PRO معاك لو واجهتك أي مشكلة",
    )
    // Compact chip labels (2–3 words) for the same three reassurances, used in the hero row.
    val benefitUnlockShort = Tr("Unlocks instantly", "بتتفتح فورًا")
    val benefitAnytimeShort = Tr("Learn anytime", "تعلّم في أي وقت")
    val benefitSupportShort = Tr("We've got you", "دعمنا معاك")
    val stepPick = Tr("Wallet", "المحفظة")
    val stepTransfer = Tr("Transfer", "التحويل")
    val stepProof = Tr("Screenshot", "الصورة")
    val stepSend = Tr("Send", "الإرسال")
    val manualStep1 = Tr("Copy the number and send %s", "انسخ الرقم وحوّل عليه %s")
    val manualStep2 = Tr("The number you sent from", "اكتب الرقم اللي حوّلت منه")
    val manualStep3 = Tr("Attach the transfer screenshot", "ارفق صورة شاشة التحويل")
    val proofDrop = Tr("Tap to attach the screenshot", "اضغط هنا وارفق صورة التحويل")
    val proofDropSub = Tr(
        "A clear shot showing the amount and number gets confirmed faster",
        "صورة واضحة فيها المبلغ والرقم = تأكيد أسرع",
    )
    val unavailableWallets = Tr("%s — not available right now", "%s — غير متاحة حاليًا")
    val missingMethod = Tr("Pick how you'd like to pay first", "اختار طريقة الدفع الأول")
    val missingSender = Tr("Almost there — add the number you sent from", "فاضل تكتب الرقم اللي حوّلت منه")
    val missingProof = Tr("Last thing — attach the transfer screenshot", "آخر حاجة — ارفق صورة التحويل")
    val readyToSend = Tr(
        "All set — send it and your course opens once we confirm",
        "كله تمام — ابعت الطلب ودورتك هتتفتح أول ما نأكّد",
    )
    val sendRequestAmount = Tr("Send payment request · %s", "أرسل طلب الدفع • %s")
    val privacyShort = Tr(
        "Your details and screenshot are seen only by the review team",
        "بياناتك وصورة التحويل محدش يشوفها غير فريق المراجعة",
    )
}
