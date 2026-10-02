package com.rork.pro.ui.i18n

/**
 * The learner-facing side of ads: the rewarded-ad offer and the ad-free stretch it buys.
 *
 * Deliberately plain about the trade. A rewarded ad is only worth showing if the learner
 * understands exactly what they get for their time.
 */
object StrAds {
    val rewardTitle = Tr("Study without ads", "ذاكر بدون إعلانات")
    val rewardBody = Tr(
        "Watch one short video and ads disappear across the app for 30 minutes.",
        "شاهد مقطعًا قصيرًا واحدًا وتختفي الإعلانات من التطبيق لمدة ٣٠ دقيقة.",
    )
    val rewardAction = Tr("Watch and remove ads", "شاهد وأزل الإعلانات")
    val adFreeActive = Tr(
        "Ads are off for the next %s minutes. Enjoy the quiet.",
        "الإعلانات متوقفة لمدة %s دقيقة. استمتع بالمذاكرة.",
    )
}
