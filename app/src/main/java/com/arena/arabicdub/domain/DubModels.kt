package com.arena.arabicdub.domain

import android.net.Uri
import java.io.File

/**
 * مراحل خط الإنتاج، ووزن كل مرحلة يُستخدم لحساب التقدم الإجمالي.
 */
enum class DubStage(val title: String, val weight: Double) {
    PREPARE("تجهيز الفيديو", 0.06),
    EXTRACT_AUDIO("استخراج الصوت", 0.06),
    MODELS("تجهيز النماذج (أول تشغيل)", 0.10),
    TRANSCRIBE("التعرف على الكلام الإنجليزي", 0.22),
    TRANSLATE("الترجمة إلى العربية", 0.12),
    TTS("توليد الصوت العربي", 0.28),
    MIX("دمج المسارات الصوتية", 0.10),
    COMPOSE("العرض النهائي (فيديو + ترجمة)", 0.06),
}

/**
 * مصدر الفيديو المراد دبلجته.
 */
sealed class SourceSpec {
    /** ملف محلي من الجهاز (فيديو أو صوت) عبر رابط Content URI. */
    data class LocalVideo(val uri: Uri) : SourceSpec()

    /** رابط يوتيوب. */
    data class YouTubeLink(val url: String) : SourceSpec()
}

/**
 * مصدر جاهز بعد التحميل/النسخ.
 *
 * @param videoFile    ملف الفيديو النهائي (المراد عرضه).
 * @param originalAudio الصوت الأصلي — إن كان منفصلًا عن الفيديو (حالة يوتيوب).
 * @param hasVideo     هل يوجد مسار فيديو حقيقي (وإلا فالإخراج صوتي فقط).
 * @param videoIsMp4   هل مسار الفيديو h264/mp4 (نستطيع إعادة الترميز السريع copy).
 */
data class Source(
    val videoFile: File,
    val originalAudio: File?,
    val title: String,
    val hasVideo: Boolean,
    val videoIsMp4: Boolean = true,
    val isDownloaded: Boolean = false,
)

data class DubSettings(
    /** tiny | base | small | medium */
    val whisperModel: String = "base",
    /** true: ترجمة عبر MyMemory المجاني. false: الترجمة محلية داخل Whisper. */
    val useOnlineTranslate: Boolean = true,
    /** مستوى الصوت الأصلي (الموسيقى/الحوار) في الخلفية 0..1 */
    val backgroundVolume: Float = 0.18f,
    /** حرق الترجمة النصية العربية داخل الفيديو. */
    val burnSubtitles: Boolean = true,
    /** سرعة الصوت العربي 1.0 = عادي، 1.2 = أسرع. */
    val ttsSpeed: Float = 1.0f,
)

/**
 * مقطع كلامي مع توقيته.
 */
data class Segment(
    val start: Float,   // بالثواني
    val end: Float,     // بالثواني
    val textEn: String = "",
    val textAr: String = "",
)

data class PipelineResult(
    val outputFile: File,
    val isVideo: Boolean,
    val segments: List<Segment>,
)

/**
 * أحداث تقدم يُبثها خط الإنتاج للواجهة.
 */
sealed class DubEvent {
    data class StageChanged(val stage: DubStage) : DubEvent()
    data class Progress(val stageLocal: Float, val indeterminate: Boolean = false) : DubEvent()
    data class Log(val message: String) : DubEvent()
}
