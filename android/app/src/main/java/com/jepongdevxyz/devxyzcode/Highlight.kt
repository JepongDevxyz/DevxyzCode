package com.jepongdevxyz.devxyzcode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Tiny keyword/string/comment highlighter for the read-only preview.
 * v2: Kotlin / Java / Python / JavaScript / generic C-like code.
 * The editor itself stays plain text; this preview is what is coloured.
 */
object Highlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "import", "package",
        "if", "else", "when", "for", "while", "return", "try", "catch", "finally",
        "throw", "private", "public", "internal", "protected", "override", "open",
        "abstract", "data", "sealed", "enum", "companion", "init", "this", "super",
        "true", "false", "null", "def", "print", "const", "let", "function",
        "new", "switch", "case", "break", "continue", "extends", "implements",
        "static", "void", "int", "string", "boolean", "async", "await"
    )

    private val keywordColor = Color(0xFF7DD3FC)
    private val stringColor = Color(0xFF86EFAC)
    private val commentColor = Color(0xFF64748B)
    private val numberColor = Color(0xFFFBBF24)

    fun highlight(code: String): AnnotatedString {
        val text = code.lines().take(200).joinToString("\n")
        return buildAnnotatedString {
            append(text)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        addStyle(SpanStyle(color = commentColor), i, end)
                        i = end
                    }
                    c == '#' -> {
                        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        addStyle(SpanStyle(color = commentColor), i, end)
                        i = end
                    }
                    c == '"' || c == '\'' -> {
                        val end = text.indexOf(c, i + 1).let { if (it < 0) text.length else it + 1 }
                        addStyle(SpanStyle(color = stringColor), i, end)
                        i = end
                    }
                    c.isDigit() -> {
                        var j = i
                        while (j < text.length && (text[j].isDigit() || text[j] == '.')) j++
                        addStyle(SpanStyle(color = numberColor), i, j)
                        i = j
                    }
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                        val word = text.substring(i, j)
                        if (word in keywords) {
                            addStyle(
                                SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                                i, j
                            )
                        }
                        i = j
                    }
                    else -> i++
                }
            }
        }
    }
}
