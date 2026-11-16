package com.jepongdevxyz.devxyzcode

enum class DiffKind { ADDED, REMOVED, UNCHANGED }

data class DiffLine(val kind: DiffKind, val text: String)

/**
 * Simple line diff (longest-common-subsequence) for the Apply preview.
 * v2 shows this BEFORE any file is changed, so Jepong can approve or discard.
 */
object DiffUtil {
    fun diff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val n = oldLines.size
        val m = newLines.size
        // Cap the DP table so a huge file cannot blow up memory.
        if (n * m > 400_000) {
            val out = mutableListOf<DiffLine>()
            oldLines.forEach { out.add(DiffLine(DiffKind.REMOVED, it)) }
            newLines.forEach { out.add(DiffLine(DiffKind.ADDED, it)) }
            return out
        }
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (oldLines[i] == newLines[j]) {
                out.add(DiffLine(DiffKind.UNCHANGED, oldLines[i]))
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(DiffLine(DiffKind.REMOVED, oldLines[i]))
                i++
            } else {
                out.add(DiffLine(DiffKind.ADDED, newLines[j]))
                j++
            }
        }
        while (i < n) {
            out.add(DiffLine(DiffKind.REMOVED, oldLines[i]))
            i++
        }
        while (j < m) {
            out.add(DiffLine(DiffKind.ADDED, newLines[j]))
            j++
        }
        return out
    }
}
