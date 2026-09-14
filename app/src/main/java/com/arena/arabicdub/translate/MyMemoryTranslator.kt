package com.arena.arabicdub.translate

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ترجمة مجانية عبر واجهة MyMemory المفتوحة (https://mymemory.translated.net)
 * — بدون حساب، بحد يومي حر (~5000 حرف). تُستخدم بعد تفهم الكلام محليًا.
 */
class MyMemoryTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * ترجمة قائمة نصوص إنجليزية.
     * @param onProgress (المكتمل، الإجمالي)
     */
    fun translate(texts: List<String>, onProgress: (Int, Int) -> Unit): List<String> {
        return texts.mapIndexed { i, text ->
            val ar = translateOne(text)
            onProgress(i + 1, texts.size)
            ar
        }
    }

    private fun translateOne(text: String): String {
        if (text.isBlank()) return ""
        val chunks = splitForApi(text, 450)
        val parts = mutableListOf<String>()
        for (chunk in chunks) {
            val url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(chunk, "UTF-8") +
                "&langpair=en|ar"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("خطأ MyMemory: HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw IOException("استجابة فارغة من MyMemory")
                val json = JSONObject(body)
                val data = json.optJSONObject("responseData")
                    ?: throw IOException("استجابة غير صالحة من MyMemory")
                val translated = data.optString("translatedText", "")
                if (translated.isBlank() || translated.equals("NOT FOUND", ignoreCase = true)) {
                    throw IOException("تعذر الترجمة (قد يكون الحد اليومي المجاني قد استُهلك)")
                }
                parts.add(unescapeHtml(translated))
            }
            // مهالة قصيرة احترامًا للخدمة المجانية
            Thread.sleep(300)
        }
        return parts.joinToString(" ").trim()
    }

    /** تقسيم النص عند حدود الكلمات بحيث لا يتجاوز [max] حرفًا. */
    private fun splitForApi(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val cut = rest.lastIndexOf(' ', max).coerceAtLeast(1)
            out.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    private fun unescapeHtml(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
}
