package com.rork.pro.ui.i18n

/** Video dubbing screens (user flow and the owner's settings card), in both languages. */
object StrDubbing {
    val title = Tr("Video dubbing", "دبلجة فيديو")
    val fromYoutube = Tr("From YouTube", "من يوتيوب")
    val fromPhone = Tr("From phone", "من الهاتف")
    val youtubeLink = Tr("YouTube link", "رابط يوتيوب")
    val pickVideo = Tr("Choose a video from your phone", "اختر فيديو من الهاتف")
    val enToAr = Tr("English → Arabic", "إنجليزي → عربي")
    val arToEn = Tr("Arabic → English", "عربي → إنجليزي")
    val durationUnreadable = Tr("Couldn't read the video's length", "تعذر قراءة مدة الفيديو")
    val freeTrial = Tr("Free within your free trial (%s min)", "مجانًا ضمن التجربة المجانية (%s دقيقة)")
    val cost = Tr("Cost: %1\$s %2\$s", "التكلفة: %1\$s %2\$s")
    val unexpected = Tr("Something unexpected went wrong", "حدث خطأ غير متوقع")
    val getQuote = Tr("Get the price", "احصل على السعر")
    val jobStatus = Tr("Job status: %s", "حالة المهمة: %s")

    val disabled = Tr("Dubbing isn't available right now", "ميزة الدبلجة غير مفعّلة حاليًا")
    val ready = Tr("Your dubbing is ready", "الدبلجة جاهزة")
    val playDubbed = Tr("Play the dubbed video", "تشغيل الفيديو المدبلج")
    val preparingLink = Tr("Preparing the playback link…", "جاري تجهيز رابط التشغيل...")
    val failed = Tr("Dubbing failed, please try again", "فشلت عملية الدبلجة، حاول تاني")
    val inProgress = Tr("Dubbing in progress — you'll get a notification when it's done", "جاري تنفيذ الدبلجة، هيوصلك إشعار لما تخلص")

    // Owner settings card
    val settingsTitle = Tr("Dubbing", "الدبلجة")
    val settingsHint = Tr(
        "Price, currency, maximum video length and the dubbing server address",
        "السعر والعملة والحد الأقصى لمدة الفيديو، وعنوان خادم الدبلجة",
    )
    val enabled = Tr("Enabled", "مفعّلة")
    val pricePerMinute = Tr("Price per minute", "السعر لكل دقيقة")
    val currency = Tr("Currency", "العملة")
    val minBillable = Tr("Minimum billable minutes", "أقل عدد دقائق للفوترة")
    val maxVideoMinutes = Tr("Maximum video length (minutes)", "أقصى مدة فيديو (دقيقة)")
    val freeMinutesPerUser = Tr("Total free minutes per user", "إجمالي الدقائق المجانية لكل مستخدم")
    val serverUrl = Tr("Dubbing server address (dubbing-server)", "عنوان خادم الدبلجة (dubbing-server)")
    val serverUrlRequired = Tr("Set the server address for the feature to work", "لازم تحدد عنوان الخادم عشان الميزة تشتغل")
    val saving = Tr("Saving…", "جارٍ الحفظ...")
    val save = Tr("Save", "حفظ")
}

/** Accessibility labels for the question video player. */
object StrQuestionVideo {
    val play = Tr("Play video", "تشغيل الفيديو")
    val failed = Tr("Video failed to load", "تعذر تحميل الفيديو")
}
