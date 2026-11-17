package com.jepongdevxyz.devxyzcode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AiMsg(val role: String, val content: String)

@Serializable
data class AiReq(val model: String, val messages: List<AiMsg>, val temperature: Double = 0.2, @SerialName("max_tokens") val maxTokens: Int? = null, @SerialName("top_p") val topP: Double? = null, @SerialName("top_k") val topK: Int? = null)

@Serializable
data class AiChoice(val message: AiMsg)

@Serializable
data class AiResp(val choices: List<AiChoice> = emptyList())

@Serializable
data class ChatMessage(val role: String, val content: String)

/** v3.3: one file from a multi-file plan — ```` ```lang:relative/path/File.ext ````. */
data class NamedCodeBlock(val filename: String, val language: String, val code: String)

object AiClient {
    val DEFAULT_BASE_URL = "https://api.apinex.bond/v1"
    val DEFAULT_MODELS = listOf(
        "Gemini 3.8 Flash",
        "GPT-6 Luna",
        "Kimi K3",
        "Muse Spark 1.3"
    )

    // v3.8: explicitNulls = false so an absent max_tokens is omitted from
    // the request (some providers reject an explicit null).
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        imageBase64: String? = null,
        // v3.8: effort selector (Low/Medium/High) maps to these.
        temperature: Double = 0.2,
        maxTokens: Int? = null,
        // v3.9: AI Studio Run-settings parity — top-p / top-k (null = default).
        topP: Double? = null,
        topK: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val url = URL("$cleanBase/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        // v3.7: make Stop real — a blocked read() on Dispatchers.IO does NOT
        // notice coroutine cancellation, so disconnect the socket when the
        // job is cancelled; the read then throws and is rethrown below as a
        // CancellationException, so the Agent shows "Stopped." at once
        // instead of waiting out the 120s read timeout.
        coroutineContext[Job]?.invokeOnCompletion {
            if (coroutineContext[Job]?.isCancelled == true) {
                try { conn.disconnect() } catch (_: Exception) { }
            }
        }
        try {
        val msgs = mutableListOf(AiMsg("system", systemPrompt))
        history.forEach { msgs.add(AiMsg(it.role, it.content)) }
        // v3.3: vision — when an image is attached, the newest user message
        // carries it as an OpenAI-style image_url part (Gemini models read it).
        val body = if (imageBase64.isNullOrBlank()) {
            json.encodeToString(AiReq(model = model, messages = msgs, temperature = temperature, maxTokens = maxTokens, topP = topP, topK = topK))
        } else {
            val messagesJson = JSONArray()
            msgs.forEachIndexed { index, msg ->
                val obj = JSONObject()
                obj.put("role", msg.role)
                if (index == msgs.lastIndex && msg.role == "user") {
                    val parts = JSONArray()
                    val textPart = JSONObject()
                    textPart.put("type", "text")
                    textPart.put("text", msg.content)
                    parts.put(textPart)
                    val imagePart = JSONObject()
                    imagePart.put("type", "image_url")
                    val imageUrl = JSONObject()
                    imageUrl.put("url", "data:image/jpeg;base64,$imageBase64")
                    imagePart.put("image_url", imageUrl)
                    parts.put(imagePart)
                    obj.put("content", parts)
                } else {
                    obj.put("content", msg.content)
                }
                messagesJson.put(obj)
            }
            JSONObject()
                .put("model", model)
                .put("temperature", temperature)
                .apply { if (maxTokens != null) put("max_tokens", maxTokens) }
                .apply { if (topP != null) put("top_p", topP) }
                .apply { if (topK != null) put("top_k", topK) }
                .put("messages", messagesJson)
                .toString()
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) {
            throw Exception("API error $code: ${text.take(300)}")
        }
        val resp = json.decodeFromString<AiResp>(text)
        resp.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty reply from model.")
        } catch (e: Exception) {
            if (coroutineContext[Job]?.isCancelled == true) throw CancellationException("Stopped.")
            throw e
        } finally {
            try { conn.disconnect() } catch (_: Exception) { }
        }
    }

    /** First fenced code block in a reply, without the fences. Null when none. */
    fun extractCodeBlock(reply: String): String? {
        val start = reply.indexOf("```")
        if (start < 0) return null
        val afterFirst = reply.indexOf('\n', start)
        if (afterFirst < 0) return null
        val end = reply.indexOf("```", afterFirst + 1)
        if (end < 0) return null
        return reply.substring(afterFirst + 1, end).trimEnd()
    }

    /** First fenced ```sh / ```bash block in a reply. Null when none. */
    fun extractShellBlock(reply: String): String? {
        val markers = listOf("```sh", "```bash", "```shell")
        for (marker in markers) {
            val start = reply.indexOf(marker)
            if (start < 0) continue
            val afterFirst = reply.indexOf('\n', start)
            if (afterFirst < 0) continue
            val end = reply.indexOf("```", afterFirst + 1)
            if (end < 0) continue
            return reply.substring(afterFirst + 1, end).trim()
        }
        return null
    }

    /** v3.3: every ```lang:relative/path/File.ext block in a reply — the
     * multi-file plan format. A block without ":filename" is not a plan
     * file (the single-block flow still handles it). */
    fun extractNamedCodeBlocks(reply: String): List<NamedCodeBlock> {
        val pattern = Regex("""```([A-Za-z0-9_+#.-]*):([^\n`]+)\n([\s\S]*?)```""")
        return pattern.findAll(reply).mapNotNull { match ->
            val language = match.groupValues[1].ifBlank { "txt" }
            val filename = match.groupValues[2].trim()
            val code = match.groupValues[3].trim('\n')
            if (filename.isBlank() || code.isBlank()) null
            else NamedCodeBlock(filename, language, code)
        }.toList()
    }
}
