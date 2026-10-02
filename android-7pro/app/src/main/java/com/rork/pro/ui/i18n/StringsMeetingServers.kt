package com.rork.pro.ui.i18n

/** Owner/admin screen that lists the Jitsi servers meetings can run on, and switches between them. */
object StrMeetingServers {
    val tile = Tr("Meeting servers", "سيرفرات الاجتماعات")
    val perm = Tr("Meeting servers (switch & edit)", "سيرفرات الاجتماعات (تبديل وتعديل)")
    val title = Tr("Meeting servers", "سيرفرات الاجتماعات")
    val intro = Tr(
        "Classes run on the server marked Active. Switching moves every class that hasn't started yet to the new server; classes running right now finish where they started so nobody gets split mid-lesson.",
        "الحصص بتشتغل على السيرفر المكتوب عليه «نشط». لما تبدّل، كل الحصص اللي لسه مبدأتش بتتنقل للسيرفر الجديد، والحصص الشغالة دلوقتي بتكمل مكانها عشان محدش يتقسم في نص الحصة.",
    )
    val active = Tr("Active", "نشط")
    val notReady = Tr("Not set up yet — add the domain", "لسه مش متجهّز — اكتب الدومين")
    val ready = Tr("Ready to switch", "جاهز للتبديل")
    val jwtOn = Tr("Protected with JWT", "محمي بـ JWT")
    val jwtOff = Tr("Open server (no JWT)", "سيرفر مفتوح (بدون JWT)")
    val liveCount = Tr("Live classes now: %s", "حصص شغالة دلوقتي: %s")
    val scheduledCount = Tr("Upcoming classes: %s", "حصص جاية: %s")
    val lastTestOk = Tr("Last test: working", "آخر اختبار: شغال")
    val lastTestFail = Tr("Last test: not reachable", "آخر اختبار: مش بيرد")

    val providerContabo = Tr("Contabo", "Contabo")
    val providerHetzner = Tr("Hetzner", "Hetzner")
    val providerCommunity = Tr("Free community server", "سيرفر مجتمعي مجاني")
    val providerOther = Tr("Other", "سيرفر تاني")

    val activate = Tr("Make active", "فعّله")
    val edit = Tr("Edit", "تعديل")
    val test = Tr("Test", "اختبار")
    val testing = Tr("Testing…", "بيختبر…")
    val delete = Tr("Delete", "حذف")
    val addServer = Tr("Add a server", "إضافة سيرفر")

    val label = Tr("Name (shown to you only)", "الاسم (بيظهرلك انت بس)")
    val provider = Tr("Provider", "الشركة")
    val domain = Tr("Server domain (e.g. meet.7pro.app)", "دومين السيرفر (مثال: meet.7pro.app)")
    val domainHint = Tr(
        "Just the domain — no https:// and no path. It must have a valid HTTPS certificate.",
        "الدومين بس — من غير https:// ومن غير أي مسار. ولازم يكون عليه شهادة HTTPS.",
    )
    val jwtSection = Tr("JWT protection (optional — recommended)", "حماية JWT (اختياري — ومُفضّل)")
    val jwtHint = Tr(
        "Use the same App ID and Secret you put in the server's .env (JWT_APP_ID / JWT_APP_SECRET). Leave both empty for an open server.",
        "حط نفس الـ App ID والـ Secret اللي في ملف ‎.env‎ على السيرفر (JWT_APP_ID / JWT_APP_SECRET). سيبهم فاضيين لو السيرفر مفتوح.",
    )
    val jwtAppId = Tr("JWT App ID", "JWT App ID")
    val jwtSecret = Tr("JWT Secret", "JWT Secret")
    val jwtSecretKeep = Tr("JWT Secret (saved — leave empty to keep it)", "JWT Secret (محفوظ — سيبه فاضي عشان يفضل زي ما هو)")
    val jwtRemove = Tr("Remove JWT from this server", "شيل الـ JWT من السيرفر ده")
    val notes = Tr("Notes (IP, plan, anything)", "ملاحظات (IP، الباقة، أي حاجة)")
    val save = Tr("Save", "حفظ")
    val cancel = Tr("Cancel", "إلغاء")

    val confirmActivateTitle = Tr("Switch meetings to this server?", "تبدّل الاجتماعات للسيرفر ده؟")
    val confirmActivateBody = Tr(
        "%s will become the server for all new and upcoming classes. Classes running now will finish on their current server. Test the server first if you haven't.",
        "%s هيبقى السيرفر لكل الحصص الجديدة والجاية. الحصص الشغالة دلوقتي هتكمل على سيرفرها الحالي. اختبر السيرفر الأول لو لسه ما اختبرتهوش.",
    )
    val confirmActivateUntested = Tr(
        "Warning: the last connection test for this server failed or it was never tested.",
        "تحذير: آخر اختبار للسيرفر ده فشل أو عمره ما اتختبر.",
    )
    val confirmDeleteTitle = Tr("Delete this server?", "تحذف السيرفر ده؟")
    val confirmDeleteBody = Tr("It will be removed from the list. Classes already on it are not affected.", "هيتشال من القائمة. الحصص اللي عليه مش هتتأثر.")
    val switched = Tr("Switched. %s upcoming classes moved; %s live classes finish on the old server.", "تم التبديل. اتنقلت %s حصة جاية، و%s حصة شغالة هتكمل على السيرفر القديم.")

    val testOk = Tr("The server answered and serves Jitsi correctly.", "السيرفر رد وبيشغّل Jitsi تمام.")
    val testFail = Tr("The server did not answer as a Jitsi server: %s", "السيرفر مردّش كسيرفر Jitsi: %s")
    val testNeedsDomain = Tr("Add the domain first.", "اكتب الدومين الأول.")

    // Server-side error codes
    val errLabel = Tr("Give the server a name.", "اكتب اسم للسيرفر.")
    val errDomain = Tr("That domain isn't valid. Write it like meet.example.com", "الدومين مش صحيح. اكتبه بالشكل ده: meet.example.com")
    val errDomainNotSupported = Tr("This public server can't be used (it requires sign-in or isn't a meeting server).", "السيرفر العام ده مينفعش يتستخدم (بيطلب تسجيل دخول أو مش سيرفر اجتماعات).")
    val errDomainUsed = Tr("Another server in the list already uses this domain.", "فيه سيرفر تاني في القائمة بنفس الدومين.")
    val errActiveNeedsDomain = Tr("The active server must keep a domain.", "السيرفر النشط لازم يفضل ليه دومين.")
    val errSecretRequired = Tr("Add the JWT Secret too (or clear the App ID).", "حط الـ JWT Secret كمان (أو امسح الـ App ID).")
    val errAppIdRequired = Tr("Add the JWT App ID too (or remove the secret).", "حط الـ JWT App ID كمان (أو شيل الـ Secret).")
    val errDomainRequired = Tr("Add the server's domain before making it active.", "اكتب دومين السيرفر قبل ما تفعّله.")
    val errDeleteActive = Tr("You can't delete the active server. Switch to another one first.", "مينفعش تحذف السيرفر النشط. بدّل لسيرفر تاني الأول.")
}
