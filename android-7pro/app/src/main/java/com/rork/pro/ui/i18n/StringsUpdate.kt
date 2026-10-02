package com.rork.pro.ui.i18n

/** The blocking screen shown while the owner requires a newer build. */
object StrUpdate {
    val title = Tr("A new version of 7PRO is ready", "يتوفر إصدار جديد من 7PRO")
    val body = Tr(
        "Update to keep learning — this version is no longer supported.",
        "حدّث التطبيق لمواصلة التعلم — لم يعد هذا الإصدار مدعومًا.",
    )
    val updateNow = Tr("Update now", "تحديث الآن")
    val alreadyUpdated = Tr("I already updated", "قمت بالتحديث بالفعل")
    val versionLine = Tr("Installed version %1${'$'}s (build %2${'$'}s)", "الإصدار المثبّت %1${'$'}s (بناء %2${'$'}s)")
}
