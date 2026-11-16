package com.jepongdevxyz.devxyzcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProjectFile(
    val name: String,
    val relativePath: String,
    val uriString: String,
    val isDir: Boolean,
    val size: Long = 0L
)

@Serializable
data class RunRecord(
    val name: String,
    val result: String,
    val note: String,
    val time: String
)

/** OpenClaw-style reusable Skill (SKILL.md equivalent, stored on-device). */
@Serializable
data class SkillRecord(
    val name: String,
    val description: String,
    val instructions: String
)

/** Hermes-style persistent memory fact, loaded into every chat. */
@Serializable
data class MemoryFact(val text: String, val time: String)

/** OpenClaw Automations / Hermes scheduled job. v3: runs in the background
 * via WorkManager every [intervalHours]; [lastRunAtMillis] is epoch ms of the
 * last run (0 = never run — the next worker cycle runs it once). */
@Serializable
data class ScheduledTask(
    val name: String,
    val whenText: String,
    val prompt: String,
    val intervalHours: Int = 24,
    val lastRunAtMillis: Long = 0L
)

/** v3: one Agent session (parallel subagent-lite — each has its own history). */
@Serializable
data class SessionRecord(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList()
)

/** v3: a Telegram message received through the user's own bot. */
@Serializable
data class TelegramMessage(
    val chatId: Long,
    val from: String,
    val text: String,
    val time: String
)

/** v3: the saved result of a scheduled task run (foreground or background). */
@Serializable
data class TaskResult(
    val taskName: String,
    val result: String,
    val time: String
)

object ProjectStore {
    private val json = Json { ignoreUnknownKeys = true }
    const val MAX_LIST = 500
    const val MAX_READ_BYTES = 200 * 1024

    fun listFiles(context: Context, treeUri: Uri): List<ProjectFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<ProjectFile>()
        fun walk(dir: DocumentFile, prefix: String) {
            if (out.size >= MAX_LIST) return
            val children = try { dir.listFiles() } catch (e: Exception) { return }
            val sorted = children.sortedWith(
                compareBy({ !it.isDirectory }, { it.name ?: "" })
            )
            for (child in sorted) {
                if (out.size >= MAX_LIST) return
                val rel = if (prefix.isEmpty()) (child.name ?: "") else "$prefix/${child.name}"
                out.add(
                    ProjectFile(
                        name = child.name ?: "",
                        relativePath = rel,
                        uriString = child.uri.toString(),
                        isDir = child.isDirectory,
                        size = if (child.isFile) child.length() else 0L
                    )
                )
                if (child.isDirectory) walk(child, rel)
            }
        }
        walk(root, "")
        return out
    }

    fun readText(context: Context, uriString: String): String {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_READ_BYTES) {
                return String(bytes.copyOf(MAX_READ_BYTES), Charsets.UTF_8) +
                    "\n\n[DevxyzCode: file truncated at 200 KB in v1]"
            }
            return String(bytes, Charsets.UTF_8)
        }
        throw Exception("Could not open file.")
    }

    fun writeText(context: Context, uriString: String, text: String) {
        val uri = Uri.parse(uriString)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw Exception("Could not save file.")
    }

    fun takePermission(context: Context, treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            // Permission may already be held; opening still works for the session.
        }
    }

    /** v3.3: create a file (and any missing parent folders) inside the picked
     * project folder, then write [text] into it. Used by multi-file Apply
     * for brand-new files from a plan / a scaffold. */
    fun createFile(context: Context, treeUri: Uri, relativePath: String, text: String): ProjectFile {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw Exception("No project folder picked.")
        var dir = root
        val parts = relativePath.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw Exception("Empty file path.")
        for (part in parts.dropLast(1)) {
            dir = dir.findFile(part)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(part)
                ?: throw Exception("Could not create folder $part.")
        }
        val name = parts.last()
        val existing = dir.findFile(name)
        val file = if (existing?.isFile == true) existing
        else dir.createFile("text/plain", name)
            ?: throw Exception("Could not create file $name.")
        writeText(context, file.uri.toString(), text)
        return ProjectFile(
            name = name,
            relativePath = relativePath,
            uriString = file.uri.toString(),
            isDir = false,
            size = text.toByteArray(Charsets.UTF_8).size.toLong()
        )
    }
}

/**
 * v2: the API key lives in EncryptedSharedPreferences. If encryption is
 * unavailable on a device, it falls back to private prefs (never a crash),
 * and a key saved by v1 in plain prefs is migrated on first read.
 */
object SecureKeyStore {
    private const val SECURE_PREFS = "devxyzcode_secure"
    private const val KEY_API = "api_key"
    private const val KEY_BOT = "telegram_bot_token"
    private const val KEY_GITHUB = "github_token"

    private fun securePrefs(context: Context) =
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun load(context: Context): String {
        return try {
            securePrefs(context).getString(KEY_API, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun save(context: Context, apiKey: String) {
        try {
            securePrefs(context).edit().putString(KEY_API, apiKey.trim()).apply()
        } catch (e: Exception) {
            // Fallback is handled by SettingsStore (plain private prefs).
            throw e
        }
    }

    /** v3: Telegram bot token, encrypted like the API key. */
    fun loadBotToken(context: Context): String {
        return try {
            securePrefs(context).getString(KEY_BOT, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun saveBotToken(context: Context, token: String) {
        try {
            securePrefs(context).edit().putString(KEY_BOT, token.trim()).apply()
        } catch (e: Exception) {
            context.getSharedPreferences("devxyzcode_settings", Context.MODE_PRIVATE).edit()
                .putString("telegram_bot_token_plain", token.trim())
                .apply()
        }
    }

    /** v3.2: GitHub Personal Access Token (repo scope) for Announce. */
    fun loadGithubToken(context: Context): String {
        return try {
            securePrefs(context).getString(KEY_GITHUB, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun saveGithubToken(context: Context, token: String) {
        try {
            securePrefs(context).edit().putString(KEY_GITHUB, token.trim()).apply()
        } catch (e: Exception) {
            context.getSharedPreferences("devxyzcode_settings", Context.MODE_PRIVATE).edit()
                .putString("github_token_plain", token.trim())
                .apply()
        }
    }
}

object SettingsStore {
    private const val PREFS = "devxyzcode_settings"
    private const val KEY_BASE = "base_url"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_RUNS = "runs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_APPROVAL_MODE = "approval_mode"

    /** v3.4: "system" (follows the phone), "dark", or "light". */
    fun loadThemeMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "system") ?: "system"

    fun saveThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_THEME, mode)
            .apply()
    }

    /** v3.5 (Codex-style approval modes): "suggest" (default — code is
     * proposed, she taps Preview / Apply all) or "auto" (single-file code
     * goes straight to the editor; disk writes still need her tap). */
    fun loadApprovalMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPROVAL_MODE, "suggest") ?: "suggest"

    fun saveApprovalMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APPROVAL_MODE, mode)
            .apply()
    }

    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    /** v3.6: first-run onboarding — false until the user finishes (or skips)
     * the welcome flow. Existing users already have the flag unset, so they
     * see onboarding once after updating; their saved keys are kept. */
    fun loadOnboardingDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DONE, false)

    fun saveOnboardingDone(context: Context, done: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDING_DONE, done)
            .apply()
    }

    fun loadBase(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE, AiClient.DEFAULT_BASE_URL) ?: AiClient.DEFAULT_BASE_URL

    fun loadApiKey(context: Context): String {
        val secure = SecureKeyStore.load(context)
        if (secure.isNotBlank()) return secure
        // v1 legacy: plain prefs. Migrate it into the secure store once.
        val legacy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""
        if (legacy.isNotBlank()) {
            try {
                SecureKeyStore.save(context, legacy)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_API).apply()
            } catch (e: Exception) {
                // Keep the legacy value readable; do not lose the key.
            }
        }
        return legacy
    }

    fun loadModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, AiClient.DEFAULT_MODELS[0]) ?: AiClient.DEFAULT_MODELS[0]

    fun save(context: Context, base: String, apiKey: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE, base.trim())
            .putString(KEY_MODEL, model)
            .apply()
        try {
            SecureKeyStore.save(context, apiKey)
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_API, apiKey.trim())
                .apply()
        }
    }

    fun loadRuns(context: Context): List<RunRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RUNS, "[]") ?: "[]"
        return try {
            Json.decodeFromString<List<RunRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRuns(context: Context, runs: List<RunRecord>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RUNS, Json.encodeToString(runs))
            .apply()
    }

    private const val KEY_SKILLS = "skills"
    private const val KEY_MEMORY = "memory_facts"
    private const val KEY_TASKS = "scheduled_tasks"

    private inline fun <reified T> loadList(context: Context, key: String): List<T> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"
        return try {
            Json.decodeFromString<List<T>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T> saveList(context: Context, key: String, items: List<T>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, Json.encodeToString(items))
            .apply()
    }

    fun loadSkills(context: Context): List<SkillRecord> = loadList(context, KEY_SKILLS)
    fun saveSkills(context: Context, items: List<SkillRecord>) = saveList(context, KEY_SKILLS, items)
    fun loadMemory(context: Context): List<MemoryFact> = loadList(context, KEY_MEMORY)
    fun saveMemory(context: Context, items: List<MemoryFact>) = saveList(context, KEY_MEMORY, items)
    fun loadTasks(context: Context): List<ScheduledTask> = loadList(context, KEY_TASKS)
    fun saveTasks(context: Context, items: List<ScheduledTask>) = saveList(context, KEY_TASKS, items)

    // v3: Agent sessions, Telegram messages, task results, bot token, auto-Skill.
    private const val KEY_SESSIONS = "agent_sessions"
    private const val KEY_TELEGRAM = "telegram_messages"
    private const val KEY_TASK_RESULTS = "task_results"
    private const val KEY_TELEGRAM_OFFSET = "telegram_offset"
    private const val KEY_AUTO_SKILL = "auto_save_skills"

    fun loadSessions(context: Context): List<SessionRecord> = loadList(context, KEY_SESSIONS)
    fun saveSessions(context: Context, items: List<SessionRecord>) = saveList(context, KEY_SESSIONS, items)
    fun loadTelegramMessages(context: Context): List<TelegramMessage> = loadList(context, KEY_TELEGRAM)
    fun saveTelegramMessages(context: Context, items: List<TelegramMessage>) = saveList(context, KEY_TELEGRAM, items)
    fun loadTaskResults(context: Context): List<TaskResult> = loadList(context, KEY_TASK_RESULTS)
    fun saveTaskResults(context: Context, items: List<TaskResult>) = saveList(context, KEY_TASK_RESULTS, items)

    fun loadTelegramOffset(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TELEGRAM_OFFSET, 0L)

    fun saveTelegramOffset(context: Context, offset: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TELEGRAM_OFFSET, offset)
            .apply()
    }

    fun loadBotToken(context: Context): String {
        val secure = SecureKeyStore.loadBotToken(context)
        if (secure.isNotBlank()) return secure
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("telegram_bot_token_plain", "") ?: ""
    }

    fun saveBotToken(context: Context, token: String) {
        SecureKeyStore.saveBotToken(context, token)
    }

    /** Hermes self-improvement lite. Default OFF (her rule: toggles off first run). */
    fun loadAutoSaveSkills(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SKILL, false)

    fun saveAutoSaveSkills(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_SKILL, enabled)
            .apply()
    }

    // v3.2: GitHub announce — token encrypted, repo is a plain setting.
    private const val KEY_ANNOUNCE_REPO = "announce_repo"

    fun loadGithubToken(context: Context): String {
        val secure = SecureKeyStore.loadGithubToken(context)
        if (secure.isNotBlank()) return secure
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("github_token_plain", "") ?: ""
    }

    fun saveGithubToken(context: Context, token: String) {
        SecureKeyStore.saveGithubToken(context, token)
    }

    fun loadAnnounceRepo(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // v3.6: blank by default — the old default was her own repo,
            // which must not ship as the default to public users.
            .getString(KEY_ANNOUNCE_REPO, "")
            ?: ""

    fun saveAnnounceRepo(context: Context, repo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ANNOUNCE_REPO, repo.trim())
            .apply()
    }
}
