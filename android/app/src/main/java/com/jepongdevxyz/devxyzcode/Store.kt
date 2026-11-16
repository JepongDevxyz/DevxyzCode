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
}

object SettingsStore {
    private const val PREFS = "devxyzcode_settings"
    private const val KEY_BASE = "base_url"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_RUNS = "runs"

    fun loadBase(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE, AiClient.DEFAULT_BASE_URL) ?: AiClient.DEFAULT_BASE_URL

    fun loadApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""

    fun loadModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, AiClient.DEFAULT_MODELS[0]) ?: AiClient.DEFAULT_MODELS[0]

    fun save(context: Context, base: String, apiKey: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE, base.trim())
            .putString(KEY_API, apiKey.trim())
            .putString(KEY_MODEL, model)
            .apply()
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
}
