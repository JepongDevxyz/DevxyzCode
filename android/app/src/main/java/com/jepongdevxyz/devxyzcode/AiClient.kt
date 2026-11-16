package com.jepongdevxyz.devxyzcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AiMsg(val role: String, val content: String)

@Serializable
data class AiReq(val model: String, val messages: List<AiMsg>, val temperature: Double = 0.2)

@Serializable
data class AiChoice(val message: AiMsg)

@Serializable
data class AiResp(val choices: List<AiChoice> = emptyList())

@Serializable
data class ChatMessage(val role: String, val content: String)

object AiClient {
    val DEFAULT_BASE_URL = "https://api.apinex.bond/v1"
    val DEFAULT_MODELS = listOf(
        "Gemini 3.8 Flash",
        "GPT-6 Luna",
        "Kimi K3",
        "Muse Spark 1.3"
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String
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
        val msgs = mutableListOf(AiMsg("system", systemPrompt))
        history.forEach { msgs.add(AiMsg(it.role, it.content)) }
        val body = json.encodeToString(AiReq(model = model, messages = msgs))
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            throw Exception("API error $code: ${text.take(300)}")
        }
        val resp = json.decodeFromString<AiResp>(text)
        resp.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty reply from model.")
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
}
