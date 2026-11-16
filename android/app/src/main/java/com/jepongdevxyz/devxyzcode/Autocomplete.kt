package com.jepongdevxyz.devxyzcode

/** v3: Cursor-style autocomplete — words from the open file + language keywords. */
object Autocomplete {
    private val KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "if", "else", "when",
        "for", "while", "return", "import", "package", "private", "public",
        "override", "suspend", "data", "sealed", "enum", "true", "false", "null",
        "this", "super", "try", "catch", "finally", "throw", "is", "in", "as",
        "by", "lateinit", "const", "companion", "init", "do", "break", "continue",
        "def", "print", "function", "const", "let", "await", "async", "new",
        "switch", "case", "default", "extends", "implements", "static", "void",
        "int", "string", "boolean", "public", "protected", "from", "pass", "with",
        "elif", "except", "lambda", "global", "yield", "and", "or", "not", "None"
    )

    private val IDENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** All identifiers in the text, most frequent first. */
    fun identifiers(text: String): List<String> {
        val counts = LinkedHashMap<String, Int>()
        IDENT.findAll(text).forEach { m ->
            val w = m.value
            if (w.length >= 2) counts[w] = (counts[w] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** The word being typed at [cursor] in [text]. */
    fun currentWord(text: String, cursor: Int): String {
        if (text.isEmpty() || cursor <= 0) return ""
        val safe = cursor.coerceIn(0, text.length)
        var start = safe
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        return text.substring(start, safe)
    }

    /** Up to 8 suggestions for [prefix], file words first, then keywords. */
    fun suggest(text: String, prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val out = ArrayList<String>()
        identifiers(text).forEach {
            if (it.startsWith(prefix) && it != prefix) out.add(it)
        }
        KEYWORDS.forEach {
            if (it.startsWith(prefix) && it != prefix && !out.contains(it)) out.add(it)
        }
        return out.take(8)
    }
}
