package com.adel.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OnlineAiClient {
    private const val BACKEND_URL = "https://adelassistant-backend.fastapicloud.dev"

    suspend fun chat(messages: List<Pair<String, String>>, memory: String = ""): String = withContext(Dispatchers.IO) {
        val url = URL("$BACKEND_URL/chat")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 95000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val messageArray = org.json.JSONArray()
            messages.takeLast(20).forEach { (role, content) ->
                messageArray.put(JSONObject().apply {
                    put("role", if (role == "assistant") "assistant" else "user")
                    put("content", content)
                })
            }
            val body = JSONObject().apply {
                put("messages", messageArray)
                put("memory", memory)
            }.toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "خطای سرور ($code)")
            }
            val text = JSONObject(response).optString("text")
            if (text.isBlank()) throw IllegalStateException("پاسخ خالی از سرور دریافت شد")
            text
        } finally {
            connection.disconnect()
        }
    }
}
