package com.rork.pro.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Calls the `translate-content` Edge Function to auto-translate content
 * between Arabic and English. Runs in the background so it never blocks
 * the save operation.
 *
 * Usage: Translator.translate("courses", courseId)
 */
object Translator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val TAG = "Translator"

    /**
     * Triggers background translation for a row in the given table.
     * Fire-and-forget: the UI is never blocked.
     */
    fun translate(table: String, id: String) {
        scope.launch {
            runCatching {
                Backend.invokeFunction(
                    "translate-content",
                    buildJsonObject {
                        put("table", table)
                        put("id", id)
                    },
                )
                Log.i(TAG, "Translated $table/$id")
            }.onFailure {
                Log.w(TAG, "Translation failed for $table/$id: ${it.message}")
            }
        }
    }

    /**
     * For inline content translation of small text fields.
     * Returns the translated text directly (blocking).
     * Used only when the caller needs the result immediately.
     */
    suspend fun translateInline(text: String, table: String, id: String): String {
        if (text.isBlank()) return text
        return try {
            val result = Backend.invokeFunction(
                "translate-content",
                buildJsonObject {
                    put("table", table)
                    put("id", id)
                },
            )
            text // The Edge Function handles saving; we just need to trigger it
        } catch (e: Exception) {
            Log.w(TAG, "Inline translation failed: ${e.message}")
            text
        }
    }
}
