package com.jepongdevxyz.devxyzcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** v3: OpenClaw/Hermes-style messaging gateway — Telegram Bot API (user's own bot). */
data class TelegramIncoming(
    val updateId: Long,
    val messageId: Long,
    val chatId: Long,
    val from: String,
    val text: String,
    val time: String
)

object TelegramClient {
    private const val API = "https://api.telegram.org"

    private fun get(path: String): String = runCatching {
        val conn = (URL("$API$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 30000
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) throw Exception("Telegram API error $code: ${text.take(200)}")
        text
    }.getOrElse { throw it }

    private fun post(path: String, body: JSONObject): String {
        val conn = (URL("$API$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) throw Exception("Telegram API error $code: ${text.take(200)}")
        return text
    }

    /** New messages since [offset] (exclusive). Returns messages + next offset. */
    suspend fun getUpdates(token: String, offset: Long): Pair<List<TelegramIncoming>, Long> =
        withContext(Dispatchers.IO) {
            val text = get("/bot$token/getUpdates?offset=$offset&timeout=0")
            val root = JSONObject(text)
            if (!root.optBoolean("ok", false)) {
                throw Exception("Telegram getUpdates failed: ${text.take(200)}")
            }
            val arr: JSONArray = root.optJSONArray("result") ?: JSONArray()
            val out = ArrayList<TelegramIncoming>()
            var nextOffset = offset
            for (i in 0 until arr.length()) {
                val upd = arr.optJSONObject(i) ?: continue
                val updateId = upd.optLong("update_id", 0L)
                nextOffset = maxOf(nextOffset, updateId + 1)
                val msg = upd.optJSONObject("message") ?: continue
                val chat = msg.optJSONObject("chat") ?: continue
                val body = msg.optString("text", "")
                if (body.isBlank()) continue
                val fromObj = msg.optJSONObject("from")
                val from = listOfNotNull(
                    fromObj?.optString("first_name", "")?.takeIf { it.isNotBlank() },
                    fromObj?.optString("username", "")?.takeIf { it.isNotBlank() }?.let { "@$it" }
                ).joinToString(" ").ifBlank { "Telegram" }
                out.add(
                    TelegramIncoming(
                        updateId = updateId,
                        messageId = msg.optLong("message_id", 0L),
                        chatId = chat.optLong("id", 0L),
                        from = from,
                        text = body,
                        time = java.text.SimpleDateFormat(
                            "MMM d, HH:mm", java.util.Locale.getDefault()
                        ).format(java.util.Date(msg.optLong("date", 0L) * 1000))
                    )
                )
            }
            out to nextOffset
        }

    /** Send a message. Only called after the user taps Send (approval-gated). */
    suspend fun sendMessage(token: String, chatId: Long, text: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val resp = JSONObject(post("/bot$token/sendMessage", body))
            resp.optBoolean("ok", false)
        }
}
