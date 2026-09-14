package com.arena.arabicdub.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * جلب الفيديو والصوت من يوتيوب عبر واجهة Piped المفتوحة المصدر
 * (https://github.com/TeamPiped/Piped) — بديلا عن استخدام واجهات يوتيوب
 * الخاصة. نجرّب عدة خوادم عامة حتى ينجح أحدها.
 */
class YouTubeSource {

    data class PipedStream(
        val videoUrl: String?,
        val videoIsMp4: Boolean,
        val audioUrl: String,
        val title: String,
    )

    companion object {
        private val ID_RE = Regex("(?:v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})")

        /** استخراج معرّف الفيديو من أي شكل من روابط يوتيوب. */
        fun extractVideoId(url: String): String? =
            ID_RE.find(url.trim())?.groupValues?.get(1)
    }

    private val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.ducks.party",
        "https://pipedapi.reallyaweso.me",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    /**
     * تحديد روابط البث من يوتيوب عبر Piped.
     * يفضّل: صوت m4a (itag 140) + فيديو h264/mp4 (≤720p لتقليل الحجم).
     */
    fun resolve(videoId: String): PipedStream {
        var lastError: Exception? = null
        for (base in instances) {
            try {
                val json = getJson("$base/streams/$videoId") ?: continue
                val title = json.optString("title", "فيديو يوتيوب")

                // --- الصوت ---
                var audioUrl: String? = null
                var bestItag = 999
                json.optJSONArray("audioStreams")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val itag = s.optInt("itag", 0)
                        if (itag == 140 || itag == 141 || itag == 251) {
                            val u = s.optString("url", "")
                            if (u.isNotEmpty() && itag < bestItag) {
                                audioUrl = u
                                bestItag = itag
                            }
                        }
                    }
                }
                val finalAudioUrl = audioUrl ?: continue

                // --- الفيديو ---
                // نفضّل mp4/h264 (ينسخ بدون إعادة ترميز)، وعند غيابه نقبل
                // أفضل فيديو آخر ويُرمَّز لاحقًا إلى h264.
                val mp4Candidates = mutableListOf<Pair<Int, String>>()
                val otherCandidates = mutableListOf<Pair<Int, String>>()
                json.optJSONArray("streams")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        if (!s.optBoolean("videoOnly", false)) continue
                        val u = s.optString("url", "")
                        if (u.isEmpty()) continue
                        val mime = s.optString("mimeType", "")
                        val height = parseHeight(s.optString("quality", ""))
                        if (mime == "video/mp4") mp4Candidates.add(height to u)
                        else otherCandidates.add(height to u)
                    }
                }
                if (mp4Candidates.isEmpty() && otherCandidates.isEmpty()) continue

                fun pickBest(candidates: List<Pair<Int, String>>): Pair<Int, String>? =
                    candidates.filter { it.first <= 720 }.maxByOrNull { it.first }
                        ?: candidates.minByOrNull { it.first }

                val bestMp4 = pickBest(mp4Candidates)
                val bestVideo = bestMp4 ?: pickBest(otherCandidates)

                return PipedStream(
                    videoUrl = bestVideo?.second,
                    videoIsMp4 = bestMp4 != null,
                    audioUrl = finalAudioUrl,
                    title = title,
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("تعذر جلب الفيديو من خوادم Piped (سيكون لديك اتصال أضعف أو الخوادم مشغولة): ${lastError?.message}")
    }

    /** تنزيل ملف من رابط مباشر مع شريط تقدم. */
    fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("فشل التحميل: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("استجابة فارغة")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, dest.name + ".part")
            FileOutputStream(part).use { out ->
                val buffer = ByteArray(128 * 1024)
                var done = 0L
                var lastNotify = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 250L) {
                            lastNotify = now
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            onProgress(1f)
        }
    }

    private fun getJson(url: String): JSONObject? {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    /** "720p60" -> 720، "audio only" -> 0 */
    private fun parseHeight(quality: String): Int =
        Regex("^\\d+").find(quality.trim())?.value?.toIntOrNull() ?: 0
}
