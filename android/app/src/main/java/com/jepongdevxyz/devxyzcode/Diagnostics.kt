package com.jepongdevxyz.devxyzcode

/** v3: LSP-lite diagnostics — static checks that run on-device, no server. */
data class Diagnostic(val line: Int, val message: String, val severity: String)

object Diagnostics {
    fun diagnose(text: String): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        val lines = text.lines()
        var braceDepth = 0
        var parenDepth = 0
        var braceLine = -1
        var parenLine = -1
        lines.forEachIndexed { index, raw ->
            val lineNo = index + 1
            var inString = false
            var escaped = false
            var inLineComment = false
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (inLineComment) break
                if (!inString && c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                    inLineComment = true
                    break
                }
                if (c == '"' && !escaped) inString = !inString
                if (c == '\\' && inString) escaped = !escaped else escaped = false
                if (!inString) {
                    when (c) {
                        '{' -> { if (braceDepth == 0) braceLine = lineNo; braceDepth++ }
                        '}' -> {
                            braceDepth--
                            if (braceDepth < 0) {
                                out.add(Diagnostic(lineNo, "Extra closing brace '}' with no opener.", "error"))
                                braceDepth = 0
                            }
                        }
                        '(' -> { if (parenDepth == 0) parenLine = lineNo; parenDepth++ }
                        ')' -> {
                            parenDepth--
                            if (parenDepth < 0) {
                                out.add(Diagnostic(lineNo, "Extra closing paren ')' with no opener.", "error"))
                                parenDepth = 0
                            }
                        }
                    }
                }
                i++
            }
            if (inString) {
                out.add(Diagnostic(lineNo, "Unclosed string literal on this line.", "warning"))
            }
            if (raw != raw.trimEnd()) {
                out.add(Diagnostic(lineNo, "Trailing whitespace.", "info"))
            }
            if (raw.contains("TODO") || raw.contains("FIXME")) {
                out.add(Diagnostic(lineNo, "Marker: ${raw.trim().take(80)}", "info"))
            }
            if (raw.length > 200) {
                out.add(Diagnostic(lineNo, "Very long line (${raw.length} chars).", "info"))
            }
        }
        if (braceDepth > 0) {
            out.add(Diagnostic(braceLine.coerceAtLeast(1), "Unclosed '{' — $braceDepth brace(s) never closed.", "error"))
        }
        if (parenDepth > 0) {
            out.add(Diagnostic(parenLine.coerceAtLeast(1), "Unclosed '(' — $parenDepth paren(s) never closed.", "error"))
        }
        return out
    }
}
