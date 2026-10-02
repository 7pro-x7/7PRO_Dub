package com.rork.pro.ui.i18n

/** Strings for the owner/admin "web ads" screen. */
object StrWebAds {
    val title = Tr("Web ads", "إعلانات الويب")
    val tile = Tr("Web ads", "إعلانات الويب")
    val note = Tr(
        "These ads appear on the teacher exercises web page, not inside the app. AdMob unit ids can't be used here — pick an image with a link, an AdSense unit, or an HTML snippet.",
        "الإعلانات دي بتظهر في صفحة تمارين المعلم على الويب، مش جوه التطبيق. وحدات AdMob ما تشتغلش هنا — اختار صورة برابط، أو وحدة AdSense، أو كود HTML.",
    )
    val globalTitle = Tr("Web ads", "إعلانات الويب")
    val globalToggle = Tr("Show ads on web pages", "عرض الإعلانات في صفحات الويب")
    val globalHelp = Tr(
        "Switching this off hides every web ad at once.",
        "إيقافه بيخفي كل إعلانات الويب مرة واحدة.",
    )

    val slotTop = Tr("Top of the page", "أعلى الصفحة")
    val slotQuestion = Tr("Under each question", "تحت كل سؤال")
    val slotResult = Tr("Result screen", "صفحة النتيجة")
    val slotBottom = Tr("Bottom of the page", "أسفل الصفحة")

    val kindImage = Tr("Image + link", "صورة + رابط")
    val kindAdsense = Tr("AdSense", "AdSense")
    val kindHtml = Tr("HTML code", "كود HTML")

    val notSet = Tr("Not set", "غير مضبوط")
    val enabled = Tr("Enabled", "مفعّل")
    val imageUrl = Tr("Image URL (https)", "رابط الصورة (https)")
    val linkUrl = Tr("Click link (optional)", "رابط الضغط (اختياري)")
    val adsenseClient = Tr("Publisher id (ca-pub-…)", "معرّف الناشر (ca-pub-…)")
    val adsenseSlot = Tr("Ad unit id (digits)", "معرّف وحدة الإعلان (أرقام)")
    val htmlCode = Tr("Ad code (HTML)", "كود الإعلان (HTML)")
    val heightPx = Tr("Height (px)", "الارتفاع (بكسل)")
    val adsenseHelp = Tr(
        "AdSense only serves on a domain approved in your AdSense account — a workers.dev address usually isn't. Use a custom domain, or pick another kind.",
        "AdSense بيشتغل بس على دومين معتمد في حساب AdSense — وعنوان workers.dev غالبًا مش معتمد. استخدم دومين خاص، أو اختار نوع تاني.",
    )
    val htmlHelp = Tr(
        "The code runs in an isolated frame that can't reach the student's session. Set the height to fit it.",
        "الكود بيشتغل في إطار معزول مايقدرش يوصل لجلسة الطالب. اضبط الارتفاع على حجمه.",
    )
    val invalid = Tr("Check the highlighted field", "راجع الحقل الناقص أو الغلط")
    val save = Tr("Save", "حفظ")
    val remove = Tr("Remove", "حذف")
    val removeConfirm = Tr("Remove this ad?", "تحذف الإعلان ده؟")
}
