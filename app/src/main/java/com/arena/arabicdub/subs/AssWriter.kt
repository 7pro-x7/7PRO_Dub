package com.arena.arabicdub.subs

import com.arena.arabicdub.domain.Segment
import java.io.File

/**
 * توليد ملف ترجمة نصية بصيغة ASS (يدعمه ffmpeg عبر libass)
 * بخط عربي مناسب وموضع أسفل الشاشة.
 */
object AssWriter {

    const val FONT_FAMILY = "Noto Naskh Arabic"

    fun write(file: File, segments: List<Segment>) {
        val sb = StringBuilder()
        sb.appendLine("[Script Info]")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: 1920")
        sb.appendLine("PlayResY: 1080")
        sb.appendLine("ScaledBorderAndShadow: yes")
        sb.appendLine()
        sb.appendLine("[V4+ Styles]")
        sb.appendLine(
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
                "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, " +
                "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, " +
                "Alignment, MarginL, MarginR, MarginV, Encoding",
        )
        // أبيض بحدود داكنة وظل خفيف، محاذاة أسفل الوسط
        sb.appendLine(
            "Style: Arabic,$FONT_FAMILY,58,&H00FFFFFF,&H000000FF,&H00101010," +
                "&H96000000,-1,0,0,0,100,100,0,0,1,2.5,1,2,40,40,70,1",
        )
        sb.appendLine()
        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        segments.forEach { s ->
            val text = s.textAr.replace("\n", " ").replace("\\", "\\\\").ifBlank { s.textEn }
            if (text.isBlank()) return@forEach
            sb.appendLine(
                "Dialogue: 0,${fmt(s.start)},${fmt(s.end)},Arabic,,0,0,0,,${text}",
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    /** تحويل الثواني إلى صيغة ASS: H:MM:SS.cc */
    private fun fmt(seconds: Float): String {
        val totalCs = (seconds * 100).toLong().coerceAtLeast(0)
        val h = totalCs / 360000
        val m = (totalCs % 360000) / 6000
        val s = (totalCs % 6000) / 100
        val cs = totalCs % 100
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs)
    }
}
