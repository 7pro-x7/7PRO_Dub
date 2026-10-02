package com.rork.pro.ui.screens.admin

/**
 * Precise, forgiving text search shared by admin lists.
 *
 * Both sides are normalised the same way (case, Arabic diacritics/tatweel, alef/yaa/taa-marbuta
 * variants, Arabic-Indic digits, emoji and punctuation) and every word the admin types must be
 * found, in any order. Nothing fuzzy: a word either appears or the row is hidden.
 */
object SearchMatch {

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (raw in input.lowercase()) {
            val c = when (raw) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى', 'ئ' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                in '٠'..'٩' -> '0' + (raw - '٠')
                in '۰'..'۹' -> '0' + (raw - '۰')
                else -> raw
            }
            when {
                c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640' -> Unit // diacritics, tatweel
                c.isLetterOrDigit() -> sb.append(c)
                else -> if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /** True when every word of [query] appears somewhere in [fields]. A blank query matches everything. */
    fun matches(query: String, vararg fields: String?): Boolean {
        val words = tokens(query)
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { haystack.contains(it) }
    }

    /** Higher = better. Used to float an exact / prefix hit on the primary field above loose matches. */
    fun rank(query: String, primary: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val p = normalize(primary)
        return when {
            p == q -> 3
            p.startsWith(q) -> 2
            p.contains(q) -> 1
            else -> 0
        }
    }
}
