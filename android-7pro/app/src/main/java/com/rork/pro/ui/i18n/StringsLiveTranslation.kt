package com.rork.pro.ui.i18n

/** Live Voice Translation inside the classroom meeting, and its owner switch. */
object StrLiveTranslation {
    val button = Tr("Translate", "ترجمة فورية")
    val title = Tr("Live voice translation", "الترجمة الصوتية الفورية")
    val subtitle = Tr(
        "Speak in your language — everyone hears you in theirs, in your own voice.",
        "اتكلم بلغتك — وكل واحد يسمعك بلغته وبنفس صوتك.",
    )
    val myLanguage = Tr("My language (speak & hear)", "لغتي (أتكلم وأسمع بيها)")
    val translateMyVoice = Tr("Translate my voice", "ترجمة صوتي للآخرين")
    val translateMyVoiceHint = Tr(
        "While on, your microphone goes through the translator. The mic button still mutes you.",
        "طول ما هي شغالة، الميكروفون بيعدّي على المترجم. زر الميكروفون لسه بيكتم صوتك عادي.",
    )
    val statusConnecting = Tr("Connecting to the translator…", "جارٍ الاتصال بالمترجم…")
    val statusLive = Tr("Translator connected", "المترجم متصل")
    val statusError = Tr("Translator unavailable", "المترجم غير متاح")
    val voiceCloningOn = Tr("Translations use each speaker's own voice", "الترجمة بتطلع بصوت المتحدث نفسه")
    val voiceCloningOff = Tr("Translations use a natural standard voice", "الترجمة بتطلع بصوت طبيعي قياسي")
    val errMicLocked = Tr("The teacher has locked your microphone.", "المعلم قافل الميكروفون بتاعك.")
    val errMicUnavailable = Tr(
        "This phone didn't share the microphone with the translator. Turn translation off to talk normally.",
        "الموبايل ده ما سمحش للمترجم يستخدم الميكروفون. اقفل الترجمة علشان تتكلم عادي.",
    )
    val errGeneric = Tr("The translator could not be reached.", "تعذّر الوصول للمترجم.")

    // Owner panel
    val ownerCardTitle = Tr("Live voice translation (meetings)", "الترجمة الصوتية الفورية (الحصص)")
    val ownerCardBody = Tr(
        "Adds a Translate button inside every meeting. Runs on your own open-source translation server (Whisper + Argos Translate + Piper + OpenVoice).",
        "بيضيف زر «ترجمة فورية» جوه كل حصة. بيشتغل على سيرفر الترجمة الخاص بيك (مشاريع مفتوحة المصدر: Whisper وArgos Translate وPiper وOpenVoice).",
    )
    val ownerEnable = Tr("Enable live translation", "تفعيل الترجمة الفورية")
    val ownerServerUrl = Tr("Translation server address (wss://…)", "عنوان سيرفر الترجمة (wss://…)")
    val ownerSave = Tr("Save translation settings", "حفظ إعدادات الترجمة")
    val ownerUrlRequired = Tr("Enter the server address before enabling.", "اكتب عنوان السيرفر قبل التفعيل.")
}

/** Language names for the in-meeting picker (native name, which is how people look for their own). */
fun liveTranslationLanguageName(code: String): String = when (code) {
    "ar" -> "العربية"
    "en" -> "English"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "es" -> "Español"
    "tr" -> "Türkçe"
    "it" -> "Italiano"
    "ru" -> "Русский"
    "zh" -> "中文"
    "pt" -> "Português"
    "hi" -> "हिन्दी"
    "ur" -> "اردو"
    "fa" -> "فارسی"
    else -> code.uppercase()
}
