package com.jepongdevxyz.devxyzcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(val output: String, val exitCode: Int)

object ShellRunner {
    private val BLOCKED = listOf(
        "rm -rf /",
        "rm -rf /*",
        "rm --no-preserve-root",
        "su",
        "reboot",
        "shutdown",
        "mkfs",
        "dd if="
    )

    fun blockedReason(command: String): String? {
        val hit = BLOCKED.firstOrNull { blocked ->
            val last = blocked.last()
            val after = when {
                last == '=' -> ""
                else -> "(?=$|[\\s;|&().])"
            }
            Regex("(?<=^|[\\s;|&()])" + Regex.escape(blocked) + after).containsMatchIn(command)
        } ?: return null
        return "Refused: \"$hit\" is not allowed in the sandboxed terminal."
    }

    suspend fun run(command: String, workDir: File): ShellResult = withContext(Dispatchers.IO) {
        blockedReason(command)?.let { return@withContext ShellResult(it, -1) }
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext ShellResult("Timed out after 30s", -1)
            }
            val raw = process.inputStream.bufferedReader().use { it.readText() }
            val output = when {
                raw.isBlank() -> "(no output)"
                raw.length > 20_000 -> raw.take(20_000) + "\n…[output truncated at 20,000 chars]"
                else -> raw
            }
            ShellResult(output, process.exitValue())
        } catch (e: Exception) {
            ShellResult("Could not run command: ${e.message}", -1)
        }
    }
}
