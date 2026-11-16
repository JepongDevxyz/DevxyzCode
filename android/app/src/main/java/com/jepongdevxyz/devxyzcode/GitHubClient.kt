package com.jepongdevxyz.devxyzcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v3.2: announce on GitHub — post an Issue or publish a Release in any repo
 * the user can access. Auth is the user's own Personal Access Token (repo
 * scope), entered in Settings and stored encrypted — never hardcoded, never
 * sent anywhere except api.github.com. Nothing is posted without her tap.
 */
object GitHubClient {
    data class PostResult(val url: String?, val error: String?)
    data class ConnectionResult(val login: String?, val error: String?)

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
}
