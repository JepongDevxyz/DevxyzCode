package com.jepongdevxyz.devxyzcode

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * v3.2: announce on GitHub — post an Issue or publish a Release in any repo
 * the user can access. Auth is the user's own Personal Access Token (repo
 * scope), entered in Settings and stored encrypted — never hardcoded, never
 * sent anywhere except api.github.com. Nothing is posted without her tap.
 *
 * v3.9: Git tab — Status / Commit / Branch / PR through the GitHub Git Data
 * API, no git binary needed (a phone has none). A commit NEVER deletes:
 * deleted files are listed, never committed. Binary files are skipped.
 * Nothing commits, branches or opens a PR without her tap.
 */
object GitHubClient {
    data class PostResult(val url: String?, val error: String?)
    data class ConnectionResult(val login: String?, val error: String?)
    data class CommitResult(val url: String?, val sha: String?, val error: String?)

    /** v3.9: git's blob id for [bytes] — "blob <len>\0" + bytes, SHA-1, hex.
     * Used by the Git tab to compare a local file with the repo tree. */
    fun gitBlobSha(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(header)
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** v3.2: "Connect" — verify the token and show whose GitHub it is. */
    suspend fun testConnection(token: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "DevxyzCode")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                if (code in 200..299) {
                    val login = try {
                        JSONObject(text).optString("login", "")
                    } catch (e: Exception) {
                        ""
                    }
                    ConnectionResult(login.ifBlank { null }, null)
                } else {
                    val message = try {
                        JSONObject(text).optString("message", "")
                    } catch (e: Exception) {
                        ""
                    }
                    ConnectionResult(null, "GitHub $code${if (message.isNotBlank()) ": $message" else ""}")
                }
            } catch (e: Exception) {
                ConnectionResult(null, "Could not reach GitHub: ${e.message}")
            }
        }

    suspend fun postIssue(repo: String, title: String, body: String, token: String): PostResult {
        val payload = JSONObject().apply {
            put("title", title)
            put("body", body)
        }
        return post("https://api.github.com/repos/$repo/issues", payload.toString(), token)
    }

    suspend fun postRelease(
        repo: String,
        tag: String,
        name: String,
        body: String,
        token: String
    ): PostResult {
        val payload = JSONObject().apply {
            put("tag_name", tag)
            put("name", name)
            put("body", body)
            put("draft", false)
            put("prerelease", false)
        }
        return post("https://api.github.com/repos/$repo/releases", payload.toString(), token)
    }

    // ---- v3.9: Git tab (Git Data API) ----

    /** GET helper: returns (HTTP code, body text). Empty body on transport failure. */
    private suspend fun getText(url: String, token: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "DevxyzCode")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                try { connection.disconnect() } catch (_: Exception) { }
                code to text
            } catch (e: Exception) {
                0 to ""
            }
        }

    private fun githubError(code: Int, text: String): String {
        val message = try {
            JSONObject(text).optString("message", "")
        } catch (e: Exception) {
            ""
        }
        return if (code == 0) "Could not reach GitHub."
        else "GitHub $code${if (message.isNotBlank()) ": $message" else ""}"
    }

    /** Branch names on the repo (newest first is NOT guaranteed — the list
     * order is GitHub's). Null + error when the repo cannot be read. */
    suspend fun getBranches(repo: String, token: String): Pair<List<String>?, String?> {
        val (code, text) = getText("https://api.github.com/repos/$repo/branches?per_page=100", token)
        if (code !in 200..299) return null to githubError(code, text)
        return try {
            val names = mutableListOf<String>()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                names.add(arr.getJSONObject(i).optString("name", ""))
            }
            names.filter { it.isNotBlank() } to null
        } catch (e: Exception) {
            null to "Could not read branches: ${e.message}"
        }
    }

    /** The repo's default branch (e.g. "main"), or null + error. */
    suspend fun getDefaultBranch(repo: String, token: String): Pair<String?, String?> {
        val (code, text) = getText("https://api.github.com/repos/$repo", token)
        if (code !in 200..299) return null to githubError(code, text)
        return try {
            JSONObject(text).optString("default_branch", "").ifBlank { null } to null
        } catch (e: Exception) {
            null to "Could not read repo: ${e.message}"
        }
    }

    /** Head commit SHA of [branch], or null + error. */
    suspend fun getHeadSha(repo: String, branch: String, token: String): Pair<String?, String?> {
        val (code, text) = getText("https://api.github.com/repos/$repo/git/refs/heads/$branch", token)
        if (code !in 200..299) return null to githubError(code, text)
        return try {
            JSONObject(text).getJSONObject("object").optString("sha", "").ifBlank { null } to null
        } catch (e: Exception) {
            null to "Could not read branch head: ${e.message}"
        }
    }

    /** Full tree (path → blob SHA) of [branch]'s head commit. */
    suspend fun getBranchTree(repo: String, branch: String, token: String): Pair<Map<String, String>?, String?> {
        val (headSha, headError) = getHeadSha(repo, branch, token)
        if (headSha == null) return null to headError
        val (treeSha, treeError) = getCommitTreeSha(repo, headSha, token)
        if (treeSha == null) return null to treeError
        return getTree(repo, treeSha, token)
    }

    /** Base tree SHA of a commit, or null + error. */
    private suspend fun getCommitTreeSha(repo: String, commitSha: String, token: String): Pair<String?, String?> {
        val (code, text) = getText("https://api.github.com/repos/$repo/git/commits/$commitSha", token)
        if (code !in 200..299) return null to githubError(code, text)
        return try {
            JSONObject(text).getJSONObject("tree").optString("sha", "").ifBlank { null } to null
        } catch (e: Exception) {
            null to "Could not read commit: ${e.message}"
        }
    }

    /** Recursive tree of [treeSha]: relative path → blob SHA (blobs only). */
    suspend fun getTree(repo: String, treeSha: String, token: String): Pair<Map<String, String>?, String?> {
        val (code, text) = getText("https://api.github.com/repos/$repo/git/trees/$treeSha?recursive=1", token)
        if (code !in 200..299) return null to githubError(code, text)
        return try {
            val out = mutableMapOf<String, String>()
            val arr = JSONObject(text).optJSONArray("tree") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("type", "") == "blob") {
                    out[entry.optString("path", "")] = entry.optString("sha", "")
                }
            }
            out to null
        } catch (e: Exception) {
            null to "Could not read tree: ${e.message}"
        }
    }

    /** Commit [files] (relative path → full text) onto [branch].
     * Creates blobs → a tree on top of the branch head → a commit → moves
     * the branch ref. Never deletes: files missing locally are untouched. */
    suspend fun commitFiles(
        repo: String,
        branch: String,
        token: String,
        files: Map<String, String>,
        message: String
    ): CommitResult {
        val (headSha, headError) = getHeadSha(repo, branch, token)
        if (headSha == null) return CommitResult(null, null, headError)
        val (baseTreeSha, treeError) = getCommitTreeSha(repo, headSha, token)
        if (baseTreeSha == null) return CommitResult(null, null, treeError)
        return try {
            // 1. Blobs.
            val entries = JSONArray()
            for ((path, content) in files) {
                val blobBody = JSONObject().apply {
                    put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    put("encoding", "base64")
                }
                // The blobs endpoint returns {"sha": ...} with no html_url,
                // so post() cannot give us the sha — use the raw call.
                val (blobCode, blobText) = postRaw("https://api.github.com/repos/$repo/git/blobs", blobBody.toString(), token)
                if (blobCode !in 200..299) return CommitResult(null, null, githubError(blobCode, blobText))
                val sha = JSONObject(blobText).optString("sha", "")
                if (sha.isBlank()) return CommitResult(null, null, "GitHub did not return a blob id for $path.")
                entries.put(JSONObject().apply {
                    put("path", path)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", sha)
                })
            }
            // 2. Tree on top of the branch head.
            val treeBody = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", entries)
            }
            val (treeCode, treeText) = postRaw("https://api.github.com/repos/$repo/git/trees", treeBody.toString(), token)
            if (treeCode !in 200..299) return CommitResult(null, null, githubError(treeCode, treeText))
            val newTreeSha = JSONObject(treeText).optString("sha", "")
            if (newTreeSha.isBlank()) return CommitResult(null, null, "GitHub did not return a tree id.")
            // 3. Commit.
            val commitBody = JSONObject().apply {
                put("message", message)
                put("tree", newTreeSha)
                put("parents", JSONArray().apply { put(headSha) })
            }
            val (commitCode, commitText) = postRaw("https://api.github.com/repos/$repo/git/commits", commitBody.toString(), token)
            if (commitCode !in 200..299) return CommitResult(null, null, githubError(commitCode, commitText))
            val commitJson = JSONObject(commitText)
            val newCommitSha = commitJson.optString("sha", "")
            val commitUrl = commitJson.optString("html_url", "")
            if (newCommitSha.isBlank()) return CommitResult(null, null, "GitHub did not return a commit id.")
            // 4. Move the branch ref.
            val refBody = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }
            val (refCode, refText) = patchRaw("https://api.github.com/repos/$repo/git/refs/heads/$branch", refBody.toString(), token)
            if (refCode !in 200..299) return CommitResult(null, null, githubError(refCode, refText))
            CommitResult(commitUrl.ifBlank { null }, newCommitSha, null)
        } catch (e: Exception) {
            CommitResult(null, null, "Could not reach GitHub: ${e.message}")
        }
    }

    /** Create branch [name] at [fromSha]. */
    suspend fun createBranch(repo: String, token: String, name: String, fromSha: String): PostResult {
        val payload = JSONObject().apply {
            put("ref", "refs/heads/$name")
            put("sha", fromSha)
        }
        return post("https://api.github.com/repos/$repo/git/refs", payload.toString(), token)
    }

    /** Open a pull request from [head] into [base]. */
    suspend fun createPull(
        repo: String,
        token: String,
        head: String,
        base: String,
        title: String,
        body: String
    ): PostResult {
        val payload = JSONObject().apply {
            put("title", title)
            put("head", head)
            put("base", base)
            put("body", body)
        }
        return post("https://api.github.com/repos/$repo/pulls", payload.toString(), token)
    }

    private suspend fun post(url: String, json: String, token: String): PostResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "DevxyzCode")
                    doOutput = true
                }
                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                try { connection.disconnect() } catch (_: Exception) { }
                if (code in 200..299) {
                    val link = try {
                        JSONObject(text).optString("html_url", "")
                    } catch (e: Exception) {
                        ""
                    }
                    PostResult(link.ifBlank { null }, null)
                } else {
                    val message = try {
                        JSONObject(text).optString("message", "")
                    } catch (e: Exception) {
                        ""
                    }
                    PostResult(null, "GitHub $code${if (message.isNotBlank()) ": $message" else ""}")
                }
            } catch (e: Exception) {
                PostResult(null, "Could not reach GitHub: ${e.message}")
            }
        }

    /** POST that returns the raw (code, body) — for endpoints whose reply
     * has no html_url (blobs, trees, commits). */
    private suspend fun postRaw(url: String, json: String, token: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "DevxyzCode")
                    doOutput = true
                }
                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                try { connection.disconnect() } catch (_: Exception) { }
                code to text
            } catch (e: Exception) {
                0 to ""
            }
        }

    /** PATCH that returns the raw (code, body) — used to move a branch ref. */
    private suspend fun patchRaw(url: String, json: String, token: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "DevxyzCode")
                    doOutput = true
                }
                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                try { connection.disconnect() } catch (_: Exception) { }
                code to text
            } catch (e: Exception) {
                0 to ""
            }
        }
}
